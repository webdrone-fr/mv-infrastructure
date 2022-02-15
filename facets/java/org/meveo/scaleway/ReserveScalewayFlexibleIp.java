package org.meveo.scaleway;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import com.google.gson.*;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.PublicIp;
import org.meveo.model.customEntities.Server;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReserveScalewayFlexibleIp extends Script {


    private static final Logger logger = LoggerFactory.getLogger(ReserveScalewayFlexibleIp.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();
    
    static final private String SCALEWAY_URL = "api.scaleway.com";
    static final private String BASE_PATH = "/instance/v1/zones/";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        PublicIp publicIp = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), PublicIp.class);

        if (publicIp.getZone() == null) {
            throw new BusinessException("Invalid Public Ip Zone");
        } else if (publicIp.getProvider() == null) {
            throw new BusinessException("Invalid Public Ip Provider");
        }
        
        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("using credential {}({}) with username {}", credential.getDomainName(), credential.getUuid(), credential.getUsername());
        }

        String zone = publicIp.getZone();
        ServiceProvider provider = publicIp.getProvider();

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+BASE_PATH+zone+"/ips");

        Map<String, Object> body = new HashMap<String, Object>();
        //Project
        // Webdrone ID = 6a0c2ca8-917a-418a-90a3-05949b55a7ae
        String projectId = "6a0c2ca8-917a-418a-90a3-05949b55a7ae";
        if (publicIp.getProject() != null) {
            projectId = publicIp.getProject();
        }
        body.put("project", projectId);

        // Tags
        if (publicIp.getTags().size() > 1) {
            ArrayList<String> ipTags = new ArrayList<String>();
            for (String tag : publicIp.getTags()) {
                ipTags.add(tag);
            }
            body.put("tags", ipTags);
        }

        // Id of Server to attach IP to
        if (publicIp.getServer() != null) {
            String serverId = publicIp.getServer().getProviderSideId();
            body.put("server", serverId);
        }

        String resp = JacksonUtil.toStringPrettyPrinted(body);
        Response response = 
            CredentialHelperService.setCredential(target.request("application/json"), credential)
                .post(Entity.json(resp));
        String value = response.readEntity(String.class);
        logger.info("response : {}", value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response: "+value);

        if (response.getStatus()<300) {
            JsonObject flexibleIpObj = new JsonParser().parse(value).getAsJsonObject();
            JsonObject publicIpObj = flexibleIpObj.get("ip").getAsJsonObject();
            
            // Default Values
            publicIp.setProviderSideId(publicIpObj.get("id").getAsString());
            publicIp.setIpVFourAddress(publicIpObj.get("address").getAsString());
            publicIp.setOrganization(publicIpObj.get("organization").getAsString());
            publicIp.setProject(publicIpObj.get("project").getAsString());
            publicIp.setProvider(provider);

            // Reverse - nullable
            if (!publicIpObj.get("reverse").isJsonNull()) {
                publicIp.setReverse(publicIpObj.get("reverse").getAsString());
            }

            // Server
            if (!publicIpObj.get("server").isJsonNull()) {
                String serverId = publicIpObj.get("server").getAsJsonObject().get("id").getAsString();
                try {
                    Server server = crossStorageApi.find(defaultRepo, Server.class).by("providerSideId", serverId).getResult();
                    publicIp.setServer(server);
                } catch (Exception e) {
                    logger.error("Error retrieving Server for Public Ip : {} : {} ", publicIp.getProviderSideId(), e.getMessage());
                }
            }

            // Tags
            if (!publicIpObj.get("tags").isJsonNull()) {
                ArrayList<String> ipTags = new ArrayList<String>();
                for (JsonElement tag : publicIpObj.get("tags").getAsJsonArray()) {
                    ipTags.add(tag.getAsString());
                }
                publicIp.setTags(ipTags);
            }

            // Location Definition
            String locationDefinition = "zone_id/platform_id/cluster_id/hypervisor_id/node_id";
            publicIp.setLocationDefinition(locationDefinition);

            // Location
            if (!flexibleIpObj.get("Location").isJsonNull()) {
                String location = flexibleIpObj.get("Location").getAsString();
                publicIp.setLocation(location);
            }

            try {
                crossStorageApi.createOrUpdate(defaultRepo, publicIp);
                logger.info("Public Ip : {} successfully created", publicIp.getProviderSideId());
            } catch (Exception e) {
                logger.error("Error reserving Public IP for Public Ip : {} : {} ", publicIp.getProviderSideId(), e.getMessage());
            }
            response.close();
        }
    }
}
