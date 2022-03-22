package org.meveo.scaleway;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.*;

import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.model.customEntities.Bootscript;
import org.meveo.model.customEntities.PublicIp;
import org.meveo.model.customEntities.ScalewayServer;
import org.meveo.model.customEntities.SecurityGroup;
import org.meveo.model.customEntities.SecurityRule;
import org.meveo.model.customEntities.Server;
import org.meveo.model.customEntities.ServerImage;
import org.meveo.model.customEntities.ServerVolume;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScalewaySetters extends Script{

    private static final Logger logger = LoggerFactory.getLogger(ScalewaySetters.class);


    public static ServerVolume setServerVolume(JsonObject volumeObj, ServerVolume volume, CrossStorageApi crossStorageApi, Repository defaultRepo) {
        String volumeId = volumeObj.get("id").getAsString();
        volume.setProviderSideId(volumeId);
        volume.setName(volumeObj.get("name").getAsString());
        volume.setVolumeType(volumeObj.get("volume_type").getAsString());
        volume.setSize(String.valueOf(volumeObj.get("size").getAsLong()));
        // Server
        if (volumeObj.has("server") && !volumeObj.get("server").isJsonNull()) {
            String serverId = volumeObj.get("server").getAsJsonObject().get("id").getAsString();
            volume.setServer(serverId);
        } else{
            volume.setServer(null);
        }
        if(volumeObj.has("creation_date") && !volumeObj.get("creation_date").isJsonNull()) {
            volume.setCreationDate(OffsetDateTime.parse(volumeObj.get("creation_date").getAsString()).toInstant());
        }
        if(volumeObj.has("modification_date") && !volumeObj.get("modification_date").isJsonNull()) {
            volume.setLastUpdated(OffsetDateTime.parse(volumeObj.get("modification_date").getAsString()).toInstant());

        }
        if(volumeObj.has("zone") && !volumeObj.get("zone").isJsonNull()) {
            volume.setZone(volumeObj.get("zone").getAsString());

        }
        if(volumeObj.has("state") && !volumeObj.get("state").isJsonNull()) {
            volume.setState(volumeObj.get("state").getAsString());
        }
        return volume;
    }

    public static ServerImage setServerImage(JsonObject imageObj, ServerImage image, CrossStorageApi crossStorageApi, Repository defaultRepo) {
        String imageId = imageObj.get("id").getAsString();
        image.setProviderSideId(imageId);
        image.setName(imageObj.get("name").getAsString());
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
                if(crossStorageApi.find(defaultRepo, ScalewayServer.class).by("providerSideId", serverId).getResult()!=null) {
                    image.setFromServer(serverId);
                }                
            } catch (Exception e) {
                // logger.error("Error retrieving server : {} attached to image : {}", serverId, imageId, e.getMessage());
                logger.error("Error retrieving server : {} attached to image : {}", serverId, imageId, e);
            }
            if(imageObj.get("public").getAsBoolean()==false) {
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
                            rootVolume = new ServerVolume();
                            rootVolume.setUuid(rootVolumeId);
                            if(crossStorageApi.find(defaultRepo, ScalewayServer.class).by("providerSideId", serverId).getResult()!=null) {
                                rootVolume.setServer(serverId);
                            } else {
                                rootVolume.setServer(imageId);
                                rootVolume.setCreationDate(OffsetDateTime.parse(imageObj.get("creation_date").getAsString()).toInstant());
                                rootVolume.setLastUpdated(OffsetDateTime.parse(imageObj.get("modification_date").getAsString()).toInstant());
                                rootVolume.setZone(imageObj.get("zone").getAsString());
                            }
                        }
                        rootVolume = ScalewaySetters.setServerVolume(rootVolumeObj, rootVolume, crossStorageApi, defaultRepo);
                        crossStorageApi.createOrUpdate(defaultRepo, rootVolume);
                        image.setRootVolume(rootVolume);
                    } catch (Exception e) {
                        // logger.error("Error retrieving root volume : {} for image : {}", rootVolumeId, imageId, e.getMessage());
                        logger.error("Error retrieving root volume : {} for image : {}", rootVolumeId, imageId, e);
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
                                additionalVolume = new ServerVolume();
                                additionalVolume.setUuid(additionalVolumeId);
                            }
                            additionalVolume = ScalewaySetters.setServerVolume(additionalVolumeObj, additionalVolume, crossStorageApi, defaultRepo);
                            crossStorageApi.createOrUpdate(defaultRepo, additionalVolume);
                            additionalVolumes.put(additionalVolumeEntry.getKey(), additionalVolume);
                        } catch (Exception e) {
                            logger.error("Error retrieving additional volume : {} for image : {}", additionalVolumeId, imageId, e.getMessage());
                        }
                    }
                    image.setAdditionalVolumes(additionalVolumes);
                }
            }
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
                    bootscript = new Bootscript();
                    bootscript.setUuid(bootscriptId);
                }
                bootscript = ScalewaySetters.setBootScript(bootscriptObj, bootscript, crossStorageApi, defaultRepo);
                crossStorageApi.createOrUpdate(defaultRepo, bootscript);
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
            logger.error("Error setting image : {}", imageId, e.getMessage());
        }
        return image;
    }

    public static Bootscript setBootScript(JsonObject bootscriptObj, Bootscript bootscript, CrossStorageApi crossStorageApi, Repository defaultRepo) {
        String bootscriptId = bootscriptObj.get("id").getAsString();
        bootscript.setProviderSideId(bootscriptId);
        bootscript.setZone(bootscriptObj.get("zone").getAsString());
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
        return bootscript;
    }

    public static ScalewayServer setScalewayServer(JsonObject serverObj, ScalewayServer server, ServiceProvider provider, CrossStorageApi crossStorageApi, Repository defaultRepo) {
        String serverId = serverObj.get("id").getAsString();
        server.setProviderSideId(serverId);
        server.setInstanceName(serverObj.get("name").getAsString());
        server.setCreationDate(OffsetDateTime.parse(serverObj.get("creation_date").getAsString()).toInstant());
        server.setLastUpdate(OffsetDateTime.parse(serverObj.get("modification_date").getAsString()).toInstant());
        server.setServerType(serverObj.get("commercial_type").getAsString());
        server.setZone(serverObj.get("zone").getAsString());
        server.setProvider(provider);
        server.setOrganization(serverObj.get("organization").getAsString());
        server.setStatus(serverObj.get("state").getAsString());
        server.setDomainName(serverObj.get("hostname").getAsString());
        server.setSergentUrl(server.getDomainName() + ":8001/sergent");
        // Public IP
        if(!serverObj.get("public_ip").isJsonNull()) {
            server.setPublicIp(serverObj.get("public_ip").getAsJsonObject().get("address").getAsString());
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
                    image = new ServerImage();
                    image.setUuid(imageId);
                }
                image = ScalewaySetters.setServerImage(imageObj, image, crossStorageApi, defaultRepo);
                crossStorageApi.createOrUpdate(defaultRepo, image);
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
                    rootVolume = new ServerVolume();
                    rootVolume.setUuid(rootVolumeId);
                }
                rootVolume = setServerVolume(rootVolumeObj, rootVolume, crossStorageApi, defaultRepo);
                crossStorageApi.createOrUpdate(defaultRepo, rootVolume);
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
                    if(!additionalVolumeEntry.getKey().equals("0")) { // key for root volume
                        JsonObject additionalVolumeObj = serverVolumesObj.get(additionalVolumeEntry.getKey()).getAsJsonObject();
                        String additionalVolumeId = additionalVolumeObj.get("id").getAsString();
                        ServerVolume additionalVolume = null;
                        try {
                            if(crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", additionalVolumeId).getResult() != null) {
                                additionalVolume = crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", additionalVolumeId).getResult();
                            } else {
                                additionalVolume = new ServerVolume();
                                additionalVolume.setUuid(additionalVolumeId);
                            }
                            additionalVolume = setServerVolume(additionalVolumeObj, additionalVolume, crossStorageApi, defaultRepo);
                            crossStorageApi.createOrUpdate(defaultRepo, additionalVolume);
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
        for (JsonElement serverAction : serverActionsArr) {
            actions.add(serverAction.getAsString());
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
                } else {
                    // TODO update Security Groups
                    securityGroup = new SecurityGroup();
                    securityGroup.setUuid(securityGroupId);
                    securityGroup.setProviderSideId(securityGroupId);
                    securityGroup.setName(securityGroupObj.get("name").getAsString());
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
                    bootscript = new Bootscript();
                    bootscript.setUuid(bootscriptId);
                }
                bootscript = setBootScript(bootscriptObj, bootscript, crossStorageApi, defaultRepo);
                crossStorageApi.createOrUpdate(defaultRepo, bootscript);
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
            logger.error("Error setting Server : {}", serverId, e.getMessage());
        }
        return server;
    }

    public static PublicIp setPublicIp(JsonObject publicIpObj, PublicIp publicIp, ServiceProvider provider, CrossStorageApi crossStorageApi, Repository defaultRepo) {
        String publicIpId = publicIpObj.get("id").getAsString();
        publicIp.setProviderSideId(publicIpId);
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
            String serverName = publicIpObj.get("server").getAsJsonObject().get("name").getAsString();
            if (serverName.toLowerCase().startsWith("dev-")|| serverName.toLowerCase().startsWith("int")){
                try {
                    if(crossStorageApi.find(defaultRepo, ScalewayServer.class).by("providerSideId", serverId).getResult()!=null) { // TODO
                        ScalewayServer server = crossStorageApi.find(defaultRepo, ScalewayServer.class).by("providerSideId", serverId).getResult();
                        publicIp.setServer(server);
                    }
                } catch (Exception e) {
                    logger.error("Error retrieving Server : {} for Public Ip : {}", serverId, publicIpId, e.getMessage());
                }
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
            logger.error("Error setting public ip : {}", publicIpId, e.getMessage());
        }
        return publicIp;
    }

    public static SecurityGroup setSecurityGroup(JsonObject securityGroupObj, SecurityGroup securityGroup, CrossStorageApi crossStorageApi, Repository defaultRepo) {
        String securityGroupId = securityGroupObj.get("id").getAsString();
        securityGroup.setProviderSideId(securityGroupId);
        securityGroup.setName(securityGroupObj.get("name").getAsString());
        securityGroup.setCreationDate(OffsetDateTime.parse(securityGroupObj.get("creation_date").getAsString()).toInstant());
        securityGroup.setLastUpdated(OffsetDateTime.parse(securityGroupObj.get("modification_date").getAsString()).toInstant());
        securityGroup.setProject(securityGroupObj.get("project").getAsString());
        securityGroup.setStateful(securityGroupObj.get("stateful").getAsBoolean());
        securityGroup.setState(securityGroupObj.get("state").getAsString());
        securityGroup.setInboundDefaultPolicy(securityGroupObj.get("inbound_default_policy").getAsString());
        securityGroup.setOutboundDefaultPolicy(securityGroupObj.get("outbound_default_policy").getAsString());
        securityGroup.setProjectDefault(securityGroupObj.get("project_default").getAsBoolean());
        securityGroup.setEnableDefaultSecurity(securityGroupObj.get("enable_default_security").getAsBoolean());
        securityGroup.setZone(securityGroupObj.get("zone").getAsString());
        // Description
        if(!securityGroupObj.get("description").isJsonNull()) {
            securityGroup.setDescription(securityGroupObj.get("description").getAsString());
        }
        // Servers
        if(!securityGroupObj.get("servers").isJsonNull()) {
            JsonArray serversArr = securityGroupObj.get("servers").getAsJsonArray();
            ArrayList<String> servers = new ArrayList<String>();
            for (JsonElement serverEl : serversArr) {
                JsonObject serverObj = serverEl.getAsJsonObject();
                String serverId = serverObj.get("id").getAsString();
                String serverInstanceName = serverObj.get("name").getAsString();
                // if(serverInstanceName.startsWith("dev-") && crossStorageApi.find(defaultRepo, Server.class).by("providerSideId", serverId).getResult() != null) {
                //     servers.add(serverId);
                // }
                servers.add(serverId+" : "+serverInstanceName);
            }
            securityGroup.setServers(servers);
        }
        return securityGroup;
    }

    public static SecurityRule setSecurityRule(JsonObject ruleObj, SecurityRule rule, CrossStorageApi crossStorageApi, Repository defaultRepo) {
        String ruleId = ruleObj.get("id").getAsString();
        rule.setProviderSideId(ruleId);
        rule.setProtocol(ruleObj.get("protocol").getAsString());
        rule.setDirection(ruleObj.get("direction").getAsString());
        rule.setAction(ruleObj.get("action").getAsString());
        rule.setIpRange(ruleObj.get("ip_range").getAsString());
        if(!ruleObj.get("dest_port_from").isJsonNull()) {
            rule.setDestPortFrom(ruleObj.get("dest_port_from").getAsLong());
        }
        if(!ruleObj.get("dest_port_to").isJsonNull()) {
            rule.setDestPortTo(ruleObj.get("dest_port_to").getAsLong());
        }
        rule.setPosition(ruleObj.get("position").getAsLong());
        if(!ruleObj.get("editable").isJsonNull()) {
            rule.setEditable(ruleObj.get("editable").getAsBoolean());
        }
        rule.setZone(ruleObj.get("zone").getAsString());
        return rule;
    }

    public static Map<String, Object> setServerType(JsonObject serverTypeObj) {
        Map<String, Object> serverType = new HashMap<String, Object>();
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
        return serverType;
    }
}