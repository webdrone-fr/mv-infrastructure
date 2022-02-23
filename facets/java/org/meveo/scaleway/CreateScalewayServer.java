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
import org.meveo.model.customEntities.ScalewayServer;
import org.meveo.model.customEntities.SecurityGroup;
import org.meveo.model.customEntities.ServerImage;
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
                rootVolume.put("id", serverRootVolume.getProviderSideId());
                rootVolume.put("boot", serverRootVolume.getIsBoot());
                rootVolume.put("name", serverRootVolume.getName());
                volumes.put("0", rootVolume);
            } catch (Exception e) {
                logger.error("Error retrieving server root volume", e.getMessage());
            }
            // Additional Volumes
            if (server.getAdditionalVolumes() != null) {
                Map<String, ServerVolume> serverAdditionalVolumes = server.getAdditionalVolumes();
                for (Map.Entry<String, ServerVolume> serverAdditionalVolume : serverAdditionalVolumes.entrySet()) {
                    Map<String, Object> additionalVolume = new HashMap<String, Object>();
                    String serverAdditionalVolumeId = serverAdditionalVolume.getValue().getUuid();
                    try {
                        ServerVolume additionalVolumeObj = crossStorageApi.find(defaultRepo, serverAdditionalVolumeId, ServerVolume.class);
                        additionalVolume.put("id", additionalVolumeObj.getProviderSideId());
                        additionalVolume.put("boot", additionalVolumeObj.getIsBoot());
                        additionalVolume.put("name", additionalVolumeObj.getName());
                        volumes.put(serverAdditionalVolume.getKey(), additionalVolume); // keys should be 1, 2, 3...
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
            String serverId = serverObj.get("id").getAsString();
            // Default server values
            server.setCreationDate(OffsetDateTime.parse(serverObj.get("creation_date").getAsString()).toInstant());
            server.setLastUpdate(OffsetDateTime.parse(serverObj.get("modification_date").getAsString()).toInstant());
            server.setProviderSideId(serverId);
            server.setInstanceName(serverObj.get("name").getAsString());
            server.setServerType(serverObj.get("commercial_type").getAsString());
            server.setZone(serverObj.get("zone").getAsString());
            server.setProvider(provider);
            server.setOrganization(serverObj.get("organization").getAsString());
            server.setStatus(serverObj.get("state").getAsString());
            server.setDomainName(serverObj.get("hostname").getAsString());
            server.setSergentUrl(server.getDomainName() + ":8001/sergent");

            // Image
            if (!serverObj.get("image").isJsonNull()) {
                String serverImageId = serverObj.get("image").getAsJsonObject().get("id").getAsString();
                ServerImage serverImage = crossStorageApi.find(defaultRepo, ServerImage.class).by("providerSideId", serverImageId).getResult();
                server.setImage(serverImage);
            }

            // Volumes
            JsonObject serverVolumesObj = serverObj.get("volumes").getAsJsonObject();
            Long serverTotalVolumeSize = 0L;
            // could be from image - could be public templates so not in default repo
            if (serverVolumesObj.entrySet().size() >= 1) {
                // Root Volume
                String serverRootVolumeId = serverVolumesObj.get("0").getAsJsonObject().get("id").getAsString();
                if (crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", serverRootVolumeId).getResult() != null) {
                    // if volume exists in default repo
                    ServerVolume serverRootVolume = crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", serverRootVolumeId).getResult();
                    server.setRootVolume(serverRootVolume);
                    serverTotalVolumeSize += Long.parseLong(serverRootVolume.getSize());
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
                        serverTotalVolumeSize += Long.parseLong(rootVolume.getSize());
                    } catch(Exception e) {
                        logger.error("error creating root volume {} : {}", serverRootVolumeId, e.getMessage());
                    }
                }
                // Additional Volumes
                if (serverVolumesObj.entrySet().size() > 1) {
                    Map<String, ServerVolume> serverAdditionalVolumes = new HashMap<String, ServerVolume>();
                    for (int i = 1; i < serverVolumesObj.entrySet().size(); i++) {
                        String serverAdditionalVolumeId = serverVolumesObj.get(String.valueOf(i)).getAsJsonObject().get("id").getAsString();
                        if (crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", serverAdditionalVolumeId).getResult() != null) {
                            // if additional volume exists in default repo
                            ServerVolume serverAdditionalVolume = crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", serverAdditionalVolumeId).getResult();
                            serverAdditionalVolumes.put(String.valueOf(i), serverAdditionalVolume);
                            serverTotalVolumeSize += Long.parseLong(serverAdditionalVolume.getSize());
                        } else { // if additional volume does not exist in default repo - create new
                            JsonObject serverAdditionalVolumeObj = serverVolumesObj.get(String.valueOf(i)).getAsJsonObject();
                            ServerVolume additionalVolume = new ServerVolume();
                            additionalVolume.setCreationDate(OffsetDateTime.parse(serverAdditionalVolumeObj.get("creation_date").getAsString()).toInstant());
                            additionalVolume.setLastUpdated(OffsetDateTime.parse(serverAdditionalVolumeObj.get("modification_date").getAsString()).toInstant()); // Or set to now?
                            additionalVolume.setProviderSideId(serverAdditionalVolumeId);
                            additionalVolume.setUuid(serverAdditionalVolumeId);
                            additionalVolume.setName(serverAdditionalVolumeObj.get("name").getAsString());
                            additionalVolume.setState(serverAdditionalVolumeObj.get("state").getAsString());
                            additionalVolume.setSize(String.valueOf(serverAdditionalVolumeObj.get("size").getAsLong()));
                            additionalVolume.setZone(serverAdditionalVolumeObj.get("zone").getAsString());
                            additionalVolume.setVolumeType(serverAdditionalVolumeObj.get("volume_type").getAsString());
                            additionalVolume.setServer(serverId);
                            additionalVolume.setIsBoot(serverAdditionalVolumeObj.get("boot").getAsBoolean());
                            try {
                                crossStorageApi.createOrUpdate(defaultRepo, additionalVolume);
                                serverAdditionalVolumes.put(String.valueOf(i), additionalVolume);
                                serverTotalVolumeSize += Long.parseLong(additionalVolume.getSize());
                            } catch(Exception e) {
                                logger.error("error creating additional volume {} : {}", serverAdditionalVolumeId, e.getMessage());
                            }
                        }
                    }
                }
            }
            server.setVolumeSize(String.valueOf(serverTotalVolumeSize));
            

            // Location Definition
            String locationDefinition = "zone_id/platform_id/cluster_id/hypervisor_id/node_id";
            server.setLocationDefinition(locationDefinition);

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
            
            // Server Actions
            JsonArray allowedActions = serverObj.get("allowed_actions").getAsJsonArray();
            ArrayList<String> serverActions = new ArrayList<String>();
            for (JsonElement allowedAction : allowedActions) {
                serverActions.add(allowedAction.getAsString());
            }
            server.setServerActions(serverActions);

            // Scaleway Specific values
            server.setArch(serverObj.get("arch").getAsString());
            server.setProject(serverObj.get("project").getAsString());
            server.setBootType(serverObj.get("boot_type").getAsString());
            server.setIsProtected(serverObj.get("protected").getAsBoolean());
            
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

            // Private NICs
            if (!serverObj.get("private_nics").isJsonNull()) {
                JsonArray nicsArr = serverObj.get("private_nics").getAsJsonArray();
                ArrayList<String> nicIds = new ArrayList<String>();
                for (JsonElement nic : nicsArr) {
                    JsonObject nicObj = nic.getAsJsonObject();
                    String nicId = nicObj.get("id").getAsString();
                    nicIds.add(nicId);
                }
                server.setPrivateNics(nicIds);
            }

            try {
                crossStorageApi.createOrUpdate(defaultRepo, server);
            } catch (Exception e) {
                logger.error("error creating server {} : {}", serverId, e.getMessage());
            }
            response.close();
        }
    }
}