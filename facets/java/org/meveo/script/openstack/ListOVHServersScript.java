package org.meveo.script.openstack;

import java.util.Map;
import org.meveo.service.script.Script;
import org.meveo.admin.exception.BusinessException;
import org.meveo.api.exception.BusinessApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.meveo.model.customEntities.CustomEntityInstance;
import javax.ws.rs.client.*;
import javax.ws.rs.core.*;
import com.google.gson.*;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.customEntities.Server;
import org.meveo.model.customEntities.Credential;
import org.meveo.service.storage.RepositoryService;
import org.meveo.model.storage.Repository;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import javax.ws.rs.client.Entity;
import java.util.ArrayList;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.security.PasswordUtils;

public class ListOVHServersScript extends Script {

    private static final Logger log = LoggerFactory.getLogger(ListOVHServersScript.class);

    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);

    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);

    private Repository defaultRepo = repositoryService.findDefaultRepository();

    private ServiceProvider getProvider(String code) {
        return crossStorageApi.find(defaultRepo, ServiceProvider.class).by("code", code).getResult();
    }

    private Credential getCredential(String domain) {
        List<Credential> matchigCredentials = crossStorageApi.find(defaultRepo, Credential.class).by("domainName", domain).getResults();
        if (matchigCredentials.size() > 0) {
            return matchigCredentials.get(0);
        } else {
            return null;
        }
    }

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        log.info("calling ListOVHServersScript");
        ServiceProvider openstack = null;
        try {
            openstack = getProvider("OVH");
        } catch (Exception e) {
            throw new BusinessException(e);
        }
        // Retreive credential
        // openstack.getApiBaseUrl() = cloud.ovh.net
        Credential credential = getCredential(openstack.getApiBaseUrl());
        if (credential == null) {
            throw new BusinessException("No credential found for " + openstack.getApiBaseUrl());
        } else {
            log.info("using credential {} with username {}", credential.getUuid(), credential.getUsername());
        }
        // Verification of the token
        OffsetDateTime currentDate = OffsetDateTime.now();
        OffsetDateTime expireDate = OffsetDateTime.parse(credential.getTokenExpiry().toString());
        if (currentDate.isAfter(expireDate)) {
            //Dechiffrement du mot de passe
            String stringToDecrypt = credential.getPasswordSecret();
            String salt = PasswordUtils.getSalt(credential.getUuid());
            String decryptedString = PasswordUtils.decrypt(salt, stringToDecrypt);
            log.info(stringToDecrypt + " VS " + decryptedString);

            //Creation du body
            HashMap<String, Object> master = new HashMap<String, Object>();
            HashMap<String, Object> auth = new HashMap<String, Object>();
            HashMap<String, Object> identity = new HashMap<String, Object>();
            HashMap<String, Object> password = new HashMap<String, Object>();
            HashMap<String, Object> user = new HashMap<String, Object>();
            HashMap<String, Object> domain = new HashMap<String, Object>();
            ArrayList <String> method = new ArrayList<String>();
            method.add("password");
            domain.put("id", "default");
            user.put("password", "yjkhNrpjaWaYkGZYbs6z3gmDa5V74R9Z");
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
            response.close();
        }
        // Call every region to list server
        Map<String, String> zones = new HashMap<String, String>();
        zones = openstack.getZone();
        for (String zone : zones.keySet()) {
            Client clientListServers = ClientBuilder.newClient();
            WebTarget targetListServer = clientListServers.target("https://compute." + zone + "." + openstack.getApiBaseUrl() + "/v2.1/servers/detail");
            Response response = targetListServer.request().header("X-Auth-Token", credential.getToken()).get();
            String value = response.readEntity(String.class);
            log.info(String.valueOf(response.getStatus()));
            log.info(value);
            if (response.getStatus() < 300) {
                JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().getAsJsonArray("servers");
                for (JsonElement element : rootArray) {
                    JsonObject serverObj = element.getAsJsonObject();
                    // Create new servers
                    Server server = new Server();
                    //UUID
                    server.setUuid(serverObj.get("id").getAsString());
                    //server name
                    server.setInstanceName(serverObj.get("name").getAsString());
                    //tenant
                    server.setOrganization(serverObj.get("tenant_id").getAsString());
                    //image
                    server.setImage(serverObj.get("image").getAsJsonObject().get("id").getAsString());
                    //Set the creation & updated date
                    server.setCreationDate(OffsetDateTime.parse(serverObj.get("created").getAsString()).toInstant());
                    server.setLastUpdate(OffsetDateTime.parse(serverObj.get("updated").getAsString()).toInstant());
                    //zone
                    server.setZone(zone);
                    //public IP
                    JsonArray publicIpArray = serverObj.get("addresses").getAsJsonObject().get("Ext-Net").getAsJsonArray();
                    for (JsonElement ip : publicIpArray) {
                       JsonObject ipElement = ip.getAsJsonObject();
                       if (ipElement.get("version").getAsInt() == 4) {
                         server.setPublicIp(ipElement.get("addr").getAsString());
                       }
                    }
                    //status
                    server.setStatus(serverObj.get("status").getAsString());
                    //provider
                    server.setProvider(openstack);
                    //flavor
                    server.setServerType(serverObj.get("flavor").getAsJsonObject().get("id").getAsString());
                    //volume
                    JsonArray volumeArray = serverObj.get("os-extended-volumes:volumes_attached").getAsJsonArray();
                    for (JsonElement volume : volumeArray) {
                      JsonObject volumeElement = volume.getAsJsonObject();
                      server.setVolumeSize(volumeElement.get("id").getAsString());
                    }
                    try {
                        crossStorageApi.createOrUpdate(defaultRepo, server);
                    } catch (Exception ex) {
                        log.error("error creating server {} :{}", server.getUuid(), ex.getMessage());
                    }
                }
            }
            response.close();
        }
    }
}
