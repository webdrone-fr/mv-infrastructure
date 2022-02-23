package org.meveo.gandi;

import java.time.Instant;
import java.util.Map;

import org.meveo.service.script.Script;
import org.meveo.admin.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.*;
import javax.ws.rs.core.*;

import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.DomainName;
import org.meveo.model.customEntities.DnsRecord;
import org.meveo.service.storage.RepositoryService;
import org.meveo.model.storage.Repository;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.persistence.CEIUtils;


public class CreateDnsRecord extends Script {
	
    private static final Logger log = LoggerFactory.getLogger(ListDomains.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
	private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

	static final private String GANDI_URL = "api.gandi.net/v5/";

	@Override
	public void execute(Map<String, Object> parameters) throws BusinessException {
		String action= (String)parameters.get("CONTEXT_ACTION");
		DnsRecord record =  CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get("CONTEXT_ENTITY"), DnsRecord.class);
		if(record.getDomainName()==null || record.getRecordType()==null || record.getName()==null 
		|| record.getName().isEmpty() || record.getValue()==null || record.getValue()==null){
			throw new BusinessException("invalid record");
		}
		DomainName domainName = record.getDomainName();
		log.info("action:{}, domain name uuid:{}",action,domainName.getUuid());
		Credential credential  = CredentialHelperService.getCredential(GANDI_URL,crossStorageApi,defaultRepo);
      	if(credential==null){
        	throw new BusinessException("No credential found for "+GANDI_URL);
      	} else {
        	log.info("using credential {} with username {}",credential.getUuid(),credential.getUsername());
      	}
		Client client = ClientBuilder.newClient();
		client.register(new CredentialHelperService.LoggingFilter());
		WebTarget target = client.target("https://api.gandi.net/v5/livedns/domains/"+domainName.getNormedName()+"/records");
		String resp = "{\n"
		+"\"rrset_type\": \""+record.getRecordType()+"\",\n"
		+"\"rrset_name\": \""+record.getName()+"\",\n"
		+"\"rrset_values\": [\""+record.getValue()+"\"]\n"
		+"}";
		
		Response response = null;
		if(record.getLastSyncDate()==null){
			response = CredentialHelperService.setCredential(target.request(),credential).post(Entity.json(resp));
		} else {
			response = CredentialHelperService.setCredential(target.request(),credential).put(Entity.json("{\"items\":["+resp+"]}"));
		}
		String value = response.readEntity(String.class);
		log.info("response  :" + value);
		log.debug("response status : {}", response.getStatus());
		parameters.put("RESULT_GUI_MESSAGE", "Status: "+response.getStatus()+", response:"+value);
		if(response.getStatus()==201){
			record.setLastSyncDate(Instant.now());
			try {
				crossStorageApi.createOrUpdate(defaultRepo, record);
			} catch (Exception ex) {
				log.error("error updating lastSyncDate record {} :{}", record.getUuid(), ex.getMessage());
			}
		}
	}

}