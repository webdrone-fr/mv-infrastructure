package org.meveo.scaleway;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import com.google.gson.*;

import org.apache.commons.io.FileUtils;
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
        } else if (server.getRootVolume() == null) {
            throw new BusinessException("Root Volume cannot be empty");
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

        // Server Type Constraints Check
        String serverType = server.getServerType();
        // Add up sizes of root volume + all additional volumes
        Long serverTotalVolumesSizes = calcServerTotalVolumesSizes(server);

        // Get server type constraints
        // minimum size requirement for sum of all volumes for server type
        Long serverMinVolumeSizeReq = getServerTypeMinVolumeSizeRequirement(server, credential);

        // Check if available size meets requirements for server type
        if (serverTotalVolumesSizes < serverMinVolumeSizeReq) {
            String serverTotalVolumesSizesStr = Long.toString(serverTotalVolumesSizes);
            String serverMinVolumeSizeReqStr = Long.toString(serverMinVolumeSizeReq);
            logger.debug("Current available volume size : {}, Volume size required for server type {} : {}", serverTotalVolumesSizesStr , serverType, serverMinVolumeSizeReqStr);
            throw new BusinessException("Current total volume size is too small for selected server type");
        }

        // Server Creation
        WebTarget target = client.target("https://"+SCALEWAY_URL+"/instance/v1/zones/"+zone+"/servers");

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
        String imageId = null;
        if (server.getImage() != null) {
            imageId = server.getImage().getProviderSideId();
            body.put("image", imageId);
        }

        // Volumes attached to Server
        Map<String, Object> volumes = new HashMap<String, Object>();
        // Root Volume
        JsonObject rootVolume = new JsonObject();
        rootVolume.addProperty("id", server.getRootVolume().getProviderSideId());
        rootVolume.addProperty("boot", server.getRootVolume().getIsBoot());
        rootVolume.addProperty("name", server.getRootVolume().getName());
        rootVolume.addProperty("size", server.getRootVolume().getSize());
        rootVolume.addProperty("volume_type", server.getRootVolume().getVolumeType()); // need to check == l_ssd OR b_ssd at volume creation
        volumes.put("0", rootVolume);
        // Additional Volumes
        if (!server.getAdditionalVolumes().isEmpty()) {
            for (Map.Entry<String, ServerVolume> serverAdditionalVolume : server.getAdditionalVolumes().entrySet()) {
                JsonObject additionalVolume = new JsonObject();
                Long serverAdditionalVolumeSize = Long.parseLong(serverAdditionalVolume.getValue().getSize());
                additionalVolume.addProperty("id", serverAdditionalVolume.getValue().getProviderSideId());
                additionalVolume.addProperty("name", serverAdditionalVolume.getValue().getName());
                additionalVolume.addProperty("size", serverAdditionalVolumeSize);
                additionalVolume.addProperty("volume_type", serverAdditionalVolume.getValue().getVolumeType());
                volumes.put(serverAdditionalVolume.getKey(), additionalVolume); // keys should be 1, 2, 3...
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
            server.setPublicIp(serverObj.get("public_ip").getAsJsonObject().get("address").getAsString());

            // Image
            if (!serverObj.get("image").isJsonNull()) {
                String serverImageId = serverObj.get("image").getAsJsonObject().get("id").getAsString();
                ServerImage serverImage = crossStorageApi.find(defaultRepo, ServerImage.class).by("providerSideId", serverImageId).getResult();
                server.setImage(serverImage);
            }

            // Volumes
            JsonObject serverVolumesObj = serverObj.get("volumes").getAsJsonObject();
            Long serverTotalVolumeSize = 0L;
            // Root Volume
            String serverRootVolumeId = serverVolumesObj.get("0").getAsJsonObject().get("id").getAsString();
            ServerVolume serverRootVolume = crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", serverRootVolumeId).getResult();
            server.setRootVolume(serverRootVolume);
            serverTotalVolumeSize += Long.parseLong(serverRootVolume.getSize());

            // Additional Volumes
            if (serverVolumesObj.entrySet().size() > 1) {
                Map<String, ServerVolume> serverAdditionalVolumes = new HashMap<String, ServerVolume>();
                for (int i = 1; i < serverVolumesObj.entrySet().size(); i++) {
                    String additionalVolumeId = serverVolumesObj.get(String.valueOf(i)).getAsString();
                    ServerVolume serverAdditionalVolume = crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", additionalVolumeId).getResult();
                    serverAdditionalVolumes.put(String.valueOf(i), serverAdditionalVolume);
                    serverTotalVolumeSize += Long.parseLong(serverAdditionalVolume.getSize()) ;
                }
                server.setAdditionalVolumes(serverAdditionalVolumes);
            }
            server.setVolumeSize(FileUtils.byteCountToDisplaySize(serverTotalVolumeSize));

            // Location
            JsonObject serverLocationObj = serverObj.get("location").getAsJsonObject();
            String serverLocation = 
                serverLocationObj.get("zone_id")+"/"+
                serverLocationObj.get("platform_id")+"/"+
                serverLocationObj.get("cluster_id")+"/"+
                serverLocationObj.get("hypervisor_id")+"/"+
                serverLocationObj.get("node_id");
            server.setLocation(serverLocation);
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
            server.setPrivateIp(serverObj.get("private_ip").getAsString());
            server.setIpVSix(serverObj.get("ipv6").getAsJsonObject().get("address").getAsString());
            
            // Private NICs
            if (serverObj.get("private_nics").isJsonNull()) {
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
        }
    }

    public static Long getServerTypeMinVolumeSizeRequirement(ScalewayServer server, Credential credential) throws BusinessException {
        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());

        String zone = server.getZone();
        String serverType = server.getServerType();
        Long serverMinVolumeSizeReq = 0L;
        WebTarget target = client.target("https://"+SCALEWAY_URL+"/instance/v1/zones/"+zone+"/products/servers");
        Response response = CredentialHelperService.setCredential(target.request(), credential).get();
        String value =response.readEntity(String.class);
        if (response.getStatus()<300) {
            JsonObject serversObj = 
                new JsonParser().parse(value).getAsJsonObject()
                .get("servers").getAsJsonObject();
            JsonObject serverConstraints = serversObj.get(serverType).getAsJsonObject();
            serverMinVolumeSizeReq = serverConstraints.get("volumes_constraint").getAsJsonObject().get("min_size").getAsLong();
        }
        return serverMinVolumeSizeReq;
    }

    public static Long calcServerTotalVolumesSizes(ScalewayServer server){
        Long serverTotalVolumesSizes = 0L;
        ArrayList<Long> allVolumesSizes = new ArrayList<Long>();
        Long rootVolumeSize = Long.parseLong(server.getRootVolume().getSize());

        allVolumesSizes.add(rootVolumeSize);
        Map<String, ServerVolume> serverAdditionalVolumes = server.getAdditionalVolumes();
        if (!serverAdditionalVolumes.isEmpty()) {
            for (Map.Entry<String, ServerVolume> serverAdditionalVolume : serverAdditionalVolumes.entrySet()) {
                Long additionalVolumeSize = Long.parseLong(serverAdditionalVolume.getValue().getSize());
                allVolumesSizes.add(additionalVolumeSize);
            }
        }
        for (Long volumeSize : allVolumesSizes) {
            serverTotalVolumesSizes = serverTotalVolumesSizes+volumeSize;
        }
        return serverTotalVolumesSizes;
    }
}
