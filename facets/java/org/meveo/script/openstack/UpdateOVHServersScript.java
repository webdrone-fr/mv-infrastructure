package org.meveo.script.openstack;

import java.util.Map;

import org.meveo.service.script.Script;
import org.meveo.admin.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.customEntities.Server;
import org.meveo.model.customEntities.Credential;
import org.meveo.service.storage.RepositoryService;
import org.meveo.model.storage.Repository;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.script.openstack.CheckOVHToken;
import javax.ws.rs.client.*;
import javax.ws.rs.core.*;
import java.util.HashMap;
import com.google.gson.*;

public class UpdateOVHServersScript extends Script {

    private static final Logger log = LoggerFactory.getLogger(ListOVHServersScript.class);

    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);

    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);

    private Repository defaultRepo = repositoryService.findDefaultRepository();

    private CheckOVHToken checkOVHToken = new CheckOVHToken();
	
	@Override
	public void execute(Map<String, Object> parameters) throws BusinessException {
		super.execute(parameters);
	}
  
    public void updateServer(Credential credential, ServiceProvider openstack, Server server) throws BusinessException {
        log.info("calling UpdateOVHServersScripts");
        //Check Token
        checkOVHToken.checkOVHToken(credential, openstack);
        //Retreive actual values from the server
        
    }
  
    private HashMap<String, Object> retreiveValues (Credential credential, String serverUuid, String zone) {
        HashMap<String, Object> oldServ = new HashMap<String, Object>();
      	Client client = ClientBuilder.newClient();
      	WebTarget target = client.target("https://compute." + zone + ".cloud.ovh.net/v2.1/servers/" + serverUuid);
      	Response response = target.request().header("X-Auth-Token", credential.getToken()).get();
        String value = response.readEntity(String.class);
      	Integer responseStatus = response.getStatus();
      	if (responseStatus < 300) {
			JsonParser parserServer = new JsonParser();
            JsonElement jsonServer = parserServer.parse(value);
            JsonObject serverObj = jsonServer.getAsJsonObject();
            serverObj = serverObj.get("server").getAsJsonObject();
            
        }
		return null;
    }
	
}