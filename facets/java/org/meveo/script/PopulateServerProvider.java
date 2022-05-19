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
import org.meveo.model.persistence.JacksonUtil;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import org.meveo.openstack.OpenstackAPI;
import org.meveo.model.customEntities.Credential;
import org.meveo.credentials.CredentialHelperService;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.HashMap;
import org.meveo.openstack.CheckOVHToken;

public class PopulateServerProvider extends Script {

    private static final Logger logger = LoggerFactory.getLogger(PopulateServerProvider.class);

    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);

    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);

    private Repository defaultRepo = repositoryService.findDefaultRepository();
  
  	private OpenstackAPI openstackAPI = new OpenstackAPI();
    
    private CheckOVHToken checkOVHToken = new CheckOVHToken(); 

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        super.execute(parameters);
        ServiceProvider serverProvider = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance) parameters.get(CONTEXT_ENTITY), ServiceProvider.class);
        logger.info("service provider: {}", JacksonUtil.toStringPrettyPrinted(serverProvider));
        switch(serverProvider.getApiBaseUrl()) {
            case "cloud.ovh.net":
            	//Populate server types
            	String url = "flavors/detail";
                logger.info("Gone in case");
        		Credential credential = CredentialHelperService.getCredential(serverProvider.getApiBaseUrl(), crossStorageApi, defaultRepo);
                checkOVHToken.checkOVHToken(credential, serverProvider);
            	List<JsonObject> flavors = openstackAPI.computeAPI(url, credential, null, "get", "flavor");
                //Map<String, String> serverTypes = new HashMap<String, String>();
            	for (JsonObject flavor : flavors) {
                  	//serverTypes.put(flavor.get("id").getAsString(), flavor.toString());
                }
            	//serverProvider.setServerType(serverTypes);
                logger.info("server types: {}", JacksonUtil.toStringPrettyPrinted(serverTypes));
            	try {
                  	crossStorageApi.createOrUpdate(defaultRepo, serverProvider);
                } catch (Exception ex) {
                  	logger.error("error updating server : {}", serverProvider.getUuid(), ex.getMessage());
                }
            	break;
            default:
                FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "Warning : No populate found for " + serverProvider.getApiBaseUrl(), null));
        }
    }
}