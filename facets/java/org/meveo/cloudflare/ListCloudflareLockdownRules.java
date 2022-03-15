package org.meveo.cloudflare;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import com.google.gson.*;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.DomainName;
import org.meveo.model.customEntities.LockdownRule;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListCloudflareLockdownRules extends Script{


    private static final Logger logger = LoggerFactory.getLogger(ListCloudflareLockdownRules.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private String CLOUDFLARE_URL = "api.cloudflare.com/client/v4";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        DomainName domainName = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), DomainName.class);

        Credential credential = CredentialHelperService.getCredential(CLOUDFLARE_URL, crossStorageApi, defaultRepo);
        if (credential==null) {
            throw new BusinessException("No credential found for "+CLOUDFLARE_URL);
        } else {
            logger.info("using credential {} with username {}",credential.getUuid(), credential.getUsername());
        }

        String domainNameId = domainName.getUuid();
        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+CLOUDFLARE_URL+"/zones/"+domainNameId+"/firewall/lockdowns");
        Response response = CredentialHelperService.setCredential(target.request(), credential).get();
        String value = response.readEntity(String.class);
        logger.info("response :", value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response: "+value);
        
        if (response.getStatus()<300) {
            JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().get("result").getAsJsonArray();
            for (JsonElement element : rootArray) {
                JsonObject lockdownRuleObj = element.getAsJsonObject();
                LockdownRule lockdownRule = new LockdownRule();

                lockdownRule.setCreationDate(OffsetDateTime.parse(lockdownRuleObj.get("created_on").getAsString()).toInstant());
                lockdownRule.setLastUpdated(OffsetDateTime.parse(lockdownRuleObj.get("modified_on").getAsString()).toInstant());
                lockdownRule.setProviderSideId(lockdownRuleObj.get("id").getAsString());
                lockdownRule.setUuid(lockdownRuleObj.get("id").getAsString());
                lockdownRule.setDomainName(domainName);

                // Paused
                if (!lockdownRuleObj.get("paused").isJsonNull()) {
                    lockdownRule.setPaused(lockdownRuleObj.get("paused").getAsBoolean());
                }

                // Description
                if (!lockdownRuleObj.get("description").isJsonNull()) {
                    lockdownRule.setDescription(lockdownRuleObj.get("description").getAsString());
                }

                // Urls
                if (!lockdownRuleObj.get("urls").isJsonNull()) {
                    ArrayList<String> urls = new ArrayList<String>();
                    JsonArray urlsArray = lockdownRuleObj.get("urls").getAsJsonArray();
                    for (JsonElement url : urlsArray) {
                        urls.add(url.getAsString());
                    }
                    lockdownRule.setUrls(urls);
                }

                // Configurations
                // Seperate into IPs and IP Ranges
                if (!lockdownRuleObj.get("configurations").isJsonNull()) {
                    ArrayList<String> ips = new ArrayList<String>();
                    ArrayList<String> ipRanges = new ArrayList<String>();
                    JsonArray configurationsArr = lockdownRuleObj.get("configurations").getAsJsonArray();

                    for (JsonElement configurationEl : configurationsArr) {
                        JsonObject configurationObj = configurationEl.getAsJsonObject();
                        String configurationTarget = configurationObj.get("target").getAsString();
                        String configurationValue = configurationObj.get("value").getAsString();
                        if (configurationTarget.equalsIgnoreCase("ip")) {
                            ips.add(configurationValue);
                        } else if (configurationTarget.equalsIgnoreCase("ip_range")) {
                            ipRanges.add(configurationValue);
                        }
                    }
                    lockdownRule.setIps(ips);
                    lockdownRule.setIpRanges(ipRanges);
                }

                try {
                    crossStorageApi.createOrUpdate(defaultRepo, lockdownRule);
                    logger.info("Lockdown Rule : {} successfully retrieved", lockdownRule.getProviderSideId());
                } catch (Exception e) {
                    logger.error("Error retrieving lockdown rule : {} {}", lockdownRule.getProviderSideId(), e.getMessage());
                }
            }
            response.close();
        }
    }
}
