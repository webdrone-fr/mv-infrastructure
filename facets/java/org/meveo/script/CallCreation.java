package org.meveo.script;

import java.util.Map;

import org.meveo.service.script.Script;
import org.meveo.admin.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.service.storage.RepositoryService;
import org.meveo.model.storage.Repository;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.customEntities.OVHServer;
import org.meveo.model.persistence.CEIUtils;
import java.util.List;
import org.meveo.api.persistence.CrossStorageApi;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import org.meveo.openstack.CreateOVHServersScript;
import org.meveo.credentials.CredentialHelperService;

public class CallCreation extends Script {
  
    private static final Logger log = LoggerFactory.getLogger(CallCreation.class);
  
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
  
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
  
    private Repository defaultRepo = repositoryService.findDefaultRepository();
  
    private CreateOVHServersScript createOVHServersScript = new CreateOVHServersScript();
	
	@Override
	public void execute(Map<String, Object> parameters) throws BusinessException {
		super.execute(parameters);
        log.info("calling CallCreation");
		OVHServer server = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), OVHServer.class);
        ServiceProvider openstack = server.getProvider();
        Credential credential = CredentialHelperService.getCredential(openstack.getApiBaseUrl(), crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for " + openstack.getApiBaseUrl()); 
        } else {
        	log.info("using credential {} with username {}", credential.getUuid(), credential.getUsername());
        }
        switch(credential.getDomainName()) {
          case "cloud.ovh.net":
            createOVHServersScript.createServer(credential, openstack, server);
            break;
          case "api.scaleway.com":
            //create
            break;
          case "api.gandi.net/v5/":
            //create
            break;
          default:
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "WArning : ", "No Creation found for " + openstack.getCode()));

        }
	}
	
}