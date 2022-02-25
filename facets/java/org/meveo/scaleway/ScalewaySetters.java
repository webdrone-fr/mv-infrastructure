package org.meveo.scaleway;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.google.gson.*;

import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.model.customEntities.Bootscript;
import org.meveo.model.customEntities.PublicIp;
import org.meveo.model.customEntities.ScalewayServer;
import org.meveo.model.customEntities.SecurityGroup;
import org.meveo.model.customEntities.Server;
import org.meveo.model.customEntities.ServerImage;
import org.meveo.model.customEntities.ServerVolume;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScalewaySetters extends Script{

    private static final Logger logger = LoggerFactory.getLogger(ScalewayHelperService.class);


    public static ServerVolume setServerVolume(JsonObject volumeObj, CrossStorageApi crossStorageApi, Repository defaultRepo) {
        String volumeId = volumeObj.get("id").getAsString();
        ServerVolume volume = new ServerVolume();
        volume.setUuid(volumeId);
        volume.setProviderSideId(volumeId);
        volume.setName(volumeObj.get("name").getAsString());
        if (!volumeObj.get("server").isJsonNull()) {
            volume.setServer(volumeObj.get("server").getAsJsonObject().get("id").getAsString());
        }
        volume.setCreationDate(OffsetDateTime.parse(volumeObj.get("creation_date").getAsString()).toInstant());
        volume.setLastUpdated(OffsetDateTime.parse(volumeObj.get("modification_date").getAsString()).toInstant());
        volume.setVolumeType(volumeObj.get("volume_type").getAsString());
        volume.setSize(String.valueOf(volumeObj.get("size").getAsLong()));
        volume.setZone(volumeObj.get("zone").getAsString());
        volume.setState(volumeObj.get("state").getAsString());
        try {
            crossStorageApi.createOrUpdate(defaultRepo, volume);
        } catch (Exception e) {
            logger.error("Error creating volume : {}", volumeId, e.getMessage());
        }
        return volume;
    }

    public static ServerImage setServerImage(JsonObject imageObj, CrossStorageApi crossStorageApi, Repository defaultRepo) {
        String imageId = imageObj.get("id").getAsString();
        ServerImage image = new ServerImage();
        image.setName(imageObj.get("name").getAsString());
        image.setUuid(imageId);
        image.setProviderSideId(imageId);
        image.setCreationDate(OffsetDateTime.parse(imageObj.get("creation_date").getAsString()).toInstant());
        image.setLastUpdated(OffsetDateTime.parse(imageObj.get("modification_date").getAsString()).toInstant());
        image.setProject(imageObj.get("project").getAsString());
        image.setIsPublic(imageObj.get("public").getAsBoolean());
        image.setZone(imageObj.get("zone").getAsString());
        image.setState(imageObj.get("state").getAsString());
        // Server
        if (!imageObj.get("from_server").isJsonNull()) {
            String serverId = imageObj.get("from_server").getAsString();
            try {
                Server server = crossStorageApi.find(defaultRepo, Server.class).by("providerSideId", serverId).getResult();
                image.setFromServer(server);
            } catch (Exception e) {
                logger.error("Error retrieving server attached to image : {}", imageId, e.getMessage());
            }
        }
        // Volumes
        // Root Volume
        if (!imageObj.get("root_volume").isJsonNull()) {
            JsonObject rootVolumeObj = imageObj.get("root_volume").getAsJsonObject();
            String rootVolumeId = rootVolumeObj.get("id").getAsString();
            ServerVolume rootVolume = null;
            try {
                if(crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", rootVolumeId).getResult() != null) {
                    rootVolume = crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", rootVolumeId).getResult();
                } else {
                    rootVolume = setServerVolume(rootVolumeObj, crossStorageApi, defaultRepo);
                }
                image.setRootVolume(rootVolume);
            } catch (Exception e) {
                logger.error("Error retrieving root volume : {} for image : {}", rootVolumeId, imageId, e.getMessage());
            }
        }
        // Additional Volumes
        if (!imageObj.get("extra_volumes").isJsonNull()) {
            Map<String, ServerVolume> additionalVolumes = new HashMap<String, ServerVolume>();
            JsonObject additionalVolumesObj = imageObj.get("extra_volumes").getAsJsonObject();
            Set<Map.Entry<String, JsonElement>> additionalVolumeEntries = additionalVolumesObj.entrySet();
            for(Map.Entry<String, JsonElement> additionalVolumeEntry : additionalVolumeEntries) {
                JsonObject additionalVolumeObj = additionalVolumesObj.get(additionalVolumeEntry.getKey()).getAsJsonObject();
                String additionalVolumeId = additionalVolumeObj.get("id").getAsString();
                ServerVolume additionalVolume = null;
                try {
                    if(crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", additionalVolumeId).getResult() != null) {
                        additionalVolume = crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", additionalVolumeId).getResult();
                    } else {
                        additionalVolume = setServerVolume(additionalVolumeObj, crossStorageApi, defaultRepo);
                    }
                    additionalVolumes.put(additionalVolumeEntry.getKey(), additionalVolume);
                } catch (Exception e) {
                    logger.error("Error retrieving additional volume : {} for image : {}", additionalVolumeId, imageId, e.getMessage());
                }
            }
            image.setAdditionalVolumes(additionalVolumes);
        }
        // Bootscript
        if (!imageObj.get("default_bootscript").isJsonNull()) {
            JsonObject bootscriptObj = imageObj.get("default_bootscript").getAsJsonObject();
            String bootscriptId = bootscriptObj.get("id").getAsString();
            Bootscript bootscript = null;
            try {
                if(crossStorageApi.find(defaultRepo, Bootscript.class).by("providerSideId", bootscriptId).getResult() != null) {
                    bootscript = crossStorageApi.find(defaultRepo, Bootscript.class).by("providerSideId", bootscriptId).getResult();
                } else {
                    bootscript = setBootScript(bootscriptObj, crossStorageApi, defaultRepo);
                }
                image.setDefaultBootscript(bootscript);
            } catch (Exception e) {
                logger.error("Error retrieving bootscript : {} for image : {}", bootscriptId, imageId, e.getMessage());
            }
        }
        // Tags
        if (!imageObj.get("tags").isJsonNull()) {
            ArrayList<String> imageTags = new ArrayList<String>();
            JsonArray imageTagsArr = imageObj.get("tags").getAsJsonArray();
            for (JsonElement tag : imageTagsArr) {
                imageTags.add(tag.getAsString());
            }
            image.setTags(imageTags);
        }
        try {
            crossStorageApi.createOrUpdate(defaultRepo, image);
        } catch (Exception e) {
            logger.error("Error creating image : {}", imageId, e.getMessage());
        }
        return image;
    }

    public static Bootscript setBootScript(JsonObject bootscriptObj, CrossStorageApi crossStorageApi, Repository defaultRepo) {
        String bootscriptId = bootscriptObj.get("id").getAsString();
        Bootscript bootscript = new Bootscript();
        bootscript.setUuid(bootscriptId);
        bootscript.setZone(bootscriptObj.get("zone").getAsString());
        bootscript.setProviderSideId(bootscriptId);
        bootscript.setArch(bootscriptObj.get("architecture").getAsString());
        bootscript.setBootcmdargs(bootscriptObj.get("bootcmdargs").getAsString());
        bootscript.setDtb(bootscriptObj.get("dtb").getAsString());
        bootscript.setInitrd(bootscriptObj.get("initrd").getAsString());
        bootscript.setKernel(bootscriptObj.get("kernel").getAsString());
        bootscript.setOrganization(bootscriptObj.get("organization").getAsString());
        bootscript.setProject(bootscriptObj.get("project").getAsString());
        bootscript.setIsDefault(bootscriptObj.get("default").getAsBoolean());
        bootscript.setIsPublic(bootscriptObj.get("public").getAsBoolean());
        bootscript.setTitle(bootscriptObj.get("title").getAsString());
        try {
            crossStorageApi.createOrUpdate(defaultRepo, bootscript);
        } catch (Exception e) {
            logger.error("Error creating Bootscript {} : {}", bootscript.getTitle(), bootscriptId, e.getMessage());
        }
        return bootscript;
    }

    public static ScalewayServer setScalewayServer(JsonObject serverObj, ServiceProvider provider, CrossStorageApi crossStorageApi, Repository defaultRepo) {
        String serverId = serverObj.get("id").getAsString();
        ScalewayServer server = new ScalewayServer();
        // Default server values
        server.setCreationDate(OffsetDateTime.parse(serverObj.get("creation_date").getAsString()).toInstant());
        server.setLastUpdate(OffsetDateTime.parse(serverObj.get("modification_date").getAsString()).toInstant());
        server.setUuid(serverId);
        server.setProviderSideId(serverId);
        server.setInstanceName(serverObj.get("name").getAsString());
        server.setServerType(serverObj.get("commercial_type").getAsString());
        server.setZone(serverObj.get("zone").getAsString());
        server.setProvider(provider);
        server.setOrganization(serverObj.get("organization").getAsString());
        server.setStatus(serverObj.get("state").getAsString());
        server.setDomainName(serverObj.get("hostname").getAsString());
        server.setSergentUrl(server.getDomainName() + ":8001/sergent");
        // Public IP
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
            JsonObject imageObj = serverObj.get("image").getAsJsonObject();
            String imageId = imageObj.get("id").getAsString();
            ServerImage image = null;
            try {
                if(crossStorageApi.find(defaultRepo, ServerImage.class).by("providerSideId", imageId).getResult() != null) {
                    image = crossStorageApi.find(defaultRepo, ServerImage.class).by("providerSideId", imageId).getResult();
                } else {
                    image = setServerImage(imageObj, crossStorageApi, defaultRepo);
                }
                server.setImage(image);
            } catch (Exception e) {
                logger.error("Error retrieving image : {} for server : {}", imageId, serverId, e.getMessage());
            } 
        }
        //Volumes
        JsonObject serverVolumesObj = serverObj.get("volumes").getAsJsonObject();
        Long serverTotalVolumesSize = 0L;
        Long serverTotalLocalVolumesSize = 0L;
        if (serverVolumesObj.entrySet().size() >= 1) {
            // Root Volume
            JsonObject rootVolumeObj = serverVolumesObj.get("0").getAsJsonObject();
            String rootVolumeId = rootVolumeObj.get("id").getAsString();
            ServerVolume rootVolume = null;
            try {
                if(crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", rootVolumeId).getResult() != null) {
                    rootVolume = crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", rootVolumeId).getResult();
                } else {
                    rootVolume = setServerVolume(rootVolumeObj, crossStorageApi, defaultRepo);
                }
                server.setRootVolume(rootVolume);
                serverTotalVolumesSize = Long.valueOf(rootVolume.getSize());
                if(rootVolume.getVolumeType().equalsIgnoreCase("l_ssd")){
                    serverTotalLocalVolumesSize = Long.valueOf(rootVolume.getSize());
                }
            } catch (Exception e) {
                logger.error("Error retrieving root volume : {} ", rootVolumeId, e.getMessage());
            }
            // Additional Volumes
            if (serverVolumesObj.entrySet().size() > 1) {
                Map<String, ServerVolume> serverAdditionalVolumes = new HashMap<String, ServerVolume>();
                Set<Map.Entry<String, JsonElement>> additionalVolumeEntries = serverVolumesObj.entrySet();
                for (Map.Entry<String, JsonElement> additionalVolumeEntry : additionalVolumeEntries)  {
                    if(!additionalVolumeEntry.getKey().equals("0")) { // key for root volume {
                        JsonObject additionalVolumeObj = serverVolumesObj.get(additionalVolumeEntry.getKey()).getAsJsonObject();
                        String additionalVolumeId = additionalVolumeObj.get("id").getAsString();
                        ServerVolume additionalVolume = null;
                        try {
                            if(crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", additionalVolumeId).getResult() != null) {
                                additionalVolume = crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", additionalVolumeId).getResult();
                            } else {
                                additionalVolume = setServerVolume(additionalVolumeObj, crossStorageApi, defaultRepo);
                            }
                            serverAdditionalVolumes.put(additionalVolumeEntry.getKey(), additionalVolume);
                            serverTotalVolumesSize += Long.valueOf(additionalVolume.getSize());
                            if(additionalVolume.getVolumeType().equalsIgnoreCase("l_ssd")){
                                serverTotalLocalVolumesSize = Long.valueOf(additionalVolume.getSize());
                            }
                        } catch (Exception e) {
                            logger.error("Error retrieving additional volume : {}", additionalVolumeId, e.getMessage());
                        }
                    }
                }
                server.setAdditionalVolumes(serverAdditionalVolumes);
            }
        }
        // Volume size
        server.setVolumeSize(String.valueOf(serverTotalVolumesSize));
        server.setTotalLocalVolumesSize(String.valueOf(serverTotalLocalVolumesSize));
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
        // Security Group
        if (!serverObj.get("security_group").isJsonNull()) {
            JsonObject securityGroupObj = serverObj.get("security_group").getAsJsonObject();
            String securityGroupId = securityGroupObj.get("id").getAsString();
            SecurityGroup securityGroup = null;
            try {
                if (crossStorageApi.find(defaultRepo, SecurityGroup.class).by("providerSideId", securityGroupId).getResult() != null) {
                    securityGroup = crossStorageApi.find(defaultRepo, SecurityGroup.class).by("providerSideId", securityGroupId).getResult();
                    server.setSecurityGroup(securityGroup);
                } else {
                    securityGroup = new SecurityGroup();
                    securityGroup.setUuid(securityGroupId);
                    securityGroup.setProviderSideId(securityGroupId);
                    securityGroup.setName(securityGroupObj.get("name").getAsString());
                    securityGroup.setZone(securityGroupObj.get("zone").getAsString());
                    try {
                        crossStorageApi.createOrUpdate(defaultRepo, securityGroup);
                    }catch (Exception e) {
                        logger.error("Error creating new security group", e.getMessage());
                    }
                }
                server.setSecurityGroup(securityGroup);
            } catch (Exception e) {
                logger.error("Error retrieving security group : {}", securityGroupId, e.getMessage());
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
            Bootscript bootscript = null;
            try {
                if(crossStorageApi.find(defaultRepo, Bootscript.class).by("providerSideId", bootscriptId).getResult() != null) {
                    bootscript = crossStorageApi.find(defaultRepo, Bootscript.class).by("providerSideId", bootscriptId).getResult();
                } else {
                    bootscript = setBootScript(bootscriptObj, crossStorageApi, defaultRepo);
                }
            server.setBootscript(bootscript);
            } catch (Exception e) {
                logger.error("Error retrieving bootscript : {}", bootscriptId, e.getMessage());
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
            logger.error("Error creating Server : {}", serverId, e.getMessage());
        }
        return server;
    }

    public static PublicIp setPublicIp(JsonObject publicIpObj,  ServiceProvider provider, CrossStorageApi crossStorageApi, Repository defaultRepo) {
        String publicIpId = publicIpObj.get("id").getAsString();
        PublicIp publicIp = new PublicIp();

        // default values
        // Need creation + update date? - update possible
        publicIp.setProviderSideId(publicIpId);
        publicIp.setUuid(publicIpId);
        publicIp.setIpVFourAddress(publicIpObj.get("address").getAsString());
        publicIp.setOrganization(publicIpObj.get("organization").getAsString());
        publicIp.setProject(publicIpObj.get("project").getAsString());
        publicIp.setZone(publicIpObj.get("zone").getAsString());
        publicIp.setProvider(provider);
        // reverse - nullable
        if (!publicIpObj.get("reverse").isJsonNull()) {
            publicIp.setReverse(publicIpObj.get("reverse").getAsString());
        }
        // Server
        if (!publicIpObj.get("server").isJsonNull()) {
            String serverId = publicIpObj.get("server").getAsJsonObject().get("id").getAsString();
            try {
                ScalewayServer server = crossStorageApi.find(defaultRepo, ScalewayServer.class).by("providerSideId", serverId).getResult();
                publicIp.setServer(server);
            } catch (Exception e) {
                logger.error("Error retrieving server {} for Public Ip : {}", serverId, publicIpId, e.getMessage());
            }
        }
        // Tags
        if (!publicIpObj.get("tags").isJsonNull()) {
            JsonArray imageTagsArr = publicIpObj.get("tags").getAsJsonArray();
            ArrayList<String> imageTags = new ArrayList<String>();
            for (JsonElement tag : imageTagsArr) {
                imageTags.add(tag.getAsString());
            }
            publicIp.setTags(imageTags);
        }
        try {
            crossStorageApi.createOrUpdate(defaultRepo, publicIp);
        } catch (Exception e) {
            logger.error("Error creating public ip : {}", publicIpId, e.getMessage());
        }
        return publicIp;
    }
}