package org.meveo.gandi;

import java.util.Map;
import java.time.OffsetDateTime;
import java.time.Instant;

import org.meveo.service.script.Script;
import org.meveo.admin.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.*;
import javax.ws.rs.core.*;
import com.google.gson.*;

import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.DomainName;
import org.meveo.model.customEntities.DnsRecord;
import org.meveo.service.storage.RepositoryService;
import org.meveo.model.storage.Repository;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.persistence.CEIUtils;
import org.apache.commons.codec.digest.DigestUtils;

public class ListDnsRecords extends Script {

	
    private static final Logger log = LoggerFactory.getLogger(ListDomains.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
	private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

	static final private String GANDI_URL = "api.gandi.net/v5/";

	@Override
	public void execute(Map<String, Object> parameters) throws BusinessException {
		DomainName domainName =  CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get("CONTEXT_ENTITY"), DomainName.class);
		if(domainName.getNormedName()==null || domainName.getNormedName().isEmpty()){
			throw new BusinessException("invalid normalized domain name");
		}
		Credential credential  = CredentialHelperService.getCredential(GANDI_URL,crossStorageApi,defaultRepo);
      	if(credential==null){
        	throw new BusinessException("No credential found for "+GANDI_URL);
      	} else {
        	log.info("using credential {} with username {}",credential.getUuid(),credential.getUsername());
      	}
		Client client = ClientBuilder.newClient();
		client.register(new CredentialHelperService.LoggingFilter());
		WebTarget target = client.target("https://api.gandi.net/v5/livedns/domains/"+domainName.getNormedName()+"/records");
		Response response = CredentialHelperService.setCredential(target.request(),credential).get();
		String value = response.readEntity(String.class);
		log.info("response  :" + value);
		log.debug("response status : {}", response.getStatus());
		if (response.getStatus() < 300) {
			JsonArray rootArray = new JsonParser().parse(value).getAsJsonArray();
			for (JsonElement element : rootArray) {
				JsonObject serverObj = element.getAsJsonObject();
				DnsRecord record = new DnsRecord();
				record.setDomainName(domainName);
				String type = serverObj.get("rrset_type").getAsString();
				if("A".equals(type)||"CNAME".equals(type)){
					record.setRecordType(type);
					record.setTtl(serverObj.get("rrset_ttl").getAsLong());
					record.setName(serverObj.get("rrset_name").getAsString());
					JsonArray values = serverObj.get("rrset_values").getAsJsonArray();
					if(values.size()==1){
						record.setValue(values.get(0).getAsString());
						record.setLastSyncDate(Instant.now());
                        String ukey=domainName.getNormedName()+"-"+record.getRecordType()+"-"+record.getName();
                		record.setUuid(DigestUtils.md5Hex(ukey));
                        log.info("record :{} {} {}", record.getRecordType(),record.getName(),record.getValue());
                        try {
                            crossStorageApi.createOrUpdate(defaultRepo, record);
                        } catch (Exception ex) {
                            log.error("error creating record {} :{}", record.getUuid(), ex.getMessage());
                        }
					}
					//TODO notify of non imported records
				}
			}
		}
	}

}