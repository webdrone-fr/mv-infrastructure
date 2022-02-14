package org.meveo.scaleway;

import java.time.Instant;
import java.time.OffsetDateTime;
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
import org.meveo.model.customEntities.Bootscript;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.Server;
import org.meveo.model.customEntities.ServerImage;
import org.meveo.model.customEntities.ServerVolume;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateScalewayImage extends Script {
    

    private static final Logger logger = LoggerFactory.getLogger(CreateScalewayImage.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private  String SCALEWAY_URL = "api.scaleway.com";
    static final private String BASE_PATH = "/instance/v1/zones/";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        String action = parameters.get(CONTEXT_ACTION).toString();
        ServerImage serverImage = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), ServerImage.class);

        if (serverImage.getZone() == null) {
            throw new BusinessException("Invalid Server Image Zone");
        } else if (serverImage.getRootVolume() == null) {
            throw new BusinessException("Invalid Server Image Root Volume");
        } else if (serverImage.getArch() == null) {
            throw new BusinessException("Invalid Server Image Arch");
        }

        String zone = serverImage.getZone();
        logger.info("Action : {}, Server Image Uuid : {}", action, serverImage.getUuid());
        
        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("using credential {}({}) with username {}", credential.getDomainName(), credential.getUuid(), credential.getUsername());
        }

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/images");

        Map<String, Object> body = new HashMap<String, Object>();
        body.put("name", serverImage.getName());
        body.put("arch", serverImage.getArch());
        body.put("public", serverImage.getIsPublic());

        // Project
        // Webdrone ID = 6a0c2ca8-917a-418a-90a3-05949b55a7ae
        String projectId = "6a0c2ca8-917a-418a-90a3-05949b55a7ae";
        if (serverImage.getProject() != null) {
            projectId = serverImage.getProject();
        }
        body.put("project", projectId);

        // Root Volume - UUID of the snapshot TODO
        ServerVolume serverImageRootVolume = serverImage.getRootVolume();
        try {
            String serverImageRootVolumeId = crossStorageApi.find(defaultRepo, serverImageRootVolume.getUuid(), ServerVolume.class).getProviderSideId();
            body.put("root_volume", serverImageRootVolumeId);
        } catch (Exception e) {
            logger.error("Error retrieving root volume {}", e.getMessage());
        }
        // Additional Volumes
        if (serverImage.getAdditionalVolumes() != null) {
            Map<String, ServerVolume> imageAdditonalVolumes = serverImage.getAdditionalVolumes();
            Map<String, Object> imageAdditonalVolumesObj = new HashMap<String, Object>();
            for (int i = 0; i < imageAdditonalVolumes.entrySet().size(); i++) {
                ServerVolume additionalVolume = null;
                Map<String, Object> additionalVolumeObj = new HashMap<String, Object>();
                try {
                    additionalVolume = crossStorageApi.find(defaultRepo, serverImage.getAdditionalVolumes().get(String.valueOf(i)).getUuid(), ServerVolume.class);
                    additionalVolumeObj.put("id", additionalVolume.getProviderSideId());
                    additionalVolumeObj.put("name", additionalVolume.getName());
                    additionalVolumeObj.put("size", additionalVolume.getSize());
                    additionalVolumeObj.put("volume_type", additionalVolume.getVolumeType());
                    // Webdrone ID = 6a0c2ca8-917a-418a-90a3-05949b55a7ae
                    additionalVolumeObj.put("project", projectId);
                    imageAdditonalVolumesObj.put(String.valueOf(i), additionalVolumeObj);
                } catch (Exception e) {
                    logger.error("Error retrieving additional volumes {}", e.getMessage());
                }
            }
            body.put("extra_volumes", imageAdditonalVolumesObj);
        }
        // Tags
        if (serverImage.getTags() != null) {
            List<String> imageTags = serverImage.getTags();
            JsonArray imageTagsArr = new JsonArray();
            for (String tag : imageTags) {
                imageTagsArr.add(tag);
            }
            body.put("tags", imageTagsArr);
        }

        String resp = JacksonUtil.toStringPrettyPrinted(body);
        Response response = 
            CredentialHelperService.setCredential(target.request("application/json"), credential)
            .post(Entity.json(resp));
        
        String value = response.readEntity(String.class);
        logger.info("response : {}", value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response: "+value);
        
        if (response.getStatus() < 300) {
            JsonObject serverImageObj = new JsonParser().parse(value).getAsJsonObject().get("image").getAsJsonObject();

            // Basic values
            serverImage.setProviderSideId(serverImageObj.get("id").getAsString());
            serverImage.setUuid(serverImageObj.get("id").getAsString());
            serverImage.setCreationDate(Instant.now());
            serverImage.setLastUpdated(Instant.now());
            serverImage.setName(serverImageObj.get("name").getAsString());
            serverImage.setArch(serverImageObj.get("arch").getAsString());
            serverImage.setOrganization(serverImageObj.get("organization").getAsString());
            serverImage.setIsPublic(serverImageObj.get("public").getAsBoolean());
            serverImage.setState(serverImageObj.get("state").getAsString());
            serverImage.setProject(serverImageObj.get("project").getAsString());
            serverImage.setZone(serverImageObj.get("zone").getAsString());

            // Default Bootscript
            if (!serverImageObj.get("default_bootscript").isJsonNull()) {
                String defaultBootscriptId = serverImageObj.get("default_bootscript").getAsString();
                try {
                    Bootscript defaultBootscript = crossStorageApi.find(defaultRepo, Bootscript.class).by("providerSideId", defaultBootscriptId).getResult();
                    serverImage.setDefaultBootscript(defaultBootscript);
                } catch (Exception e) {
                    logger.error("error retrieving default bootscript for server image {} : {}", serverImage.getUuid(), e.getMessage());
                }
            }

            // Root Volume
            if(!serverImageObj.get("root_volume").isJsonNull()) {
                JsonObject rootVolumeObj = serverImageObj.get("root_volume").getAsJsonObject();
                String rootVolumeId = rootVolumeObj.get("id").getAsString();
                if (crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", rootVolumeId).getResult() != null) {
                    try {
                        ServerVolume rootVolume = crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", rootVolumeId).getResult();
                        serverImage.setRootVolume(rootVolume);
                    } catch (Exception e) {
                        logger.error("error retrieving root volume {} : {}", rootVolumeId, e.getMessage());
                    }
                } else {
                    ServerVolume newRootVolume = new ServerVolume();
                    newRootVolume.setCreationDate(OffsetDateTime.parse(rootVolumeObj.get("creation_date").getAsString()).toInstant());
                    newRootVolume.setLastUpdated(OffsetDateTime.parse(rootVolumeObj.get("modification_date").getAsString()).toInstant()); // Or set to now?
                    newRootVolume.setProviderSideId(rootVolumeObj.get("id").getAsString());
                    newRootVolume.setUuid(rootVolumeObj.get("id").getAsString());
                    newRootVolume.setName(rootVolumeObj.get("name").getAsString());
                    newRootVolume.setSize(String.valueOf(rootVolumeObj.get("size").getAsLong()));
                    newRootVolume.setZone(zone);
                    newRootVolume.setVolumeType(rootVolumeObj.get("volume_type").getAsString());
                    // TODO mising server, isBoot, state
                    try {
                        crossStorageApi.createOrUpdate(defaultRepo, newRootVolume);
                        serverImage.setRootVolume(newRootVolume);
                    } catch (Exception e) {
                        logger.error("error creating root volume {} : {}", rootVolumeId, e.getMessage());
                    }
                }
            }

            // Extra Volumes
            if(!serverImageObj.get("extra_volumes").isJsonNull()) {
                Map <String, ServerVolume> additionalVolumes = new HashMap<String, ServerVolume>();
                JsonObject additionalVolumesObj = serverImageObj.get("extra_volumes").getAsJsonObject();
                for(int i = 0; i < additionalVolumesObj.entrySet().size(); i++) {
                    String additionalVolumeId = additionalVolumesObj.get(String.valueOf(i)).getAsJsonObject().get("id").getAsString();
                    if(crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", additionalVolumeId).getResult() != null) {
                        try {
                            ServerVolume additionalVolume = crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", additionalVolumeId).getResult();
                            additionalVolumes.put(String.valueOf(i), additionalVolume);
                        } catch (Exception e) {
                            logger.error("error retrieving additional volume {} : {}", additionalVolumeId, e.getMessage());
                        }
                    } else {
                        JsonObject additionalVolumeObj = additionalVolumesObj.get(String.valueOf(i)).getAsJsonObject();
                        ServerVolume newAdditionalVolume = new ServerVolume();
                        newAdditionalVolume.setCreationDate(OffsetDateTime.parse(additionalVolumeObj.get("creation_date").getAsString()).toInstant());
                        newAdditionalVolume.setLastUpdated(OffsetDateTime.parse(additionalVolumeObj.get("modification_date").getAsString()).toInstant()); // Or set to now?
                        newAdditionalVolume.setProviderSideId(additionalVolumeObj.get("id").getAsString());
                        newAdditionalVolume.setUuid(additionalVolumeObj.get("id").getAsString());
                        newAdditionalVolume.setName(additionalVolumeObj.get("name").getAsString());
                        newAdditionalVolume.setSize(String.valueOf(additionalVolumeObj.get("size").getAsLong()));
                        newAdditionalVolume.setZone(additionalVolumeObj.get("zone").getAsString());
                        newAdditionalVolume.setVolumeType(additionalVolumeObj.get("volume_type").getAsString());
                        newAdditionalVolume.setServer(additionalVolumeObj.get("server").getAsJsonObject().get("id").getAsString());
                        newAdditionalVolume.setState(additionalVolumeObj.get("state").getAsString());
                        try {
                            crossStorageApi.createOrUpdate(defaultRepo, newAdditionalVolume);
                            additionalVolumes.put(String.valueOf(i), newAdditionalVolume);
                        } catch (Exception e) {
                            logger.error("error creating additional volume {} : {}", newAdditionalVolume.getUuid(), e.getMessage());
                        }
                    }
                }
            }

            // From Server
            if (!serverImageObj.get("from_server").isJsonNull()) {
                String serverId = serverImageObj.get("from_server").getAsString();
                try {
                    Server server = crossStorageApi.find(defaultRepo, Server.class).by("providerSideId", serverId).getResult();
                    serverImage.setFromServer(server);
                } catch (Exception e) {
                    logger.error("error retrieving source server {} : {}", serverId, e.getMessage());
                }
            }
            
            // Tags
            if (!serverImageObj.get("tags").isJsonNull()) {
                JsonArray imageTagsArr = serverImageObj.get("tags").getAsJsonArray();
                ArrayList<String> imageTags = new ArrayList<String>();
                for (JsonElement tag : imageTagsArr) {
                    imageTags.add(tag.getAsString());
                }
                serverImage.setTags(imageTags);
            }

            try {
                crossStorageApi.createOrUpdate(defaultRepo, serverImage);
                logger.info("Server Image : {} successfully created", serverImage.getName());
            } catch (Exception e) {
                logger.error("error creating server image {} : {}", serverImage.getUuid(), e.getMessage());
            }
        }
    }
}