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
        //Check Input
        
    }
	
}