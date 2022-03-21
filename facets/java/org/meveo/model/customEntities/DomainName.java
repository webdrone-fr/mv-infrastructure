package org.meveo.model.customEntities;

import org.meveo.model.CustomEntity;
import java.util.List;
import org.meveo.model.persistence.DBStorageType;
import org.meveo.model.customEntities.ServiceProvider;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class DomainName implements CustomEntity {

    public DomainName() {
    }

    public DomainName(String uuid) {
        this.uuid = uuid;
    }

    private String uuid;

    @JsonIgnore()
    private DBStorageType storages;

    private ServiceProvider registrar;

    private Instant registrationEndDate;

    private Instant lastUpdate;

    private String name;

    private Instant registrationDate;

    private Instant creationDate;

    private String tld;

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

    public ServiceProvider getRegistrar() {
        return registrar;
    }

    public void setRegistrar(ServiceProvider registrar) {
        this.registrar = registrar;
    }

    public Instant getRegistrationEndDate() {
        return registrationEndDate;
    }

    public void setRegistrationEndDate(Instant registrationEndDate) {
        this.registrationEndDate = registrationEndDate;
    }

    public Instant getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(Instant lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(Instant registrationDate) {
        this.registrationDate = registrationDate;
    }

    public Instant getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Instant creationDate) {
        this.creationDate = creationDate;
    }

    public String getTld() {
        return tld;
    }

    public void setTld(String tld) {
        this.tld = tld;
    }

    @Override()
    public String getCetCode() {
        return "DomainName";
    }
}
