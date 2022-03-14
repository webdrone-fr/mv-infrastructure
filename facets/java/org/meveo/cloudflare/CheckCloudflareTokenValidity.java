package org.meveo.cloudflare;

import java.util.ArrayList;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import com.google.gson.*;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckCloudflareTokenValidity extends Script{
    

    private static final Logger logger = LoggerFactory.getLogger(CheckCloudflareTokenValidity.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private String CLOUDFLARE_URL = "api.cloudflare.com/client/v4";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        ServiceProvider registrar = crossStorageApi.find(defaultRepo, ServiceProvider.class).by("code", "CLOUDFLARE").getResult();

        Credential credential = CredentialHelperService.getCredential(CLOUDFLARE_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+CLOUDFLARE_URL);
        } else {
            logger.info("using credential {} with username {}", credential.getUuid(), credential.getUsername());
        }

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+CLOUDFLARE_URL+"/user/tokens/verify");
        Response response =  CredentialHelperService.setCredential(target.request(), credential).get();
        String value = response.readEntity(String.class);
        logger.info("response : " + value);
        logger.debug("response status : {}", response.getStatus());

        ArrayList<String> messages = new ArrayList<String>();
        if(response.getStatus()<300) {
            JsonObject responseObj = new JsonParser().parse(value).getAsJsonObject();
            JsonArray messagesArr = responseObj.get("messages").getAsJsonArray();
            for (JsonElement messageEl : messagesArr) {
                String message = messageEl.getAsJsonObject().get("message").getAsString();
                messages.add(message);
            }
            parameters.put(RESULT_GUI_MESSAGE, "Token Status Messages: "+ JacksonUtil.toStringPrettyPrinted(messages));
        }
        response.close();
    }
}
