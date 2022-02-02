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
            WebTarget target = client.target("https://"+SCALEWAY_URL+"/instance/v1/zones/"+zone+"/servers");
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
                    if (name.startsWith("dev-")) { // type necessary?

                        // Default server values
                        server.setUuid(serverObj.get("id").getAsString());
                        server.setInstanceName(name);
                        server.setServerType(type);
                        server.setProvider(provider);
                        server.setProviderSideId(serverObj.get("id").getAsString());
                        server.setDomainName(serverObj.get("hostname").getAsString());
                        server.setOrganization(serverObj.get("organization").getAsString());
                        server.setZone(serverObj.get("zone").getAsString());
                        server.setSergentUrl(server.getDomainName() + ":8001/sergent");
                        server.setBootType(serverObj.get("boot_type").getAsString());
                        server.setPublicIp(serverObj.get("public_ip").getAsJsonObject().get("address").getAsString());
                        server.setCreationDate(OffsetDateTime.parse(serverObj.get("creation_date").getAsString()).toInstant());
                        server.setLastUpdate(OffsetDateTime.parse(serverObj.get("modification_date").getAsString()).toInstant());
                        server.setStatus(serverObj.get("state").getAsString());

                        // Image
                        if(!serverObj.get("image").isJsonNull()) {
                            // Image
                            String imageId = serverObj.get("image").getAsJsonObject().get("id").getAsString();
                            ServerImage image = crossStorageApi.find(defaultRepo, ServerImage.class).by("providerSideId", imageId).getResult();
                            server.setImage(image);
                        }

                        // Root Volume - Considered to be volume at position 0
                        if (!serverObj.get("volumes").isJsonNull()) {
                            String rootVolumeId = serverObj.get("volumes").getAsJsonObject().get("0").getAsJsonObject().get("id").getAsString();
                            ServerVolume rootVolume = crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", rootVolumeId).getResult();
                            if (rootVolume != null) {
                                String rootVolumeSize = rootVolume.getSize();
                                server.setRootVolume(rootVolume); 
                                server.setVolumeSize(rootVolumeSize);// could be changed to rootVolumeSize for clarity
                            }
                        }

                        // Additional Volumes
                        if (!serverObj.get("volumes").isJsonNull()) {
                            JsonObject volumesObj = serverObj.get("volumes").getAsJsonObject();
                            Map<String, ServerVolume> additionalVolumes = new HashMap<>();
                            ArrayList<String> volumeIds = new ArrayList<String>();
                            for (Map.Entry<String, JsonElement> volume : volumesObj.entrySet()) {
                                String volumeId = volume.getValue().getAsJsonObject().get("id").getAsString();
                                volumeIds.add(volumeId);
                            }
                            if (volumeIds.size()>1) { // Volume at position 0 considered to be main volume
                                for (int i = 1; i < volumeIds.size(); i++) {
                                    ServerVolume additionalVolume = crossStorageApi.find(defaultRepo, ServerVolume.class).by("providerSideId", volumeIds.get(i)).getResult();
                                    additionalVolumes.put(String.valueOf(i), additionalVolume);
                                }
                            }
                            server.setAdditionalVolumes(additionalVolumes);
                        }
                        // Server Actions
                        if (!serverObj.get("allowed_actions").isJsonNull()) {
                            ArrayList<String> actions = new ArrayList<String>();
                            JsonArray serverActionsArr = serverObj.get("allowed_actions").getAsJsonArray();
                            for (JsonElement action : serverActionsArr) {
                                actions.add(action.getAsString());
                            }
                            server.setServerActions(actions);
                        }

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

                        // Bootscript
                        if (serverObj.get("bootscript").isJsonNull()){
                            String bootscriptId = serverObj.get("bootscript").getAsJsonObject().get("id").getAsString();
                            Bootscript bootscript = crossStorageApi.find(defaultRepo, Bootscript.class).by("providerSideId", bootscriptId).getResult();
                            // server.setBootscript(bootscript);
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
                        // Security Group CET
                       if (!serverObj.get("security_group").isJsonNull()) {
                            String securityGroupId = serverObj.get("security_group").getAsJsonObject().get("id").getAsString();
                            SecurityGroup securityGroup = crossStorageApi.find(defaultRepo, SecurityGroup.class).by("providerSideId", securityGroupId).getResult();
                            server.setSecurityGroup(securityGroup);
                       }

                        // Private NICs
                        // CET? or List of Ids
                       if (serverObj.get("private_nics").isJsonNull()) {
                            JsonArray nicsArr = serverObj.get("private_nics").getAsJsonArray();
                            ArrayList<String> nicIds = new ArrayList<String>();
                            for (JsonElement nic : nicsArr) {
                                JsonObject privateNic = nic.getAsJsonObject();
                                nicIds.add(privateNic.get("id").getAsString());
                            }
                            // server.setPrivateNics(serverObj.get("private_nics").getAsString()); // Array
                       }
                        
                        // Scaleway-specific Server Values
                        server.setDynamicIpRequired(serverObj.get("dynamic_ip_required").getAsBoolean());
                        server.setIsProtected(serverObj.get("protected").getAsBoolean());
                        server.setPrivateIp(serverObj.get("private_ip").getAsString());
                        server.setArch(serverObj.get("arch").getAsString());
                        // server.setPlacementGroup(serverObj.get("placement_group").getAsString()); // nullable necessary?
                        server.setProject(serverObj.get("project").getAsString());

                        // Ipv6
                        server.setEnableIPvSix(serverObj.get("enable_ipv6").getAsBoolean());
                        if(server.getEnableIPvSix()) {
                            server.setIpVSix(serverObj.get("ipv6").getAsJsonObject().get("address").getAsString());
                        }

                        logger.info("Server Name : {}", server.getInstanceName());
                        try {
                            crossStorageApi.createOrUpdate(defaultRepo, server);
                        } catch (Exception e) {
                            logger.error("Error creating Server {} : {}", server.getInstanceName(), e.getMessage());
                        }
                    }
                }
            }
        }
    }
}
