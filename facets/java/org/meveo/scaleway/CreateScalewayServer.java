package org.meveo.scaleway;

import java.time.Instant;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import com.google.gson.*;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.Server;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateScalewayServer extends Script {
    

    private static final Logger logger = LoggerFactory.getLogger(CreateScalewayServer.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private  String SCALEWAY_URL = "api.scaleway.com";

    @Override
    public void execute(Map<String, Object>parameters) throws BusinessException {
        String action = (String)parameters.get(CONTEXT_ACTION);
        Server server =CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), Server.class);
        // INPUT
        String zone_id = parameters.get("zone").toString();

        if(server.getInstanceName() == null) {
            throw new BusinessException("Invalid Server Instance Name");
        } else if (server.getServerType() == null) {
            throw new BusinessException("Invalid Server Type");
        } else if (server.getImage() == null) {
            throw new BusinessException("Invalid Server Image"); // TBC
        } else if (server.getVolume() == null) { // Need to change to CET Volume
            throw new BusinessException("Invalid Server Volume"); // TBC
        } else if(server.getZone() == null) {
            throw new BusinessException("Invalid Server Zone"); //TBC
        } else if (server.getDomainName() == null) {
            throw new BusinessException("Invalid Server Domain");
        }

        ServiceProvider serviceProvider = server.getProvider();
        logger.info("action : {}, provider uuid : {}", action, serviceProvider.getUuid());
        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("using credential {}({}) with username {}", credential.getDomainName(), credential.getUuid(), credential.getUsername());
        }
        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+"/instance/v1/zones/"+zone_id+"/servers");

        Map<String, Object> body = Map.of(
            "name", server.getInstanceName(),
            // "dynamic_ip_required", 
            "commercial_type", server.getServerType()
            // "image", server.getImage(), // .getProviderSideId() reference to CET
            // "volumes", server.getVolume(), // .getVolume().getproviderSideId() Need to add volume key(iterate through)
            // "enable_ipv6"
            // "public_ip", server.getPublicIp()
            // "boot_type",
            // "bootscript",
            // "project",
            // "tags",
            // "security_group", //nullable
            // "placement_group" //nullable
        );
        String resp = JacksonUtil.toStringPrettyPrinted(body);

        Response response = 
            CredentialHelperService.setCredential(target.request("application/json"), credential)
                .post(Entity.json(resp));
        
        String value = response.readEntity(String.class);
        logger.info("response : {}", value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response: "+value);
        if (response.getStatus()<300) {
            server.setCreationDate(Instant.now());
            server.setLastUpdate(Instant.now());
            JsonObject serverObj = (JsonObject) new JsonParser().parse(value).getAsJsonObject().get("server");
            server.setProviderSideId(serverObj.get("id").getAsString());
            try {
                crossStorageApi.createOrUpdate(defaultRepo, server);
            } catch (Exception e) {
                logger.error("error updating server {} : {}", server.getUuid(), e.getMessage());
            }
        }
    }
}
