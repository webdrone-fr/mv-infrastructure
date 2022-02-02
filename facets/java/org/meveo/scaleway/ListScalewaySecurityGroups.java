package org.meveo.scaleway;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import com.google.gson.*;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.SecurityGroup;
// import org.meveo.model.customEntities.Server;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListScalewaySecurityGroups extends Script {
    

    private static final Logger logger = LoggerFactory.getLogger(ListScalewaySecurityGroups.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private String SCALEWAY_URL = "api.scaleway.com";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException{
        
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
            WebTarget target = client.target("https://"+SCALEWAY_URL+"/instance/v1/zones/"+zone+"/security_groups");
            Response response = CredentialHelperService.setCredential(target.request(), credential).get();
            String value = response.readEntity(String.class);
            logger.info("response : " + value);
            logger.debug("response status : {}", response.getStatus());
            if (response.getStatus() < 300) {
                JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().get("security_groups").getAsJsonArray();
                for (JsonElement element: rootArray) {
                    JsonObject secGroupObj = element.getAsJsonObject();
                    SecurityGroup securityGroup = new SecurityGroup();

                    securityGroup.setUuid(secGroupObj.get("id").getAsString());
                    securityGroup.setProviderSideId(secGroupObj.get("id").getAsString());
                    securityGroup.setName(secGroupObj.get("name").getAsString());
                    securityGroup.setDescription(secGroupObj.get("description").getAsString());
                    securityGroup.setCreationDate(OffsetDateTime.parse(secGroupObj.get("creation_date").getAsString()).toInstant());
                    securityGroup.setLastUpdated(OffsetDateTime.parse(secGroupObj.get("modification_date").getAsString()).toInstant());
                    securityGroup.setOrganization(secGroupObj.get("organization").getAsString());
                    securityGroup.setProject(secGroupObj.get("project").getAsString());
                    securityGroup.setStateful(secGroupObj.get("stateful").getAsBoolean());
                    securityGroup.setState(secGroupObj.get("state").getAsString());
                    securityGroup.setInboundDefaultPolicy(secGroupObj.get("inbound_default_policy").getAsString());
                    securityGroup.setOutboundDefaultPolicy(secGroupObj.get("outbound_default_policy").getAsString());
                    securityGroup.setProjectDefault(secGroupObj.get("project_default").getAsBoolean());
                    securityGroup.setEnableDefaultSecurity(secGroupObj.get("enable_default_security").getAsBoolean());
                    securityGroup.setZone(zone);

                    // Servers
                    JsonArray serversArr = secGroupObj.get("servers").getAsJsonArray();
                    // ArrayList<Server> serversList = new ArrayList<Server>();
                    ArrayList<String> serversList = new ArrayList<String>();
                    for (JsonElement serverEl : serversArr) {
                        JsonObject serverObj = serverEl.getAsJsonObject();
                        String serverId = serverObj.get("id").getAsString();
                        serversList.add(serverId);
                        // try {
                        //     Server server = crossStorageApi.find(defaultRepo, Server.class).by("providerSideId", serverId).getResult();
                        //     serversList.add(server);
                        // } catch (Exception ex) {
                        //     logger.info("Unable to find server : {}, Error : ", serverId, ex.getMessage());
                        //     parameters.put(RESULT_GUI_MESSAGE, "Server not found : "+serverId);
                        // }
                    }
                    securityGroup.setServers(serversList);

                    try {
                        crossStorageApi.createOrUpdate(defaultRepo, securityGroup);
                    } catch (Exception e) {
                        logger.error("Error creating Security Group {} : {}", securityGroup.getName(), e.getMessage());
                    }
                }
            }
        }
    }
}
