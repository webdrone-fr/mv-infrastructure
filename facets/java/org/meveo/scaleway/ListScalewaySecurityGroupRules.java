package org.meveo.scaleway;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import com.google.gson.*;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.SecurityGroup;
import org.meveo.model.customEntities.SecurityRule;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListScalewaySecurityGroupRules extends Script{


    private static final Logger logger = LoggerFactory.getLogger(ListScalewaySecurityGroupRules.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private String SCALEWAY_URL = "api.scaleway.com";
    static final private String BASE_PATH = "/instance/v1/zones/";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        String action = parameters.get(CONTEXT_ACTION).toString();
        SecurityGroup securityGroup = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), SecurityGroup.class);

        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("Using Credential {} with username {}", credential.getUuid(), credential.getUsername());
        }

        String zone = securityGroup.getZone();
        String securityGroupId = securityGroup.getProviderSideId();
        List<String> providerSideIds = new ArrayList<String>();
        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/security_groups/"+securityGroupId+"/rules");
        Response response = CredentialHelperService.setCredential(target.request(), credential).get();
        String value = response.readEntity(String.class);
        logger.info("response : " + value);
        logger.debug("response status : {}", response.getStatus());
        if(response.getStatus()<300) {
            JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().get("rules").getAsJsonArray();
            for (JsonElement element : rootArray) {
                JsonObject ruleObj = element.getAsJsonObject();
                String ruleId = ruleObj.get("id").getAsString();
                providerSideIds.add(ruleId);
                SecurityRule rule = null;
                try {
                    if(crossStorageApi.find(defaultRepo, SecurityRule.class).by("providerSideId", ruleId).getResult()!=null){
                        rule = crossStorageApi.find(defaultRepo, SecurityRule.class).by("providerSideId", ruleId).getResult();
                    } else {
                        rule = new SecurityRule();
                        rule.setUuid(ruleId);
                        rule.setSecurityGroup(securityGroup);
                    }
                    rule = ScalewaySetters.setSecurityRule(ruleObj, rule, crossStorageApi, defaultRepo);
                    crossStorageApi.createOrUpdate(defaultRepo, rule);
                } catch (Exception e) {
                    logger.error("Error retrieving security rule : {} for security group : {}", ruleId, securityGroupId, e.getMessage());
                }
            }
        }
        // ScalewayHelperService.filterToLatestValues("SecurityRule", providerSideIds, crossStorageApi, defaultRepo);
        response.close();
    }
}
