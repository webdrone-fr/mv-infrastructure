package org.meveo.scaleway;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
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

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        // String action = (String)parameters.get(CONTEXT_ACTION); // needs to be changed to get selected action from list - default is poweron
        String action = parameters.get("action").toString(); // Possible values include poweron, backup, stop_in_place, poweroff, terminate and reboot
        ScalewayServer server =CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), ScalewayServer.class);
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
        WebTarget target = client.target("https://"+SCALEWAY_URL+"/instance/v1/zones/"+zone+"/servers/"+serverId+"/action");

        // Action Conditions
        // String serverStatus = server.getStatus(); // possible values include running, stopped, stopped in place, starting, stopping and locked
        if (action == "terminate") {
            // when terminating a server, all the attached volumes (local and block storage) are deleted
            if (server.getRootVolume() != null || !server.getAdditionalVolumes().isEmpty()) {
                // TODO make an alert + check for confirmation 
                // Alternative is to detach volumes prior to termination
                // for local volumes, use archive action
                // for block volumes, volumes must be detached before Server termination
                parameters.put(RESULT_GUI_MESSAGE, "All Volumes for Server "+serverId+"will be deleted, are you sure wou wish to proceed?");
            }
        }
        // Need to wait for task to be completed?

        Map<String, Object> body = Map.of(
            "action", action // to to ensure is correct action, see above for action
        );

        //If action is backup - check for name of Image to be created
        if (action == "backup") {
             String imageName = server.getBackupName();
             body.put("name", imageName);
        }
        
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
            serverAction.setServer(server);
            serverAction.setUuid(serverActionObj.get("id").getAsString());
            // Need to add providersideId
            serverAction.setCreationDate(OffsetDateTime.parse(serverActionObj.get("started_at").getAsString()).toInstant());
            Duration timeElapsed = Duration.between(
                OffsetDateTime.parse(serverActionObj.get("creation_date").getAsString()).toInstant(),
                OffsetDateTime.parse(serverActionObj.get("terminated_at").getAsString()).toInstant()); //will need to be updated with job until terminated
            serverAction.setElapsedTimeMs(timeElapsed.toMillis());
            serverAction.setResponse(String.valueOf(response.getStatus()));
            serverAction.setResponseStatus(serverActionObj.get("status").getAsString());
            serverAction.setAction(action);
            try {
                crossStorageApi.createOrUpdate(defaultRepo, serverAction);
            } catch (Exception e) {
                logger.error("error creating server action {} : {}", serverAction.getUuid(), e.getMessage());
            }
        }
    }
}
