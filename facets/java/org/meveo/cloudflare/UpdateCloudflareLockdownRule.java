package org.meveo.cloudflare;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import com.google.gson.*;

import org.apache.commons.validator.routines.InetAddressValidator;
import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.LockdownRule;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateCloudflareLockdownRule extends Script {

    private static final Logger logger = LoggerFactory.getLogger(UpdateCloudflareLockdownRule.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private String CLOUDFLARE_URL = "api.cloudflare.com/client/v4";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        String action = parameters.get(CONTEXT_ACTION).toString();
        LockdownRule lockdownRule = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance) parameters.get(CONTEXT_ENTITY), LockdownRule.class);
        InetAddressValidator ipValidator = InetAddressValidator.getInstance();

        if (lockdownRule.getDomainName() == null) {
            throw new BusinessException("Invalid Domain Name");
        } else if (lockdownRule.getProviderSideId() == null) {
            throw new BusinessException("Invalid Provider-side ID");
        }

        Credential credential = CredentialHelperService.getCredential(CLOUDFLARE_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for " + CLOUDFLARE_URL);
        } else {
            logger.info("using credential {} with username {}", credential.getUuid(), credential.getUsername());
        }

        String domainNameId = lockdownRule.getDomainName().getUuid();
        String lockdownRuleId = lockdownRule.getProviderSideId();
        logger.info("action:{}, lockdown rule:{}", action, lockdownRuleId);

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+CLOUDFLARE_URL+"/zones/"+domainNameId+"/firewall/lockdowns/"+lockdownRuleId);

        Map<String, Object> body = new HashMap<String, Object>();

        // Urls
        List<String> urls = lockdownRule.getUrls();
        ArrayList<String> urlsArr = new ArrayList<String>();
        for (String url : urls) {
            urlsArr.add(url);
        }
        body.put("urls", urlsArr);

        // Configurations
        ArrayList<Object> configurations = new ArrayList<Object>();
        // IPs
        if (lockdownRule.getIps()!= null) {
            List<String> ips = lockdownRule.getIps();
            for (String ip : ips) {
                if(ipValidator.isValid(ip)) {
                    Map<String,String> ipConfig = new HashMap<String, String>();
                    ipConfig.put("target", "ip");
                    ipConfig.put("value", ip);
                    configurations.add(ipConfig);
                } else {
                    throw new BusinessException("IPs contain invalid IP address : "+ip);
                }
            }
        }
        // // IP Ranges
        // // TODO Need to validate Ip ranges
        // // possible solution : https://gist.github.com/madan712/6651967
        if (lockdownRule.getIpRanges() != null) {
            List<String> ipRanges = lockdownRule.getIpRanges();
            for (String ipRange : ipRanges) {
                Map<String,String> ipRangeConfig = new HashMap<String, String>();
                ipRangeConfig.put("target", "ip_range");
                ipRangeConfig.put("value", ipRange);
                configurations.add(ipRangeConfig);
            }
        }
        body.put("configurations", configurations);

        // Paused - Optional - default false
        body.put("paused", lockdownRule.getPaused());

        // Description - Optional
        if(lockdownRule.getDescription() != null) {
            body.put("description", lockdownRule.getDescription());
        }

        String resp = JacksonUtil.toStringPrettyPrinted(body);
        Response response = CredentialHelperService.setCredential(target.request("application/json"), credential)
            .put(Entity.json(resp));
        String value = response.readEntity(String.class);
        logger.info("response  :" + value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response:"+value);

        if(response.getStatus()<300) {
            JsonObject lockdownRuleObj = new JsonParser().parse(value).getAsJsonObject().get("result").getAsJsonObject();

            lockdownRule.setLastUpdated(OffsetDateTime.parse(lockdownRuleObj.get("modified_on").getAsString()).toInstant());

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
                ArrayList<String> newUrls = new ArrayList<String>();
                JsonArray urlsArray = lockdownRuleObj.get("urls").getAsJsonArray();
                for (JsonElement url : urlsArray) {
                    newUrls.add(url.getAsString());
                }
                lockdownRule.setUrls(newUrls);
            }

            // Configurations
            // Seperate into IPs and IP Ranges
            if (!lockdownRuleObj.get("configurations").isJsonNull()) {
                ArrayList<String> newIps = new ArrayList<String>();
                ArrayList<String> newIpRanges = new ArrayList<String>();
                JsonArray configurationsArr = lockdownRuleObj.get("configurations").getAsJsonArray();

                for (JsonElement configurationEl : configurationsArr) {
                    JsonObject configurationObj = configurationEl.getAsJsonObject();
                    String configurationTarget = configurationObj.get("target").getAsString();
                    String configurationValue = configurationObj.get("value").getAsString();
                    if (configurationTarget.equalsIgnoreCase("ip")) {
                        newIps.add(configurationValue);
                    } else if (configurationTarget.equalsIgnoreCase("ip_range")) {
                        newIpRanges.add(configurationValue);
                    }
                }
                lockdownRule.setIps(newIps);
                lockdownRule.setIpRanges(newIpRanges);
            }

            try {
                crossStorageApi.createOrUpdate(defaultRepo, lockdownRule);
                logger.info("Lockdown Rule : {} successfully updated", lockdownRule.getProviderSideId());
            } catch (Exception e) {
                logger.error("Error updating lockdown rule : {} {}", lockdownRule.getProviderSideId(), e.getMessage());
            }
            response.close();
        }
    }
}