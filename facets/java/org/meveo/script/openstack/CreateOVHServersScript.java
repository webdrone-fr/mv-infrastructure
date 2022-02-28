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
import org.meveo.model.persistence.JacksonUtil;
import com.google.gson.*;
import java.time.OffsetDateTime;
import org.meveo.script.openstack.OpenstackAPI;

public class CreateOVHServersScript extends Script {

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

    public void createServer(Credential credential, ServiceProvider openstack, Server server) throws BusinessException {
        log.info("calling CreateOVHServersScript");
        // Check Token
        checkOVHToken.checkOVHToken(credential, openstack);
        // Check Input
        if (server.getName() == null) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "Warning : ", "Instance name not found for server: " + server.getUuid()));
            throw new BusinessException("Cannot create new server (missing instance name) for uuid : " + server.getUuid());
        } else if (!server.getName().startsWith("dev-")) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "Warning : ", "Instance Name needs to start by <dev-> : " + server.getUuid()));
            throw new BusinessException("Cannot create new server (missing image id) for uuid : " + server.getUuid());
        } else if (server.getImageRef() == null) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "Warning : ", "Image id not found for server: " + server.getUuid()));
            throw new BusinessException("Cannot create new server (missing image id) for uuid : " + server.getUuid());
        } else if (server.getFlavorRef() == null) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "Warning : ", "Flavor id not found for server: " + server.getUuid()));
            throw new BusinessException("Cannot create new server (missing flavor id) for uuid : " + server.getUuid());
        } else if (server.getNetworks() == null) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "Warning : ", "Network id not found for server: " + server.getUuid()));
            throw new BusinessException("Cannot create new server (missing network id) for uuid : " + server.getUuid());
        } else if (server.getKeyName() == null) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "Warning : ", "Key Name id not found for server: " + server.getUuid()));
            throw new BusinessException("Cannot create new server (missing key pair id) for uuid : " + server.getUuid());
        } else if (server.getZone() == null) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "Warning : ", "Zone  not found for server: " + server.getUuid()));
            throw new BusinessException("Cannot create new server (missing Zone) for uuid : " + server.getUuid());
        } else {
            // Build the request
            HashMap<String, Object> master = new HashMap<String, Object>();
            HashMap<String, Object> newServer = new HashMap<String, Object>();
            ArrayList<HashMap> networks = new ArrayList<HashMap>();
            HashMap<String, String> network = new HashMap<String, String>();
            List<String> networksList = new ArrayList<>();
            networksList = server.getNetworks();
            networksList.forEach((net) -> network.put("uuid", net));
            networks.add(network);
            newServer.put("key_name", server.getKeyName());
            newServer.put("networks", networks);
            newServer.put("flavorRef", server.getFlavorRef());
            newServer.put("imageRef", server.getImageRef());
            newServer.put("name", server.getName());
            master.put("server", newServer);
            String resp = JacksonUtil.toStringPrettyPrinted(master);
            // Request
          	List<JsonObject> servers = openstackAPI.computeAPI("servers", credential, resp, "post", "server");
          	String oldUuid = server.getUuid();
          	for (JsonObject serverObj : servers) {
                // UUID
                server.setUuid(serverObj.get("id").getAsString());
              	String urlServer = "servers/" + server.getUuid();
                List<JsonObject> newServers = openstackAPI.computeAPI(urlServer, credential, null, "get", "server");
          		log.info(newServers.toString());
              	for (JsonObject newServerObj : newServers) {
                    // Status
                    server.setStatus(newServerObj.get("status").getAsString());
                    // volume & flavor
                    String idFlavor = newServerObj.get("flavor").getAsJsonObject().get("id").getAsString();
                  	String urlFlavor = "flavors/" + idFlavor;
                  	List<JsonObject> flavors = openstackAPI.computeAPI(urlFlavor, credential, null, "get", "flavor");
          			log.info(flavors.toString());
                  	for (JsonObject flavorObj : flavors) {
                        // flavor
                        //server.setServerType(flavorObj.get("name").getAsString());
                        // volume
                        server.setVolumeSize(flavorObj.get("disk").getAsString() + " GiB");
                    }
                    // public IP
                  	/*
                    JsonArray publicIpArray = newServerObj.get("addresses").getAsJsonObject().get("Ext-Net").getAsJsonArray();
                    for (JsonElement ip : publicIpArray) {
                        JsonObject ipElement = ip.getAsJsonObject();
                        if (ipElement.get("version").getAsInt() == 4) {
                            server.setPublicIp(ipElement.get("addr").getAsString());
                        }
                    }*/
                    // Set the creation & updated date
                    server.setCreationDate(OffsetDateTime.parse(newServerObj.get("created").getAsString()).toInstant());
                    server.setLastUpdate(OffsetDateTime.parse(newServerObj.get("updated").getAsString()).toInstant());
                    // domain name
                    server.setDomainName(newServerObj.get("name").getAsString().toLowerCase() + ".webdrone.fr");
                    // server name
                    server.setInstanceName(newServerObj.get("name").getAsString());
                    // tenant
                    server.setOrganization(newServerObj.get("tenant_id").getAsString());
                    // Image
                    String idImage = newServerObj.get("image").getAsJsonObject().get("id").getAsString();
                  	String urlImage = "images/" + idImage;
                  	List<JsonObject> images = openstackAPI.computeAPI(urlImage, credential, null, "get", "image");
          			log.info(images.toString());
                  	for (JsonObject imageObj : images) {
                        if (imageObj != null) {
                            //server.setImage(imageObj.get("name").getAsString());
                        }
                    }
                }
              	try {
                    crossStorageApi.createOrUpdate(defaultRepo, server);
                  	crossStorageApi.remove(defaultRepo, oldUuid, server.getClass().getSimpleName());
                  	
                } catch (Exception ex) {
                    log.error("error updating server {} :{}", server.getUuid(), ex.getMessage());
                }
            }
        }
    }
}
