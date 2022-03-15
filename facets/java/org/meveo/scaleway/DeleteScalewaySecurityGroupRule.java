package org.meveo.scaleway;

import java.time.Instant;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

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

public class DeleteScalewaySecurityGroupRule extends Script{


    private static final Logger logger = LoggerFactory.getLogger(DeleteScalewaySecurityGroupRule.class);
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
        } else if(rule.getIpRange()==null) {
            throw new BusinessException("Invalid Security Rule IP Range"); // required
        }

        String zone = rule.getZone();
        String ruleId = rule.getProviderSideId();
        // Security Group
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
        Response response = CredentialHelperService.setCredential(target.request(), credential).delete();
        String value = response.readEntity(String.class);
        logger.info("response : {}", value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response:"+value);
        if (response.getStatus()<300) {
            try {
                crossStorageApi.remove(defaultRepo, rule.getUuid(), SecurityRule.class);
                logger.info("security rule : {} for security group : {} deleted at : {}", ruleId, securityGroupId, Instant.now());
            } catch (Exception e) {
                logger.error("error deleting security rule : {} for security group : {}", ruleId, securityGroupId, e.getMessage());
            }
        }
        response.close();
    }
}