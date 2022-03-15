package org.meveo.scaleway;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
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
import org.meveo.model.customEntities.ServiceProvider;
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
        logger.info("Performing {} on server : {}", action,  serverId);

        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("Using Credential {} with username {}", credential.getUuid(), credential.getUsername());
        }

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/servers/"+serverId+"/action");

        // Server Type Constraints Check
        String serverType = server.getServerType();
        // Add up sizes of root volume + all additional volumes - Local
        Long serverTotalLocalVolumesSizes = ScalewayHelperService.calcServerTotalLocalVolumesSize(server, crossStorageApi, defaultRepo);

        // Get server type constraints
        JsonObject serverConstraintsObj = ScalewayHelperService.getServerTypeRequirements(server, credential);
        // Size requirements for sum of all volumes for server type
        Long serverMinVolumeSizeReq = serverConstraintsObj.get("volumes_constraint").getAsJsonObject().get("min_size").getAsLong();
        Long serverMaxVolumeSizeReq = serverConstraintsObj.get("volumes_constraint").getAsJsonObject().get("max_size").getAsLong();

        // Action Conditions
        // Block volumes are only available for DEV1, GP1 and RENDER offers
        Map <String, Object> body = new HashMap<String, Object>();
        if (action.equalsIgnoreCase("poweron")) {
            // Check if available volume size meets requirements for server type
            String serverTotalLocalVolumesSizesStr = Long.toString(serverTotalLocalVolumesSizes);
            String serverMinVolumeSizeReqStr = Long.toString(serverMinVolumeSizeReq);
            String serverMaxVolumeSizeReqStr = Long.toString(serverMaxVolumeSizeReq);
            if (serverTotalLocalVolumesSizes < serverMinVolumeSizeReq) {
                logger.debug("Current available local volume size : {}, Minimum Local Volume size required for server type {} : {}", serverTotalLocalVolumesSizesStr , serverType, serverMinVolumeSizeReqStr);
                throw new BusinessException("Current total volume size is too small for selected server type");
            } else if (serverTotalLocalVolumesSizes > serverMaxVolumeSizeReq) {
                logger.debug("Current available local volume size : {}, Maximum Local Volume size allowed for server type {} : {}", serverTotalLocalVolumesSizesStr , serverType, serverMaxVolumeSizeReqStr);
                throw new BusinessException("Current total volume size is too large for selected server type");
            } else {
                logger.info("Server Total Local Volume size : {}; Min Total Volume size : {}; Max Total Volume Size : {}", serverTotalLocalVolumesSizesStr, serverMinVolumeSizeReqStr, serverMaxVolumeSizeReqStr);
            }
        } else if (action.equalsIgnoreCase("backup")) {
            // If action is backup - check for name of Backup to be created
            if (server.getBackupName() != null) { // nullable
                String backupName = server.getBackupName();
                body.put("name", backupName);
            }
        }
        body.put("action", action);
        
        String resp = JacksonUtil.toStringPrettyPrinted(body);
        Response response = CredentialHelperService.setCredential(target.request("application/json"), credential).post(Entity.json((resp)));
        String value = response.readEntity(String.class);
        logger.info("response : {}", value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response: "+value);

        if (response.getStatus() < 300) {
            JsonObject serverActionObj = new JsonParser().parse(value).getAsJsonObject().get("task").getAsJsonObject();
            ServerAction serverAction = new ServerAction();
            serverAction.setUuid(serverActionObj.get("id").getAsString());
            serverAction.setProviderSideId(serverActionObj.get("id").getAsString());
            serverAction.setServer(server);
            // Provider
            String providerId = server.getProvider().getUuid();
            try {
                ServiceProvider provider = crossStorageApi.find(defaultRepo, providerId, ServiceProvider.class);
                serverAction.setProvider(provider);
            } catch (Exception e) {
                logger.error("Error retrieving server provider : {}", providerId, e.getMessage());
            }
            serverAction.setCreationDate(OffsetDateTime.parse(serverActionObj.get("started_at").getAsString()).toInstant());
            serverAction.setResponse(serverActionObj.get("status").getAsString());
            serverAction.setResponseStatus(String.valueOf(response.getStatus()));
            serverAction.setAction(action);
            try {
                crossStorageApi.createOrUpdate(defaultRepo, serverAction);
            } catch (Exception e) {
                logger.error("error creating server action : {}", serverAction.getUuid(), e.getMessage());
            }
            response.close();
        }
    }
}