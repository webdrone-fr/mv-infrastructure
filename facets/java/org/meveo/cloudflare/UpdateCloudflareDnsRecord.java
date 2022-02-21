package org.meveo.cloudflare;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import com.google.gson.*;

import org.apache.commons.validator.routines.InetAddressValidator;
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

public class UpdateCloudflareDnsRecord extends Script {
    

    private static final Logger logger = LoggerFactory.getLogger(UpdateCloudflareDnsRecord.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private String CLOUDFLARE_URL = "api.cloudflare.com/client/v4";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        String action = parameters.get(CONTEXT_ACTION).toString();
        DnsRecord record = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), DnsRecord.class);
        InetAddressValidator ipValidator = InetAddressValidator.getInstance();

        if (record.getDomainName()==null) {
            throw new BusinessException("Invalid Record Domain");
        } else if (record.getRecordType()==null) {
            throw new BusinessException("Invalid Record Type");
        } else if (record.getName()==null || record.getName().isEmpty()) {
            throw new BusinessException("Invalid Record Name");
        } else if (record.getValue()==null || record.getValue().isEmpty()) {
            throw new BusinessException("Invalid Record Value");
        } else if (!ipValidator.isValidInet4Address(record.getValue())) {
            throw new BusinessException("Invalid Record IP provided");
        }

        DomainName domainName = record.getDomainName();
        String domainNameId = domainName.getUuid();
        String recordId = record.getProviderSideId();
        logger.info("action:{}, domain name uuid:{}", action, domainNameId);

        Credential credential = CredentialHelperService.getCredential(CLOUDFLARE_URL, crossStorageApi, defaultRepo);
        if (credential==null) {
            throw new BusinessException("No credential found for "+CLOUDFLARE_URL);
        } else {
            logger.info("Using Credential {} with username {}", credential.getDomainName(), credential.getUsername());
        }
        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+CLOUDFLARE_URL+"/zones/"+domainNameId+"/dns_records/"+recordId);

        Map<String, Object> body = new HashMap<String, Object>();
        body.put("type", record.getRecordType());
        body.put("name", record.getName());
        body.put("content", record.getValue());
        body.put("ttl", String.valueOf(record.getTtl()));
        body.put("proxied", record.getProxied()); // default false
        
        String resp = JacksonUtil.toStringPrettyPrinted(body);
        Response response = 
            CredentialHelperService.setCredential(target.request("application/json"), credential)
                .put(Entity.json(resp));
        String value = response.readEntity(String.class);
        logger.info("response  :" + value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response:"+value);

        if (response.getStatus()<300) {
            JsonObject recordObj = new JsonParser().parse(value).getAsJsonObject().get("result").getAsJsonObject();

            record.setCreationDate(OffsetDateTime.parse(recordObj.get("created_on").getAsString()).toInstant());
            record.setLastSyncDate(OffsetDateTime.parse(recordObj.get("modified_on").getAsString()).toInstant());
            record.setName(recordObj.get("name").getAsString());
            record.setRecordType(recordObj.get("type").getAsString());
            record.setValue(recordObj.get("content").getAsString());
            record.setTtl(recordObj.get("ttl").getAsLong());
            record.setProxiable(recordObj.get("proxiable").getAsBoolean());
            record.setIsLocked(recordObj.get("locked").getAsBoolean());

            try {
                crossStorageApi.createOrUpdate(defaultRepo, record);
                logger.info("Record : {} updated successfully", recordId);
            } catch (Exception e) {
                logger.error("error updating record {} :{}", record.getUuid(), e.getMessage());
            }
        }
    }
}
