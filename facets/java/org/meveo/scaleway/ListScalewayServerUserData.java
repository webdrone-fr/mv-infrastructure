package org.meveo.scaleway;

import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import com.google.gson.*;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.ScalewayServer;
import org.meveo.model.customEntities.Server;
import org.meveo.model.customEntities.ServerUserData;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListScalewayServerUserData extends Script {
    

    private static final Logger logger = LoggerFactory.getLogger(ListScalewayServerUserData.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private  String SCALEWAY_URL = "api.scaleway.com";
    static final private String BASE_PATH = "/instance/v1/zones/";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        String action = parameters.get(CONTEXT_ACTION).toString();
        ScalewayServer server = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), ScalewayServer.class);

        String zone = server.getZone();
        String serverId = server.getProviderSideId();
        
        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("using credential {} with username {}", credential.getDomainName(), credential.getUsername());
        }
        
        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/servers/"+serverId+"/user_data");
        Response response = CredentialHelperService.setCredential(target.request("application/json"), credential).get();
        String value = response.readEntity(String.class);
        logger.info("response : {}", value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response: "+value);

        if (response.getStatus()<300) {
            JsonArray userDataArr = new JsonParser().parse(value).getAsJsonObject().get("user_data").getAsJsonArray();
            for (JsonElement userDataKeyEl : userDataArr) {
                ServerUserData userData = new ServerUserData();
                String userDataKey = userDataKeyEl.getAsString();
                String serverUserDataValue = ScalewayHelperService.getServerUserData(zone, serverId, userDataKey, credential);
                userData.setName(userDataKey);
                userData.setContentType("text/plain");
                userData.setContent(serverUserDataValue);
                userData.setServer(server);
                userData.setZone(server.getZone());
                userData.setServerSideKey(userDataKey);

                try {
                    crossStorageApi.createOrUpdate(defaultRepo, userData);
                    logger.info("User Data : {} successfully retrieved for Server : {}", userDataKey, serverId);
                } catch (Exception e) {
                    logger.error("Error retrieving User Data {} for Server: {}", userDataKey, serverId, e.getMessage());
                }
            }
        }
        response.close();
    }
}