package org.meveo.scaleway;

import java.time.Instant;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.Server;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateScalewayServer extends Script {
    

    
    private static final Logger logger = LoggerFactory.getLogger(UpdateScalewayServer.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private  String SCALEWAY_URL = "api.scaleway.com";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        String action = (String)parameters.get(CONTEXT_ACTION);
        Server server =CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), Server.class);

        if (server.getZone()==null) { //Required
            throw new BusinessException("Invalid Server Zone");
        } else if(server.getProviderSideId()==null) { //Required
            throw new BusinessException("Invalid Server Provider-side ID");
        }
        //INPUT
        String zone_id = server.getZone();
        String server_id = server.getProviderSideId();
        logger.info("action : {}, server uuid : {}", action, server_id);
        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("Using Credential {} with username {}", credential.getUuid(), credential.getUsername());
        }
        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+"/instance/v1/zones/"+zone_id+"/servers/"+server_id);
        Map<String, Object> body = Map.of(
            "name", server.getInstanceName(), //nullable
            // "boot_type", server.getBootType(),
            // "tags", server.getTags(), //nullable
            "volumes", server.getVolume()
            // "bootscript", server.getBootScript(), //nullable
            // "dynamic_ip_required", //nullable
            // "enable_ipv6", //nullable
            // "protected", //nullable
            // "security_group",
            // "placement_group", //nullable
            // "private_nics"
        );
        String resp = JacksonUtil.toStringPrettyPrinted(body);
        Response response = CredentialHelperService.setCredential(target.request("application/json"), credential)
            .put(Entity.json(resp)); // To be changed, needs a patch method
        String value = response.readEntity(String.class);
        logger.info("response : " + value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response:"+value);
        if(response.getStatus()>300) {
            server.setLastUpdate(Instant.now());
            try {
                crossStorageApi.createOrUpdate((defaultRepo), server);
            } catch (Exception e) {
                logger.error("error updating record {} :{}", server.getUuid(), e.getMessage());
            }
        }
    }
}
