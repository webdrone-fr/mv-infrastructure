package org.meveo.scaleway;

import java.time.Duration;
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
import org.meveo.model.customEntities.ScalewayServer;
import org.meveo.model.customEntities.ServerAction;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckScalewayServerActionStatus extends Script{
    

    private static final Logger logger = LoggerFactory.getLogger(CheckScalewayServerActionStatus.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private String SCALEWAY_URL = "api.scaleway.com";
    static final private String BASE_PATH = "/instance/v1/zones/";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        ServerAction action = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance) parameters.get("event"), ServerAction.class);
        
        if (action.getProviderSideId()==null){
            throw new BusinessException("Invalid Action ID");
        } else if (action.getServer()== null){
            throw new BusinessException("Action not affected to server");
        }

        String actionId = action.getProviderSideId();
        String serverId = action.getServer().getUuid();
        ScalewayServer server = null;
        String zone = null;
        try {
            server = crossStorageApi.find(defaultRepo, serverId, ScalewayServer.class);
            zone = server.getZone();
        } catch (Exception e) {
            logger.error("Error retrieving server : {}", serverId, e.getMessage());
            throw new BusinessException("Server not found");
        }

        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("Using Credential {} with username {}", credential.getUuid(), credential.getUsername());
        }

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/tasks/"+actionId);
        Boolean actionComplete = false;
        do {
            Response response = CredentialHelperService.setCredential(target.request(), credential).get();
            String value = response.readEntity(String.class);
            logger.info("response : " + value);
            logger.debug("response status : {}", response.getStatus());
            if(response.getStatus()<300) {
                JsonObject taskObj = new JsonParser().parse(value).getAsJsonObject().get("task").getAsJsonObject();
                if (!taskObj.get("terminated_at").isJsonNull()) {
                    action.setResponse(taskObj.get("status").getAsString());
                    Duration timeElapsed = Duration.between(
                        OffsetDateTime.parse(taskObj.get("started_at").getAsString()).toInstant(),
                        OffsetDateTime.parse(taskObj.get("terminated_at").getAsString()).toInstant()); 
                    action.setElapsedTimeMs(timeElapsed.toMillis());
                    action.setProgress(taskObj.get("progress").getAsLong());
                    actionComplete = true;
                    parameters.put(RESULT_GUI_MESSAGE, "Action : "+action.getAction() +" terminated in : "+action.getElapsedTimeMs()+" with status : "+action.getResponse());
                } else if (action.getResponse().equalsIgnoreCase("failure")) {
                    throw new BusinessException("Task failed");
                } else {
                    action.setProgress(taskObj.get("progress").getAsLong());
                    action.setResponse(taskObj.get("status").getAsString());
                }
                try {
                    crossStorageApi.createOrUpdate(defaultRepo, action);
                } catch (Exception e) {
                    logger.error("Error with Action Status : {}", action.getResponse(), e.getMessage());
                }
            }
            response.close();
        } while (actionComplete != true);

        if (action.getResponse().equalsIgnoreCase("success")) {
            JsonObject serverDetailsObj = ScalewayHelperService.getServerDetails(zone, server.getProviderSideId(), credential);
            // Values to update
            // Default Server values
            String publicIp = null;
            JsonArray allowedActions = new JsonArray();
            ArrayList<String> serverActions = new ArrayList<String>();
            String location = null;
            // Scaleway specific values
            String privateIp = null;
            String ipVSix = null;
            String status = null;

            switch (action.getAction()) {
                case "poweron": 
                    // Public Ip
                    if(!serverDetailsObj.get("public_ip").isJsonNull()){
                        publicIp = serverDetailsObj.get("public_ip").getAsJsonObject().get("address").getAsString();
                    }
                    // Server Actions
                    allowedActions = serverDetailsObj.get("allowed_actions").getAsJsonArray();
                    for (JsonElement allowedAction : allowedActions) {
                        serverActions.add(allowedAction.getAsString());
                    }
                    // Location
                    if (!serverDetailsObj.get("location").isJsonNull()) {
                        JsonObject locationObj = serverDetailsObj.get("location").getAsJsonObject();
                        String zone_id = locationObj.get("zone_id").getAsString();
                        String platform_id = locationObj.get("platform_id").getAsString();
                        String cluster_id = locationObj.get("cluster_id").getAsString();
                        String hypervisor_id = locationObj.get("hypervisor_id").getAsString();
                        String node_id = locationObj.get("node_id").getAsString();
                        location = zone_id+"/"+platform_id+"/"+cluster_id+"/"+hypervisor_id+"/"+node_id;
                    }
                    // Private IP
                    if(!serverDetailsObj.get("private_ip").isJsonNull()) {
                        privateIp = serverDetailsObj.get("private_ip").getAsString();
                    }
                    // IPV6
                    if (!serverDetailsObj.get("ipv6").isJsonNull()) {
                        ipVSix = serverDetailsObj.get("ipv6").getAsJsonObject().get("address").getAsString();
                    }
                    // Status
                    if (!serverDetailsObj.get("state").isJsonNull()) {
                        status = serverDetailsObj.get("state").getAsString();
                    }
                    break;
                case "poweroff":
                    // Server Actions
                    allowedActions = serverDetailsObj.get("allowed_actions").getAsJsonArray();
                    for (JsonElement allowedAction : allowedActions) {
                        serverActions.add(allowedAction.getAsString());
                    }
                    // Status
                    if (!serverDetailsObj.get("state").isJsonNull()) {
                        status = serverDetailsObj.get("state").getAsString();
                    }
                    break;
                case "stop_in_place":
                    // Public Ip
                    if(!serverDetailsObj.get("public_ip").isJsonNull()){
                        publicIp = serverDetailsObj.get("public_ip").getAsJsonObject().get("address").getAsString();
                    }
                    // Server Actions
                    allowedActions = serverDetailsObj.get("allowed_actions").getAsJsonArray();
                    for (JsonElement allowedAction : allowedActions) {
                        serverActions.add(allowedAction.getAsString());
                    }
                    // Location
                    if (!serverDetailsObj.get("location").isJsonNull()) {
                        JsonObject locationObj = serverDetailsObj.get("location").getAsJsonObject();
                        String zone_id = locationObj.get("zone_id").getAsString();
                        String platform_id = locationObj.get("platform_id").getAsString();
                        String cluster_id = locationObj.get("cluster_id").getAsString();
                        String hypervisor_id = locationObj.get("hypervisor_id").getAsString();
                        String node_id = locationObj.get("node_id").getAsString();
                        location = zone_id+"/"+platform_id+"/"+cluster_id+"/"+hypervisor_id+"/"+node_id;
                    }
                    // Private Ip
                    if(!serverDetailsObj.get("private_ip").isJsonNull()) {
                        privateIp = serverDetailsObj.get("private_ip").getAsString();
                    }
                    // IPV6
                    if (!serverDetailsObj.get("ipv6").isJsonNull()) {
                        ipVSix = serverDetailsObj.get("ipv6").getAsJsonObject().get("address").getAsString();
                    }
                    // Status
                    if (!serverDetailsObj.get("state").isJsonNull()) {
                        status = serverDetailsObj.get("state").getAsString();
                    }
                    break;
                case "backup":
                        // Public Ip
                        if(!serverDetailsObj.get("public_ip").isJsonNull()){
                            publicIp = serverDetailsObj.get("public_ip").getAsJsonObject().get("address").getAsString();
                        }
                        // Server Actions
                        allowedActions = serverDetailsObj.get("allowed_actions").getAsJsonArray();
                        for (JsonElement allowedAction : allowedActions) {
                            serverActions.add(allowedAction.getAsString());
                        }
                        // Location
                        if (!serverDetailsObj.get("location").isJsonNull()) {
                            JsonObject locationObj = serverDetailsObj.get("location").getAsJsonObject();
                            String zone_id = locationObj.get("zone_id").getAsString();
                            String platform_id = locationObj.get("platform_id").getAsString();
                            String cluster_id = locationObj.get("cluster_id").getAsString();
                            String hypervisor_id = locationObj.get("hypervisor_id").getAsString();
                            String node_id = locationObj.get("node_id").getAsString();
                            location = zone_id+"/"+platform_id+"/"+cluster_id+"/"+hypervisor_id+"/"+node_id;
                        }
                        // Private IP
                        if(!serverDetailsObj.get("private_ip").isJsonNull()) {
                            privateIp = serverDetailsObj.get("private_ip").getAsString();
                        }
                        // IPV6
                        if (!serverDetailsObj.get("ipv6").isJsonNull()) {
                            ipVSix = serverDetailsObj.get("ipv6").getAsJsonObject().get("address").getAsString();
                        }
                        // Status
                        if (!serverDetailsObj.get("state").isJsonNull()) {
                            status = serverDetailsObj.get("state").getAsString();
                        }
                    break;
            }
            server.setLastUpdate(OffsetDateTime.parse(serverDetailsObj.get("modification_date").getAsString()).toInstant());
            server.setPublicIp(publicIp);
            server.setServerActions(serverActions);
            server.setLocation(location);
            server.setPrivateIp(privateIp);
            server.setIpVSix(ipVSix);
            server.setStatus(status);
            try {
                crossStorageApi.createOrUpdate(defaultRepo, server);
            }catch(Exception e){
                logger.error("Error updating server after action", e.getMessage());
            }
        } else if(action.getResponse().equalsIgnoreCase("pending")) {
            String serverStatus = null;
            switch(action.getAction()) {
                case "poweron":
                    serverStatus = "starting";
                    break;
                case "poweroff":
                    serverStatus = "stopping";
                    break;
                case "stop_in_place":
                    serverStatus = "stopping";
                    break;
                case "reboot":
                    serverStatus = "rebooting";
                    break;
                case "backup":
                    serverStatus = "backing up";
                    break;
            }
            server.setStatus(serverStatus);
            try {
                crossStorageApi.createOrUpdate(defaultRepo, server);
            }catch(Exception e){
                logger.error("Error updating server after action", e.getMessage());
            }
        }
    }
}