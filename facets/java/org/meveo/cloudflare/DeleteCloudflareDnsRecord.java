package org.meveo.cloudflare;

import java.time.Instant;
import java.util.Map;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;

import org.meveo.admin.exception.BusinessException;
import org.meveo.api.persistence.CrossStorageApi;
import org.meveo.credentials.CredentialHelperService;
import org.meveo.model.customEntities.Credential;
import org.meveo.model.customEntities.DnsRecord;
import org.meveo.model.customEntities.DomainName;
import org.meveo.model.persistence.CEIUtils;
import org.meveo.model.storage.Repository;
import org.meveo.service.script.Script;
import org.meveo.service.storage.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteCloudflareDnsRecord extends Script {
    
    private static final Logger logger = LoggerFactory.getLogger(DeleteCloudflareDnsRecord.class);
    private CrossStorageApi crossStorageApi = getCDIBean(CrossStorageApi.class);
    private RepositoryService repositoryService = getCDIBean(RepositoryService.class);
    private Repository defaultRepo = repositoryService.findDefaultRepository();

    static final private String CLOUDFLARE_URL = "api.cloudflare.com/client/v4";

    @Override
    public void execute(Map<String, Object> parameters) throws BusinessException {
        String action = parameters.get(CONTEXT_ACTION).toString();
        DnsRecord record = CEIUtils.ceiToPojo((org.meveo.model.customEntities.CustomEntityInstance)parameters.get(CONTEXT_ENTITY), DnsRecord.class);

        if (record.getProviderSideId()==null) { // Required
            throw new BusinessException("Invalid Record Provider-side ID");
        }

        DomainName domainName = record.getDomainName();
        String domainNameId = domainName.getUuid();
        logger.info("action : {}, domain name uuid : {}", action, domainNameId);
        Credential credential = CredentialHelperService.getCredential(CLOUDFLARE_URL, crossStorageApi, defaultRepo);
        if (credential==null) {
            throw new BusinessException("No credential found for "+CLOUDFLARE_URL);
        } else {
            logger.info("using credential {} with username {}",credential.getUuid(), credential.getUsername()); //Need to verify username
        }

        Client client = ClientBuilder.newClient();
        client.register(new CredentialHelperService.LoggingFilter());
        WebTarget target = client.target("https://"+CLOUDFLARE_URL+"/zones/"+domainNameId+"/dns_records/"+record.getProviderSideId());
        Response response = CredentialHelperService.setCredential(target.request(), credential).delete();
        String value = response.readEntity(String.class);
        logger.info("response : {}", value);
        logger.debug("response status : {}", response.getStatus());
        parameters.put(RESULT_GUI_MESSAGE, "Status: "+response.getStatus()+", response:"+value);
        
        if (response.getStatus()<300) {
            logger.info("record : {} deleted at: {}", record.getUuid(), Instant.now());
            try {
                crossStorageApi.remove(defaultRepo, record.getUuid(), record.getCetCode());
            } catch (Exception e) {
                logger.error("error deleting record : {}", record.getUuid(), e.getMessage());
            }
        }
    }
}
