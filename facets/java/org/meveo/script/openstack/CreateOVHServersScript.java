package org.meveo.script.openstack;

import java.util.Map;
import org.meveo.service.script.Script;
import org.meveo.admin.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.customEntities.Server;
import org.meveo.model.customEntities.Credential;
import org.meveo.service.storage.RepositoryService;
import org.meveo.model.storage.Repository;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.script.openstack.CheckOVHToken;
import org.meveo.model.persistence.CEIUtils;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.client.*;
import javax.ws.rs.core.*;
import org.meveo.model.persistence.JacksonUtil;
import com.google.gson.*;
import java.time.OffsetDateTime;

public class CreateOVHServersScript extends Script {

    private static final Logger log = LoggerFactory.getLogger(ListOVHServersScript.class);

    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);

    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);

    private Repository defaultRepo = repositoryService.findDefaultRepository();

    private CheckOVHToken checkOVHToken = new CheckOVHToken();

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        super.execute(parameters);
    }

    public void createServer(Credential credential, ServiceProvider openstack, Server server) throws BusinessException {
        log.info("calling CreateOVHServersScript");
        // Check Token
        checkOVHToken.checkOVHToken(credential, openstack);
        // Check Input
        if (server.getInstanceName() == null) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "Warning : ", "Instance name not found for server: " + server.getUuid()));
            throw new BusinessException("Cannot create new server (missing instance name) for uuid : " + server.getUuid());
        } else if (server.getImage() == null) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "Warning : ", "Image id not found for server: " + server.getUuid()));
            throw new BusinessException("Cannot create new server (missing image id) for uuid : " + server.getUuid());
        } else if (/*server.getFlavor() == null*/
        true) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "Warning : ", "Flavor id not found for server: " + server.getUuid()));
            throw new BusinessException("Cannot create new server (missing flavor id) for uuid : " + server.getUuid());
        } else if (/*server.getNetwork() == null*/
        false) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "Warning : ", "Network id not found for server: " + server.getUuid()));
            throw new BusinessException("Cannot create new server (missing network id) for uuid : " + server.getUuid());
        } else if (/*server.getKeyPair() == null*/
        false) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "Warning : ", "Key pair id not found for server: " + server.getUuid()));
            throw new BusinessException("Cannot create new server (missing key pair id) for uuid : " + server.getUuid());
        } else {
            // Build the request
            HashMap<String, Object> master = new HashMap<String, Object>();
            HashMap<String, Object> newServer = new HashMap<String, Object>();
            ArrayList<HashMap> networks = new ArrayList<HashMap>();
            HashMap<String, String> network = new HashMap<String, String>();
            List<String> networksList = new ArrayList<>();
            networksList = server.getNetworks();
            networksList.forEach((net) -> {
                
            });
            /*
            network.put("uuid", server.getNetwork());
            networks.add(network);
            newServer.put("key_name", server.getKeyName());
            newServer.put("networks", networks);
            newServer.put("flavorRef", server.getFlavorRef());
            newServer.put("ImageRef", server.getImageRef());
            newServer.put("name", server.getName());
            */
            master.put("server", newServer);
            String resp = JacksonUtil.toStringPrettyPrinted(master);
            // Request
            Client client = ClientBuilder.newClient();
            WebTarget target = client.target("https://compute." + server.getZone() + ".cloud.ovh.net/v2.1/servers");
            Response response = target.request().header("X-Auth-Token", credential.getToken()).post(Entity.json(resp));
            String value = response.readEntity(String.class);
            Integer responseStatus = response.getStatus();
            // Verification
            if (responseStatus < 300) {
                JsonParser parserServer = new JsonParser();
                JsonElement jsonServer = parserServer.parse(value);
                JsonObject serverObj = jsonServer.getAsJsonObject();
                serverObj = serverObj.get("server").getAsJsonObject();
                server.setUuid(serverObj.get("id").getAsString());
                try {
                    crossStorageApi.createOrUpdate(defaultRepo, server);
                } catch (Exception ex) {
                    log.error("error updating server {} :{}", server.getUuid(), ex.getMessage());
                }
                WebTarget targetNewServ = client.target("https://compute." + server.getZone() + ".cloud.ovh.net/v2.1/servers/" + server.getUuid());
                Response newServReponse = targetNewServ.request().header("X-Auth-Token", credential.getToken()).get();
                String valueNewServ = response.readEntity(String.class);
                if (response.getStatus() < 300) {
                    FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "INFO : ", "Server successfully created : " + server.getUuid()));
                    // Status
                    server.setStatus(serverObj.get("status").getAsString());
                    // volume & flavor
                    String idFlavor = serverObj.get("flavor").getAsJsonObject().get("id").getAsString();
                    WebTarget targetVolume = client.target("https://compute." + server.getZone() + "." + openstack.getApiBaseUrl() + "/v2.1/flavors/" + idFlavor);
                    Response responseVolume = targetVolume.request().header("X-Auth-Token", credential.getToken()).get();
                    String flavorValue = responseVolume.readEntity(String.class);
                    if (response.getStatus() < 300) {
                        JsonParser parserFlavor = new JsonParser();
                        JsonElement jsonFlavor = parserFlavor.parse(flavorValue);
                        JsonObject flavorObj = jsonFlavor.getAsJsonObject();
                        flavorObj = flavorObj.get("flavor").getAsJsonObject();
                        // flavor
                        server.setServerType(flavorObj.get("name").getAsString());
                        // volume
                        server.setVolumeSize(flavorObj.get("disk").getAsString() + " GiB");
                    }
                    // public IP
                    JsonArray publicIpArray = serverObj.get("addresses").getAsJsonObject().get("Ext-Net").getAsJsonArray();
                    for (JsonElement ip : publicIpArray) {
                        JsonObject ipElement = ip.getAsJsonObject();
                        if (ipElement.get("version").getAsInt() == 4) {
                            server.setPublicIp(ipElement.get("addr").getAsString());
                        }
                    }
                    // Set the creation & updated date
                    server.setCreationDate(OffsetDateTime.parse(serverObj.get("created").getAsString()).toInstant());
                    server.setLastUpdate(OffsetDateTime.parse(serverObj.get("updated").getAsString()).toInstant());
                    // domain name
                    server.setDomainName(serverObj.get("name").getAsString().toLowerCase() + ".webdrone.fr");
                }
            } else {
                FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "Warning : ", "Error while creating the server : " + server.getUuid()));
                log.info("Error while creating the server : {}", server.getUuid());
            }
            response.close();
        }
    }
}
