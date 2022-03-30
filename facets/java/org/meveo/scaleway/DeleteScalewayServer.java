package org.meveo.scaleway;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.ScalewayServer;
import org.meveo.model.customEntities.ServerAction;
import org.meveo.model.customEntities.ServerVolume;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteScalewayServer extends Script{
    

    private static final Logger logger = LoggerFactory.getLogger(DeleteScalewayServer.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private  String SCALEWAY_URL = "api.scaleway.com";
    static final private String BASE_PATH = "/instance/v1/zones/";

    @Override
    public void execute(Map<String, Object>parameters) throws BusinessException {
        String action = parameters.get(CONTEXT_ACTION).toString();
        ScalewayServer server =CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), ScalewayServer.class);
        
        if (server.getZone()==null) { //Required
            throw new BusinessException("Invalid Server Zone");
        } else if(server.getProviderSideId()==null) { //Required
            throw new BusinessException("Invalid Server Provider-side ID");
        } else if (!server.getStatus().equalsIgnoreCase("stopped")) {
            throw new BusinessException("Unable to delete Server \n Server is still running");
        }

        String zone = server.getZone();
        String serverId = server.getProviderSideId();
        logger.info("action : {}, server : {}", action, serverId);

        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential==null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("using credential {} with username {}",credential.getUuid(),credential.getUsername());
        }

        // Remove server actions related to server as linked in reference to CET Server
        if (crossStorageApi.find(defaultRepo, ServerAction.class).by("server", server).getResults() != null) {
            List<ServerAction> serverActionsList = crossStorageApi.find(defaultRepo, ServerAction.class).by("server", server).getResults();
            for (ServerAction serverAction : serverActionsList) {
                try {
                    crossStorageApi.remove(defaultRepo, serverAction.getUuid(), serverAction.getCetCode());
                } catch (Exception e) {
                    logger.debug("Error deleting Server Action : {}", serverAction.getUuid());
                }
            }
        }
        // TODO could use terminate call to delete volumes as well
        // Option to delete associated volumes
        if (server.getRootVolume() != null || server.getAdditionalVolumes() != null) {
            if(action.equalsIgnoreCase("deleteScalewayServerWithVolumes")) {
                // Root volume
                String rootVolumeId = server.getRootVolume().getUuid();
                try {
                    ServerVolume rootVolume = crossStorageApi.find(defaultRepo, rootVolumeId, ServerVolume.class);
                    ScalewayHelperService.deleteVolume(rootVolume, crossStorageApi, defaultRepo, credential);
                } catch (Exception e) {
                    logger.error("Error deleting root volume : {}", rootVolumeId, e.getMessage());
                }
                // Additional Volumes
                if(server.getAdditionalVolumes()!= null) {
                    for (Map.Entry<String, ServerVolume> additionalVolumeEnt : server.getAdditionalVolumes().entrySet()) {
                        String additionalVolumeId = additionalVolumeEnt.getValue().getUuid();
                        try {
                            ServerVolume additionalVolume = crossStorageApi.find(defaultRepo, additionalVolumeId, ServerVolume.class);
                            ScalewayHelperService.deleteVolume(additionalVolume, crossStorageApi, defaultRepo, credential);
                        } catch(Exception e) {
                            logger.error("Error deleting additional volume : {}", additionalVolumeId, e.getMessage());
                        }
                    }
                }
            } else {
                throw new BusinessException("Unable to delete Server \n Volumes are still attached");
            }
        }

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/servers/"+serverId);

        Response response = CredentialHelperService.setCredential(target.request(), credential).delete();
        String value = response.readEntity(String.class);
        logger.info("response : {}", value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response:"+value);
        if (response.getStatus()<300) {
            logger.info("server : {} deleted at : {}", serverId, Instant.now());
            try {
                crossStorageApi.remove(defaultRepo, server.getUuid(), server.getCetCode());
            } catch (Exception e) {
                logger.error("error deleting server : {}", serverId, e.getMessage());
            }
        }
        response.close();
    }
}