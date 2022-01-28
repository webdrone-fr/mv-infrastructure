package org.meveo.scaleway;

import java.time.OffsetDateTime;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.*;

import com.google.gson.*;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
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

    static final private  String SCALEWAY_URL = "api.scaleway.com";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        ServiceProvider provider = crossStorageApi.find(defaultRepo, ServiceProvider.class).by("code", "SCALEWAY").getResult();
        // INPUT
        String zone_id = parameters.get("zone").toString();// Select from list
        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("Using Credential {} with username {}", credential.getUuid(), credential.getUsername());
        }
        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+"/instance/v1/zones/"+zone_id+"/servers");
        Response response = CredentialHelperService.setCredential(target.request(), credential).get();
        String value = response.readEntity(String.class);
        logger.info("response : " + value);
        logger.debug("response status : {}", response.getStatus());
        if (response.getStatus() < 300) {
            JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().get("servers").getAsJsonArray();
            for (JsonElement element : rootArray) {
                JsonObject serverObj = element.getAsJsonObject();
                Server server = new Server();
                server.setProvider(provider);
                String type = serverObj.get("commercial_type").getAsString();
                String name = serverObj.get("name").getAsString();
                if (type.startsWith("DEV") && name.startsWith("dev-")) { //type necessary?
                    server.setServerType(type);
                    server.setInstanceName(name);
                    server.setImage(serverObj.get("image").getAsJsonObject().get("name").getAsString());
                    server.setOrganization(serverObj.get("organization").getAsString());
                    server.setZone(serverObj.get("zone").getAsString());
                    server.setPublicIp(serverObj.get("public_ip").getAsJsonObject().get("address").getAsString());
                    server.setDomainName(serverObj.get("hostname").getAsString());
                    server.setVolumeSize(serverObj.get("volumes")
                        .getAsJsonObject().get("0")
                        .getAsJsonObject().get("size").getAsString());
                    server.setCreationDate(OffsetDateTime.parse(serverObj.get("creation_date").getAsString()).toInstant());
                    server.setLastUpdate(OffsetDateTime.parse(serverObj.get("modification_date").getAsString()).toInstant());
                    server.setStatus(serverObj.get("state").getAsString());
                    logger.info("Server Name: {}", server.getInstanceName());
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
