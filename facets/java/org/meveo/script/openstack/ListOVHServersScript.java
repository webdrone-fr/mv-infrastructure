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
import org.meveo.model.customEntities.ServerOVH;
import org.meveo.model.customEntities.Credential;
import org.meveo.service.storage.RepositoryService;
import org.meveo.model.storage.Repository;
import org.meveo.api.persistence.CrossStorageApi;
import java.util.ArrayList;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.security.PasswordUtils;
import org.meveo.script.openstack.CheckOVHToken;
import org.meveo.persistence.CrossStorageService;
import org.meveo.model.customEntities.CustomEntityTemplate;
import org.meveo.service.custom.CustomEntityTemplateService;
import org.meveo.api.exception.EntityDoesNotExistsException;

public class ListOVHServersScript extends Script {

    private static final Logger log = LoggerFactory.getLogger(ListOVHServersScript.class);

    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);

    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);

    private Repository defaultRepo = repositoryService.findDefaultRepository();
  
    private CheckOVHToken checkOVHToken = new CheckOVHToken();
  
  	private CrossStorageService crossStorageService = getCDIBean(CrossStorageService.class);

    private CustomEntityTemplateService customEntityTemplateService = getCDIBean(CustomEntityTemplateService.class);

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        super.execute(parameters);
    }

    public void callOVH(Credential credential, ServiceProvider openstack) {
        log.info("calling ListOVHServersScript");
      	//List actual Server register in meveo
      	ServerOVH s = new ServerOVH();
      	String codeClass = s.getClass().getSimpleName();
		CustomEntityTemplate servCET = customEntityTemplateService.findByCode(codeClass);
      	listActualServer(servCET);
        //Check the token
        checkOVHToken.checkOVHToken(credential, openstack);
        // Call every region to list server
        Map<String, String> zones = new HashMap<String, String>();
        zones = openstack.getZone();
        for (String zone : zones.keySet()) {
            Client clientListServers = ClientBuilder.newClient();
            WebTarget targetListServer = clientListServers.target("https://compute." + zone + "." + openstack.getApiBaseUrl() + "/v2.1/servers/detail");
            Response response = targetListServer.request().header("X-Auth-Token", credential.getToken()).get();
            String value = response.readEntity(String.class);
            if (response.getStatus() < 300) {
                JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().getAsJsonArray("servers");
                for (JsonElement element : rootArray) {
                    JsonObject serverObj = element.getAsJsonObject();
                    // Create new servers
                    ServerOVH server = new ServerOVH();
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
                    WebTarget targetImage = clientListServers.target("https://image.compute." + zone + "." + openstack.getApiBaseUrl() + "/v2/images/" + idImage);
                    Response responseImage = targetImage.request().header("X-Auth-Token", credential.getToken()).get();
                    String ImageValue = responseImage.readEntity(String.class);
                    if (!(ImageValue.startsWith("404"))) {
                        JsonParser parser = new JsonParser();
                        JsonElement jsonE = parser.parse(ImageValue);
                        JsonObject ImageObj = jsonE.getAsJsonObject();
                        if (ImageObj != null) {
                            server.setImage(ImageObj.get("name").getAsString());
                        }
                    } else {
                        server.setImage("Image not found");
                        log.error("Image with id : " + idImage + " cannot be found for the server : " + serverObj.get("name").getAsString());
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
                    WebTarget targetVolume = clientListServers.target("https://compute." + zone + "." + openstack.getApiBaseUrl() + "/v2.1/flavors/" + idFlavor);
                    Response responseVolume = targetVolume.request().header("X-Auth-Token", credential.getToken()).get();
                    String flavorValue = responseVolume.readEntity(String.class);
                    if (response.getStatus() < 300) {
                        JsonParser parser = new JsonParser();
                        JsonElement jsonE = parser.parse(flavorValue);
                        JsonObject flavorObj = jsonE.getAsJsonObject();
                        flavorObj = flavorObj.get("flavor").getAsJsonObject();
                        // flavor
                        server.setServerType(flavorObj.get("name").getAsString());
                        // volume
                        server.setVolumeSize(flavorObj.get("disk").getAsString() + " GiB");
                    }
                    try {
                        crossStorageApi.createOrUpdate(defaultRepo, server);
                    } catch (Exception ex) {
                        log.error("error creating server {} :{}", server.getUuid(), ex.getMessage());
                    }
                }
            }
            response.close();
        }
    }
  
  	public void listActualServer(CustomEntityTemplate serverOVH) {
      	try {
    		List<Map<String, Object>> listSer = crossStorageService.find(defaultRepo, serverOVH, null);
          	log.info(listSer.toString());
        } catch (EntityDoesNotExistsException ex) {
          	
        }
    }
}
