package org.meveo.scaleway;

import java.time.Instant;
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
import org.meveo.model.customEntities.CustomEntityInstance;
import org.meveo.model.customEntities.ScalewayServer;
import org.meveo.model.customEntities.ServerVolume;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScalewayHelperService extends Script{
    

    private static final Logger logger = LoggerFactory.getLogger(ScalewayHelperService.class);

    static final private String SCALEWAY_URL = "api.scaleway.com";
    static final private String BASE_PATH = "/instance/v1/zones/";

    public static Long calcServerTotalVolumesSize(ScalewayServer server, CrossStorageApi crossStorageApiInstance, Repository defaultRepo) {
        Long serverTotalVolumesSize = 0L;
        ArrayList<Long> allVolumesSizes = new ArrayList<Long>();
        // Root volume
        try {
            String serverRootVolumeId = server.getRootVolume().getUuid();
            ServerVolume serverRootVolume = crossStorageApiInstance.find(defaultRepo, serverRootVolumeId, ServerVolume.class);
            Long rootVolumeSize = Long.valueOf(serverRootVolume.getSize());
            allVolumesSizes.add(rootVolumeSize);
        } catch (Exception e) {
            logger.error("Error retrieving root volume", e.getMessage());
        }
        // Additional volumes
        if (server.getAdditionalVolumes() != null){
            Map<String, ServerVolume> serverAdditionalVolumes = server.getAdditionalVolumes();
            for (Map.Entry<String, ServerVolume> serverAdditionalVolumeEnt : serverAdditionalVolumes.entrySet()) {
                try {
                    String serverAdditionalVolumeId = serverAdditionalVolumes.get(serverAdditionalVolumeEnt.getKey()).getUuid();
                    ServerVolume serverAdditionalVolume = crossStorageApiInstance.find(defaultRepo, serverAdditionalVolumeId, ServerVolume.class);
                    Long serverAdditionalVolumeSize = Long.valueOf(serverAdditionalVolume.getSize());
                    allVolumesSizes.add(serverAdditionalVolumeSize);
                } catch (Exception e) {
                    logger.error("Error retrieving additional volumes", e.getMessage());
                }
            }
        }
        // Sum of all values
        for (Long volumeSize : allVolumesSizes) {
            serverTotalVolumesSize += volumeSize;
        }
        return serverTotalVolumesSize;
    }

    public static Long calcServerTotalLocalVolumesSize(ScalewayServer server, CrossStorageApi crossStorageApiInstance, Repository defaultRepo) {
        Long serverTotalLocalVolumesSize = 0L;
        ArrayList<Long> allLocalVolumesSizes = new ArrayList<Long>();
        // Root volume
        try {
            String serverRootVolumeId = server.getRootVolume().getUuid();
            ServerVolume serverRootVolume = crossStorageApiInstance.find(defaultRepo, serverRootVolumeId, ServerVolume.class);
            String serverRootVolumeType = serverRootVolume.getVolumeType();
            if(serverRootVolumeType.equalsIgnoreCase("l_ssd")){
                Long rootVolumeSize = Long.valueOf(serverRootVolume.getSize());
                allLocalVolumesSizes.add(rootVolumeSize);
            }
        } catch (Exception e) {
            logger.error("Error retrieving local root volume", e.getMessage());
        }
        // Additional volumes
        if (server.getAdditionalVolumes() != null){
            Map<String, ServerVolume> serverAdditionalVolumes = server.getAdditionalVolumes();
            for (Map.Entry<String, ServerVolume> serverAdditionalVolumeEnt : serverAdditionalVolumes.entrySet()) {
                try {
                    String serverAdditionalVolumeId = serverAdditionalVolumes.get(serverAdditionalVolumeEnt.getKey()).getUuid();
                    ServerVolume serverAdditionalVolume = crossStorageApiInstance.find(defaultRepo, serverAdditionalVolumeId, ServerVolume.class);
                    String serverAdditionalVolumeType = serverAdditionalVolume.getVolumeType();
                    if (serverAdditionalVolumeType.equalsIgnoreCase("l_ssd")) {
                        Long serverAdditionalVolumeSize = Long.valueOf(serverAdditionalVolume.getSize());
                        allLocalVolumesSizes.add(serverAdditionalVolumeSize);
                    }
                } catch (Exception e) {
                    logger.error("Error retrieving local additional volumes {}", e.getMessage());
                }
            }
        }
        // Sum of all values
        for (Long volumeSize : allLocalVolumesSizes) {
            serverTotalLocalVolumesSize += volumeSize;
        }
        return serverTotalLocalVolumesSize;
    }

    public static JsonObject getServerTypeRequirements(ScalewayServer server, Credential credential) throws BusinessException {
        JsonObject serverConstraints = new JsonObject();
        String zone = server.getZone();
        String serverType = server.getServerType();
        
        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/products/servers");
        Response response = CredentialHelperService.setCredential(target.request("application/json"), credential).get();
        String value = response.readEntity(String.class);
        if (response.getStatus()<300) {
            serverConstraints = 
                new JsonParser().parse(value).getAsJsonObject()
                    .get("servers").getAsJsonObject()
                    .get(serverType).getAsJsonObject();
        } else {
            throw new BusinessException("Error retrieving Server type constraints");
        }
        response.close();
        return serverConstraints;
    }

    public static JsonObject getServerTypeAvailabilityInZone(String zone, Credential credential) throws BusinessException {
        JsonObject serverAvailabilityObj = new JsonObject();

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/products/servers/availability");
        Response response = CredentialHelperService.setCredential(target.request("application/json"), credential).get();
        String value = response.readEntity(String.class);
        if (response.getStatus()<300) {
            serverAvailabilityObj = new JsonParser().parse(value).getAsJsonObject().get("servers").getAsJsonObject();
        } else {
            throw new BusinessException("Error retrieving Server type availability");
        }
        response.close();
        return serverAvailabilityObj;
    }

    public static String getServerUserData(String zone, String serverId, String key, Credential credential) throws BusinessException {
        String serverUserDataStr = null;

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/servers/"+serverId+"/user_data/"+key);
        Response response = CredentialHelperService.setCredential(target.request("application/json"), credential).get();
        String value = response.readEntity(String.class);
        logger.debug("server user data value: {}", value);

        if(response.getStatus()<300) {
            serverUserDataStr = value;
        } else {
            throw new BusinessException("Error retrieving Server : "+serverId+" User data");
        }
        response.close();
        return serverUserDataStr;
    }

    public static JsonObject getServerDetails(String zone, String serverId, Credential credential) throws BusinessException {
        JsonObject serverDetailsObj = new JsonObject();

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/servers/"+serverId);
        Response response = CredentialHelperService.setCredential(target.request("application/json"), credential).get();
        String value = response.readEntity(String.class);

        if(response.getStatus()<300) {
            serverDetailsObj = new JsonParser().parse(value).getAsJsonObject().get("server").getAsJsonObject();
        } else {
            throw new BusinessException("Error retrieving Details for Server : "+serverId);
        }
        response.close();
        return serverDetailsObj;
    }

    public static JsonObject getProviderServerTypes(String zone, ServiceProvider provider, Credential credential) throws BusinessException {
        JsonObject serverTypesObj = new JsonObject();
        String providerId = provider.getUuid();

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/products/servers");
        Response response = CredentialHelperService.setCredential(target.request(), credential).get();
        String value = response.readEntity(String.class);

        if (response.getStatus() < 300) {
        serverTypesObj = new JsonParser().parse(value).getAsJsonObject().get("servers").getAsJsonObject();
        } else {
            throw new BusinessException("Error retrieving server types for provider : "+providerId);
        }
        response.close();
        return serverTypesObj;
    }

    public static JsonArray getProviderImages(String zone, ServiceProvider provider, Credential credential) throws BusinessException {
        JsonArray imagesArr = new JsonArray();
        String providerId = provider.getUuid();

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/images");
        Response response = CredentialHelperService.setCredential(target.request(), credential).get();
        String value = response.readEntity(String.class);

        if (response.getStatus() < 300) {
            imagesArr = new JsonParser().parse(value).getAsJsonObject().get("images").getAsJsonArray();
        } else {
            throw new BusinessException("Error retrieving images for provider : "+providerId);
        }
        response.close();
        return imagesArr;
    }

    public static JsonArray getProviderPublicIps(String zone, ServiceProvider provider, Credential credential) throws BusinessException {
        JsonArray ipsArr = new JsonArray();
        String providerId = provider.getUuid();

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/ips");
        Response response = CredentialHelperService.setCredential(target.request(), credential).get();
        String value = response.readEntity(String.class);
        if (response.getStatus() < 300) {
            ipsArr = new JsonParser().parse(value).getAsJsonObject().get("ips").getAsJsonArray();
        } else {
            throw new BusinessException("Error retrieving public ips for provider : "+providerId);
        }
        response.close();
        return ipsArr;
    }

    public static void deleteVolume(ServerVolume volume, CrossStorageApi crossStorageApi, Repository defaultRepo, Credential credential) throws BusinessException {
        String zone = volume.getZone();
        String volumeId = volume.getProviderSideId();

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/volumes/"+volumeId);
        Response response = CredentialHelperService.setCredential(target.request(), credential).delete();
        String value = response.readEntity(String.class);
        logger.info("response : {}", value);
        logger.debug("response status : {}", response.getStatus());
        if(response.getStatus()<300) {
            try {
                crossStorageApi.remove(defaultRepo, volume.getUuid(), ServerVolume.class);
                logger.info("volume : {} deleted at : {}", volumeId, Instant.now());
            } catch (Exception e) {
                logger.error("Error deleting volume : {}", volumeId, e.getMessage());
            }
        }
    }

    // public static JsonObject getSecurityGroupDetails(String zone, String securityGroupId, Credential credential) throws BusinessException {
    //     JsonObject securityGroupObj = new JsonObject();

    //     Client client = ClientBuilder.newClient();
    //     client.register(new CredentialHelperService.LoggingFilter());
    //     WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/security_groups/"+securityGroupId);
    //     Response response = CredentialHelperService.setCredential(target.request("application/json"), credential).get();
    //     String value = response.readEntity(String.class);
    //     if(response.getStatus()<300) {
    //         securityGroupObj = new JsonParser().parse(value).getAsJsonObject()
    //         .get("security_group").getAsJsonObject();
    //     } else {
    //         throw new BusinessException("Error retrieving Details for Security Group : "+securityGroupId);
    //     }
    //     response.close();
    //     return securityGroupObj;
    // }

    public static void filterToLatestValues(String cetCode, List<String> providerUuids, CrossStorageApi crossStorageApi, Repository defaultRepo) throws BusinessException {
        List<String> entitiesToRemove = new ArrayList<String>();
        List<String> clientSideIds = new ArrayList<String>();

        // get client side ids for cet
        List<CustomEntityInstance> cetInstances = crossStorageApi.find(defaultRepo, cetCode).getResults();
        for (CustomEntityInstance cetInstance : cetInstances) {
            clientSideIds.add(cetInstance.getUuid());
        }

        // check if matching latest
        for (String clientUuid : clientSideIds) {
            if (!providerUuids.contains(clientUuid)) {
                entitiesToRemove.add(clientUuid);
            }
        }

        for (String entityId : entitiesToRemove) {
            try {
                crossStorageApi.remove(defaultRepo, entityId, cetCode);
            } catch (Exception e) {
                logger.error("Error clearing {} : {}",cetCode, entityId, e.getMessage());
            }
        }
    }
}