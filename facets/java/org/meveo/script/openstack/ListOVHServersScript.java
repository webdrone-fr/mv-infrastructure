package org.meveo.script.openstack;

import java.util.Map;
import org.meveo.service.script.Script;
import org.meveo.admin.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.ws.rs.client.*;
import javax.ws.rs.core.*;
import com.google.gson.*;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.customEntities.Server;
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

    public void callOVH(Credential credential, ServiceProvider openstack) {
        log.info("calling ListOVHServersScript");
        // Check the token
        checkOVHToken.checkOVHToken(credential, openstack);
        // Call every region to list server
        Map<String, String> zones = new HashMap<String, String>();
        zones = openstack.getZone();
        for (String zone : zones.keySet()) {
            List<JsonObject> servers = openstackAPI.computeAPI("servers/detail", credential.getToken(), null);
            for (JsonElement element : servers) {
                JsonObject serverObj = element.getAsJsonObject();
                // Create new servers
                Server server = new Server();
                // UUID
                server.setUuid(serverObj.get("id").getAsString());
                // server name
                server.setInstanceName(serverObj.get("name").getAsString());
                // domain name
                server.setDomainName(serverObj.get("name").getAsString().toLowerCase() + ".webdrone.fr");
                // tenant
                server.setOrganization(serverObj.get("tenant_id").getAsString());
                // image
                String idImage = serverObj.get("image").getAsJsonObject().get("id").getAsString();
                String urlImage = "images/" + idImage;
                log.info(urlImage);
                List<JsonObject> images = openstackAPI.computeAPI(urlImage, credential.getToken(), null);
                for (JsonObject imageElement : images) {
                    server.setImage(imageElement.get("name").getAsString());
                }
                // Set the creation & updated date
                server.setCreationDate(OffsetDateTime.parse(serverObj.get("created").getAsString()).toInstant());
                server.setLastUpdate(OffsetDateTime.parse(serverObj.get("updated").getAsString()).toInstant());
                // zone
                server.setZone(zone);
                // public IP
                JsonArray publicIpArray = serverObj.get("addresses").getAsJsonObject().get("Ext-Net").getAsJsonArray();
                for (JsonElement ip : publicIpArray) {
                    JsonObject ipElement = ip.getAsJsonObject();
                    if (ipElement.get("version").getAsInt() == 4) {
                        server.setPublicIp(ipElement.get("addr").getAsString());
                    }
                }
                // status
                server.setStatus(serverObj.get("status").getAsString());
                // provider
                server.setProvider(openstack);
                // volume & flavor
                String idFlavor = serverObj.get("flavor").getAsJsonObject().get("id").getAsString();
                String urlFlavor = "flavors/" + idFlavor;
                List<JsonObject> flavors = openstackAPI.computeAPI(urlFlavor, credential.getToken(), null);
                for (JsonObject flavor : flavors) {
                    server.setServerType(flavor.get("name").getAsString());
                    server.setVolumeSize(flavor.get("disk").getAsString() + " GiB");
                }
                try {
                    crossStorageApi.createOrUpdate(defaultRepo, server);
                } catch (Exception ex) {
                    log.error("error creating server {} :{}", server.getUuid(), ex.getMessage());
                }
            }
        }
    }
}
