package org.meveo.model.customEntities;

import org.meveo.model.CustomEntity;
import java.util.List;
import org.meveo.model.persistence.DBStorageType;
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

    private String normedName;

    private Instant registrationEndDate;

    private Instant lastUpdate;

    private String name;

    private Instant registrationDate;

    private Boolean autoRenew;

    private String registar;

    private ServiceProvider registrar;

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

    public String getNormedName() {
        return normedName;
    }

    public void setNormedName(String normedName) {
        this.normedName = normedName;
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

    public Boolean getAutoRenew() {
        return autoRenew;
    }

    public void setAutoRenew(Boolean autoRenew) {
        this.autoRenew = autoRenew;
    }

    public String getRegistar() {
        return registar;
    }

    public void setRegistar(String registar) {
        this.registar = registar;
    }

    public ServiceProvider getRegistrar() {
        return registrar;
    }

    public void setRegistrar(ServiceProvider registrar) {
        this.registrar = registrar;
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
