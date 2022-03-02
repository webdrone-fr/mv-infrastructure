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
import org.meveo.api.exception.EntityDoesNotExistsException;
import org.meveo.model.customEntities.CustomEntityTemplate;
import java.util.List;
import java.util.ArrayList;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.script.openstack.CheckOVHToken;
import org.meveo.script.openstack.OpenstackAPI;
import org.meveo.model.customEntities.ServerNetwork;
import java.time.OffsetDateTime;

public class ListServerNetworks extends Script {

    private static final Logger log = LoggerFactory.getLogger(ListServerImages.class);

    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);

    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);

    private Repository defaultRepo = repositoryService.findDefaultRepository();

    private CrossStorageService crossStorageService = getCDIBean(CrossStorageService.class);

    private CustomEntityTemplateService customEntityTemplateService = getCDIBean(CustomEntityTemplateService.class);

    private CheckOVHToken checkOVHToken = new CheckOVHToken();

    private OpenstackAPI openstackAPI = new OpenstackAPI();
	
	@Override
	public void execute(Map<String, Object> parameters) throws BusinessException {
		super.execute(parameters);
        ServiceProvider sp = new ServiceProvider();
        String codeClass = sp.getClass().getSimpleName();
        CustomEntityTemplate cet = customEntityTemplateService.findByCode(codeClass);
        try {
            List<Map<String, Object>> providers = crossStorageService.find(defaultRepo, cet, null);
            for (Map<String, Object> provider : providers) {
                log.info(provider.toString());
                ServiceProvider matchingProvider = crossStorageApi.find(defaultRepo, ServiceProvider.class).by("uuid", provider.get("uuid").toString()).getResult();
                Credential credential = CredentialHelperService.getCredential(matchingProvider.getApiBaseUrl(), crossStorageApi, defaultRepo);
              	if (credential.getDomainName().equalsIgnoreCase("cloud.ovh.net")) {
                    checkOVHToken.checkOVHToken(credential, matchingProvider);
                    List<JsonObject> networks = openstackAPI.networkAPI("networks", credential, null, "get", "network");
                    for (JsonObject networkObj : networks) {
                        ServerNetwork network = new ServerNetwork();
                      	network.setUuid(networkObj.get("id").getAsString());
                      	network.setName(networkObj.get("name").getAsString());
                      	ArrayList<String> subnets = new ArrayList<>();
                      	JsonArray jsonArray = (JsonArray)networkObj.get("subnets");
                      	if (jsonArray != null) {
                            for (JsonElement o : jsonArray){ 
                            	subnets.add(o.getAsString());
                            }
                        }
                      	network.setSubnet(subnets);
                      	network.setCreationDate(OffsetDateTime.parse(networkObj.get("created_at").getAsString()).toInstant());
                      	network.setLastUpdated(OffsetDateTime.parse(networkObj.get("updated_at").getAsString()).toInstant());
                        try {
                            crossStorageApi.createOrUpdate(defaultRepo, network);
                        } catch (Exception ex) {
                            log.error("error creating network {} :{}", network.getUuid(), ex.getMessage());
                        }
                    }
                }
            }
        } catch (EntityDoesNotExistsException ex) {
            log.error("Entity does not exist : {} : {}", codeClass, ex.getMessage());
        }
	}
	
}