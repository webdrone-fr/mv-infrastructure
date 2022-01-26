package org.meveo.gandi;

import java.util.Map;
import java.time.OffsetDateTime;

import org.meveo.service.script.Script;
import org.meveo.admin.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.*;
import javax.ws.rs.core.*;
import com.google.gson.*;

import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.DomainName;
import org.meveo.service.storage.RepositoryService;
import org.meveo.model.storage.Repository;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;

public class ListDomains extends Script {

	
    private static final Logger log = LoggerFactory.getLogger(ListDomains.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
	private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

	static final private String GANDI_URL = "api.gandi.net/v5/";

	@Override
	public void execute(Map<String, Object> parameters) throws BusinessException {
		Credential credential  = CredentialHelperService.getCredential(GANDI_URL,crossStorageApi,defaultRepo);
      	if(credential==null){
        	throw new BusinessException("No credential found for "+GANDI_URL);
      	} else {
        	log.info("using credential {} with username {}",credential.getUuid(),credential.getUsername());
      	}
		Client client = ClientBuilder.newClient();
		client.register(new CredentialHelperService.LoggingFilter());
		WebTarget target = client.target("https://api.gandi.net/v5/domain/domains");
		Response response = CredentialHelperService.setCredential(target.request(),credential).get();
		String value = response.readEntity(String.class);
		log.info("response  :" + value);
		log.debug("response status : {}", response.getStatus());
		if (response.getStatus() < 300) {
			JsonArray rootArray = new JsonParser().parse(value).getAsJsonArray();
			for (JsonElement element : rootArray) {
				JsonObject serverObj = element.getAsJsonObject();
				DomainName domainName = new DomainName();
				domainName.setRegistar("GANDI");
				domainName.setUuid(serverObj.get("id").getAsString());
				domainName.setName(serverObj.get("fqdn_unicode").getAsString());
				domainName.setNormedName(serverObj.get("fqdn").getAsString());
				JsonObject dates = serverObj.get("dates").getAsJsonObject();
				domainName.setCreationDate(OffsetDateTime.parse(dates.get("created_at").getAsString()).toInstant());
				domainName.setRegistrationDate(OffsetDateTime.parse(dates.get("registry_created_at").getAsString()).toInstant());
				domainName.setRegistrationEndDate(OffsetDateTime.parse(dates.get("registry_ends_at").getAsString()).toInstant());
				domainName.setLastUpdate(OffsetDateTime.parse(dates.get("updated_at").getAsString()).toInstant());
				domainName.setTld(serverObj.get("tld").getAsString());
				domainName.setAutoRenew(serverObj.get("autorenew").getAsBoolean());
				log.info("domaine name:{}", domainName.getName());
				try {
					crossStorageApi.createOrUpdate(defaultRepo, domainName);
				} catch (Exception ex) {
					log.error("error creating domainName {} :{}", domainName.getUuid(), ex.getMessage());
				}
			}
		}
	}

}