package org.meveo.cloudflare;

import java.time.OffsetDateTime;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import com.google.gson.*;

import org.apache.commons.lang3.StringUtils;
import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.DomainName;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListCloudflareDomains extends Script {
    
    private static final Logger logger = LoggerFactory.getLogger(ListCloudflareDomains.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private String CLOUDFLARE_URL = "api.cloudflare.com/client/v4";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        ServiceProvider registrar = crossStorageApi.find(defaultRepo, ServiceProvider.class).by("code", "CLOUDFLARE").getResult();

        Credential credential = CredentialHelperService.getCredential(CLOUDFLARE_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+CLOUDFLARE_URL);
        } else {
            logger.info("using credential {} with username {}", credential.getUuid(), credential.getUsername());
        }
        
        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        // Cloudflare Zone: A Zone is a domain name along with its subdomains and other identities
        WebTarget target = client.target("https://"+CLOUDFLARE_URL+"/zones");
        Response response = CredentialHelperService.setCredential(target.request(), credential).get();
        String value = response.readEntity(String.class);
        logger.info("response : " + value);
        logger.debug("response status : {}", response.getStatus());

        if (response.getStatus() < 300) {
            JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().get("result").getAsJsonArray();
            for (JsonElement element : rootArray) {
                JsonObject serverObj = element.getAsJsonObject();
                DomainName domainName = new DomainName();
                domainName.setRegistrar(registrar);
                domainName.setUuid(serverObj.get("id").getAsString());
                domainName.setName(serverObj.get("name").getAsString());
                domainName.setCreationDate(OffsetDateTime.parse(serverObj.get("created_on").getAsString()).toInstant());
                domainName.setRegistrationDate(OffsetDateTime.parse(serverObj.get("activated_on").getAsString()).toInstant());
                domainName.setLastUpdate(OffsetDateTime.parse(serverObj.get("modified_on").getAsString()).toInstant());
                String tld = StringUtils.split(serverObj.get("name").getAsString(), ".")[1];
                domainName.setTld(tld);
                try {
                    crossStorageApi.createOrUpdate(defaultRepo, domainName);
                    logger.info("Domain : {} successfully created", domainName.getName());
                } catch (Exception e) {
                    logger.error("Error creating domainName {} : {}", domainName.getUuid(), e.getMessage());
                }
            }
            response.close();
        }
    }
}