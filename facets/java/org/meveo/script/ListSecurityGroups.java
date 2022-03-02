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
import org.meveo.model.customEntities.CustomEntityTemplate;
import org.meveo.service.custom.CustomEntityTemplateService;
import java.util.List;
import java.util.HashMap;
import org.meveo.persistence.CrossStorageService;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.script.openstack.CheckOVHToken;
import com.google.gson.*;
import org.meveo.script.openstack.OpenstackAPI;
import org.meveo.api.exception.EntityDoesNotExistsException;
import org.meveo.model.customEntities.SecurityGroup;
import java.time.OffsetDateTime;

public class ListSecurityGroups extends Script {

    private static final Logger log = LoggerFactory.getLogger(ListServerImages.class);

    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);

    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);

    private Repository defaultRepo = repositoryService.findDefaultRepository();

    private CustomEntityTemplateService customEntityTemplateService = getCDIBean(CustomEntityTemplateService.class);

    private CrossStorageService crossStorageService = getCDIBean(CrossStorageService.class);
  
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
                    List<JsonObject> securityGroups = openstackAPI.networkAPI("security-groups", credential, null, "get", "security-group");
                    for (JsonObject securityGroup : securityGroups) {
                      	SecurityGroup secuGroup = new SecurityGroup();
                      	secuGroup.setUuid(securityGroup.get("id").getAsString());
                      	secuGroup.setName(securityGroup.get("Name").getAsString());
                      	secuGroup.setProviderSideId(securityGroup.get("id").getAsString());
                      	secuGroup.setCreationDate(OffsetDateTime.parse(securityGroup.get("created_at").getAsString()).toInstant());
                      	secuGroup.setLastUpdated(OffsetDateTime.parse(securityGroup.get("updated_at").getAsString()).toInstant());
                      	secuGroup.setDescription(securityGroup.get("description").getAsString());
                      	secuGroup.setOrganization(securityGroup.get("tenant_id").getAsString());
                      	secuGroup.setProject(securityGroup.get("project_id").getAsString());
                      	secuGroup.setZone("GRA11");
                      	HashMap<String, String> rules = new HashMap<String, String>();
                      	JsonArray rulesArray = (JsonArray)securityGroup.get("security_group_rules");
                      	for (JsonElement ruleElement : rulesArray) {
                          	JsonObject ruleObject = ruleElement.getAsJsonObject(); 
							rules.put(ruleObject.get("security_group_id").getAsString(), ruleObject.get("direction").getAsString());
                        }
                        try {
                            crossStorageApi.createOrUpdate(defaultRepo, secuGroup);
                        } catch (Exception ex) {
                            log.error("error creating server {} :{}", secuGroup.getUuid(), ex.getMessage());
                        }
                    }
                }
            }
        } catch (EntityDoesNotExistsException ex) {
            log.error("Entity does not exist : {} : {}", codeClass, ex.getMessage());
        }	
	}
	
}