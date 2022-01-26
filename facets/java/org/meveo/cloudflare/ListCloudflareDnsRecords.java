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
            logger.info("using credential {} with username {}",credential.getUuid(),credential.getUsername());
        }
        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+CLOUDFLARE_URL+"/zones/"+domainName.getUuid()+"/dns_records");
        Response response = CredentialHelperService.setCredential(target.request(), credential).get();
        String value = response.readEntity(String.class);
        ArrayList<String> nonImportedRecords = new ArrayList<String>();
        logger.info("response :", value);
        logger.debug("response status : {}", response.getStatus());
        if (response.getStatus() < 300) {
            JsonArray rootArray = new JsonParser().parse(value).getAsJsonObject().get("result").getAsJsonArray();
            for (JsonElement element : rootArray) {
                JsonObject serverObj = element.getAsJsonObject();
                DnsRecord record = new DnsRecord();
                record.setDomainName(domainName);
                String type = serverObj.get("type").getAsString();
                String name = serverObj.get("name").getAsString();
                if (("A".equals(type) || "CNAME".equals(type)) && name.startsWith("dev-")) { // also has AAAA, see dev-meveo.webdrone.fr
                    record.setRecordType(type);
                    record.setTtl(serverObj.get("ttl").getAsLong());
                    record.setName(name);
                    record.setValue(serverObj.get("content").getAsString());
                    record.setLastSyncDate(Instant.now());
                    record.setUuid(serverObj.get("id").getAsString());
                    record.setProviderSideId(serverObj.get("id").getAsString());
                    record.setProxied(serverObj.get("proxied").getAsBoolean());
                    logger.info("record :{} {} {}", record.getRecordType(),record.getName(),record.getValue());
                    try {
                        crossStorageApi.createOrUpdate(defaultRepo, record);
                    } catch (Exception e) {
                        logger.error("error creating record {} :{}", record.getUuid(), e.getMessage());
                    }
                } else {
                    //TODO notify of non imported records
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