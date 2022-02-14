package org.meveo.scaleway;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PerformActionOnScalewayServer extends Script {


    private static final Logger logger = LoggerFactory.getLogger(PerformActionOnScalewayServer.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private String SCALEWAY_URL = "api.scaleway.com";
    static final private String BASE_PATH = "/instance/v1/zones/";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        String action = parameters.get("action").toString(); // Possible values include poweron, backup, stop_in_place, poweroff, terminate and reboot - default is poweron
        logger.debug("ACTION TO PERFORM : {}", action);
        ScalewayServer server = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), ScalewayServer.class);
        List<String> allowedServerActions = server.getServerActions();

        if (server.getZone()==null) { //Required
            throw new BusinessException("Invalid Server Zone");
        } else if(server.getProviderSideId()==null) { //Required
            throw new BusinessException("Invalid Server Provider-side ID");
        } else if (!allowedServerActions.contains(action)) {
            throw new BusinessException("Action "+action+" not allowed on Server "+server.getUuid());
        }

        String zone = server.getZone();
        String serverId = server.getProviderSideId();
        logger.info("Performing {} on server with uuid : {}", action,  serverId);

        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("Using Credential {} with username {}", credential.getUuid(), credential.getUsername());
        }

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/servers/"+serverId+"/action");

        Map <String, Object> body = new HashMap<String, Object>();
        // Get latest server details
        // Boolean actionComplete = false;

        // do {
        //     // request server details
        //     // check if status == expected outcome of action
        //     // wait delay
        //     // change actionCompleted to true
        // }
        // while (actionComplete == false);

        // Server status
        // String serverStatus = server.getStatus(); // possible values include running, stopped, stopped in place, starting, stopping and locked
        String serverChangingStatus;
        String serverExpectedStatus;

        
        // Server Type Constraints Check
        String serverType = server.getServerType();
        // Add up sizes of root volume + all additional volumes
        Long serverTotalVolumesSizes = ScalewayHelperService.calcServerTotalVolumesSizes(server, crossStorageApi, defaultRepo);

        // Get server type constraints
        JsonObject serverConstraintsObj = ScalewayHelperService.getServerTypeRequirements(server, crossStorageApi, defaultRepo, credential);
        // Size requirements for sum of all volumes for server type
        Long serverMinVolumeSizeReq = serverConstraintsObj.get("volumes_constraint").getAsJsonObject().get("min_size").getAsLong();
        Long serverMaxVolumeSizeReq = serverConstraintsObj.get("volumes_constraint").getAsJsonObject().get("max_size").getAsLong();

        // Action Conditions
        logger.info("ACTION : {}",action);
        // Block volumes are only available for DEV1, GP1 and RENDER offers
        if (action.equalsIgnoreCase("poweron")) {
            // Check if available volume size meets requirements for server type
            String serverTotalVolumesSizesStr = Long.toString(serverTotalVolumesSizes);
            String serverMinVolumeSizeReqStr = Long.toString(serverMinVolumeSizeReq);
            String serverMaxVolumeSizeReqStr = Long.toString(serverMaxVolumeSizeReq);
            if (serverTotalVolumesSizes < serverMinVolumeSizeReq) {
                logger.debug("Current available volume size : {}, Minimum Volume size required for server type {} : {}", serverTotalVolumesSizesStr , serverType, serverMinVolumeSizeReqStr);
                throw new BusinessException("Current total volume size is too small for selected server type");
            } else if (serverTotalVolumesSizes > serverMaxVolumeSizeReq) {
                logger.debug("Current available volume size : {}, Maximum Volume size allowed for server type {} : {}", serverTotalVolumesSizesStr , serverType, serverMaxVolumeSizeReqStr);
                throw new BusinessException("Current total volume size is too large for selected server type");
            } else {
                logger.info("Server Total Volume size : {}; Min Total Volume size : {}; Max Total Volume Size : {}", serverTotalVolumesSizesStr, serverMinVolumeSizeReqStr, serverMaxVolumeSizeReqStr);
            }
            serverChangingStatus = "starting";
            serverExpectedStatus = "running";
        } else if (action.equalsIgnoreCase("poweroff")) {
            // When a server is powered off, only its volumes and any reserved flexible IP address are billed.
            // Check if volumes still attached to server
            if(server.getRootVolume() != null || server.getAdditionalVolumes() != null) {
                // Notify of billing condition
            }
            serverChangingStatus = "stopping";
            serverExpectedStatus = "stopped";
        } else if (action.equalsIgnoreCase("backup")) {
            // If action is backup - check for name of Backup to be created
            if (server.getBackupName() != null) { // nullable
                String backupName = server.getBackupName();
                body.put("name", backupName);
            }
            serverExpectedStatus = "running";
        } else if (action.equalsIgnoreCase("stop_in_place")) {
            serverChangingStatus = "stopping";
            serverExpectedStatus = "stopped in place";
        } else if (action.equalsIgnoreCase("terminate")) {
            // when terminating a server, all the attached volumes (local and block storage) are deleted
            // check if user wants to keep volumes or delete them
        }
        body.put("action", action);
        
        String resp = JacksonUtil.toStringPrettyPrinted(body);
        Response response = CredentialHelperService.setCredential(target.request("application/json"), credential).post(Entity.json((resp)));
        String value = response.readEntity(String.class);
        logger.info("response : {}", value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response: "+value);
        if (response.getStatus() < 300) {
            server.setLastUpdate(Instant.now());
            JsonObject serverActionObj = new JsonParser().parse(value).getAsJsonObject().get("task").getAsJsonObject();
            ServerAction serverAction = new ServerAction();
            serverAction.setUuid(serverActionObj.get("id").getAsString());
            serverAction.setProviderSideId(serverActionObj.get("id").getAsString());
            serverAction.setServer(server);
            serverAction.setCreationDate(OffsetDateTime.parse(serverActionObj.get("started_at").getAsString()).toInstant());
            // Duration timeElapsed = Duration.between(
            //     OffsetDateTime.parse(serverActionObj.get("creation_date").getAsString()).toInstant(),
            //     OffsetDateTime.parse(serverActionObj.get("terminated_at").getAsString()).toInstant()); //will need to be updated with job until terminated
            // serverAction.setElapsedTimeMs(timeElapsed.toMillis());
            serverAction.setResponse(serverActionObj.get("status").getAsString());
            serverAction.setResponseStatus(String.valueOf(response.getStatus()));
            serverAction.setAction(action);
            try {
                crossStorageApi.createOrUpdate(defaultRepo, serverAction);
            } catch (Exception e) {
                logger.error("error creating server action {} : {}", serverAction.getUuid(), e.getMessage());
            }
            response.close();
        }
    }

    public static String getActionComplete(ScalewayServer server, CrossStorageApi crossStorageApiInstance, Repository repo, Credential credential) throws BusinessException {
        String zone = server.getZone();
        String serverId = server.getProviderSideId();
        String serverCurrentStatus = server.getStatus();

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/servers/"+serverId);
        Response response = CredentialHelperService.setCredential(target.request("application/json"), credential).get();
        String value = response.readEntity(String.class);
        if (response.getStatus() < 300) {
            JsonObject serverObj = new JsonParser().parse(value).getAsJsonObject().get("server").getAsJsonObject();
            serverCurrentStatus = serverObj.get("state").getAsString();
        }
        response.close();
        return serverCurrentStatus;
    }
}
