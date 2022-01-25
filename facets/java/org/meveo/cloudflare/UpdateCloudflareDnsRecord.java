package org.meveo.cloudflare;

import java.time.Instant;
import java.util.Map;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

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
        String action = (String)parameters.get(CONTEXT_ACTION);
        DnsRecord record = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), DnsRecord.class);
        if (record.getDomainName()==null || record.getRecordType()==null || record.getName()==null
        || record.getName().isEmpty() || record.getValue()==null || record.getValue().isEmpty()) {
            throw new BusinessException("invalid record");
        }
        DomainName domainName = record.getDomainName();
        logger.info("action:{}, domain name uuid:{}", action, domainName.getUuid());
        Credential credential = CredentialHelperService.getCredential(CLOUDFLARE_URL, crossStorageApi, defaultRepo);
        if (credential==null) {
            throw new BusinessException("No credential found for "+CLOUDFLARE_URL);
        } else {
            logger.info("using credential {}({}) with username {}", credential.getDomainName(), credential.getUuid(), credential.getUsername()); //Need to verify username
        }
        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+CLOUDFLARE_URL+"/zones/"+domainName.getUuid()+"/dns_records/"+record.getProviderSideId());

        Map<String, Object> body = Map.of(
            "type", record.getRecordType(), 
            "name", record.getName(), 
            "content", record.getValue(),
            "ttl", String.valueOf(record.getTtl()),
            // Optional setting
            // set default proxied value to true for A and CNAME records?
            // proxied: Whether the record is receiving the performance and security benefits of Cloudflare
            "proxied", record.getProxied());
        String resp = JacksonUtil.toStringPrettyPrinted(body);

        Response response = 
            CredentialHelperService.setCredential(target.request(), credential)
                .header("Content-Type", "application/json")
                .put(Entity.json(resp));
        
        String value = response.readEntity(String.class);
        logger.info("response  :" + value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response:"+value);
        if (response.getStatus()==200) {
            record.setLastSyncDate(Instant.now());
            try {
                crossStorageApi.createOrUpdate(defaultRepo, record);
            } catch (Exception e) {
                logger.error("error updating lastSyncDate record {} :{}", record.getUuid(), e.getMessage());
            }
        }
    }
}
