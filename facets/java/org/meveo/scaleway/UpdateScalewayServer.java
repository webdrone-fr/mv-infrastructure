package org.meveo.scaleway;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import com.google.gson.*;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.ScalewayServer;
import org.meveo.model.customEntities.ServerVolume;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class UpdateScalewayServer extends Script {
    

    
    private static final Logger logger = LoggerFactory.getLogger(UpdateScalewayServer.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private  String SCALEWAY_URL = "api.scaleway.com";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        String action = (String)parameters.get(CONTEXT_ACTION);
        ScalewayServer server =CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), ScalewayServer.class);

        if (server.getZone()==null) { //Required
            throw new BusinessException("Invalid Server Zone");
        } else if(server.getProviderSideId()==null) { //Required
            throw new BusinessException("Invalid Server Provider-side ID");
        }
        //INPUT
        String zone = server.getZone();
        String serverId = server.getProviderSideId();
        logger.info("action : {}, server uuid : {}", action, serverId);
        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("Using Credential {} with username {}", credential.getUuid(), credential.getUsername());
        }
        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+SCALEWAY_URL+"/instance/v1/zones/"+zone+"/servers/"+serverId);

        Map<String, Object> body = Map.of(
            "boot_type", server.getBootType(), // From List of values, includes local, bootscript, rescue -> default is local
            "dynamic_ip_required", server.getDynamicIpRequired(), // nullable, default to false
            "enable_ipv6",server.getEnableIPvSix(), //nullable default to true
            "protected", server.getIsProtected() //nullable default to false
            // "placement_group", //nullable
        );

        //Server Name
        // nullable
        if (server.getInstanceName() != null) {
            body.put("name", server.getInstanceName());
        }

        // Tags
        // currently not used
        ArrayList<String> tags = new ArrayList<String>();
        // if (server.getTags().length() > 1) {
        //     for (String tag : (server.getTags())) {
        //         tags.add(tag);
        //     }
        // }
        body.put("tags", tags);

        // Volumes
        Map<String, Object> volumes = new HashMap<String, Object>();
        // Root Volume
        JsonObject rootVolume = new JsonObject();
        if (server.getRootVolume() != null) {
            rootVolume.addProperty("id", server.getRootVolume().getProviderSideId());
            rootVolume.addProperty("boot", server.getRootVolume().getIsBoot());
            rootVolume.addProperty("name", server.getRootVolume().getName());
            rootVolume.addProperty("size", Long.parseLong(server.getRootVolume().getSize()));
            rootVolume.addProperty("volume_type", server.getRootVolume().getVolumeType()); // need to check = l_ssd OR b_ssd at volume creation
            volumes.put("0", rootVolume);
        }
        // Additional Volumes
        Map<String, ServerVolume> serverAdditionalVolumes = server.getAdditionalVolumes();
        if (serverAdditionalVolumes.size() > 0) {
            for (Map.Entry<String, ServerVolume> serverAdditionalVolume : serverAdditionalVolumes.entrySet()) {
                JsonObject additionalVolume = new JsonObject();
                additionalVolume.addProperty("id", serverAdditionalVolume.getValue().getProviderSideId());
                additionalVolume.addProperty("name", serverAdditionalVolume.getValue().getName());
                additionalVolume.addProperty("size", Long.parseLong(serverAdditionalVolume.getValue().getSize()));
                additionalVolume.addProperty("volume_type", serverAdditionalVolume.getValue().getVolumeType());
                volumes.put(serverAdditionalVolume.getKey(), additionalVolume); // keys should be 1, 2, 3...
            }
        }
        body.put("volumes", volumes);

        // Security Group
        if (server.getSecurityGroup() != null) {
            body.put("security_group", server.getSecurityGroup().getProviderSideId());
        }

        // Private NICs
        // Cannot be null but not currently used
        ArrayList<String> privateNics = new ArrayList<String>();
        if (server.getPrivateNics() != null) {
            for (String privateNic : (server.getPrivateNics())) {
                privateNics.add(privateNic);
            }
        }
        body.put("private_nics", privateNics);

        String resp = JacksonUtil.toStringPrettyPrinted(body);
        Response response = CredentialHelperService.setCredential(target.request("application/json"), credential)
            .method("PATCH", Entity.json(resp)); // To be changed, needs a patch method
        String value = response.readEntity(String.class);
        logger.info("response : " + value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response:"+value);
        if(response.getStatus()>300) {
            server.setLastUpdate(Instant.now());
            try {
                crossStorageApi.createOrUpdate((defaultRepo), server);
            } catch (Exception e) {
                logger.error("error updating record {} :{}", server.getUuid(), e.getMessage());
            }
        }
    }
}
