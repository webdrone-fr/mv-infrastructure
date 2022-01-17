package org.meveo.script;

import java.util.Map;

import org.meveo.service.script.Script;
import org.meveo.admin.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.meveo.service.storage.RepositoryService;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.model.customEntities.Server;
import org.meveo.model.customEntities.CrudEventListenerScript;

public class ScalewayServerCreationScript extends Script implements CrudEventListenerScript<Server> {
    private static final Logger log = LoggerFactory.getLogger(ScalewayServerCreationScript.class);

	private CrossStorageApi crossStorageApi;
	private RepositoryService rService;

	public ScalewayServerCreationScript() {
		crossStorageApi = getCDIBean(CrossStorageApi.class);
		rService = getCDIBean(RepositoryService.class);
	}

	public Class<Server> getEntityClass() {
		return Server.class;
	}

	/**
	 * Called just before entity persistence
	 * 
	 * @param entity entity being persisted
	 */
	public void prePersist(Server entity) {
		log.info("prePersist "+entity);
	}

	/**
	 * Called just before entity update
	 * 
	 * @param entity entity being updated
	 */
	public void preUpdate(Server entity) {
		log.info("preUpdate "+entity);
	}

	/**
	 * Called just before entity removal
	 * 
	 * @param entity entity being removed
	 */
	public void preRemove(Server entity) {
		log.info("preRemove "+entity);
	}

	/**
	 * Called just after entity persistence
	 * 
	 * @param entity persisted entity
	 */
	public void postPersist(Server entity) {
		log.info("postPersist "+entity);
	}

	/**
	 * Called just after entity update
	 * 
	 * @param entity updated entity
	 */
	public void postUpdate(Server entity) {
		log.info("postUpdate "+entity);
	}

	/**
	 * Called just after entity removal
	 * 
	 * @param entity removed entity
	 */
	public void postRemove(Server entity) {
		log.info("postRemove "+entity);
	}
}