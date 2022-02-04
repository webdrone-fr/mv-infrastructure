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
import java.util.List;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.script.openstack.ListOVHServersScript;
import org.meveo.script.ListScalewayServersScript;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;

public class CallListing extends Script {
  
    private static final Logger log = LoggerFactory.getLogger(CallListing.class);
  
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
  
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
  
    private Repository defaultRepo = repositoryService.findDefaultRepository();
  
    private ListOVHServersScript listOVHServerScript = new ListOVHServersScript();
  
    private ListScalewayServersScript listScalewayServersScript = new ListScalewayServersScript();
      
    private ServiceProvider getProvider(String code) {
		return crossStorageApi.find(defaultRepo, ServiceProvider.class).by("code", code).getResult();
    }
  
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
        log.info("calling CallListing");
        ServiceProvider serviceProvider = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), ServiceProvider.class);
        Credential credential = getCredential(serviceProvider.getApiBaseUrl());
        if (credential == null) {
            throw new BusinessException("No credential found for " + serviceProvider.getApiBaseUrl()); 
        } else {
        	log.info("using credential {} with username {}", credential.getUuid(), credential.getUsername());
        }
        switch(credential.getDomainName()) {
          case "cloud.ovh.net":
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "INFO : ", "Listing done for OVH servers"));
            listOVHServerScript.callOVH(credential, serviceProvider);
            break;
          case "api.scaleway.com":
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "INFO : ", "Listing done for scaleway servers"));
            listScalewayServersScript.listScaleway(credential, serviceProvider);
            break;
          case "api.gandi.net/v5/":
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "INFO : ", "Listing done for gandi servers"));
            //listGandiServer
            break;
          default:
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "WArning : ", "No listing found for " + serviceProvider.getCode()));
        }
	}
	
}