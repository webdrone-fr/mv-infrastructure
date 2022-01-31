package org.meveo.model.customEntities;

import org.meveo.model.CustomEntity;
import java.util.List;
import org.meveo.model.persistence.DBStorageType;
import org.meveo.model.customEntities.DomainName;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class DnsRecord implements CustomEntity {

    public DnsRecord() {
    }

    public DnsRecord(String uuid) {
        this.uuid = uuid;
    }

    private String uuid;

    @JsonIgnore()
    private DBStorageType storages;

    private String recordType;

    private DomainName domainName;

    private String name;

    private Instant lastSyncDate;

    private Long ttl;

    private String value;

    private String providerSideId;

    private Boolean proxied;

    @Override()
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public DBStorageType getStorages() {
        return storages;
    }

    public void setStorages(DBStorageType storages) {
        this.storages = storages;
    }

    public String getRecordType() {
        return recordType;
    }

    public void setRecordType(String recordType) {
        this.recordType = recordType;
    }

    public DomainName getDomainName() {
        return domainName;
    }

    public void setDomainName(DomainName domainName) {
        this.domainName = domainName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getLastSyncDate() {
        return lastSyncDate;
    }

    public void setLastSyncDate(Instant lastSyncDate) {
        this.lastSyncDate = lastSyncDate;
    }

    public Long getTtl() {
        return ttl;
    }

    public void setTtl(Long ttl) {
        this.ttl = ttl;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getProviderSideId() {
        return providerSideId;
    }

    public void setProviderSideId(String providerSideId) {
        this.providerSideId = providerSideId;
    }

    public Boolean getProxied() {
        return proxied;
    }

    public void setProxied(Boolean proxied) {
        this.proxied = proxied;
    }

    @Override()
    public String getCetCode() {
        return "DnsRecord";
    }
}
