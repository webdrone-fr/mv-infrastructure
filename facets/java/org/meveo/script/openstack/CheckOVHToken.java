package org.meveo.script.openstack;

import java.util.Map;
import org.meveo.service.script.Script;
import org.meveo.admin.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.customEntities.Credential;
import java.util.ArrayList;
import java.util.HashMap;
import java.time.OffsetDateTime;
import org.meveo.model.persistence.JacksonUtil;
import javax.ws.rs.client.*;
import javax.ws.rs.core.*;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.model.storage.Repository;
import org.meveo.service.storage.RepositoryService;
import java.util.List;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.customEntities.CustomEntityInstance;
import org.meveo.service.custom.CustomEntityTemplateService;
import org.meveo.model.customEntities.CustomEntityTemplate;

public class CheckOVHToken extends Script {

    private static final Logger log = LoggerFactory.getLogger(ListOVHServersScript.class);
  
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
  
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
  
    private Repository defaultRepo = repositoryService.findDefaultRepository();
  
    private CustomEntityTemplateService customEntityTemplateService = new CustomEntityTemplateService();

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        super.execute(parameters);
    }

    public void checkOVHToken(Credential credential, ServiceProvider openstack) {
        log.info("call CheckOVHToken");
        // Verification of the token
        OffsetDateTime currentDate = OffsetDateTime.now();
        OffsetDateTime expireDate = OffsetDateTime.parse(credential.getTokenExpiry().toString());
        if (currentDate.isAfter(expireDate)) {
            // Dechiffrement du mot de passe (moche mais temporaire)
            String stringToDecrypt = credential.getPasswordSecret();
            List<Object> objectsToHash = new ArrayList<>();
            CustomEntityInstance credentialCEI = CEIUtils.pojoToCei(credential);
            CustomEntityTemplate customEntityTemplate = customEntityTemplateService.findByCodeOrDbTablename(credential.getClass().getSimpleName());
            // Creation du body
            HashMap<String, Object> master = new HashMap<String, Object>();
            HashMap<String, Object> auth = new HashMap<String, Object>();
            HashMap<String, Object> identity = new HashMap<String, Object>();
            HashMap<String, Object> password = new HashMap<String, Object>();
            HashMap<String, Object> user = new HashMap<String, Object>();
            HashMap<String, Object> domain = new HashMap<String, Object>();
            ArrayList<String> method = new ArrayList<String>();
            method.add("password");
            domain.put("id", "default");
            user.put("password", "");
            user.put("domain", domain);
            user.put("name", credential.getUsername());
            password.put("user", user);
            identity.put("methods", method);
            identity.put("password", password);
            auth.put("identity", identity);
            master.put("auth", auth);
            String resp = JacksonUtil.toStringPrettyPrinted(master);
            // Creation of the identity token
            Client client = ClientBuilder.newClient();
            WebTarget target = client.target("https://auth." + openstack.getApiBaseUrl() + "/v3/auth/tokens");
            Response response = target.request().post(Entity.json(resp));
            credential.setToken(response.getHeaderString("X-Subject-Token"));
            credential.setTokenExpiry(currentDate.plusDays(1).toInstant());
            try {
                crossStorageApi.createOrUpdate(defaultRepo, credential);
            } catch (Exception ex) {
                log.error("error update credentials {} :{}", credential.getUuid(), ex.getMessage());
            }
            response.close();
        }
    }
}
