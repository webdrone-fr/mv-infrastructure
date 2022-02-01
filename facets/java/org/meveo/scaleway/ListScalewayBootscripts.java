package org.meveo.scaleway;

import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import com.google.gson.*;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Bootscript;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListScalewayBootscripts extends Script {


    private static final Logger logger = LoggerFactory.getLogger(ListScalewayBootscripts.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private String SCALEWAY_URL = "api.scaleway.com";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        
        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("Using Credential {} with username {}", credential.getUuid(), credential.getUsername());
        }

        String[] zones = new String[] {"fr-par-1", "fr-par-2", "fr-par-3", "nl-ams-1", "pl-waw-1"};
        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        for (String zone : zones) {
            WebTarget target = client.target("https://"+SCALEWAY_URL+"/instance/v1/zones/"+zone+"/bootscripts");
            Response response = CredentialHelperService.setCredential(target.request(), credential).get();
            String value = response.readEntity(String.class);
            logger.info("response : " + value);
            logger.debug("response status : {}", response.getStatus());
            if (response.getStatus() < 300) {
                JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().get("bootscripts").getAsJsonArray();
                for (JsonElement element : rootArray) {
                    JsonObject bootscriptObj = element.getAsJsonObject();
                    Bootscript bootscript = new Bootscript();
                    bootscript.setUuid(bootscriptObj.get("id").getAsString());
                    bootscript.setZone(bootscriptObj.get("zone").getAsString());
                    bootscript.setProviderSideId(bootscriptObj.get("id").getAsString());
                    bootscript.setArch(bootscriptObj.get("architecture").getAsString());
                    bootscript.setBootcmdargs(bootscriptObj.get("bootcmdargs").getAsString());
                    bootscript.setDtb(bootscriptObj.get("dtb").getAsString());
                    bootscript.setInitrd(bootscriptObj.get("initrd").getAsString());
                    bootscript.setKernel(bootscriptObj.get("kernel").getAsString());
                    bootscript.setOrganization(bootscriptObj.get("organization").getAsString());
                    bootscript.setProject(bootscriptObj.get("project").getAsString());
                    bootscript.setIsDefault(bootscriptObj.get("default").getAsBoolean());
                    bootscript.setIsPublic(bootscriptObj.get("public").getAsBoolean());
                    bootscript.setTitle(bootscriptObj.get("title").getAsString());
                    logger.info("Bootscript Title : {}", bootscript.getTitle());
                    try {
                        crossStorageApi.createOrUpdate(defaultRepo, bootscript);
                    } catch (Exception e) {
                        logger.error("Error creating Bootscript {} : {}", bootscript.getTitle(), e.getMessage());
                    }
                }
            }
        }
    }
}
