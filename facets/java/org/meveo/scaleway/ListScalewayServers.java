package org.meveo.scaleway;

import java.time.OffsetDateTime;
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
import org.meveo.model.customEntities.PublicIp;
import org.meveo.model.customEntities.ScalewayServer;
import org.meveo.model.customEntities.SecurityGroup;
import org.meveo.model.customEntities.Server;
import org.meveo.model.customEntities.ServerImage;
import org.meveo.model.customEntities.ServerVolume;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListScalewayServers extends Script {
   
   
    private static final Logger logger = LoggerFactory.getLogger(ListScalewayServers.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private String SCALEWAY_URL = "api.scaleway.com";
    static final private String BASE_PATH = "/instance/v1/zones/";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        ServiceProvider provider = crossStorageApi.find(defaultRepo, ServiceProvider.class).by("code", "SCALEWAY").getResult();
        
        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("Using Credential {} with username {}", credential.getUuid(), credential.getUsername());
        }

        String[] zones = new String[] {"fr-par-1", "fr-par-2", "fr-par-3", "nl-ams-1", "pl-waw-1"};
        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        for (String zone : zones) {
            WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/servers");
            Response response = CredentialHelperService.setCredential(target.request(), credential).get();
            String value = response.readEntity(String.class);
            logger.info("response : " + value);
            logger.debug("response status : {}", response.getStatus());
            if (response.getStatus() < 300) {
                JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().get("servers").getAsJsonArray();
                for (JsonElement element : rootArray) {
                    JsonObject serverObj = element.getAsJsonObject();
                    ScalewayServer server = new ScalewayServer();
                    String type = serverObj.get("commercial_type").getAsString(); // used for check
                    String name = serverObj.get("name").getAsString(); // used for check
                    String serverId = serverObj.get("id").getAsString();
                    if (name.startsWith("dev-")) { // type necessary?

                        // Default server values
                        server.setCreationDate(OffsetDateTime.parse(serverObj.get("creation_date").getAsString()).toInstant());
                        server.setLastUpdate(OffsetDateTime.parse(serverObj.get("modification_date").getAsString()).toInstant());
                        server.setUuid(serverId);
                        ((Server) server).setUuid(serverId);
                        server.setProviderSideId(serverId);
                        server.setInstanceName(name);
                        server.setServerType(type);
                        server.setZone(zone);
                        server.setProvider(provider);
                        server.setOrganization(serverObj.get("organization").getAsString());
                        server.setStatus(serverObj.get("state").getAsString());
                        server.setDomainName(serverObj.get("hostname").getAsString());
                        server.setSergentUrl(server.getDomainName() + ":8001/sergent");

                        // public IP CET
                        // If Server is powered off => no Ip assigned
                        if(!serverObj.get("public_ip").isJsonNull()) {
                            String publicIpId = serverObj.get("public_ip").getAsJsonObject().get("id").getAsString();
                            try {
                                if (crossStorageApi.find(defaultRepo, PublicIp.class).by("providerSideId", publicIpId).getResult() != null) {
                                    PublicIp publicIp = crossStorageApi.find(defaultRepo, PublicIp.class).by("providerSideId", publicIpId).getResult();
                                    server.setPublicIp(publicIp.getIpVFourAddress());
                                } else {
                                    server.setPublicIp(serverObj.get("public_ip").getAsJsonObject().get("address").getAsString());
                                }
                            } catch (Exception e) {
                                logger.error("Error retrieving public ip : {}", publicIpId, e.getMessage());
                            }
                        }
                        
                        // Image
                        if(!serverObj.get("image").isJsonNull()) {
                            String imageId = serverObj.get("image").getAsJsonObject().get("id").getAsString();
                            try {
                                ServerImage image = crossStorageApi.find(defaultRepo, ServerImage.class).by("providerSideId", imageId).getResult();
                                server.setImage(image);
                            } catch (Exception e) {
                                logger.error("Error retrieving Server Image : {}", imageId, e.getMessage());
                            }
                        }

                        // Volumes
                        JsonObject serverVolumesObj = serverObj.get("volumes").getAsJsonObject();
                        Long serverTotalVolumeSize = 0L;
                        if (serverVolumesObj.entrySet().size() >= 1) {
                            // Root Volume
                            String serverRootVolumeId = serverVolumesObj.get("0").getAsJsonObject().get("id").getAsString();
                            if(crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", serverRootVolumeId).getResult() != null){
                                // if volume exists in default repo
                                try {
                                    ServerVolume serverRootVolume = crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", serverRootVolumeId).getResult();
                                    server.setRootVolume(serverRootVolume);
                                    serverTotalVolumeSize = serverVolumesObj.get("0").getAsJsonObject().get("size").getAsLong();
                                } catch (Exception e) {
                                    logger.error("Error retrieving root volume {} ", serverRootVolumeId, e.getMessage());
                                }
                            } else { // if root volume does not exist in default repo - create new
                                JsonObject serverRootVolumeObj = serverVolumesObj.get("0").getAsJsonObject();
                                ServerVolume rootVolume = new ServerVolume();
                                rootVolume.setCreationDate(OffsetDateTime.parse(serverRootVolumeObj.get("creation_date").getAsString()).toInstant());
                                rootVolume.setLastUpdated(OffsetDateTime.parse(serverRootVolumeObj.get("modification_date").getAsString()).toInstant()); // Or set to now?
                                rootVolume.setUuid(serverRootVolumeId);
                                rootVolume.setProviderSideId(serverRootVolumeId);
                                rootVolume.setName(serverRootVolumeObj.get("name").getAsString());
                                rootVolume.setState(serverRootVolumeObj.get("state").getAsString());
                                rootVolume.setSize(String.valueOf(serverRootVolumeObj.get("size").getAsLong()));
                                rootVolume.setZone(zone);
                                rootVolume.setVolumeType(serverRootVolumeObj.get("volume_type").getAsString());
                                rootVolume.setServer(serverId);
                                rootVolume.setIsBoot(serverRootVolumeObj.get("boot").getAsBoolean());
                                try {
                                    crossStorageApi.createOrUpdate(defaultRepo, rootVolume);
                                    server.setRootVolume(rootVolume);
                                    serverTotalVolumeSize += Long.parseLong(rootVolume.getSize());
                                } catch(Exception e) {
                                    logger.error("error creating root volume {} : {}", serverRootVolumeId, e.getMessage());
                                }
                            }
                            
                            // Additional Volumes
                            if (serverVolumesObj.entrySet().size() > 1) {
                                Map<String, ServerVolume> serverAdditionalVolumes = new HashMap<String, ServerVolume>();
                                for (int i = 1; i < serverVolumesObj.entrySet().size(); i++) {
                                    String additionalVolumeId = serverVolumesObj.get(String.valueOf(i)).getAsJsonObject().get("id").getAsString();
                                    if(crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", additionalVolumeId).getResult() != null) {
                                        // if additional volume exists in default repo
                                        try {
                                            ServerVolume serverAdditionalVolume = crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", additionalVolumeId).getResult();
                                            serverAdditionalVolumes.put(String.valueOf(i), serverAdditionalVolume);
                                            serverTotalVolumeSize += serverVolumesObj.get(String.valueOf(i)).getAsJsonObject().get("size").getAsLong();
                                        } catch (Exception e) {
                                            logger.error("Error retieving additional volume : {}", additionalVolumeId, e.getMessage());
                                        }
                                    } else { // if additional volume does not exist in default repo - create new
                                        JsonObject serverAdditionalVolumeObj = serverVolumesObj.get(String.valueOf(i)).getAsJsonObject();
                                        ServerVolume additionalVolume = new ServerVolume();
                                        additionalVolume.setCreationDate(OffsetDateTime.parse(serverAdditionalVolumeObj.get("creation_date").getAsString()).toInstant());
                                        additionalVolume.setLastUpdated(OffsetDateTime.parse(serverAdditionalVolumeObj.get("modification_date").getAsString()).toInstant()); // Or set to now?
                                        additionalVolume.setProviderSideId(additionalVolumeId);
                                        additionalVolume.setUuid(additionalVolumeId);
                                        additionalVolume.setName(serverAdditionalVolumeObj.get("name").getAsString());
                                        additionalVolume.setState(serverAdditionalVolumeObj.get("state").getAsString());
                                        additionalVolume.setSize(String.valueOf(serverAdditionalVolumeObj.get("size").getAsLong()));
                                        additionalVolume.setZone(zone);
                                        additionalVolume.setVolumeType(serverAdditionalVolumeObj.get("volume_type").getAsString());
                                        additionalVolume.setServer(serverId);
                                        additionalVolume.setIsBoot(serverAdditionalVolumeObj.get("boot").getAsBoolean());
                                        try {
                                            crossStorageApi.createOrUpdate(defaultRepo, additionalVolume);
                                            serverAdditionalVolumes.put(String.valueOf(i), additionalVolume);
                                            serverTotalVolumeSize += Long.parseLong(additionalVolume.getSize());
                                        } catch(Exception e) {
                                            logger.error("error creating additional volume {} : {}", additionalVolumeId, e.getMessage());
                                        }
                                    }
                                }
                                server.setAdditionalVolumes(serverAdditionalVolumes);
                            }
                            // Volume size
                            server.setVolumeSize(String.valueOf(serverTotalVolumeSize));
                        }

                        // Server Actions
                        ArrayList<String> actions = new ArrayList<String>();
                        JsonArray serverActionsArr = serverObj.get("allowed_actions").getAsJsonArray();
                        for (JsonElement action : serverActionsArr) {
                            actions.add(action.getAsString());
                        }
                        server.setServerActions(actions);
                        
                        // Location Definition
                        String locationDefinition = "zone_id/platform_id/cluster_id/hypervisor_id/node_id";
                        server.setLocationDefinition(locationDefinition);

                        // Location
                        if (!serverObj.get("location").isJsonNull()) {
                            JsonObject locationObj = serverObj.get("location").getAsJsonObject();
                            String zone_id = locationObj.get("zone_id").getAsString();
                            String platform_id = locationObj.get("platform_id").getAsString();
                            String cluster_id = locationObj.get("cluster_id").getAsString();
                            String hypervisor_id = locationObj.get("hypervisor_id").getAsString();
                            String node_id = locationObj.get("node_id").getAsString();
                            String location = zone_id+"/"+platform_id+"/"+cluster_id+"/"+hypervisor_id+"/"+node_id;
                            server.setLocation(location);
                        }
                        
                        // Security Group CET
                       if (!serverObj.get("security_group").isJsonNull()) {
                           JsonObject securityGroupObj = serverObj.get("security_group").getAsJsonObject();
                            String securityGroupId = securityGroupObj.get("id").getAsString();
                            if (crossStorageApi.find(defaultRepo, SecurityGroup.class).by("providerSideId", securityGroupId).getResult() != null) {
                                SecurityGroup securityGroup = crossStorageApi.find(defaultRepo, SecurityGroup.class).by("providerSideId", securityGroupId).getResult();
                                server.setSecurityGroup(securityGroup);
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
                        
                        // Scaleway-specific Server Values
                        server.setDynamicIpRequired(serverObj.get("dynamic_ip_required").getAsBoolean());
                        server.setIsProtected(serverObj.get("protected").getAsBoolean());
                        server.setArch(serverObj.get("arch").getAsString());
                        server.setProject(serverObj.get("project").getAsString());
                        server.setBootType(serverObj.get("boot_type").getAsString());

                        // Private IP
                        if (!serverObj.get("private_ip").isJsonNull()) {
                            server.setPrivateIp(serverObj.get("private_ip").getAsString());
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
                                    logger.error("Error creating bootscript for server : ", serverId, e.getMessage());
                                }
                            }
                        }

                        // Placement Group
                        if (!serverObj.get("placement_group").isJsonNull()) {
                            server.setPlacementGroup(serverObj.get("placement_group").getAsJsonObject().get("name").getAsString());
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

                        // Private NICs
                        if (!serverObj.get("private_nics").isJsonNull()) {
                            JsonArray nicsArr = serverObj.get("private_nics").getAsJsonArray();
                            ArrayList<String> nicIds = new ArrayList<String>();
                            for (JsonElement nic : nicsArr) {
                                JsonObject privateNic = nic.getAsJsonObject();
                                String nicId = privateNic.get("id").getAsString();
                                nicIds.add(nicId);
                            }
                            server.setPrivateNics(nicIds);
                        }
                        try {
                            crossStorageApi.createOrUpdate(defaultRepo, server);
                        } catch (Exception e) {
                            logger.error("Error retrieving Server {}", serverId, e.getMessage());
                        }
                    }
                }
            }
            response.close();
        }
    }
}