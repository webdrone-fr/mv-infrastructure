package org.meveo.scaleway;

import java.time.Instant;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.ServerImage;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteScalewayImage extends Script {
    


    private static final Logger logger = LoggerFactory.getLogger(DeleteScalewayImage.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();
    
    static final private  String SCALEWAY_URL = "api.scaleway.com";
    static final private String BASE_PATH = "/instance/v1/zones/";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        String action = (String)parameters.get(CONTEXT_ACTION);
        ServerImage image =  CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), ServerImage.class);

        if (image.getZone() == null) {
            throw new BusinessException("Invalid Image Zone");
        } else if (image.getProviderSideId() == null) {
            throw new BusinessException("Invalid Image Provider-side ID");
        } else if (image.getIsPublic()==true) {
            throw new BusinessException("Unable to Delete, Image is public");
        }

        String zone = image.getZone();
        String imageId = image.getProviderSideId();
        logger.info("Action : {}, Image Uuid : {}", action, image.getUuid());

        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("Using Credential {} with username {}", credential.getUuid(), credential.getUsername());
        }

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/images/"+imageId);
        Response response = CredentialHelperService.setCredential(target.request(), credential).delete();
        String value = response.readEntity(String.class);
        logger.info("response : {}", value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response:"+value);
        if(response.getStatus()<300) {
            image.setLastUpdated(Instant.now());
            logger.info("image : {} deleted at: {}", image.getUuid(), image.getLastUpdated());
            try{
                crossStorageApi.remove(defaultRepo, image.getUuid(), ServerImage.class);
            } catch(Exception e) {
                logger.error("Error deleting image : {}", image.getUuid(), e.getMessage());
            }
        }
        response.close();
    }
}
