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
import javax.ws.rs.client.*;
import javax.ws.rs.core.*;
import java.util.HashMap;
import com.google.gson.*;
import java.time.OffsetDateTime;
import org.meveo.model.customEntities.CustomEntityTemplate;
import org.meveo.service.custom.CustomEntityTemplateService;
import org.meveo.model.crm.CustomFieldTemplate;
import org.meveo.service.crm.impl.CustomFieldTemplateService;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.customEntities.CustomEntityInstance;

public class UpdateOVHServersScript extends Script {

    private static final Logger log = LoggerFactory.getLogger(ListOVHServersScript.class);

    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);

    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);

    private Repository defaultRepo = repositoryService.findDefaultRepository();

    private CustomEntityTemplateService customEntityTemplateService = getCDIBean(CustomEntityTemplateService.class);

    private CustomFieldTemplateService customFieldTemplateService = getCDIBean(CustomFieldTemplateService.class);

    private CheckOVHToken checkOVHToken = new CheckOVHToken();

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        super.execute(parameters);
    }

    public void updateServer(Credential credential, ServiceProvider openstack, Server server) throws BusinessException {
        log.info("calling UpdateOVHServersScripts");
        // Check Token
        checkOVHToken.checkOVHToken(credential, openstack);
    	// Retreive actual values from the server
      	CustomEntityInstance newToCEI = CEIUtils.pojoToCei(server);
      	HashMap<String, Object> oldServ = new HashMap<String, Object>();
      	oldServ = retreiveValues(credential, server.getUuid(), server.getZone());
      	String codeClass = server.getClass().getSimpleName();
		CustomEntityTemplate newServCET = customEntityTemplateService.findByCode(codeClass);
      	Map<String, CustomFieldTemplate> newServCFT = customFieldTemplateService.findByAppliesTo(newServCET.getAppliesTo());
      	for(Map.Entry<String, CustomFieldTemplate> entry : newServCFT.entrySet()) {
          	Object oldValue = oldServ.get(entry.getKey());
          	Object newValue = newToCEI.get(entry.getKey());
          	log.info("UPDATE " + entry.getKey() + ": " + oldValue.toString() + " VS " + newValue.toString());
        }
    }

    private HashMap<String, Object> retreiveValues(Credential credential, String serverUuid, String zone) {
        HashMap<String, Object> oldServ = new HashMap<String, Object>();
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target("https://compute." + zone + ".cloud.ovh.net/v2.1/servers/" + serverUuid);
        Response response = target.request().header("X-Auth-Token", credential.getToken()).get();
        String value = response.readEntity(String.class);
        Integer responseStatus = response.getStatus();
        if (responseStatus < 300) {
            JsonParser parserServer = new JsonParser();
            JsonElement jsonServer = parserServer.parse(value);
            JsonObject serverObj = jsonServer.getAsJsonObject();
            serverObj = serverObj.get("server").getAsJsonObject();
            // Store values in hashMap
            oldServ.put("uuid", serverObj.get("id").getAsString());
            oldServ.put("instanceName", serverObj.get("name").getAsString());
            oldServ.put("domainName", serverObj.get("name").getAsString().toLowerCase() + ".webdrone.fr");
            oldServ.put("organization", serverObj.get("tenant_id").getAsString());
            String idImage = serverObj.get("image").getAsJsonObject().get("id").getAsString();
            WebTarget targetImage = client.target("https://image.compute." + zone + ".cloud.ovh.net/v2/images/" + idImage);
            Response responseImage = targetImage.request().header("X-Auth-Token", credential.getToken()).get();
            String ImageValue = responseImage.readEntity(String.class);
            if (!(ImageValue.startsWith("404"))) {
                JsonParser parser = new JsonParser();
                JsonElement jsonE = parser.parse(ImageValue);
                JsonObject ImageObj = jsonE.getAsJsonObject();
                if (ImageObj != null) {
                    oldServ.put("image", ImageObj.get("name").getAsString());
                }
            } else {
                oldServ.put("image", "Image not found");
                log.error("Image with id : " + idImage + " cannot be found for the server : " + serverObj.get("name").getAsString());
            }
            oldServ.put("creationDate", OffsetDateTime.parse(serverObj.get("created").getAsString()).toInstant());
            oldServ.put("lastUpdate", OffsetDateTime.parse(serverObj.get("updated").getAsString()).toInstant());
            oldServ.put("zone", zone);
            JsonArray publicIpArray = serverObj.get("addresses").getAsJsonObject().get("Ext-Net").getAsJsonArray();
            for (JsonElement ip : publicIpArray) {
                JsonObject ipElement = ip.getAsJsonObject();
                if (ipElement.get("version").getAsInt() == 4) {
                    oldServ.put("publicIp", ipElement.get("addr").getAsString());
                }
            }
            oldServ.put("status", serverObj.get("status").getAsString());
            String idFlavor = serverObj.get("flavor").getAsJsonObject().get("id").getAsString();
            WebTarget targetVolume = client.target("https://compute." + zone + ".cloud.ovh.net/v2.1/flavors/" + idFlavor);
            Response responseVolume = targetVolume.request().header("X-Auth-Token", credential.getToken()).get();
            String flavorValue = responseVolume.readEntity(String.class);
            if (response.getStatus() < 300) {
                JsonParser parser = new JsonParser();
                JsonElement jsonE = parser.parse(flavorValue);
                JsonObject flavorObj = jsonE.getAsJsonObject();
                flavorObj = flavorObj.get("flavor").getAsJsonObject();
                // flavor
                oldServ.put("serverType", flavorObj.get("name").getAsString());
                // volume
                oldServ.put("volumeSize", flavorObj.get("disk").getAsString() + " GiB");
            }
        }
        return oldServ;
    }
}
