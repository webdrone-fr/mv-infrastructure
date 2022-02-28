package org.meveo.scaleway;

import java.util.ArrayList;
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
                JsonObject serverTypesObj = new JsonParser().parse(value).getAsJsonObject().get("servers").getAsJsonObject();
                Map<String, String> serverTypes = new HashMap<String, String>();
                Set<Map.Entry<String, JsonElement>> entries = serverTypesObj.entrySet();
                for(Map.Entry<String, JsonElement> entry: entries) {
                    Map<String, Object> serverType = new HashMap<String, Object>();
                    JsonObject serverTypeObj = entry.getValue().getAsJsonObject();
                    serverType.put("hourly_price", serverTypeObj.get("hourly_price").getAsLong());
                    serverType.put("ncpus", serverTypeObj.get("ncpus").getAsLong());
                    serverType.put("ram", serverTypeObj.get("ram").getAsLong());
                    serverType.put("arch", serverTypeObj.get("arch").getAsString());
                    serverType.put("baremetal", serverTypeObj.get("baremetal").getAsBoolean());
                    if(!serverTypeObj.get("alt_names").isJsonNull()) {
                        JsonArray altNamesArr = serverTypeObj.get("alt_names").getAsJsonArray();
                        List<String> altNames = new ArrayList<String>();
                        for(JsonElement altName :altNamesArr){
                            altNames.add(altName.getAsString());
                        }
                        serverType.put("alt_names", altNames);
                    }
                    if(!serverTypeObj.get("per_volume_constraint").isJsonNull()) {
                        Map<String, Object> perVolumeConstraint = new HashMap<String, Object>();
                        JsonObject perVolumeConstraintObj = serverTypeObj.get("per_volume_constraint").getAsJsonObject();
                        Set<Map.Entry<String, JsonElement>> perVolumeConstraintEntries = perVolumeConstraintObj.entrySet();
                        for(Map.Entry<String, JsonElement> perVolumeConstraintEntry : perVolumeConstraintEntries) {
                            Map<String, Long> perVolumeConstraints = new HashMap<String, Long>();
                            JsonObject volumeConstraintsObj = perVolumeConstraintEntry.getValue().getAsJsonObject();
                            perVolumeConstraints.put("min_size", volumeConstraintsObj.get("min_size").getAsLong());
                            perVolumeConstraints.put("max_size", volumeConstraintsObj.get("max_size").getAsLong());
                            perVolumeConstraint.put(perVolumeConstraintEntry.getKey(), perVolumeConstraints);
                        }
                        serverType.put("per_volume_constraint", perVolumeConstraint);
                    }
                    if(!serverTypeObj.get("volumes_constraint").isJsonNull()) {
                        Map<String, Long> volumesConstraint = new HashMap<String, Long>();
                        JsonObject volumesConstraintObj = serverTypeObj.get("volumes_constraint").getAsJsonObject();
                        volumesConstraint.put("min_size", volumesConstraintObj.get("min_size").getAsLong());
                        volumesConstraint.put("max_size", volumesConstraintObj.get("max_size").getAsLong());
                        serverType.put("volumes_constraint", volumesConstraint);
                    }
                    if(!serverTypeObj.get("gpu").isJsonNull()){
                        serverType.put("gpu", serverTypeObj.get("gpu").getAsLong());
                    }
                    if (!serverTypeObj.get("network").isJsonNull()) {
                        Map<String, Object> network = new HashMap<String, Object>();
                        JsonObject networkObj = serverTypeObj.get("network").getAsJsonObject();
                        JsonArray interfacesArr = networkObj.get("interfaces").getAsJsonArray();
                        List<Object> interfaces = new ArrayList<Object>();
                        for (JsonElement interfaceEl : interfacesArr) {
                            Map<String, Long> networkInterface = new HashMap<String, Long>();
                            JsonObject interfaceObj = interfaceEl.getAsJsonObject();
                            if(!interfaceObj.get("internal_bandwidth").isJsonNull()) {
                                networkInterface.put("internal_bandwidth", interfaceObj.get("internal_bandwidth").getAsLong());
                            }
                            if(!interfaceObj.get("internet_bandwidth").isJsonNull()) {
                                networkInterface.put("internet_bandwidth", interfaceObj.get("internet_bandwidth").getAsLong());
                            }
                            interfaces.add(networkInterface);
                        }
                        network.put("interfaces", interfaces);
                        if(!networkObj.get("sum_internal_bandwidth").isJsonNull()) {
                            network.put("sum_internal_bandwidth", networkObj.get("sum_internal_bandwidth").getAsLong());
                        }
                        if(!networkObj.get("sum_internet_bandwidth").isJsonNull()) {
                            network.put("sum_internet_bandwidth", networkObj.get("sum_internet_bandwidth").getAsLong());
                        }
                        network.put("ipv6_support", networkObj.get("ipv6_support").getAsBoolean());
                        serverType.put("network", network);
                    }
                    serverTypes.put(entry.getKey(), JacksonUtil.toStringPrettyPrinted(serverType));
                }
                provider.setServerType(serverTypes);
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