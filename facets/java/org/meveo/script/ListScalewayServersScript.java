package org.meveo.script;

import java.util.Map;
import org.meveo.service.script.Script;
import org.meveo.admin.exception.BusinessException;
import org.meveo.api.exception.BusinessApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.meveo.model.customEntities.CustomEntityInstance;
import javax.ws.rs.client.*;
import javax.ws.rs.core.*;
import com.google.gson.*;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.customEntities.Server;
import org.meveo.model.customEntities.Credential;
import org.meveo.service.storage.RepositoryService;
import org.meveo.model.storage.Repository;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;

public class ListScalewayServersScript extends Script {

    private static final Logger log = LoggerFactory.getLogger(ListScalewayServersScript.class);

    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);

    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);

    private Repository defaultRepo = repositoryService.findDefaultRepository();

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        super.execute(parameters);
    }

    public void listScaleway(Credential credential, ServiceProvider scaleway) throws BusinessException {
        log.info("Call ListScalewayServersScript");
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target("https://" + scaleway.getApiBaseUrl() + "/instance/v1/zones/fr-par-1/servers");
        Response response = CredentialHelperService.setCredential(target.request(), credential).get();
        String value = response.readEntity(String.class);
        log.info("response  :" + value);
        log.debug("response status : {}", response.getStatus());
        if (response.getStatus() < 300) {
            JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().getAsJsonArray("servers");
            /*if(rootObject.get("servers")!=JsonNull.INSTANCE){
            Object servers = rootObject.get("servers");
            log.info("received servers:{}",servers);
          }*/
            for (JsonElement element : rootArray) {
                JsonObject serverObj = element.getAsJsonObject();
                Server server = new Server();
                server.setUuid(serverObj.get("id").getAsString());
                String name = serverObj.get("name").getAsString();
                server.setInstanceName(name);
                if (name.contains(".")) {
                    int ldi = name.lastIndexOf(".");
                    String ext = name.substring(ldi);
                    String part = name.substring(0, ldi);
                    if (part.contains(".")) {
                        ldi = part.lastIndexOf(".");
                        server.setDomainName(name.substring(ldi + 1));
                    } else {
                        server.setDomainName(name);
                    }
                }
                if (serverObj.has("image") && !serverObj.get("image").isJsonNull()) {
                    server.setImage(serverObj.get("image").getAsJsonObject().get("name").getAsString());
                }
                server.setCreationDate(OffsetDateTime.parse(serverObj.get("creation_date").getAsString()).toInstant());
                server.setLastUpdate(OffsetDateTime.parse(serverObj.get("modification_date").getAsString()).toInstant());
                server.setOrganization(serverObj.get("organization").getAsString());
                server.setServerType(serverObj.get("commercial_type").getAsString());
                server.setZone(serverObj.get("zone").getAsString());
                if (serverObj.has("public_ip")) {
                    server.setPublicIp(serverObj.get("public_ip").getAsJsonObject().get("address").getAsString());
                }
                if (serverObj.has("volumes") && serverObj.get("volumes").getAsJsonObject().has("0")) {
                    BigInteger size = serverObj.get("volumes").getAsJsonObject().get("0").getAsJsonObject().get("size").getAsBigInteger();
                    server.setVolumeSize(size.divide(new BigInteger("1000000")).toString() + "MB");
                }
                server.setStatus(serverObj.get("state").getAsString());
                server.setProvider(scaleway);
                log.info("server: {} provider:{}", server.getUuid(), server.getProvider().getUuid());
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
