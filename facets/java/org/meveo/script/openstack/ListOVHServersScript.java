package org.meveo.script.openstack;

import java.util.Map;
import org.meveo.service.script.Script;
import org.meveo.admin.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.*;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.customEntities.ServerOVH;
import org.meveo.model.customEntities.ServerImage;
import org.meveo.model.customEntities.ServerNetwork;
import org.meveo.model.customEntities.Credential;
import org.meveo.service.storage.RepositoryService;
import org.meveo.model.storage.Repository;
import org.meveo.api.persistence.CrossStorageApi;
import java.util.ArrayList;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.security.PasswordUtils;
import org.meveo.script.openstack.CheckOVHToken;
import org.meveo.script.openstack.OpenstackAPI;

public class ListOVHServersScript extends Script {

    private static final Logger log = LoggerFactory.getLogger(ListOVHServersScript.class);

    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);

    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);

    private Repository defaultRepo = repositoryService.findDefaultRepository();

    private CheckOVHToken checkOVHToken = new CheckOVHToken();

    private OpenstackAPI openstackAPI = new OpenstackAPI();

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        super.execute(parameters);
    }

    public void callOVH(Credential credential, ServiceProvider openstack) throws BusinessException {
        log.info("calling ListOVHServersScript");
        checkOVHToken.checkOVHToken(credential, openstack);
        Map<String, String> zones = new HashMap<String, String>();
        zones = openstack.getZone();
        for (String zone : zones.keySet()) {
            List<JsonObject> servers = openstackAPI.computeAPI("servers/detail", credential, null, "get", "server");
            for (JsonObject serverObj : servers) {
                ServerOVH server = new ServerOVH();
                server.setUuid(serverObj.get("id").getAsString());
              	server.setProviderSideId(serverObj.get("id").getAsString());
                server.setInstanceName(serverObj.get("name").getAsString());
                server.setDomainName(serverObj.get("name").getAsString().toLowerCase() + ".webdrone.fr");
                server.setOrganization(serverObj.get("tenant_id").getAsString());
                String idImage = serverObj.get("image").getAsJsonObject().get("id").getAsString();
                String urlImage = "images/" + idImage;
                List<JsonObject> images = openstackAPI.computeAPI(urlImage, credential, null, "get", "image");
                for (JsonObject imageElement : images) {
                    //server.setImage(imageElement.get("name").getAsString());
                  	ServerImage image = crossStorageApi.find(defaultRepo, ServerImage.class).by("uuid", imageElement.get("id").getAsString()).getResult();
                  	server.setImage(image);
                }
                server.setCreationDate(OffsetDateTime.parse(serverObj.get("created").getAsString()).toInstant());
                server.setLastUpdate(OffsetDateTime.parse(serverObj.get("updated").getAsString()).toInstant());
                server.setZone(zone);
              	server.setLocation(zone);
                JsonArray publicIpArray = serverObj.get("addresses").getAsJsonObject().get("Ext-Net").getAsJsonArray();
                for (JsonElement ip : publicIpArray) {
                    JsonObject ipElement = ip.getAsJsonObject();
                    if (ipElement.get("version").getAsInt() == 4) {
                        server.setPublicIp(ipElement.get("addr").getAsString());
                    }
                }
                server.setStatus(serverObj.get("status").getAsString());
                server.setProvider(openstack);
                String idFlavor = serverObj.get("flavor").getAsJsonObject().get("id").getAsString();
                String urlFlavor = "flavors/" + idFlavor;
                List<JsonObject> flavors = openstackAPI.computeAPI(urlFlavor, credential, null, "get", "flavor");
                for (JsonObject flavor : flavors) {
                    //server.setServerType(flavor.get("name").getAsString());
                    server.setVolumeSize(flavor.get("disk").getAsString() + " GiB");
                }
              	JsonObject addresses = serverObj.get("addresses").getAsJsonObject();
              	List<JsonObject> networks = openstackAPI.networkAPI("networks", credential, null, "get", "network");
              	for (JsonObject network : networks) {
                  	String networkName = network.get("name").getAsString();
                  	log.info(networkName);
                  	log.info(addresses.get(networkName).getAsString());
                  	if (addresses.get(networkName) != null) {
                      	log.info("SET DU NETWORK");
                      	ServerNetwork networkObject = crossStorageApi.find(defaultRepo, ServerNetwork.class).by("uuid", network.get("id").getAsString()).getResult();
                      	server.setNetwork(networkObject);
                    }
                }
              	//Security Group
              	//TODO
              	//Root volume
              	//TODO
              	//Additional volume
              	//TODO
              	
                try {
                    crossStorageApi.createOrUpdate(defaultRepo, server);
                } catch (Exception ex) {
                    log.error("error creating server {} :{}", server.getUuid(), ex.getMessage());
                }
            }
        }
    }
}
