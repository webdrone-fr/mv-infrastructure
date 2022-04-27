package org.meveo.scaleway;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import com.google.gson.*;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.ServerVolume;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateScalewayVolume extends Script{
    

    private static final Logger logger = LoggerFactory.getLogger(CreateScalewayVolume.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private  String SCALEWAY_URL = "api.scaleway.com";
    static final private String BASE_PATH = "/instance/v1/zones/";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        String action = parameters.get(CONTEXT_ACTION).toString();
        ServiceProvider provider = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), ServiceProvider.class);
        ServerVolume volume = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), ServerVolume.class);

        if (volume.getName() == null) {
            throw new BusinessException("Invalid Volume Name");
        } else if (volume.getVolumeType() == null) {
            throw new BusinessException("Invalid Volume Type");
        }else if (volume.getZone() == null) {
            throw new BusinessException("Invalid Volume Zone"); // required
        } else if (volume.getSize() == null && volume.getBaseVolume() == null) {
            throw new BusinessException("One of Volume Size or Base Volume can be selected");
        } else if(volume.getSize()!= null && volume.getBaseVolume()!= null) {
            throw new BusinessException("Only one of Volume Size or Base Volume can be selected");
        }

        String zone = volume.getZone(); // required for path
        logger.info("Action : {}, Volume Uuid : {}", action, volume.getUuid());

        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("using Credential {} with username {}", credential.getUuid(), credential.getUsername());
        }

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());

        // Volume Size check
        // Need to check Min/ Max values for volume type
        String volumeType = volume.getVolumeType();
        Long volumeTypeMinSize = 0L;
        Long volumeTypeMaxSize = 1L;

        WebTarget volumesTarget = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/products/volumes");
        Response volumesResponse = CredentialHelperService.setCredential(volumesTarget.request(), credential).get();
        String volumesValue = volumesResponse.readEntity(String.class);
        if (volumesResponse.getStatus()<300) {
            JsonObject volumesObj = new JsonParser().parse(volumesValue).getAsJsonObject().get("volumes").getAsJsonObject();
            JsonObject volumesConstraints = volumesObj.get(volumeType).getAsJsonObject().get("constraints").getAsJsonObject();
            volumeTypeMinSize = volumesConstraints.get("min").getAsLong();
            volumeTypeMaxSize = volumesConstraints.get("max").getAsLong();
        }

        // Volume Creation
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/volumes");
        Map<String, Object> body = new HashMap<>();
        body.put("name", volume.getName());
        body.put("volume_type", volumeType);

        // Only 1 of volume size and base_volume can be set
        // base_volume is id of volume whose parameters are to "copy"
        if(volume.getSize()!= null) {
            // Size
            Long volumeSize = Long.valueOf(volume.getSize());
            if (volumeSize >= volumeTypeMinSize && volumeSize < volumeTypeMaxSize) {
                logger.debug("volume size = {}, volume size long = {} ", volume.getSize(), volumeSize);
                body.put("size", volumeSize); // check output after size formating
            }
        } else if (volume.getBaseVolume()!= null) {
            String baseVolumeId = volume.getBaseVolume();
            try {
                ServerVolume baseVolume = crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", baseVolumeId).getResult();
                if (baseVolume.getVolumeType()!= volume.getVolumeType()) {
                    logger.error("Invalid volume type : {} selected, base volume type is :{}", volume.getVolumeType(), baseVolume.getVolumeType());
                }
                // Long baseVolumeSize = Long.valueOf(baseVolume.getSize());
                // if (baseVolumeSize >= volumeTypeMinSize && baseVolumeSize < volumeTypeMaxSize) {
                //     logger.debug("base volume size = {}, volume size long = {} ", volume.getSize(), baseVolumeSize);
                // }
            } catch (Exception e) {
               throw new BusinessException("Invalid base volume for volume type selected");
            }
            body.put("base_volume", volume.getBaseVolume());
        }

        // Project
        // Webdrone ID = 6a0c2ca8-917a-418a-90a3-05949b55a7ae
        // String projectId = "6a0c2ca8-917a-418a-90a3-05949b55a7ae";
        String projectId = provider.getOrganization().get("Webdrone");
        // if (volume.getProject() != null) {
        //     projectId = volume.getProject();
        // }
        body.put("project", projectId);

        String resp = JacksonUtil.toStringPrettyPrinted(body);
        Response response = 
            CredentialHelperService.setCredential(target.request("application/json"), credential)
            .post(Entity.json(resp));
        String value = response.readEntity(String.class);
        logger.info("response : {}", value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response: "+value);
        if (response.getStatus()<300) {
            JsonObject volumeObj = new JsonParser().parse(value).getAsJsonObject().get("volume").getAsJsonObject();
            volume = ScalewaySetters.setServerVolume(volumeObj, volume, crossStorageApi, defaultRepo);
            try {
                crossStorageApi.createOrUpdate(defaultRepo, volume);
            } catch (Exception e) {
                logger.error("error creating volume : {}", volume.getUuid(), e.getMessage());
            }
        }
        response.close();
    }
}