package org.meveo.scaleway;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.ws.rs.client.*;

import com.google.gson.*;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.MeveoMatrix;
import org.meveo.model.customEntities.PublicIp;
import org.meveo.model.customEntities.ServerImage;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PopulateProviderFields extends Script {

    private static final Logger logger = LoggerFactory.getLogger(PopulateProviderFields.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();
    
    static final private String SCALEWAY_URL = "api.scaleway.com";
    static final private String BASE_PATH = "/instance/v1/zones/";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        ServiceProvider provider = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), ServiceProvider.class);

        Credential credential = CredentialHelperService.getCredential(SCALEWAY_URL, crossStorageApi, defaultRepo);
        if (credential == null) {
            throw new BusinessException("No credential found for "+SCALEWAY_URL);
        } else {
            logger.info("Using Credential {} with username {}", credential.getUuid(), credential.getUsername());
        }

        String[] zones = new String[] {"fr-par-1", "fr-par-2", "fr-par-3", "nl-ams-1", "pl-waw-1"};
        
        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());

        List<String> providerZones = new ArrayList<String>();
        Map<String, String> providerOrganizations = new HashMap<String, String>();
        Map<String, String> serverTypes = new HashMap<String, String>();
        MeveoMatrix<String> serverTypesMatrix = new MeveoMatrix<String>();
        MeveoMatrix<String> imagesMatrix = new MeveoMatrix<String>();
        
        List<String> providerSideImageIds = new ArrayList<String>();
        List<String> providerSidePublicIpIds = new ArrayList<String>();
        List<String> publicIpRecords = new ArrayList<String>();
        for(String zone : zones) {
            providerZones.add(zone);
            // Project/ Organization
            String organizationName = "Webdrone";
            String organizationId = "6a0c2ca8-917a-418a-90a3-05949b55a7ae";
            providerOrganizations.put(organizationName, organizationId);
            
            // Server Types
            JsonObject serverTypesObj = ScalewayHelperService.getProviderServerTypes(zone, provider, credential);
            Set<Map.Entry<String, JsonElement>> entries = serverTypesObj.entrySet();
            for(Map.Entry<String, JsonElement> entry : entries) {
                JsonObject serverTypeObj =  entry.getValue().getAsJsonObject();
                Map<String, Object> serverType = ScalewaySetters.setServerType(serverTypeObj);
                serverTypes.put(entry.getKey(), JacksonUtil.toStringPrettyPrinted(serverType));

                String ram = serverType.get("ram").toString();
                String disk = serverType.get("volumes_constraint").toString();
                String ncpus = serverType.get("ncpus").toString();
                String name = entry.getKey();
                serverTypesMatrix.set(zone, ram, disk, ncpus, name, JacksonUtil.toStringPrettyPrinted(serverType));
            }
            
            // Images
            JsonArray imagesArr = ScalewayHelperService.getProviderImages(zone, provider, credential);
            for (JsonElement imageEl : imagesArr) {
                JsonObject imageObj = imageEl.getAsJsonObject();
                String imageId = imageObj.get("id").getAsString();
                providerSideImageIds.add(imageId);
                ServerImage image = null;
                try {
                    if(crossStorageApi.find(defaultRepo, ServerImage.class).by("providerSideId", imageId).getResult()!=null)  {
                        image = crossStorageApi.find(defaultRepo, ServerImage.class).by("providerSideId", imageId).getResult();
                    } else {
                        image = new ServerImage();
                        image.setUuid(imageId);
                    }
                    image = ScalewaySetters.setServerImage(imageObj, image, crossStorageApi, defaultRepo);
                    crossStorageApi.createOrUpdate(defaultRepo, image);
                } catch (Exception e) {
                    logger.error("ERROR WITH IMAGE CREATION", e);
                    logger.error("Error creating image : {}", imageId, e.getMessage());
                }
                String imageName = imageObj.get("name").getAsString();
                Instant instant = image.getCreationDate();
                OffsetDateTime odt = instant.atOffset(ZoneOffset.UTC);
                String creationDate = odt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace("T", " ");
                imagesMatrix.set(zone, imageName, creationDate, imageId);
            }
            
            // Public Ips
            JsonArray ipsArr = ScalewayHelperService.getProviderPublicIps(zone, provider, credential);
            for (JsonElement ipEl : ipsArr) {
                JsonObject publicIpObj = ipEl.getAsJsonObject();
                String publicIpId = publicIpObj.get("id").getAsString();
                providerSidePublicIpIds.add(publicIpId);
                PublicIp publicIp = null;
                try {
                    if (crossStorageApi.find(defaultRepo, PublicIp.class).by("providerSideId", publicIpId).getResult()!=null) {
                        publicIp = crossStorageApi.find(defaultRepo, PublicIp.class).by("providerSideId", publicIpId).getResult();
                    } else {
                        publicIp = new PublicIp();
                        publicIp.setUuid(publicIpId);
                    }
                    publicIp = ScalewaySetters.setPublicIp(publicIpObj, publicIp, provider, crossStorageApi, defaultRepo);
                    crossStorageApi.createOrUpdate(defaultRepo, publicIp);

                    Map<String, String> publicIpRecord = new HashMap<String, String>();
                    String address = publicIpObj.get("address").getAsString();
                    String serverName = null;
                    if (!publicIpObj.get("server").isJsonNull()) {
                        serverName = publicIpObj.get("server").getAsJsonObject().get("name").getAsString();
                    }
                    publicIpRecord.put(address, serverName);
                    publicIpRecords.add(String.valueOf(publicIpRecord));
                } catch (Exception e) {
                    logger.error("Error retrieving public ip : {}", publicIpId, e.getMessage());
                }
            }
        }

        // Sort Provider Server Types
        Map<String, String> sortedServerTypes = new TreeMap<String, String>(serverTypes);
        // logger.info("sorted server types: {}", sortedServerTypes);

        // clear values that are not present on provider side
        // server images
        ScalewayHelperService.filterToLatestValues("ServerImage", providerSideImageIds, crossStorageApi, defaultRepo);
        // public Ips
        // ScalewayHelperService.filterToLatestValues("PublicIp", providerSidePublicIpIds, crossStorageApi, defaultRepo);

        provider.setZones(providerZones);
        provider.setOrganization(providerOrganizations);
        provider.setServerType(sortedServerTypes);
        provider.setProviderServerTypes(serverTypesMatrix);
        provider.setProviderImages(imagesMatrix);
        logger.info("query matrix : {}", imagesMatrix.getClosestMatch("fr-par-3", "ArchLinux"));
        // logger.info("Images Matrix : {}", JacksonUtil.toStringPrettyPrinted(imagesMatrix));
        provider.setPublicIp(publicIpRecords);
        try {
            crossStorageApi.createOrUpdate(defaultRepo, provider);
        } catch (Exception e) {
            logger.error("Error retrieving provider details", e.getMessage());
        }
    }
}