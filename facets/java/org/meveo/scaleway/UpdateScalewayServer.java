package org.meveo.scaleway;

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
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.ScalewayServer;
import org.meveo.model.customEntities.ServerVolume;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class UpdateScalewayServer extends Script {
    

    
    private static final Logger logger = LoggerFactory.getLogger(UpdateScalewayServer.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private  String SCALEWAY_URL = "api.scaleway.com";
    static final private String BASE_PATH = "/instance/v1/zones/";
    
    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        String action = parameters.get(CONTEXT_ACTION).toString();
        ScalewayServer server =CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), ScalewayServer.class);

        if (server.getZone()==null) { //Required
            throw new BusinessException("Invalid Server Zone");
        } else if(server.getProviderSideId()==null) { //Required
            throw new BusinessException("Invalid Server Provider-side ID");
        } else if(server.getProvider()==null) {
            throw new BusinessException("Invalid Server Provider");
        }
        
        String zone = server.getZone();
        String serverId = server.getProviderSideId();
        ServiceProvider provider = null;
        String providerId = server.getProvider().getUuid();
        try {
            provider = crossStorageApi.find(defaultRepo, providerId, ServiceProvider.class);
        }catch (Exception e) {
            logger.error("Error retrieving provider for server : ", serverId, e.getMessage());
        }
        logger.info("action : {}, server ID : {}", action, serverId);

        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("Using Credential {} with username {}", credential.getUuid(), credential.getUsername());
        }

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/servers/"+serverId);

        Map<String, Object> body = new HashMap<String, Object>();
        body.put("dynamic_ip_required", server.getDynamicIpRequired()); // nullable, default to false
        body.put("enable_ipv6",server.getEnableIPvSix()); //nullable default to true
        body.put("protected", server.getIsProtected()); //nullable default to false
        body.put("boot_type", server.getBootType()); // From List of values, includes local, bootscript, rescue -> default is local
        
        // Server Name
        // nullable
        if (server.getInstanceName() != null) {
            body.put("name", server.getInstanceName());
        }

        // Volumes
        // Block volumes are only available for DEV1, GP1 and RENDER offers
        Map<String, Object> volumes = new HashMap<String, Object>();
        // Root Volume
        String serverType = server.getServerType();
        if (server.getRootVolume() != null) {
            Map<String, Object> rootVolume = new HashMap<String, Object>();
            String serverRootVolumeId = server.getRootVolume().getUuid();
            try {
                ServerVolume serverRootVolume = crossStorageApi.find(defaultRepo, serverRootVolumeId, ServerVolume.class);
                String serverRootVolumetype = serverRootVolume.getVolumeType();
                if(serverRootVolumetype.equalsIgnoreCase("l_ssd")) {
                    rootVolume.put("id", serverRootVolume.getProviderSideId());
                    rootVolume.put("boot", serverRootVolume.getIsBoot());
                    rootVolume.put("name", serverRootVolume.getName());
                } else if (serverType.startsWith("DEV1") || serverType.startsWith("GP1") || serverType.startsWith("RENDER")) {
                    rootVolume.put("id", serverRootVolume.getProviderSideId());
                    rootVolume.put("boot", serverRootVolume.getIsBoot());
                    rootVolume.put("name", serverRootVolume.getName());
                } else {
                    throw new BusinessException("Invalid Root Volume Type for Server Type : "+serverType);
                }
                volumes.put("0", rootVolume);
            } catch (Exception e) {
                logger.error("Error retrieving server root volume", e.getMessage());
            }
        }
        // Additional Volumes
        if (server.getAdditionalVolumes() != null) {
            Map<String, ServerVolume> serverAdditionalVolumes = server.getAdditionalVolumes();
            for (Map.Entry<String, ServerVolume> serverAdditionalVolumeEnt : serverAdditionalVolumes.entrySet()) {
                Map<String, Object> additionalVolume = new HashMap<String, Object>();
                String serverAdditionalVolumeId = serverAdditionalVolumeEnt.getValue().getUuid();
                try {
                    ServerVolume serverAdditionalVolume = crossStorageApi.find(defaultRepo, serverAdditionalVolumeId, ServerVolume.class);
                    String serverAdditionalVolumeType = serverAdditionalVolume.getVolumeType();
                    if(serverAdditionalVolumeType.equalsIgnoreCase("l_ssd")) {
                        additionalVolume.put("id", serverAdditionalVolume.getProviderSideId());
                        additionalVolume.put("boot", serverAdditionalVolume.getIsBoot());
                        additionalVolume.put("name", serverAdditionalVolume.getName());
                    } else if(serverType.startsWith("DEV1") || serverType.startsWith("GP1") || serverType.startsWith("RENDER")) {
                        additionalVolume.put("id", serverAdditionalVolume.getProviderSideId());
                        additionalVolume.put("boot", serverAdditionalVolume.getIsBoot());
                        additionalVolume.put("name", serverAdditionalVolume.getName());
                    } else {
                        throw new BusinessException("Invalid Additional Volume Type for Server Type : "+serverType);
                    }
                    volumes.put(serverAdditionalVolumeEnt.getKey(), additionalVolume); // keys should be 1, 2, 3...
                } catch (Exception e) {
                    logger.error("Error retrieving additional volume", e.getMessage());
                }
            }
        }
        body.put("volumes", volumes);

        // Security Group
        Map<String, Object> securityGroupMap = new HashMap<String, Object>();
        if (server.getSecurityGroup() != null) {
            securityGroupMap.put("id", server.getSecurityGroup().getProviderSideId());
            securityGroupMap.put("name", server.getSecurityGroup().getName());
        }
        body.put("security_group", securityGroupMap);

        // Bootscript
        if (server.getBootType() != null && server.getBootType().equalsIgnoreCase("bootscript") && server.getBootscript() != null) {
            String bootscriptId = server.getBootscript().getProviderSideId();
            body.put("bootscript", bootscriptId);
        }

        // Private NICs
        // Cannot be null but not currently used
        ArrayList<String> privateNics = new ArrayList<String>();
        if (server.getPrivateNics() != null) {
            List<String> serverPrivateNics = server.getPrivateNics();
            for (String privateNic : serverPrivateNics) {
                privateNics.add(privateNic);
            }
            body.put("private_nics", privateNics);
        }
        
        String resp = JacksonUtil.toStringPrettyPrinted(body);
        Response response = CredentialHelperService.setCredential(target.request("application/json"), credential)
            .method("PATCH", Entity.json(resp));
        String value = response.readEntity(String.class);
        logger.info("response : " + value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response:"+value);
        if(response.getStatus() < 300) {
            JsonObject serverObj = new JsonParser().parse(value).getAsJsonObject().get("server").getAsJsonObject();
            server = ScalewaySetters.setScalewayServer(serverObj, action, provider, crossStorageApi, defaultRepo);
            try {
                crossStorageApi.createOrUpdate(defaultRepo, server);
            } catch (Exception e) {
                logger.error("error updating Server : {}", server.getUuid(), e.getMessage());
            }
        }
        response.close();
    }
}