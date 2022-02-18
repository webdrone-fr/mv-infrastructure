package org.meveo.scaleway;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import com.google.gson.*;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.Server;
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
        String zone = null;
        try {
            Server server = crossStorageApi.find(defaultRepo, serverId, Server.class);
            zone = server.getZone();
        } catch (Exception e) {
            logger.error("Error retrieving server : {}", serverId, e.getMessage());
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
                } else {
                    action.setProgress(taskObj.get("progress").getAsLong());
                }
                try {
                    crossStorageApi.createOrUpdate(defaultRepo, action);
                } catch (Exception e) {
                    logger.error("Error with Action Status : {}", e.getMessage());
                }
            }
            response.close();
        } while (actionComplete != true);
    }
}