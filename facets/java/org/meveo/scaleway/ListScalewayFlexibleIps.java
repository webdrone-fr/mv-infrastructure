package org.meveo.scaleway;

import java.util.ArrayList;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import com.google.gson.*;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.PublicIp;
import org.meveo.model.customEntities.Server;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListScalewayFlexibleIps extends Script{
    

    private static final Logger logger = LoggerFactory.getLogger(ListScalewayFlexibleIps.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    // private CrossStorageService crossStorageService = getCDIBean(CrossStorageService.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();
    
    static final private String SCALEWAY_URL = "api.scaleway.com";
    static final private String BASE_PATH = "/instance/v1/zones/";

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

        for(String zone : zones) {
            WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/ips");
            Response response = CredentialHelperService.setCredential(target.request(), credential).get();
            String value = response.readEntity(String.class);
            logger.info("response : " + value);
            logger.debug("response status : {}", response.getStatus());
            if (response.getStatus() < 300) {
                JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().get("ips").getAsJsonArray();
                for (JsonElement element : rootArray) {
                    JsonObject flexibleIp = element.getAsJsonObject();
                    PublicIp publicIp = new PublicIp();

                    // default values
                    // Need creation + update date? - update possible
                    publicIp.setProviderSideId(flexibleIp.get("id").getAsString());
                    publicIp.setUuid(flexibleIp.get("id").getAsString());
                    publicIp.setIpVFourAddress(flexibleIp.get("address").getAsString());
                    publicIp.setOrganization(flexibleIp.get("organization").getAsString());
                    publicIp.setProject(flexibleIp.get("project").getAsString());
                    publicIp.setZone(flexibleIp.get("zone").getAsString());
                    publicIp.setProvider(provider);

                    // reverse - nullable
                    if (!flexibleIp.get("reverse").isJsonNull()) {
                        publicIp.setReverse(flexibleIp.get("reverse").getAsString());
                    }

                    // Server
                    if (!flexibleIp.get("server").isJsonNull()) {
                        String serverName = flexibleIp.get("server").getAsJsonObject().get("name").getAsString();
                        if(serverName.startsWith("dev-")) {
                            String serverId = flexibleIp.get("server").getAsJsonObject().get("id").getAsString();
                            try {
                                Server server = crossStorageApi.find(defaultRepo, Server.class).by("providerSideId", serverId).getResult();
                                publicIp.setServer(server);
                            } catch (Exception e) {
                                logger.error("Error retrieving server {}", serverId, e.getMessage());
                            }
                        }
                    }

                    // Tags
                    if (!flexibleIp.get("tags").isJsonNull()) {
                        JsonArray imageTagsArr = flexibleIp.get("tags").getAsJsonArray();
                        ArrayList<String> imageTags = new ArrayList<String>();
                        for (JsonElement tag : imageTagsArr) {
                            imageTags.add(tag.getAsString());
                        }
                        publicIp.setTags(imageTags);
                    }

                    try {
                        crossStorageApi.createOrUpdate(defaultRepo, publicIp);
                        logger.info("Public IP : {} imported successfully", publicIp.getIpVFourAddress());
                    } catch (Exception e) {
                        logger.error("error creating public ip {} : {}", publicIp.getUuid(), e.getMessage());
                    }
                }
            }
            response.close();
        }
    }
}
