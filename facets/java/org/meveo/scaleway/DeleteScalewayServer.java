package org.meveo.scaleway;

import java.time.Instant;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.ScalewayServer;
import org.meveo.model.customEntities.Server;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteScalewayServer extends Script{
    

    private static final Logger logger = LoggerFactory.getLogger(DeleteScalewayServer.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private  String SCALEWAY_URL = "api.scaleway.com";

    @Override
    public void execute(Map<String, Object>parameters) throws BusinessException {
        String action = (String)parameters.get(CONTEXT_ACTION);
        ScalewayServer server =CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), ScalewayServer.class);
        
        if (server.getZone()==null) { //Required
            throw new BusinessException("Invalid Server Zone");
        } else if(server.getProviderSideId()==null) { //Required
            throw new BusinessException("Invalid Server Provider-side ID");
        }

        // INPUT
        String zone = server.getZone();
        String serverId = server.getProviderSideId();
        logger.info("action : {}, server uuid : {}", action, serverId);

        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential==null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("using credential {} with username {}",credential.getUuid(),credential.getUsername()); //Need to verify username
        }

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+"/zones/"+zone+"/servers/"+serverId);

        // check if Server has Image/ Backup
            // check if Image is recent?/ Any changes have been made between Image and current state of server ?
            // Ask if User wants to make a backup of Server
            
        // check if Server still has Volumes attached
            // Ask if user wants to make snapshot of Volume(s) (Snapshot to implement)
            // Detach Volume(s)

        Response response = CredentialHelperService.setCredential(target.request(), credential).delete();
        String value = response.readEntity(String.class);
        logger.info("response : {}", value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response:"+value);
        if (response.getStatus()<300) {
            server.setLastUpdate(Instant.now());
            logger.info("server {} deleted at: {}", server.getUuid(), server.getLastUpdate());
            try {
                crossStorageApi.remove(defaultRepo, server.getUuid(), server.getCetCode());
            } catch (Exception e) {
                logger.error("error deleting server {} :{}", server.getUuid(), e.getMessage());
            }
        }
    } 
}
