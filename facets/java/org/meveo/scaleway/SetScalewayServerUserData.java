package org.meveo.scaleway;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.ScalewayServer;
import org.meveo.model.customEntities.ServerUserData;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetScalewayServerUserData extends Script {


    
    private static final Logger logger = LoggerFactory.getLogger(SetScalewayServerUserData.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private  String SCALEWAY_URL = "api.scaleway.com";
    static final private String BASE_PATH = "/instance/v1/zones/";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        String action = parameters.get(CONTEXT_ACTION).toString();
        ServerUserData sUserData = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), ServerUserData.class);

        if (sUserData.getZone()==null) { //Required
            throw new BusinessException("Invalid Server Zone");
        } else if(sUserData.getServer()==null) { //Required
            throw new BusinessException("User Data is not attached to a Server");
        } else if (sUserData.getServerSideKey()==null) {
            throw new BusinessException("Invalid Server Side Key");
        } else if (sUserData.getContent()==null) {
            throw new BusinessException("No Content included for Server Side Key");
        }

        String zone = sUserData.getZone();
        String serverSideKey = sUserData.getServerSideKey();
        String content = sUserData.getContent();
        String serverUuid = sUserData.getServer().getUuid();
        // get server provider side id
        String serverId = null;
        try {
            ScalewayServer server = crossStorageApi.find(defaultRepo, serverUuid, ScalewayServer.class);
            serverId = server.getProviderSideId();
        } catch (Exception e) {
            logger.error("Error retrieving Server for Server User Data", e.getMessage());
            throw new BusinessException("Error retrieving Server : "+ serverUuid +" for Server User Data");
        }

        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("using credential {} with username {}", credential.getDomainName(), credential.getUsername());
        }

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/servers/"+serverId+"/user_data/"+serverSideKey);

        // Map<String, Object> body = new HashMap<String, Object>();
        // body.put(serverSideKey, content);
        // String resp = JacksonUtil.toStringPrettyPrinted(body);
        String resp = content;
        logger.debug("server user data body: {}", resp);
        Response response = CredentialHelperService.setCredential(target.request(), credential).method("PATCH", Entity.text(resp));
        String value = response.readEntity(String.class);
        logger.info("response : {}", value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response: "+value);

        if(response.getStatus()<300) {
            try {
                crossStorageApi.createOrUpdate(defaultRepo, sUserData);
                logger.info("User Data : {} successfully set for Server : {}", serverSideKey, serverId);
            } catch (Exception e) {
                logger.error("Error setting User Data {} for Server: {}", serverSideKey, serverId, e.getMessage());
            }
        }
        response.close();
    }
}