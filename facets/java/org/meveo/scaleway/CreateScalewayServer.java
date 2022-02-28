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

public class CreateScalewayServer extends Script {
    

    private static final Logger logger = LoggerFactory.getLogger(CreateScalewayServer.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private  String SCALEWAY_URL = "api.scaleway.com";
    static final private String BASE_PATH = "/instance/v1/zones/";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        String action = parameters.get(CONTEXT_ACTION).toString();
        ScalewayServer server = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), ScalewayServer.class);
        ServiceProvider provider = crossStorageApi.find(defaultRepo, ServiceProvider.class).by("code", "SCALEWAY").getResult();

        if(server.getInstanceName() == null) { // required
            throw new BusinessException("Invalid Server Instance Name");
        } else if (server.getServerType() == null) { // required
            throw new BusinessException("Invalid Server Type");
        } else if(server.getZone() == null) { // required
            throw new BusinessException("Invalid Server Zone");
        }

        String zone = server.getZone(); // Required for path
        logger.info("Action : {}, Provider : {}", action, provider.getCode());

        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("using credential {} with username {}", credential.getDomainName(), credential.getUsername());
        }

        // Server Availability for all server types in zone - possible values include available, scarce and shortage
        String serverType = server.getServerType();
        JsonObject serverAvailabilityObj = ScalewayHelperService.getServerTypeAvailabilityInZone(zone, credential);
        String serverTypeAvailability = serverAvailabilityObj.get(serverType).getAsJsonObject().get("availability").getAsString();
        if (serverTypeAvailability.equalsIgnoreCase("scarce")) {
            parameters.put(RESULT_GUI_MESSAGE, "Server Type "+serverType+" has scarce availability in zone "+zone);
            logger.info("Zone : {} has a scarce supply of Server Type : {}", zone, serverType);
        } else if (serverTypeAvailability.equalsIgnoreCase("shortage")) {
            logger.info("Zone : {} has a shortage of Server Type : {}", zone, serverType);
            throw new BusinessException("Server Type : "+ serverType+ " is currently unavailable in zone " +zone);
        }

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/servers");

        Map<String, Object> body = new HashMap<String, Object>();
        body.put("name", server.getInstanceName());// required
        body.put("dynamic_ip_required", server.getDynamicIpRequired()); // nullable, default to true
        body.put("commercial_type", server.getServerType()); // required
        body.put("enable_ipv6", server.getEnableIPvSix()); // default to true
        body.put("boot_type", server.getBootType()); // From List of values, includes local, bootscript, rescue -> default is local

        // Bootscript - nullable
        if (server.getBootType() != null && server.getBootType().equalsIgnoreCase("bootscript") && server.getBootscript() != null) {
            String bootscriptId = server.getBootscript().getProviderSideId();
            body.put("bootscript", bootscriptId);
        }

        // Public IP
        if (server.getPublicIp() != null) {
            body.put("public_ip", server.getPublicIp());
        }

        // Project
        // Webdrone ID = 6a0c2ca8-917a-418a-90a3-05949b55a7ae
        String projectId = "6a0c2ca8-917a-418a-90a3-05949b55a7ae";
        if (server.getProject() != null) {
            projectId = server.getProject();
        }
        body.put("project", projectId);

        Map<String, Object> volumes = new HashMap<String, Object>();
        // Image
        if (server.getImage() != null) {
            String imageId = server.getImage().getProviderSideId();
            body.put("image", imageId);
        } else if (server.getRootVolume() != null) {
            // Root Volume
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
        }
        body.put("volumes", volumes);

        // Security Group
        // nullable - if null, defaults to "Default security group"
        if (server.getSecurityGroup() != null) {
            body.put("security_group", server.getSecurityGroup().getProviderSideId());
        }
        
        String resp = JacksonUtil.toStringPrettyPrinted(body);
        Response response = 
            CredentialHelperService.setCredential(target.request("application/json"), credential)
                .post(Entity.json(resp));
        String value = response.readEntity(String.class);
        logger.info("response : {}", value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response: "+value);

        if (response.getStatus()<300) {
            JsonObject serverObj = new JsonParser().parse(value).getAsJsonObject().get("server").getAsJsonObject();
            server = ScalewaySetters.setScalewayServer(serverObj, action, provider, crossStorageApi, defaultRepo);
            try {
                crossStorageApi.createOrUpdate(defaultRepo, server);
            } catch (Exception e) {
                logger.error("error creating server", e.getMessage());
            }
            response.close();
        }
    }
}