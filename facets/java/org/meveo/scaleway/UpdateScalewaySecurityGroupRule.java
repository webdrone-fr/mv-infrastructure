package org.meveo.scaleway;

import java.util.HashMap;
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
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateScalewaySecurityGroupRule extends Script{

    
    private static final Logger logger = LoggerFactory.getLogger(UpdateScalewaySecurityGroupRule.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private  String SCALEWAY_URL = "api.scaleway.com";
    static final private String BASE_PATH = "/instance/v1/zones/";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        String action = parameters.get(CONTEXT_ACTION).toString();
        SecurityRule rule = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), SecurityRule.class);

        if(rule.getZone()==null) {
            throw new BusinessException("Invalid Security Rule Zone"); // required
        } else if(rule.getSecurityGroup()==null) {
            throw new BusinessException("Invalid Security Group for Security Rule"); // required
        } else if(rule.getProviderSideId()==null) { // required
            throw new BusinessException("Invalid Security Rule Provider-side ID");
        }

        String zone = rule.getZone();
        String ruleId = rule.getProviderSideId();
        // security group
        String securityGroupUuid = rule.getSecurityGroup().getUuid();
        String securityGroupId = null;
        SecurityGroup securityGroup = null;
        try {
            securityGroup = crossStorageApi.find(defaultRepo, securityGroupUuid, SecurityGroup.class);
            securityGroupId = securityGroup.getProviderSideId();
        } catch (Exception e) {
            throw new BusinessException("Error retrieving security group : "+securityGroupUuid +e.getMessage());
        }

        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("using Credential {} with username {}", credential.getUuid(), credential.getUsername());
        }

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/security_groups/"+securityGroupId+"/rules/"+ruleId);
        
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("protocol", rule.getProtocol());
        body.put("direction", rule.getDirection());
        body.put("action", rule.getAction());
        body.put("ip_range", rule.getIpRange());
        if(rule.getDestPortFrom()!=null) {
            body.put("dest_port_from", rule.getDestPortFrom());
        }
        if(rule.getDestPortTo()!=null) {
            body.put("dest_port_to", rule.getDestPortTo());
        }
        body.put("editable", rule.getEditable());

        String resp = JacksonUtil.toStringPrettyPrinted(body);
        Response response = 
        CredentialHelperService.setCredential(target.request("application/json"), credential)
            .put(Entity.json(resp));
        String value = response.readEntity(String.class);
        logger.info("response : {}", value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response: "+value);
        if(response.getStatus()<300){
            JsonObject ruleObj = new JsonParser().parse(value).getAsJsonObject().get("rule").getAsJsonObject();
            rule = ScalewaySetters.setSecurityRule(ruleObj, rule, crossStorageApi, defaultRepo);
            try {
                crossStorageApi.createOrUpdate(defaultRepo, rule);
            } catch (Exception e) {
                logger.error("Error updating security rule : {} for security group : {}", ruleId, securityGroupId, e.getMessage());
            }
        }
        response.close();
    }
}