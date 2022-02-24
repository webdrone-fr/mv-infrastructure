package org.meveo.scaleway;

import java.util.ArrayList;
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
            logger.error("Error retrieving root volume, {}", e.getMessage());
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
                    logger.error("Error retrieving additional volumes {}", e.getMessage());
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
            logger.error("Error retrieving root volume, {}", e.getMessage());
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
                    logger.error("Error retrieving additional volumes {}", e.getMessage());
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
        if (server != null) {
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
        }
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

    public static JsonObject getServerUserData(String zone, String serverId, String key, Credential credential) throws BusinessException {
        JsonObject serverUserDataObj = new JsonObject();

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/servers/"+serverId+"/user_data/"+key);
        Response response = CredentialHelperService.setCredential(target.request("application/json"), credential).get();
        String value = response.readEntity(String.class);

        if(response.getStatus()<300) {
            serverUserDataObj = new JsonParser().parse(value).getAsJsonObject();
        } else {
            throw new BusinessException("Error retrieving Server : "+serverId+" User data");
        }
        response.close();
        return serverUserDataObj;
    }

    public static JsonObject getServerDetailsAfterSuccessfulAction(String zone, String serverId, Credential credential) throws BusinessException {
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
}