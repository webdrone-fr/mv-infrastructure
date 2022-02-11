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
import org.meveo.model.customEntities.Bootscript;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.Server;
import org.meveo.model.customEntities.ServerImage;
import org.meveo.model.customEntities.ServerVolume;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListScalewayImages extends Script{
    
    private static final Logger logger = LoggerFactory.getLogger(ListScalewayImages.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private  String SCALEWAY_URL = "api.scaleway.com";
    static final private String BASE_PATH = "/instance/v1/zones/";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {

        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("Using Credential {} with username {}", credential.getUuid(), credential.getUsername());
        }

        String[] zones = new String[] {"fr-par-1", "fr-par-2", "fr-par-3", "nl-ams-1", "pl-waw-1"};
        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        for (String zone : zones) {
            WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/images");
            Response response = CredentialHelperService.setCredential(target.request(), credential).get();
            String value = response.readEntity(String.class);
            logger.info("response : " + value);
            logger.debug("response status : {}", response.getStatus());
            if (response.getStatus() < 300) {
                JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().get("images").getAsJsonArray();
                for (JsonElement element : rootArray) {
                    JsonObject imageObj = element.getAsJsonObject();
                    ServerImage serverImage = new ServerImage();
                    serverImage.setName(imageObj.get("name").getAsString());
                    serverImage.setUuid(imageObj.get("id").getAsString());
                    serverImage.setProviderSideId(imageObj.get("id").getAsString());
                    serverImage.setCreationDate(OffsetDateTime.parse(imageObj.get("creation_date").getAsString()).toInstant());
                    serverImage.setLastUpdated(OffsetDateTime.parse(imageObj.get("modification_date").getAsString()).toInstant());
                    // Server
                    if (!imageObj.get("from_server").isJsonNull()) {
                        String serverId = imageObj.get("from_server").getAsString();
                        try {
                            Server server = crossStorageApi.find(defaultRepo, Server.class).by("providerSideId", serverId).getResult();
                            serverImage.setFromServer(server);
                        } catch (Exception e) {
                            logger.error("Error retriving server attached to image : {}", serverImage.getUuid(), e.getMessage());
                        }
                    }
                    serverImage.setProject(imageObj.get("project").getAsString());
                    serverImage.setIsPublic(imageObj.get("public").getAsBoolean());
                    // Volumes
                    // Root Volume
                    if (!imageObj.get("root_volume").isJsonNull()) {
                        String rootVolumeId = imageObj.get("root_volume").getAsJsonObject().get("id").getAsString();
                        try {
                            ServerVolume rootVolume = crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", rootVolumeId).getResult();
                            serverImage.setRootVolume(rootVolume);
                        } catch (Exception e) {
                            logger.error("Error retriving root volume attached to image : {}", serverImage.getUuid(), e.getMessage());
                        }
                    }
                    // Additional Volumes
                    if (!imageObj.get("extra_volumes").isJsonNull()) {
                        Map<String, ServerVolume> additionalVolumes = new HashMap<String, ServerVolume>();
                        JsonObject additionalVolumesObj = imageObj.get("extra_volumes").getAsJsonObject();
                        for (int i = 1; i < additionalVolumesObj.entrySet().size(); i++) {
                            String additionalVolumeId = additionalVolumesObj.get(String.valueOf(i)).getAsJsonObject().get("id").getAsString();
                            try {
                                ServerVolume additionalVolume = crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", additionalVolumeId).getResult();
                                additionalVolumes.put(String.valueOf(i), additionalVolume);
                            } catch (Exception e) {
                                logger.error("Error retriving additional volume attached to image : {}", serverImage.getUuid(), e.getMessage());
                            }
                        }
                        serverImage.setAdditionalVolumes(additionalVolumes);
                    }
                    // Bootscript
                    if (!imageObj.get("default_bootscript").isJsonNull()) {
                        String bootscriptId = imageObj.get("default_bootscript").getAsJsonObject().get("id").getAsString();
                        try {
                            Bootscript bootscript = crossStorageApi.find(defaultRepo, Bootscript.class).by("providerSideId", bootscriptId).getResult();
                            serverImage.setDefaultBootscript(bootscript);
                        } catch (Exception e) {
                            logger.error("Error retriving bootscript attached to image : {}", serverImage.getUuid(), e.getMessage());
                        }
                    }
                    serverImage.setZone(imageObj.get("zone").getAsString());
                    serverImage.setState(imageObj.get("state").getAsString());
                    if (!imageObj.get("tags").isJsonNull()) {
                        ArrayList<String> imageTags = new ArrayList<String>();
                        JsonArray imageTagsArr = imageObj.get("tags").getAsJsonArray();
                        for (JsonElement tag : imageTagsArr) {
                            imageTags.add(tag.getAsString());
                        }
                        serverImage.setTags(imageTags);
                    }
                    try {
                        crossStorageApi.createOrUpdate(defaultRepo, serverImage);
                    } catch (Exception e) {
                        logger.error("Error retrieving Server Image {} : {}", serverImage.getName(), e.getMessage());
                    }
                }
            }
            response.close();
        }
    }
}
