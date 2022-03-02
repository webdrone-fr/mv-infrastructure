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
import org.meveo.persistence.CrossStorageService;
import org.meveo.service.custom.CustomEntityTemplateService;

public class ListServerNetworks extends Script {

    private static final Logger log = LoggerFactory.getLogger(ListServerImages.class);

    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);

    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);

    private Repository defaultRepo = repositoryService.findDefaultRepository();

    private CrossStorageService crossStorageService = getCDIBean(CrossStorageService.class);

    private CustomEntityTemplateService customEntityTemplateService = getCDIBean(CustomEntityTemplateService.class);
	
	@Override
	public void execute(Map<String, Object> parameters) throws BusinessException {
		super.execute(parameters);
	}
	
}