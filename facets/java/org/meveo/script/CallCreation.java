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
import org.meveo.model.customEntities.Server;
import org.meveo.model.persistence.CEIUtils;
import java.util.List;
import org.meveo.api.persistence.CrossStorageApi;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import org.meveo.script.openstack.CreateOVHServersScript;

public class CallCreation extends Script {
  
    private static final Logger log = LoggerFactory.getLogger(CallListing.class);
  
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
  
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
  
    private Repository defaultRepo = repositoryService.findDefaultRepository();
  
    private CreateOVHServersScript createOVHServersScript = new CreateOVHServersScript();
  
    private Credential getCredential(String domain) {
        List<Credential> matchigCredentials = crossStorageApi.find(defaultRepo, Credential.class).by("domainName", domain).getResults();
        if (matchigCredentials.size() > 0) {
            return matchigCredentials.get(0);
        } else {
            return null;
        }
    }
	
	@Override
	public void execute(Map<String, Object> parameters) throws BusinessException {
		super.execute(parameters);
        log.info("calling CallCreation");
		Server server = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), Server.class);
        ServiceProvider openstack = server.getProvider();
        Credential credential = getCredential(openstack.getApiBaseUrl());
        if (credential == null) {
            throw new BusinessException("No credential found for " + openstack.getApiBaseUrl()); 
        } else {
        	log.info("using credential {} with username {}", credential.getUuid(), credential.getUsername());
        }
        switch(credential.getDomainName()) {
          case "cloud.ovh.net":
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "INFO : ", "Creation done for OVH servers"));
            //createOVHServersScript.createServer(credential, openstack, server);
            break;
          case "api.scaleway.com":
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "INFO : ", "Creation done for scaleway servers"));
            //create
            break;
          case "api.gandi.net/v5/":
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "INFO : ", "Creation done for gandi servers"));
            //create
            break;
          default:
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "WArning : ", "No Creation found for " + openstack.getCode()));

        }
	}
	
}