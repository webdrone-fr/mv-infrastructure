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
import org.meveo.model.customEntities.ServerVolume;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListScalewayVolumes extends Script{
    
    private static final Logger logger = LoggerFactory.getLogger(ListScalewayVolumes.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private  String SCALEWAY_URL = "api.scaleway.com";
    static final private String BASE_PATH = "/instance/v1/zones/";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        ServiceProvider provider = crossStorageApi.find(defaultRepo, ServiceProvider.class).by("code", "SCALEWAY").getResult();
        String action = null;
        if (parameters.get(CONTEXT_ACTION)!= null) {
            action = parameters.get(CONTEXT_ACTION).toString();
        } else {
            action = "listRefresh";
        }

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
            WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/volumes");
            Response response = CredentialHelperService.setCredential(target.request(), credential).get();
            String value = response.readEntity(String.class);
            logger.info("response : " + value);
            logger.debug("response status : {}", response.getStatus());
            if (response.getStatus() < 300) {
                JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().get("volumes").getAsJsonArray();
                for (JsonElement element : rootArray) {
                    JsonObject volumeObj = element.getAsJsonObject();
                    String volumeId = volumeObj.get("id").getAsString();
                    providerSideIds.add(volumeId);
                    ServerVolume volume = null;
                    try {
                        if(crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", volumeId).getResult() != null) {
                            volume = crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", volumeId).getResult();
                        } else {
                            volume = new ServerVolume();
                            volume.setUuid(volumeId);
                        }
                        volume = ScalewaySetters.setServerVolume(volumeObj, volume, crossStorageApi, defaultRepo);
                        crossStorageApi.createOrUpdate(defaultRepo, volume);
                    } catch (Exception e) {
                        logger.error("Error retrieving Volume : {}", volumeId, e.getMessage());
                    }
                }
            }
            // ScalewayHelperService.filterToLatestValues("ServerVolume", providerSideIds, crossStorageApi, defaultRepo);
            response.close();
        }
    }
}