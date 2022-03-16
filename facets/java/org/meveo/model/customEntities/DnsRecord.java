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

    private Boolean isLocked;

    private String name;

    private Boolean proxied;

    private Instant creationDate;

    private Boolean proxiable;

    private String value;

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

    public Boolean getIsLocked() {
        return isLocked;
    }

    public void setIsLocked(Boolean isLocked) {
        this.isLocked = isLocked;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getProxied() {
        return proxied;
    }

    public void setProxied(Boolean proxied) {
        this.proxied = proxied;
    }

    public Instant getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Instant creationDate) {
        this.creationDate = creationDate;
    }

    public Boolean getProxiable() {
        return proxiable;
    }

    public void setProxiable(Boolean proxiable) {
        this.proxiable = proxiable;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override()
    public String getCetCode() {
        return "DnsRecord";
    }
}
