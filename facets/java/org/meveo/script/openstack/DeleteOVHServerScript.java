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
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import org.meveo.api.persistence.CrossStorageApi;
import javax.ws.rs.client.*;
import javax.ws.rs.core.*;
import java.util.List;
import com.google.gson.*;
import org.meveo.script.openstack.OpenstackAPI;

public class DeleteOVHServerScript extends Script {

    private static final Logger log = LoggerFactory.getLogger(ListOVHServersScript.class);

    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);

    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);

    private Repository defaultRepo = repositoryService.findDefaultRepository();

    private CheckOVHToken checkOVHToken = new CheckOVHToken();
  
  	private OpenstackAPI openstackAPI = new OpenstackAPI();

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        super.execute(parameters);
    }

    public void DeleteServer(Credential credential, ServiceProvider openstack, Server server) throws BusinessException {
        log.info("calling DeleteOVHServerScript");
        // Check Token
        checkOVHToken.checkOVHToken(credential, openstack);
        // Check Server
        if (server.getUuid() == null) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "Warning : ", "server id not found for server: " + server.getUuid()));
            throw new BusinessException("Cannot delete server");
        }
        boolean smt = server.getInstanceName().startsWith("dev-");
        log.info("condition to delete {}", smt);
        if (server.getInstanceName().startsWith("dev-")) {
            // Build and execute
            Client client = ClientBuilder.newClient();
            log.info("uuid used {}", server.getUuid());
          	String url = "servers/" + server.getUuid();
          	List<JsonObject> servers = openstackAPI.computeAPI(url, credential, null, "delete", null);
            //WebTarget target = client.target("https://compute." + server.getZone() + ".cloud.ovh.net/v2.1/servers/" + server.getUuid());
            //Response response = target.request().header("X-Auth-Token", credential.getToken()).delete();
            //if (response.getStatus() < 300) {
                server.setStatus("DELETED");
                server.setCreationDate(null);
                server.setLastUpdate(null);
                server.setPublicIp(null);
                server.setDomainName(null);
                try {
                    crossStorageApi.createOrUpdate(defaultRepo, server);
                } catch (Exception ex) {
                    log.error("error updating server {} :{}", server.getUuid(), ex.getMessage());
                }
            //}
        } else {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "Warning : ", "The server you're trying to delete is not a dev server : " + server.getInstanceName()));
        }
    }
}
