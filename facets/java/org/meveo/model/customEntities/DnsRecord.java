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

    @Override()
    public String getCetCode() {
        return "DnsRecord";
    }
}
