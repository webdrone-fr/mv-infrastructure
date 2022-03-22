package org.meveo.scaleway;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import com.google.gson.*;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.PublicIp;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListScalewayFlexibleIps extends Script{
    

    private static final Logger logger = LoggerFactory.getLogger(ListScalewayFlexibleIps.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();
    
    static final private String SCALEWAY_URL = "api.scaleway.com";
    static final private String BASE_PATH = "/instance/v1/zones/";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        String action = parameters.get(CONTEXT_ACTION).toString();
        ServiceProvider provider = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), ServiceProvider.class);

        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("Using Credential {} with username {}", credential.getUuid(), credential.getUsername());
        }

        // String[] zones = new String[] {"fr-par-1", "fr-par-2", "fr-par-3", "nl-ams-1", "pl-waw-1"};
        List<String> zones = provider.getZones();
        List<String> providerSideIds = new ArrayList<String>();
        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());

        List<String> publicIpRecords = new ArrayList<String>();

        for(String zone : zones) {
            WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/ips");
            Response response = CredentialHelperService.setCredential(target.request(), credential).get();
            String value = response.readEntity(String.class);
            logger.info("response : " + value);
            logger.debug("response status : {}", response.getStatus());
            if (response.getStatus() < 300) {
                JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().get("ips").getAsJsonArray();
                for (JsonElement element : rootArray) {
                    JsonObject publicIpObj = element.getAsJsonObject();
                    String publicIpId = publicIpObj.get("id").getAsString();
                    providerSideIds.add(publicIpId);
                    PublicIp publicIp = null;
                    try {
                        if(crossStorageApi.find(defaultRepo, PublicIp.class).by("providerSideId", publicIpId).getResult() != null) {
                            publicIp = crossStorageApi.find(defaultRepo, PublicIp.class).by("providerSideId", publicIpId).getResult();
                        } else {
                            publicIp = new PublicIp();
                            publicIp.setUuid(publicIpId);
                        }
                        publicIp = ScalewaySetters.setPublicIp(publicIpObj, publicIp, provider, crossStorageApi, defaultRepo);
                        crossStorageApi.createOrUpdate(defaultRepo, publicIp);
                        
                        Map<String, String> publicIpRecord = new HashMap<String, String>();
                        String address = publicIpObj.get("address").getAsString();
                        String serverName = null;
                        if (!publicIpObj.get("server").isJsonNull()) {
                            serverName = publicIpObj.get("server").getAsJsonObject().get("name").getAsString();
                        }
                        publicIpRecord.put(address, serverName);
                        publicIpRecords.add(String.valueOf(publicIpRecord));
                    } catch (Exception e) {
                        logger.error("Error retrieving public ip : {}", publicIpId, e.getMessage());
                    }
                }
                try {
                    provider.setPublicIp(publicIpRecords);
                    crossStorageApi.createOrUpdate(defaultRepo, provider);
                } catch (Exception e) {
                    logger.error("Error retrieving public ip records for provider : {}", provider.getCode(), e.getMessage());
                }
            }
            // ScalewayHelperService.filterToLatestValues("PublicIp", providerSideIds, crossStorageApi, defaultRepo);
            response.close();
        }
    }
}