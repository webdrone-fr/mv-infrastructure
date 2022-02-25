package org.meveo.script;

import java.util.Map;

import org.meveo.service.script.Script;
import org.meveo.admin.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.service.storage.RepositoryService;
import org.meveo.model.storage.Repository;
import org.meveo.model.customEntities.ServiceProvider;
import java.util.List;
import org.meveo.persistence.CrossStorageService;
import org.meveo.model.customEntities.CustomEntityTemplate;
import org.meveo.service.custom.CustomEntityTemplateService;
import org.meveo.api.exception.EntityDoesNotExistsException;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;

public class ListServerImages extends Script {

    private static final Logger log = LoggerFactory.getLogger(ListServerImages.class);

    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);

    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);

    private Repository defaultRepo = repositoryService.findDefaultRepository();
  
  	private CrossStorageService crossStorageService = getCDIBean(CrossStorageService.class);

    private CustomEntityTemplateService customEntityTemplateService = getCDIBean(CustomEntityTemplateService.class);
	
	@Override
	public void execute(Map<String, Object> parameters) throws BusinessException {
		super.execute(parameters);
      	ServiceProvider sp = new ServiceProvider();
      	String codeClass = sp.getClass().getSimpleName();
		CustomEntityTemplate cet = customEntityTemplateService.findByCode(codeClass);
      	try {
      		List<Map<String, Object>> providers =crossStorageService.find(defaultRepo, cet, null);
          	for(Map<String, Object> provider : providers) {
          		log.info(provider.toString());
              	String baseURL = provider.get("apiBaseUrl").toString();
              	Credential credential = CredentialHelperService.getCredential(baseURL, crossStorageApi, defaultRepo);
            }
        } catch (EntityDoesNotExistsException ex) {
          	log.error("Entity does not exist : {} : {}", codeClass, ex.getMessage());
        }
	}
	
}