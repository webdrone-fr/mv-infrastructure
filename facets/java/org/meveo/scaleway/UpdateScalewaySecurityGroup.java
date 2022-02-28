package org.meveo.scaleway;

import java.time.OffsetDateTime;
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
import org.meveo.model.customEntities.SecurityGroup;
import org.meveo.model.customEntities.Server;
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

        if(securityGroup.getProviderSideId()==null) {
            throw new BusinessException("Invalid Security Group Provider-side ID");
        } else if(securityGroup.getZone()==null) {
            throw new BusinessException("Invalid Security Group Zone");
        }

        String zone = securityGroup.getZone();
        String securityGroupId = securityGroup.getProviderSideId();
        logger.info("action : {}, security group ID : {}", action, securityGroupId);

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
        body.put("creation_date", securityGroup.getCreationDate());
        body.put("modification_date", securityGroup.getLastUpdated());
        body.put("enable_default_security", securityGroup.getEnableDefaultSecurity());
        body.put("inbound_default_policy", securityGroup.getInboundDefaultPolicy()); // default is accept
        body.put("outbound_default_policy", securityGroup.getOutboundDefaultPolicy()); // default is accept
        body.put("organization", securityGroup.getOrganization());
        body.put("project", securityGroup.getProject());
        body.put("project_default", securityGroup.getProjectDefault());
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
                Map<String, Object> serverObj = new HashMap<String, Object>();
                try {
                    Server server = crossStorageApi.find(defaultRepo, Server.class).by("providerSideId", securityGroupId).getResult();
                    serverObj.put("id", serverId);
                    serverObj.put("name", server.getInstanceName());
                } catch (Exception e) {
                    logger.error("Error retrieving Security Group {} : {}", securityGroup.getName(), e.getMessage());
                }
                serversArr.add(serverObj);
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

            securityGroup.setLastUpdated(OffsetDateTime.parse(securityGroupObj.get("modification_date").getAsString()).toInstant());
            securityGroup.setName(securityGroupObj.get("name").getAsString());
            securityGroup.setEnableDefaultSecurity(securityGroupObj.get("enable_default_security").getAsBoolean());
            securityGroup.setInboundDefaultPolicy(securityGroupObj.get("inbound_default_policy").getAsString());
            securityGroup.setOutboundDefaultPolicy(securityGroupObj.get("outbound_default_policy").getAsString());
            securityGroup.setOrganization(securityGroupObj.get("organization").getAsString());
            securityGroup.setProject(securityGroupObj.get("project").getAsString());
            securityGroup.setProjectDefault(securityGroupObj.get("project_default").getAsBoolean());

            // Description
            if(!securityGroupObj.get("description").isJsonNull()) {
                securityGroup.setDescription(securityGroupObj.get("description").getAsString());
            }

            // Servers
            if(!securityGroupObj.get("servers").isJsonNull()) {
                JsonArray serversArr = securityGroupObj.get("servers").getAsJsonArray();
                ArrayList<String> servers = new ArrayList<String>();
                for (JsonElement serverEl : serversArr) {
                    JsonObject serverObj = serverEl.getAsJsonObject();
                    String serverId = serverObj.get("id").getAsString();
                    String serverInstanceName = serverObj.get("name").getAsString();
                    if(serverInstanceName.startsWith("dev-") && crossStorageApi.find(defaultRepo, Server.class).by("providerSideId", serverId).getResult() != null) {
                        servers.add(serverId);
                    }
                }
                securityGroup.setServers(servers);
            }

            try {
                crossStorageApi.createOrUpdate(defaultRepo, securityGroup);
            } catch (Exception e) {
                logger.error("Error updating Security Group {} : {}", securityGroup.getName(), e.getMessage());
            }
        }
        response.close();
    }
}
