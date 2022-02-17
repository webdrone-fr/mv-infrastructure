package org.meveo.scaleway;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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

        // Get Server Type requirements
        String serverType = server.getServerType();
        JsonObject serverConstraintsObj = ScalewayHelperService.getServerTypeRequirements(server, crossStorageApi, defaultRepo, credential);
        
        Long serverTotalLocalVolumeSize = 0L;
        Long serverMinVolumeSizeReq = serverConstraintsObj.get("volumes_constraint").getAsJsonObject().get("min_size").getAsLong();
        Long serverMaxVolumeSizeReq = serverConstraintsObj.get("volumes_constraint").getAsJsonObject().get("max_size").getAsLong();

        // Can be null when no volumes attached eg at creation
        if (server.getRootVolume() != null) {
            try {
                ServerVolume rootVolume = crossStorageApi.find(defaultRepo,server.getRootVolume().getUuid(), ServerVolume.class);
                if (rootVolume.getVolumeType().equalsIgnoreCase("l_ssd")) {
                    serverTotalLocalVolumeSize += Long.valueOf(rootVolume.getSize());
                }
            } catch (Exception e) {
                logger.error("error retrieving root volume : {}", e.getMessage());
            }
            // Additional Volumes
            if(server.getAdditionalVolumes() != null) {
                Map<String, ServerVolume> additionalVolumes = server.getAdditionalVolumes();
                for (int i = 1; i < additionalVolumes.size(); i++) {
                    try {
                        ServerVolume additionalVolume = crossStorageApi.find(defaultRepo, additionalVolumes.get(String.valueOf(i)).getUuid(), ServerVolume.class);
                        if (additionalVolume.getVolumeType().equalsIgnoreCase("l_ssd")) {
                            serverTotalLocalVolumeSize += Long.valueOf(additionalVolume.getSize());
                        }
                    } catch (Exception e) {
                        logger.error("Error retieving additional volume : {}", e.getMessage());
                    }
                }
            }
            String serverTotalLocalVolumesSizeStr = Long.toString(serverTotalLocalVolumeSize);
            String serverMinVolumeSizeReqStr = Long.toString(serverMinVolumeSizeReq);
            String serverMaxVolumeSizeReqStr = Long.toString(serverMaxVolumeSizeReq);
            if (serverTotalLocalVolumeSize < serverMinVolumeSizeReq) {
                logger.debug("Current available volume size : {}, Minimum Volume size required for server type {} : {}", serverTotalLocalVolumesSizeStr , serverType, serverMinVolumeSizeReqStr);
                throw new BusinessException("Current total volume size is too small for selected server type");
            } else if (serverTotalLocalVolumeSize > serverMaxVolumeSizeReq) {
                logger.debug("Current available volume size : {}, Maximum Volume size allowed for server type {} : {}", serverTotalLocalVolumesSizeStr , serverType, serverMaxVolumeSizeReqStr);
                throw new BusinessException("Current total volume size is too large for selected server type");
            } else {
                logger.info("Server Total Volume size : {}; Min Total Volume size : {}; Max Total Volume Size : {}", serverTotalLocalVolumesSizeStr, serverMinVolumeSizeReqStr, serverMaxVolumeSizeReqStr);
            }
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
        // Block volumes are only available for DEV1, GP1 and RENDER offers TODO
        Map<String, Object> volumes = new HashMap<String, Object>();
        // Root Volume
        if (server.getRootVolume() != null) {
            Map<String, Object> rootVolume = new HashMap<String, Object>();
            rootVolume.put("id", server.getRootVolume().getProviderSideId());
            rootVolume.put("boot", server.getRootVolume().getIsBoot());
            rootVolume.put("name", server.getRootVolume().getName());
            volumes.put("0", rootVolume);
        }
        // Additional Volumes
        if (server.getAdditionalVolumes() != null) {
            Map<String, ServerVolume> serverAdditionalVolumes = server.getAdditionalVolumes();
            for (Map.Entry<String, ServerVolume> serverAdditionalVolume : serverAdditionalVolumes.entrySet()) {
                Map<String, Object> additionalVolume = new HashMap<String, Object>();
                try {
                    ServerVolume additionalVolumeObj = crossStorageApi.find(defaultRepo, serverAdditionalVolume.getValue().getUuid(), ServerVolume.class);
                    additionalVolume.put("id", additionalVolumeObj.getProviderSideId());
                    additionalVolume.put("boot", additionalVolumeObj.getIsBoot());
                    additionalVolume.put("name", additionalVolumeObj.getName());
                    volumes.put(serverAdditionalVolume.getKey(), additionalVolume); // keys should be 1, 2, 3...
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
            for (String privateNic : (server.getPrivateNics())) {
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
                ServerImage serverImage = crossStorageApi.find(defaultRepo, ServerImage.class).by("providerSideId", serverImageId).getResult();
                server.setImage(serverImage);
            }

            // Volumes
            JsonObject serverVolumesObj = serverObj.get("volumes").getAsJsonObject();
            Long serverTotalVolumeSize = 0L;
            if (serverVolumesObj.entrySet().size() >= 1) {
                // Root Volume
                String serverRootVolumeId = serverVolumesObj.get("0").getAsJsonObject().get("id").getAsString();
                if (crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", serverRootVolumeId).getResult() != null) {
                    ServerVolume serverRootVolume = crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", serverRootVolumeId).getResult();
                    server.setRootVolume(serverRootVolume);
                    serverTotalVolumeSize += Long.parseLong(serverRootVolume.getSize());
                }
                // Additional Volumes
                if (serverVolumesObj.entrySet().size() > 1) {
                    Map<String, ServerVolume> serverAdditionalVolumes = new HashMap<String, ServerVolume>();
                    for (int i = 1; i < serverVolumesObj.entrySet().size(); i++) {
                        String additionalVolumeId = serverVolumesObj.get(String.valueOf(i)).getAsJsonObject().get("id").getAsString();
                        ServerVolume serverAdditionalVolume = crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", additionalVolumeId).getResult();
                        serverAdditionalVolumes.put(String.valueOf(i), serverAdditionalVolume);
                        serverTotalVolumeSize += Long.parseLong(serverAdditionalVolume.getSize()) ;
                    }
                    server.setAdditionalVolumes(serverAdditionalVolumes);
                }
                server.setVolumeSize(String.valueOf(serverTotalVolumeSize));
            }

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
            
            String locationDefinition = "zone_id/platform_id/cluster_id/hypervisor_id/node_id";
            server.setLocationDefinition(locationDefinition);

            // Security Group CET
            if (!serverObj.get("security_group").isJsonNull()) {
                String securityGroupId = serverObj.get("security_group").getAsJsonObject().get("id").getAsString();
                SecurityGroup securityGroup = crossStorageApi.find(defaultRepo, SecurityGroup.class).by("providerSideId", securityGroupId).getResult();
                server.setSecurityGroup(securityGroup);
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
                    Bootscript bootscript = crossStorageApi.find(defaultRepo, Bootscript.class).by("providerSideId", bootscriptId).getResult();
                    server.setBootscript(bootscript);
                } else {
                    Bootscript newBootscript = new Bootscript();
                    newBootscript.setArch(bootscriptObj.get("arch").getAsString());
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
