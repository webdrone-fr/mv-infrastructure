package org.meveo.scaleway;

import java.util.ArrayList;
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
import org.meveo.model.customEntities.ServiceProvider;
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
        String action = parameters.get(CONTEXT_ACTION).toString();
        ServiceProvider provider = crossStorageApi.find(defaultRepo, ServiceProvider.class).by("code", "SCALEWAY").getResult();

        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("Using Credential {} with username {}", credential.getUuid(), credential.getUsername());
        }

        // String[] zones = new String[] {"fr-par-1", "fr-par-2", "fr-par-3", "nl-ams-1", "pl-waw-1"};
        List<String> zones = provider.getZones();
        List<String> providerSideIds = new ArrayList<String>();
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
                    String imageId = imageObj.get("id").getAsString();
                    providerSideIds.add(imageId);
                    ServerImage image = null;
                    try {
                        if(crossStorageApi.find(defaultRepo, ServerImage.class).by("providerSideId", imageId).getResult() != null) {
                            image = crossStorageApi.find(defaultRepo, ServerImage.class).by("providerSideId", imageId).getResult();
                        } else {
                            image = new ServerImage();
                            image.setUuid(imageId);
                        }
                        image = ScalewaySetters.setServerImage(imageObj, image, crossStorageApi, defaultRepo);
                        crossStorageApi.createOrUpdate(defaultRepo, image);
                    } catch (Exception e) {
                        logger.error("Error retrieving Image : {}", imageId, e.getMessage());
                    }
                }
            }
            // ScalewayHelperService.filterToLatestValues("ServerImage", providerSideIds, crossStorageApi, defaultRepo);
            response.close();
        }
    }
}