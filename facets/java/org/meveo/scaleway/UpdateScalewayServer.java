package org.meveo.scaleway;

import java.time.Instant;
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
import org.meveo.model.customEntities.Bootscript;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.ScalewayServer;
import org.meveo.model.customEntities.SecurityGroup;
import org.meveo.model.customEntities.ServerImage;
import org.meveo.model.customEntities.ServerVolume;
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
        }
        
        String zone = server.getZone();
        String serverId = server.getProviderSideId();
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

            // Default Server Values
            server.setLastUpdate(Instant.now());
            server.setInstanceName(serverObj.get("name").getAsString());
            server.setServerType(serverObj.get("commercial_type").getAsString());
            server.setOrganization(serverObj.get("organization").getAsString());
            server.setStatus(serverObj.get("state").getAsString());
            server.setDomainName(serverObj.get("hostname").getAsString());
            server.setSergentUrl(server.getDomainName() + ":8001/sergent");

            // Public IP
            if (!serverObj.get("public_ip").isJsonNull()) {
                server.setPublicIp(serverObj.get("public_ip").getAsJsonObject().get("address").getAsString());
            }

            // Image
            if (!serverObj.get("image").isJsonNull()) {
                String serverImageId = serverObj.get("image").getAsJsonObject().get("id").getAsString();
                try {
                    ServerImage serverImage = crossStorageApi.find(defaultRepo, ServerImage.class).by("providerSideId", serverImageId).getResult();
                    server.setImage(serverImage);
                } catch (Exception e) {
                    logger.error("Error retrieving image", e.getMessage());
                }
            }

            // Volumes
            JsonObject serverVolumesObj = serverObj.get("volumes").getAsJsonObject();
            Long totalVolumeSize = 0L;
            if (serverVolumesObj.entrySet().size() >= 1) {
                // Root Volume
                String serverRootVolumeId = serverVolumesObj.get("0").getAsJsonObject().get("id").getAsString();
                if (crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", serverRootVolumeId).getResult() != null) {
                    try {
                        ServerVolume serverRootVolume = crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", serverRootVolumeId).getResult();
                        server.setRootVolume(serverRootVolume);
                        totalVolumeSize += Long.valueOf(serverRootVolume.getSize());
                    } catch (Exception e) {
                        logger.error("Error retrieving root volume", e.getMessage());
                    }
                }
                // Additional Volumes
                if (serverVolumesObj.entrySet().size() > 1) {
                    Map<String, ServerVolume> serverAdditionalVolumes = new HashMap<String, ServerVolume>();
                    Set<Map.Entry<String, JsonElement>> additionalVolumeEntries = serverVolumesObj.entrySet();
                    for (Map.Entry<String, JsonElement> additionalVolumeEntry : additionalVolumeEntries) {
                        if(additionalVolumeEntry.getKey() != "0") { // key for root volume
                            String additionalVolumeId = serverVolumesObj.get(additionalVolumeEntry.getKey()).getAsJsonObject().get("id").getAsString();
                            try{
                                ServerVolume serverAdditionalVolume = crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", additionalVolumeId).getResult();
                                serverAdditionalVolumes.put(additionalVolumeEntry.getKey(), serverAdditionalVolume);
                                totalVolumeSize += Long.valueOf(serverAdditionalVolume.getSize());
                            } catch (Exception e) {
                                logger.error("Error retrieving additional volume", e.getMessage());
                            }
                        }
                    }
                    server.setAdditionalVolumes(serverAdditionalVolumes);
                }
                server.setVolumeSize(String.valueOf(totalVolumeSize));
            }

            // Location Definition
            String locationDefinition = "zone_id/platform_id/cluster_id/hypervisor_id/node_id";
            server.setLocationDefinition(locationDefinition);

            // Location
            if (!serverObj.get("location").isJsonNull()) {
                JsonObject serverLocationObj = serverObj.get("location").getAsJsonObject();
                String serverLocation = 
                    serverLocationObj.get("zone_id")+"/"+
                    serverLocationObj.get("platform_id")+"/"+
                    serverLocationObj.get("cluster_id")+"/"+
                    serverLocationObj.get("hypervisor_id")+"/"+
                    serverLocationObj.get("node_id");
                server.setLocation(serverLocation);
            }

            // Security Group CET
            if (!serverObj.get("security_group").isJsonNull()) {
                JsonObject securityGroupObj = serverObj.get("security_group").getAsJsonObject();
                String securityGroupId = securityGroupObj.get("id").getAsString();
                if (crossStorageApi.find(defaultRepo, SecurityGroup.class).by("providerSideId", securityGroupId).getResult() != null) {
                    try {
                        SecurityGroup securityGroup = crossStorageApi.find(defaultRepo, SecurityGroup.class).by("providerSideId", securityGroupId).getResult();
                        server.setSecurityGroup(securityGroup);
                    } catch(Exception e) {
                        logger.error("Error retrieving security group", e.getMessage());
                    }
                } else {
                    SecurityGroup newSecurityGroup = new SecurityGroup();
                    newSecurityGroup.setUuid(securityGroupId);
                    newSecurityGroup.setProviderSideId(securityGroupId);
                    newSecurityGroup.setName(securityGroupObj.get("name").getAsString());
                    newSecurityGroup.setZone(zone);
                    try {
                        crossStorageApi.createOrUpdate(defaultRepo, newSecurityGroup);
                        server.setSecurityGroup(newSecurityGroup);
                    }catch (Exception e) {
                        logger.error("Error creating new security group", e.getMessage());
                    }
                }
            }

            // Server Actions
            JsonArray allowedActions = serverObj.get("allowed_actions").getAsJsonArray();
            ArrayList<String> serverActions = new ArrayList<String>();
            for (JsonElement allowedAction : allowedActions) {
                serverActions.add(allowedAction.getAsString());
            }
            server.setServerActions(serverActions);
            
            // Scaleway specific values
            server.setDynamicIpRequired(serverObj.get("dynamic_ip_required").getAsBoolean());
            server.setArch(serverObj.get("arch").getAsString());
            server.setProject(serverObj.get("project").getAsString());
            server.setBootType(serverObj.get("boot_type").getAsString());
            server.setIsProtected(serverObj.get("protected").getAsBoolean());

            // Private IP
            if (!serverObj.get("private_ip").isJsonNull()) {
                server.setPrivateIp(serverObj.get("private_ip").getAsString());
            }

            // Ipv6
            server.setEnableIPvSix(serverObj.get("enable_ipv6").getAsBoolean());
            if(!serverObj.get("ipv6").isJsonNull()) {
                server.setIpVSix(serverObj.get("ipv6").getAsJsonObject().get("address").getAsString());
            }

            // Maintenances
            if (!serverObj.get("maintenances").isJsonNull()) {
                ArrayList<String> maintenances = new ArrayList<String>();
                JsonArray maintenancesArr = serverObj.get("maintenances").getAsJsonArray();
                for (JsonElement maintenance : maintenancesArr) {
                    maintenances.add(maintenance.getAsString()); // could be Objects
                }
                server.setMaintenances(maintenances); // Array
            }

            // Bootscript
            if(!serverObj.get("bootscript").isJsonNull()) {
                JsonObject bootscriptObj = serverObj.get("bootscript").getAsJsonObject();
                String bootscriptId = bootscriptObj.get("id").getAsString();
                if (crossStorageApi.find(defaultRepo, Bootscript.class).by("providerSideId", bootscriptId).getResult() != null) {
                    try {
                        Bootscript bootscript = crossStorageApi.find(defaultRepo, Bootscript.class).by("providerSideId", bootscriptId).getResult();
                        server.setBootscript(bootscript);
                    } catch (Exception e) {
                        logger.error("Error retrieving bootscript", e.getMessage());
                    }
                } else {
                    Bootscript newBootscript = new Bootscript();
                    newBootscript.setUuid(bootscriptId);
                    newBootscript.setProviderSideId(bootscriptId);
                    newBootscript.setArch(bootscriptObj.get("architecture").getAsString());
                    newBootscript.setBootcmdargs(bootscriptObj.get("bootcmdargs").getAsString());
                    newBootscript.setIsDefault(bootscriptObj.get("default").getAsBoolean());
                    newBootscript.setDtb(bootscriptObj.get("dtb").getAsString());
                    newBootscript.setInitrd(bootscriptObj.get("initrd").getAsString());
                    newBootscript.setKernel(bootscriptObj.get("kernel").getAsString());
                    newBootscript.setOrganization(bootscriptObj.get("organization").getAsString());
                    newBootscript.setProject(bootscriptObj.get("project").getAsString());
                    newBootscript.setIsPublic(bootscriptObj.get("public").getAsBoolean());
                    newBootscript.setTitle(bootscriptObj.get("title").getAsString());
                    newBootscript.setZone(bootscriptObj.get("zone").getAsString());
                    try {
                        crossStorageApi.createOrUpdate(defaultRepo, newBootscript);
                        server.setBootscript(newBootscript);
                    } catch (Exception e) {
                        logger.error("Error creating bootscript for server : ", server.getUuid(), e.getMessage());
                    }
                }
            }

            // Private NICs
            if (!serverObj.get("private_nics").isJsonNull()) {
                JsonArray nicsArr = serverObj.get("private_nics").getAsJsonArray();
                ArrayList<String> nicIds = new ArrayList<String>();
                for (JsonElement nic : nicsArr) {
                    JsonObject privateNic = nic.getAsJsonObject();
                    nicIds.add(privateNic.get("id").getAsString());
                }
                server.setPrivateNics(nicIds);
            }

            try {
                crossStorageApi.createOrUpdate(defaultRepo, server);
            } catch (Exception e) {
                logger.error("error updating Server {} :{}", server.getUuid(), e.getMessage());
            }
        }
        response.close();
    }
}