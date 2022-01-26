package org.meveo.script;

import java.util.List;
import java.util.Map;
import java.time.Instant;

import org.meveo.service.script.Script;
import org.meveo.admin.exception.BusinessException;
import org.meveo.model.customEntities.Server;
import org.meveo.model.customEntities.ServerAction;
import javax.ws.rs.client.*;
import javax.ws.rs.core.*;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.service.storage.RepositoryService;
import org.meveo.model.storage.Repository;
import org.meveo.api.persistence.CrossStorageApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.meveo.model.persistence.CEIUtils;

public class SergentCommand extends Script {
  
    private static final Logger log = LoggerFactory.getLogger(SergentCommand.class);
	private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
	private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();
  
    private static List<String> allowedCommands = List.of("list","gitpull","dockerpull");

	private String command;

	public void setCommand(String command) {
		this.command = command;
	}


	@Override
	public void execute(Map<String, Object> parameters) throws BusinessException {
        if(command==null){
          command="dockerpull";
        }
		if(!allowedCommands.contains(command)){
			throw new BusinessException("invalid command, should be one of "+allowedCommands);
		}
		Server server =  CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get("CONTEXT_ENTITY"), Server.class);
		if(server.getDomainName()==null || server.getDomainName().isEmpty()){
			throw new BusinessException("invalid server domain name");
		}
        ServerAction action = new ServerAction(); 
        action.setCreationDate(Instant.now());
        action.setServer(server);
        action.setAction(command);
		Client client = ClientBuilder.newClient();
     	WebTarget target = client.target(server.getSergentUrl()).queryParam("command", command);
      	Response response= target.request().get();
		String responseContent = "";
        if(response.getStatus()==Response.Status.OK.getStatusCode()){
          action.setResponseStatus("OK");
        } else {
          action.setResponseStatus("ERROR");
          responseContent += "Status:"+response.getStatus()+"\n";
        }
        responseContent+=response.readEntity(String.class);
        action.setResponse(responseContent);
        try {
           crossStorageApi.createOrUpdate(defaultRepo,action);
        } catch(Exception ex){
            log.error("error creating action {} :{}",action.getUuid(),ex.getMessage());
        }
	}
	
}