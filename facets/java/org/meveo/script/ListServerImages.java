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
import org.meveo.script.openstack.CheckOVHToken;
import javax.ws.rs.client.*;
import javax.ws.rs.core.*;
import com.google.gson.*;
import org.meveo.model.customEntities.ServerImage;

public class ListServerImages extends Script {

    private static final Logger log = LoggerFactory.getLogger(ListServerImages.class);

    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);

    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);

    private Repository defaultRepo = repositoryService.findDefaultRepository();
  
  	private CrossStorageService crossStorageService = getCDIBean(CrossStorageService.class);

    private CustomEntityTemplateService customEntityTemplateService = getCDIBean(CustomEntityTemplateService.class);
  
    private CheckOVHToken checkOVHToken = new CheckOVHToken();
	
	@Override
	public void execute(Map<String, Object> parameters) throws BusinessException {
		super.execute(parameters);
        Client client = ClientBuilder.newClient();
      	ServiceProvider sp = new ServiceProvider();
      	String codeClass = sp.getClass().getSimpleName();
		CustomEntityTemplate cet = customEntityTemplateService.findByCode(codeClass);
      	try {
      		List<Map<String, Object>> providers =crossStorageService.find(defaultRepo, cet, null);
          	for(Map<String, Object> provider : providers) {
          		log.info(provider.toString());
              	String baseURL = provider.get("apiBaseUrl").toString();
              	ServiceProvider matchingProvider = crossStorageApi.find(defaultRepo, ServiceProvider.class).by("uuid", provider.get("uuid").toString()).getResult();
              	Credential credential = CredentialHelperService.getCredential(baseURL, crossStorageApi, defaultRepo);
              	checkOVHToken.checkOVHToken(credential, matchingProvider);
              	//https://image.compute.gra11.cloud.ovh.net/v2/images
              	WebTarget target = client.target("https://image.compute.gra11." + matchingProvider.getApiBaseUrl() + "/v2/images");
              	Response response = target.request().header("X-Auth-Token", credential.getToken()).get();
				String value = response.readEntity(String.class);
              	if (response.getStatus() < 300) {
                  	JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().getAsJsonArray("images");
                  	for (JsonElement element : rootArray) {
                    	JsonObject imageObj = element.getAsJsonObject();
                      	//Create a new image
                      	ServerImage image = new ServerImage();
                      	image.setUuid(imageObj.get("id").getAsString());
                      	image.setName(imageObj.get("name").getAsString());
                      	log.info(imageObj.get("visibility").getAsString());
						if (imageObj.get("visibility").getAsString().equalsIgnoreCase("private"))
							image.setIsPublic(false);
                        else
							image.setIsPublic(true);
                        try {
                            crossStorageApi.createOrUpdate(defaultRepo, image);
                        } catch (Exception ex) {
                            log.error("error creating server {} :{}", image.getUuid(), ex.getMessage());
                        }
                    }
				}
            }
        } catch (EntityDoesNotExistsException ex) {
          	log.error("Entity does not exist : {} : {}", codeClass, ex.getMessage());
        }
      	client.close();
	}
	
}