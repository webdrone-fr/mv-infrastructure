package org.meveo.script;

import java.util.Map;
import org.meveo.service.script.Script;
import org.meveo.admin.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.meveo.service.storage.RepositoryService;
import org.meveo.model.storage.Repository;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.persistence.CEIUtils;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import org.meveo.script.openstack.OpenstackAPI;
import org.meveo.model.customEntities.Credential;
import org.meveo.credentials.CredentialHelperService;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.HashMap;

public class PopulateServerProvider extends Script {

    private static final Logger log = LoggerFactory.getLogger(PopulateServerProvider.class);

    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);

    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);

    private Repository defaultRepo = repositoryService.findDefaultRepository();
  
  	private OpenstackAPI openstackAPI = new OpenstackAPI();

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        super.execute(parameters);
        ServiceProvider serverProvider = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance) parameters.get(CONTEXT_ENTITY), ServiceProvider.class);
        switch(serverProvider.getApiBaseUrl()) {
            case "cloud.ovh.net":
            	//Populate server types
            	String url = "flavors/detail";
        		Credential credential = CredentialHelperService.getCredential(serverProvider.getApiBaseUrl(), crossStorageApi, defaultRepo);
            	List<JsonObject> flavors = openstackAPI.computeAPI(url, credential, null, "get", "flavor");
            	for (JsonObject flavor : flavors) {
                  	HashMap<String, String> serverTypes = new HashMap<String, String>();
                }
            default:
                FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "WArning : ", "No populate found for " + serverProvider.getApiBaseUrl()));
        }
    }
}
