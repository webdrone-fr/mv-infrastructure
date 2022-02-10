package org.meveo.scaleway;

import java.time.Instant;
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
        String action = (String)parameters.get(CONTEXT_ACTION);
        ScalewayServer server = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), ScalewayServer.class);
        
        if(server.getInstanceName() == null) { // required
            throw new BusinessException("Invalid Server Instance Name");
        } else if (server.getServerType() == null) { // required
            throw new BusinessException("Invalid Server Type");
        } else if(server.getZone() == null) {
            throw new BusinessException("Invalid Server Zone");
        }

        String zone = server.getZone(); // Required for path
        ServiceProvider serviceProvider = server.getProvider();
        logger.info("Action : {}, Server Uuid : {}, Provider Uuid : {}", action, server.getUuid(), serviceProvider.getUuid());

        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("using credential {}({}) with username {}", credential.getDomainName(), credential.getUuid(), credential.getUsername());
        }

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/servers");

        Map<String, Object> body = new HashMap<String, Object>();
        body.put("name", server.getInstanceName());// required
        body.put("dynamic_ip_required", server.getDynamicIpRequired()); // nullable, default to false
        body.put("commercial_type", server.getServerType()); // required
        body.put("enable_ipv6", server.getEnableIPvSix()); // default to true
        body.put("boot_type", server.getBootType()); // From List of values, includes local, bootscript, rescue -> default is local

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

        // Image
        if (server.getImage() != null) {
            String imageId = server.getImage().getProviderSideId();
            body.put("image", imageId);
        } else {
            // Volumes attached to Server => Empty dictionary at creation => unless has image included so will have volumes from image
            Map<String, Object> volumes = new HashMap<String, Object>();
            body.put("volumes", volumes);
        }

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
            
            // Default server values
            server.setCreationDate(Instant.now());
            server.setLastUpdate(Instant.now());
            server.setProviderSideId(serverObj.get("id").getAsString());
            server.setInstanceName(serverObj.get("name").getAsString());
            server.setServerType(serverObj.get("commercial_type").getAsString());
            server.setZone(serverObj.get("zone").getAsString());
            server.setProvider(serviceProvider);
            server.setOrganization(serverObj.get("organization").getAsString());
            server.setStatus(serverObj.get("state").getAsString());
            server.setDomainName(serverObj.get("hostname").getAsString());
            server.setSergentUrl(server.getDomainName() + ":8001/sergent");

            // Image
            if (!serverObj.get("image").isJsonNull()) {
                String serverImageId = serverObj.get("image").getAsJsonObject().get("id").getAsString();
                ServerImage serverImage = crossStorageApi.find(defaultRepo, ServerImage.class).by("providerSideId", serverImageId).getResult();
                server.setImage(serverImage);

                // Volumes
                JsonObject serverVolumesObj = serverObj.get("volumes").getAsJsonObject();
                Long serverTotalVolumeSize = 0L;
                // will be from image - could be public templates so not in default repo
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
                        rootVolume.setProviderSideId(serverRootVolumeObj.get("id").getAsString());
                        rootVolume.setName(serverRootVolumeObj.get("name").getAsString());
                        rootVolume.setState(serverRootVolumeObj.get("state").getAsString());
                        rootVolume.setSize(String.valueOf(serverRootVolumeObj.get("size").getAsLong()));
                        rootVolume.setZone(zone);
                        rootVolume.setVolumeType(serverRootVolumeObj.get("volume_type").getAsString());
                        rootVolume.setServer(server.getProviderSideId());
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
                        for (int i = 1; i < serverVolumesObj.entrySet().size(); i++) {
                            Map<String, ServerVolume> serverAdditionalVolumes = new HashMap<String, ServerVolume>();
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
                                additionalVolume.setProviderSideId(serverAdditionalVolumeObj.get("id").getAsString());
                                additionalVolume.setName(serverAdditionalVolumeObj.get("name").getAsString());
                                additionalVolume.setState(serverAdditionalVolumeObj.get("state").getAsString());
                                additionalVolume.setSize(String.valueOf(serverAdditionalVolumeObj.get("size").getAsLong()));
                                additionalVolume.setZone(zone);
                                additionalVolume.setVolumeType(serverAdditionalVolumeObj.get("volume_type").getAsString());
                                additionalVolume.setServer(server.getProviderSideId());
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
            }

            // Location Definition
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

            // Scaleway Specific values
            server.setArch(serverObj.get("arch").getAsString());
            server.setProject(serverObj.get("project").getAsString());
            server.setBootType(serverObj.get("boot_type").getAsString());
            server.setIsProtected(serverObj.get("protected").getAsBoolean());
            
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
                logger.info("Server Name : {} successfully created", server.getInstanceName());
            } catch (Exception e) {
                logger.error("error creating server {} : {}", server.getUuid(), e.getMessage());
            }
            response.close();
        }
    }
}
