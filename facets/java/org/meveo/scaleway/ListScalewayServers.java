package org.meveo.scaleway;

import java.time.OffsetDateTime;
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
import org.meveo.model.customEntities.Bootscript;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.Server;
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
        // String zone_id = parameters.get("zone").toString();// Select from list
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
                    Server server = new Server();
                    String type = serverObj.get("commercial_type").getAsString(); // used for check
                    String name = serverObj.get("name").getAsString(); // used for check
                    if (name.startsWith("dev-")) { // type necessary?
                        // Image
                        JsonObject image = serverObj.get("image").getAsJsonObject();

                        // Root Volume
                        JsonObject rootVolume = serverObj.get("image").getAsJsonObject().get("root_volume").getAsJsonObject();

                        // Additional Volumes
                        JsonObject volumes = serverObj.get("volumes").getAsJsonObject();
                        Map<String, Object> additionalVolumes = new HashMap<>();
                        for (Map.Entry<String, JsonElement> volume : volumes.entrySet()) {
                            additionalVolumes.put(volume.getKey(), volume.getValue());
                        }

                        // Server Actions
                        ArrayList<String> actions = new ArrayList<String>();
                        JsonArray serverActionsArr = serverObj.get("allowed_actions").getAsJsonArray();
                        for (JsonElement action : serverActionsArr) {
                            actions.add(action.getAsString());
                        }

                        // Location
                        // includes zone_id, platform_id, cluster_id, hypervisor_id, node_id
                        JsonObject location = serverObj.get("location").getAsJsonObject();

                        // Bootscript
                        String bootscriptId = serverObj.get("bootscript").getAsJsonObject().get("id").getAsString();
                        Bootscript bootscript = crossStorageApi.find(defaultRepo, Bootscript.class).by("providerSideId", bootscriptId).getResult();

                        // Maintenances
                        ArrayList<String> maintenances = new ArrayList<String>();
                        JsonArray maintenancesArr = serverObj.get("maintenances").getAsJsonArray();
                        for (JsonElement maintenance : maintenancesArr) {
                            maintenances.add(maintenance.getAsString()); // could be Objects
                        }
                        // Security Group
                        // Could be CET or List(hardcoded) of existing groups as unlikely to change often
                        // includes id and name
                        String securityGroup = serverObj.get("security_group").getAsJsonObject().get("name").getAsString();

                        // Private NICs
                        // CET? or List of Ids
                        JsonArray nicsArr = serverObj.get("private_nics").getAsJsonArray();
                        ArrayList<String> nicIds = new ArrayList<String>();
                        for (JsonElement nic : nicsArr) {
                            JsonObject privateNic = nic.getAsJsonObject();
                            nicIds.add(privateNic.get("id").getAsString());
                        }

                        // Setting Server Values
                        // Default server values
                        server.setUuid(serverObj.get("id").getAsString());
                        server.setInstanceName(name);
                        server.setServerType(type);
                        server.setProvider(provider);
                        server.setProviderSideId(serverObj.get("id").getAsString());
                        server.setImage(image.get("id").getAsString()); // To be changed  as reference to CET
                        server.setOrganization(serverObj.get("organization").getAsString());
                        server.setZone(serverObj.get("zone").getAsString());
                        server.setPublicIp(serverObj.get("public_ip").getAsJsonObject().get("address").getAsString());
                        server.setVolume(rootVolume.get("id").getAsString()); // should be root volume
                        server.setVolumeSize(FileUtils.byteCountToDisplaySize(rootVolume.get("size").getAsLong()));
                        server.setCreationDate(OffsetDateTime.parse(serverObj.get("creation_date").getAsString()).toInstant());
                        server.setLastUpdate(OffsetDateTime.parse(serverObj.get("modification_date").getAsString()).toInstant());
                        server.setServerActions(actions);
                        server.setStatus(serverObj.get("state").getAsString());

                        // Scaleway-specific Server Values
                        // server.setDynamicIpRequired(serverObj.get("dynamic_ip_required").getAsBoolean());
                        // server.setEnableIPvSix(serverObj.get("enable_ipv6").getAsBoolean());
                        // server.setHostname(serverObj.get("hostname").getAsString()); hostname != domain name
                        // server.setProtected(serverObj.get("protected").getAsBoolean());
                        // server.setPrivateIp(serverObj.get("private_ip").getAsString());
                        // server.setStateDetail(serverObj.get("state_detail").getAsString()); // necessary?
                        // location server.setLocation(serverObj.get("location").getAsString()); // CET? necessary ?
                        // server.setIpvSix(serverObj.get("ipv6").getAsString());
                        // server.setBootscript(bootscript); // Reference to CET
                        // server.setBootType(serverObj.get("boot_type").getAsString());
                        // server.setSecurityGroup(serverObj.get("security_group").getAsJsonObject()); // CET?
                        // maintenances server.setMaintenances(serverObj.get("maintenances").getAsString()); // Array
                        // server.setArch(serverObj.get("arch").getAsString());
                        // server.setPlacementGroup(serverObj.get("placement_group").getAsString()); // nullable necessary?
                        // privateNics server.setPrivateNics(serverObj.get("private_nics").getAsString()); // Array
                        // server.setProject(serverObj.get("project").getAsString());
                        // server.setAdditionalVolumes(additionalVolumes) // Should be list of IDs of volumes
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
