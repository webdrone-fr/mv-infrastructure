package org.meveo.scaleway;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import com.google.gson.*;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.MeveoMatrix;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListScalewayServerTypes extends Script{
    
    private static final Logger logger = LoggerFactory.getLogger(ListScalewayServerTypes.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private  String SCALEWAY_URL = "api.scaleway.com";
    static final private String BASE_PATH = "/instance/v1/zones/";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        String action = parameters.get(CONTEXT_ACTION).toString();
        ServiceProvider provider = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), ServiceProvider.class);

        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("Using Credential {} with username {}", credential.getUuid(), credential.getUsername());
        }

        // String[] zones = new String[] {"fr-par-1", "fr-par-2", "fr-par-3", "nl-ams-1", "pl-waw-1"};
        List<String> zones = provider.getZones();
        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        for(String zone : zones) {
            WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/products/servers");
            Response response = CredentialHelperService.setCredential(target.request(), credential).get();
            String value = response.readEntity(String.class);
            logger.info("response : " + value);
            logger.debug("response status : {}", response.getStatus());
            if (response.getStatus() < 300) {
                MeveoMatrix<String> serverTypesMatrix = new MeveoMatrix<String>();
                JsonObject serverTypesObj = new JsonParser().parse(value).getAsJsonObject().get("servers").getAsJsonObject();
                Set<Map.Entry<String, JsonElement>> entries = serverTypesObj.entrySet();
                // Map<String, String> serverTypes = new HashMap<String, String>();
                // for(Map.Entry<String, JsonElement> entry: entries) {
                //     JsonObject serverTypeObj = entry.getValue().getAsJsonObject();
                //     Map<String, Object> serverType = ScalewaySetters.setServerType(serverTypeObj);
                //     serverTypes.put(entry.getKey(), JacksonUtil.toStringPrettyPrinted(serverType));
                // }
                // provider.setServerType(serverTypes);
                for(Map.Entry<String, JsonElement> entry : entries) {
                    JsonObject serverTypeObj =  entry.getValue().getAsJsonObject();
                    Map<String, Object> serverType = ScalewaySetters.setServerType(serverTypeObj);
    
                    String ram = serverType.get("ram").toString();
                    String disk = serverType.get("volumes_constraint").toString();
                    String ncpus = serverType.get("ncpus").toString();
                    String name = entry.getKey();
                    serverTypesMatrix.set(zone, ram, disk, ncpus, name, JacksonUtil.toStringPrettyPrinted(serverType));
                }

                provider.setProviderServerTypes(serverTypesMatrix);
                try {
                    crossStorageApi.createOrUpdate(defaultRepo, provider);
                } catch (Exception e) {
                    logger.error("Error retrieving Server Types for provider : {}", provider.getCode(), e.getMessage());
                }
            }
            response.close();
        }
    }
}