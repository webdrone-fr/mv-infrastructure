package org.meveo.scaleway;

import java.util.ArrayList;
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
import org.meveo.model.customEntities.ServerVolume;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateScalewayVolume extends Script {


    private static final Logger logger = LoggerFactory.getLogger(UpdateScalewayVolume.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private  String SCALEWAY_URL = "api.scaleway.com";
    static final private String BASE_PATH = "/instance/v1/zones/";
    
    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        String action = parameters.get(CONTEXT_ACTION).toString();
        ServerVolume volume = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), ServerVolume.class);
        
        if (volume.getZone()==null) { //Required
            throw new BusinessException("Invalid Volume Zone");
        } else if(volume.getProviderSideId()==null) { //Required
            throw new BusinessException("Invalid Volume Provider-side ID");
        }
        
        String zone = volume.getZone();
        String volumeId = volume.getProviderSideId();
        logger.info("action : {}; volume : {}", action, volumeId);

        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("Using Credential {} with username {}", credential.getUuid(), credential.getUsername());
        }

        Map<String, Object> body = new HashMap<>();
        String volumeName = null; // nullable
        if(volume.getName()!= null) {
            volumeName = volume.getName();
        }

        // Volume type:  l_ssd or b_ssd
        String volumeType = volume.getVolumeType();
        // on l_ssd, only name can be changed
        if(volumeType.equalsIgnoreCase("l_ssd")) {
            body.put("name", volumeName);
        } else { // on b_ssd, 
            Long volumeSize = Long.valueOf(volume.getSize());
            // if volume attached to a Server, get Server details/ requirements
            if(volume.getServer()!= null){ // volume growing is supported; b_ssd only available fo DEV1, GP1 and RENDER offers
                String serverId = volume.getServer();
                String serverType = null;
                Long serverTotalVolumeSize = 0L;
                String serverRootVolumeId = null;
                List<String> serverAdditionalVolumesIds = new ArrayList<String>();
                ScalewayServer server = null;
                Boolean isServerRootVolume = false;
                try {
                    server = crossStorageApi.find(defaultRepo, ScalewayServer.class).by("providerSideId", serverId).getResult();
                    serverType = server.getServerType();
                } catch (Exception e){
                    logger.error("Error retrieving server : {}", serverId, e.getMessage());
                    throw new BusinessException("Unable to retrieve server");
                }
                // check if valid server type for b_ssd volume type
                if(serverType.startsWith("DEV1") || serverType.startsWith("GP1") || serverType.startsWith("RENDER")) {
                    // check if volume to update is root volume or additional volume
                    serverRootVolumeId = server.getRootVolume().getUuid();
                    if(volumeId.equalsIgnoreCase(serverRootVolumeId)) { // If volume to update is root volume
                        isServerRootVolume = true;
                    } else { // if volume to update is not root volume - must be additional volume
                        Map<String, ServerVolume> serverAdditionalVolumes = server.getAdditionalVolumes();
                        for (int i = 1; i < serverAdditionalVolumes.size(); i++) {
                            String serverAdditionalVolumeId = serverAdditionalVolumes.get(String.valueOf(i)).getUuid();
                            serverAdditionalVolumesIds.add(serverAdditionalVolumeId);
                        }
                    }

                    if (isServerRootVolume && server.getAdditionalVolumes()== null) { // if server only has one volume
                        serverTotalVolumeSize = volumeSize;
                    } else { // if server has more than one volume
                        try {
                            ServerVolume serverRootVolume = crossStorageApi.find(defaultRepo, serverRootVolumeId, ServerVolume.class);
                            serverTotalVolumeSize  = Long.valueOf(serverRootVolume.getSize()); 
                        } catch (Exception e) {
                            logger.error("error retrieving root volume", e.getMessage());
                        }
                        // remove volume to update from list of additional volumes
                        serverAdditionalVolumesIds.remove(volumeId);
                        // calc server total volume size - size of volume to update
                        for (String additionalVolumeId : serverAdditionalVolumesIds) {
                            try {
                                ServerVolume additionalVolume = crossStorageApi.find(defaultRepo, additionalVolumeId, ServerVolume.class);
                                serverTotalVolumeSize += Long.valueOf(additionalVolume.getSize());
                            } catch (Exception e) {
                                logger.error("error retrieving additional volume", e.getMessage());
                            }
                        }
                        // add new size of volume to update to server total volume size
                        serverTotalVolumeSize += volumeSize;
                    }
                    // get server type volume size constraints
                    JsonObject serverConstraintsObj = ScalewayHelperService.getServerTypeRequirements(server, credential);
                    Long serverMinVolumeSizeReq = serverConstraintsObj.get("volumes_constraint").getAsJsonObject().get("min_size").getAsLong();
                    Long serverMaxVolumeSizeReq = serverConstraintsObj.get("volumes_constraint").getAsJsonObject().get("max_size").getAsLong();

                    // check if new total volume size meets server type constraints
                    String serverTotalLocalVolumesSizeStr = Long.toString(serverTotalVolumeSize);
                    String serverMinVolumeSizeReqStr = Long.toString(serverMinVolumeSizeReq);
                    String serverMaxVolumeSizeReqStr = Long.toString(serverMaxVolumeSizeReq);
                    if (serverTotalVolumeSize < serverMinVolumeSizeReq) {
                        logger.debug("Current available volume size : {}, Minimum Volume size required for server type {} : {}", serverTotalLocalVolumesSizeStr , serverType, serverMinVolumeSizeReqStr);
                        throw new BusinessException("Current total volume size is too small for selected server type");
                    } else if (serverTotalVolumeSize > serverMaxVolumeSizeReq) {
                        logger.debug("Current available volume size : {}, Maximum Volume size allowed for server type {} : {}", serverTotalLocalVolumesSizeStr , serverType, serverMaxVolumeSizeReqStr);
                        throw new BusinessException("Current total volume size is too large for selected server type");
                    } else {
                        logger.info("Server Total Volume size : {}; Min Total Volume size : {}; Max Total Volume Size : {}", serverTotalLocalVolumesSizeStr, serverMinVolumeSizeReqStr, serverMaxVolumeSizeReqStr);
                    }
                } else {
                    logger.error("b_ssd volume type currently unavailable for server type : {}", serverType);
                    throw new BusinessException("Invalid volume type for server type : "+serverType);
                }
            }
            body.put("name", volumeName);
            body.put("size", volumeSize);
        }

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/volumes/"+volumeId);
        String resp = JacksonUtil.toStringPrettyPrinted(body);
        Response response = CredentialHelperService.setCredential(target.request("application/json"), credential)
            .method("PATCH", Entity.json(resp));
        String value = response.readEntity(String.class);
        logger.info("response : " + value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response:"+value);

        if(response.getStatus()<300) {
            JsonObject volumeObj = new JsonParser().parse(value).getAsJsonObject().get("volume").getAsJsonObject();
            volume = ScalewaySetters.setServerVolume(volumeObj, volume, crossStorageApi, defaultRepo);
            try {
                crossStorageApi.createOrUpdate(defaultRepo, volume);
            } catch (Exception e) {
                logger.error("error updating volume : {}", volumeId, e.getMessage());
            }
        }
        response.close();
    }
}