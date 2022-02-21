package org.meveo.cloudflare;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
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
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.persistence.JacksonUtil;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateCloudflareDnsRecord extends Script {
    

    private static final Logger logger = LoggerFactory.getLogger(CreateCloudflareDnsRecord.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private String CLOUDFLARE_URL = "api.cloudflare.com/client/v4";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        DnsRecord record;
        String domainNameId = "webdrone.fr";

        if (parameters.get(CONTEXT_ACTION) != null) {
        
            String action = parameters.get(CONTEXT_ACTION).toString();
            record = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), DnsRecord.class);
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
            domainNameId = domainName.getUuid();
            logger.info("action : {}, domain name uuid : {}", action, domainNameId);

        } else {
            record = new DnsRecord();
            try {
                String providerId = parameters.get("providerId").toString();
                ServiceProvider provider = crossStorageApi.find(defaultRepo, providerId, ServiceProvider.class);
                List<DomainName> providerDomainNames = crossStorageApi.find(defaultRepo, DomainName.class).by("registrar", provider).getResults();
                for (DomainName domainName : providerDomainNames) {
                    if(domainNameId.equals(domainName.getName())) {
                        record.setDomainName(domainName);
                    }
                }
            } catch(Exception e) {
                logger.error("Error retrieving domain name", e.getMessage());
            }
            
            // try {
            //     DomainName domainName = crossStorageApi.find(defaultRepo, DomainName.class).by("name", domainNameId).getResult();
            //     record.setDomainName(domainName);
            // } catch (Exception e){
            //     logger.error("Error retrieving domain name", e.getMessage());
            // }
            record.setRecordType(parameters.get("recordType").toString());
            record.setName(parameters.get("name").toString());
            record.setValue(parameters.get("value").toString());
            record.setTtl(Long.valueOf(parameters.get("ttl").toString()));
            if(parameters.get("priority") != null) { // optional
                record.setPriority(Long.valueOf(parameters.get("priority").toString()));
            }
            if(parameters.get("proxied")!= null) { // optional
                record.setProxied(Boolean.valueOf(parameters.get("isProxied").toString()));
            }
        }

        Credential credential = CredentialHelperService.getCredential(CLOUDFLARE_URL, crossStorageApi, defaultRepo);
        if (credential==null) {
            throw new BusinessException("No credential found for "+CLOUDFLARE_URL);
        } else {
            logger.info("Using Credential {} with username {}", credential.getDomainName(), credential.getUsername());
        }

        Map<String, Object> body = new HashMap<String, Object>();

        body.put("type", record.getRecordType());
        body.put("name", record.getName());
        body.put("content", record.getValue());
        body.put("ttl", String.valueOf(record.getTtl()));
        body.put("proxied", record.getProxied()); // default false

        if(record.getPriority()!=null){
            body.put("priority", record.getPriority());
        }

        WebTarget target = client.target("https://"+CLOUDFLARE_URL+"/zones/"+domainNameId+"/dns_records");
        String resp = JacksonUtil.toStringPrettyPrinted(body);
        Response response = 
            CredentialHelperService.setCredential(target.request("application/json"), credential)
                .post(Entity.json(resp));
        String value = response.readEntity(String.class);
        logger.info("response  :" + value);
        logger.debug("response status : {}", response.getStatus());

        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response: "+value);
        if (response.getStatus()<300) {
            JsonObject recordObj = new JsonParser().parse(value).getAsJsonObject().get("result").getAsJsonObject();
            record.setCreationDate(OffsetDateTime.parse(recordObj.get("created_on").getAsString()).toInstant());
            record.setLastSyncDate(OffsetDateTime.parse(recordObj.get("modified_on").getAsString()).toInstant());
            record.setProviderSideId(recordObj.get("id").getAsString());
            record.setRecordType(recordObj.get("type").getAsString());
            record.setName(recordObj.get("name").getAsString());
            record.setValue(recordObj.get("content").getAsString());
            record.setProxiable(recordObj.get("proxiable").getAsBoolean());
            record.setIsLocked(recordObj.get("locked").getAsBoolean());

            try {
                crossStorageApi.createOrUpdate(defaultRepo, record);
                logger.info("Record : {} successfully created", record.getProviderSideId());
            } catch (Exception e) {
                logger.error("error creating record {} :{}", record.getUuid(), e.getMessage());
            }
        }
    }
}