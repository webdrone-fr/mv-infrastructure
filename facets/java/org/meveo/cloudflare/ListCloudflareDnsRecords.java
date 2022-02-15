package org.meveo.cloudflare;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.*;

import com.google.gson.*;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.DnsRecord;
import org.meveo.model.customEntities.DomainName;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListCloudflareDnsRecords extends Script {
    

    private static final Logger logger = LoggerFactory.getLogger(ListCloudflareDnsRecords.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private String CLOUDFLARE_URL = "api.cloudflare.com/client/v4";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        DomainName domainName = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), DomainName.class);

        Credential credential = CredentialHelperService.getCredential(CLOUDFLARE_URL, crossStorageApi, defaultRepo);
        if (credential==null) {
            throw new BusinessException("No credential found for "+CLOUDFLARE_URL);
        } else {
            logger.info("using credential {} with username {}",credential.getUuid(), credential.getUsername());
        }
        
        String domainNameId = domainName.getUuid();
        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+CLOUDFLARE_URL+"/zones/"+domainNameId+"/dns_records");
        Response response = CredentialHelperService.setCredential(target.request(), credential).get();
        String value = response.readEntity(String.class);
        logger.info("response :", value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response: "+value);
        
        ArrayList<String> nonImportedRecords = new ArrayList<String>();
        if (response.getStatus() < 300) {
            JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().get("result").getAsJsonArray();
            for (JsonElement element : rootArray) {
                JsonObject recordObj = element.getAsJsonObject();
                DnsRecord record = new DnsRecord();
                record.setDomainName(domainName);
                String type = recordObj.get("type").getAsString();
                String name = recordObj.get("name").getAsString();
                if (("A".equals(type) || "CNAME".equals(type)) && name.startsWith("dev-")) { // also has AAAA, see dev-meveo.webdrone.fr
                    record.setRecordType(type);
                    record.setTtl(recordObj.get("ttl").getAsLong());
                    record.setName(name);
                    record.setValue(recordObj.get("content").getAsString());
                    record.setLastSyncDate(Instant.now());
                    record.setUuid(recordObj.get("id").getAsString());
                    record.setProviderSideId(recordObj.get("id").getAsString());
                    record.setProxied(recordObj.get("proxied").getAsBoolean());
                    record.setIsLocked(recordObj.get("locked").getAsBoolean());
                    record.setProxiable(recordObj.get("proxiable").getAsBoolean());

                    try {
                        crossStorageApi.createOrUpdate(defaultRepo, record);
                        logger.info("record : {} with address {} successfully retrieved", record.getRecordType(), record.getValue());
                    } catch (Exception e) {
                        logger.error("error retrieving record {} : {}", record.getProviderSideId(), e.getMessage());
                    }
                } else {
                    if (name.startsWith("dev-")) {
                        nonImportedRecords.add(name + ": "+ type);
                    }
                }
            }
            // parameters.put(RESULT_GUI_MESSAGE, "Total Non-imported Records: " + nonImportedRecords.size());
            parameters.put(RESULT_GUI_MESSAGE, "Non-imported Records: " + JacksonUtil.toStringPrettyPrinted(nonImportedRecords));
        }
    }
}