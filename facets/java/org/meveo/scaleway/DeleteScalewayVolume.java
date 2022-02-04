package org.meveo.scaleway;

import java.time.Instant;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.ServerVolume;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteScalewayVolume extends Script {

    
    private static final Logger logger = LoggerFactory.getLogger(DeleteScalewayVolume.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private  String SCALEWAY_URL = "api.scaleway.com";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        String action = (String)parameters.get(CONTEXT_ACTION);
        ServerVolume volume = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), ServerVolume.class);

        if (volume.getZone() == null) {
            throw new BusinessException("Invalid Volume Zone");
        } else if (volume.getProviderSideId() == null) {
            throw new BusinessException("Invalid Volume Provider-side ID");
        } else if (volume.getServer() != null) {
            throw new BusinessException("Unable to Delete, Volume is still attached to a Server");
        }

        String zone = volume.getZone();
        String volumeId = volume.getProviderSideId();
        logger.info("Action : {}, Volume Uuid : {}", action, volume.getUuid());

        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("Using Credential {} with username {}", credential.getUuid(), credential.getUsername());
        }

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+"/instance/v1/zones/"+zone+"/volumes/"+volumeId);
        Response response = CredentialHelperService.setCredential(target.request(), credential).delete();
        String value = response.readEntity(String.class);
        logger.info("response : {}", value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response:"+value);
        if (response.getStatus()<300) {
            volume.setLastUpdated(Instant.now());
            logger.info("volume {} deleted at: {}", volume.getUuid(), volume.getLastUpdated());
            try {
                crossStorageApi.remove(defaultRepo, volume.getUuid(), volume.getCetCode());
            } catch (Exception e) {
                logger.error("error deleting volume {} :{}", volume.getUuid(), e.getMessage());
            }
        }
    }
}
