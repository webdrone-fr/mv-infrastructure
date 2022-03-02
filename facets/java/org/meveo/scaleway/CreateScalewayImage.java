package org.meveo.scaleway;

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
            JsonObject imageObj = new JsonParser().parse(value).getAsJsonObject().get("image").getAsJsonObject();
            serverImage = ScalewaySetters.setServerImage(imageObj, serverImage, crossStorageApi, defaultRepo);
            try {
                crossStorageApi.createOrUpdate(defaultRepo, serverImage);
                logger.info("Server Image : {} successfully created", serverImage.getName());
            } catch (Exception e) {
                logger.error("error creating server image {} : {}", serverImage.getUuid(), e.getMessage());
            }
        }
    }
}