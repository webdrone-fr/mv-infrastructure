package org.meveo.scaleway;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import com.google.gson.*;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.ScalewayServer;
import org.meveo.model.customEntities.SecurityGroup;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateScalewaySecurityGroup extends Script{


    private static final Logger logger = LoggerFactory.getLogger(ListScalewaySecurityGroups.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private String SCALEWAY_URL = "api.scaleway.com";
    static final private String BASE_PATH = "/instance/v1/zones/";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        String action = parameters.get(CONTEXT_ACTION).toString();
        SecurityGroup securityGroup = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), SecurityGroup.class);

        if(securityGroup.getProviderSideId()==null) { // required
            throw new BusinessException("Invalid Security Group Provider-side ID");
        } else if(securityGroup.getZone()==null) { // required
            throw new BusinessException("Invalid Security Group Zone");
        }

        String zone = securityGroup.getZone();
        String securityGroupId = securityGroup.getProviderSideId();

        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("Using Credential {} with username {}", credential.getUuid(), credential.getUsername());
        }

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/security_groups/"+securityGroupId);

        Map<String, Object> body = new HashMap<String, Object>();
        body.put("enable_default_security", securityGroup.getEnableDefaultSecurity()); // nullable; read only
        body.put("inbound_default_policy", securityGroup.getInboundDefaultPolicy()); // default is accept
        body.put("outbound_default_policy", securityGroup.getOutboundDefaultPolicy()); // default is accept
        body.put("project", securityGroup.getProject());
        body.put("project_default", securityGroup.getProjectDefault()); // default false
        body.put("stateful", securityGroup.getStateful());
        
        // Name
        if(securityGroup.getName()!=null) {
            body.put("name", securityGroup.getName());
        }

        // Description
        if(securityGroup.getDescription()!=null){
            body.put("description", securityGroup.getDescription());
        }

        // Servers
        if (securityGroup.getServers() != null) {
            ArrayList<Object> serversArr = new ArrayList<Object>();
            for (String serverId : (securityGroup.getServers())){
                Map<String, Object> serverMap = new HashMap<String, Object>();
                try {
                    ScalewayServer server = crossStorageApi.find(defaultRepo, ScalewayServer.class).by("providerSideId", serverId).getResult();
                    serverMap.put("id", serverId);
                    serverMap.put("name", server.getInstanceName());
                } catch (Exception e) {
                    logger.error("Error retrieving server : {} for Security Group {}", serverId, securityGroup.getName(), e.getMessage());
                }
                serversArr.add(serverMap);
            }
            body.put("servers", serversArr);
        }
        
        String resp = JacksonUtil.toStringPrettyPrinted(body);
        Response response = CredentialHelperService.setCredential(target.request("application/json"), credential)
            .put(Entity.json(resp));
        String value = response.readEntity(String.class);
        logger.info("response : " + value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response:"+value);
        if(response.getStatus()<300){
            JsonObject securityGroupObj = new JsonParser().parse(value).getAsJsonObject().get("security_group").getAsJsonObject();
            securityGroup = ScalewaySetters.setSecurityGroup(securityGroupObj, securityGroup, crossStorageApi, defaultRepo);
            try {
                crossStorageApi.createOrUpdate(defaultRepo, securityGroup);
            } catch (Exception e) {
                logger.error("Error updating Security Group : {}", securityGroup.getName(), e.getMessage());
            }
        }
        response.close();
    }
}