package org.meveo.model.customEntities;

import org.meveo.model.CustomEntity;
import java.util.List;
import org.meveo.model.persistence.DBStorageType;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class ServerAction implements CustomEntity {

    public ServerAction() {
    }

    public ServerAction(String uuid) {
        this.uuid = uuid;
    }

    private String uuid;

    @JsonIgnore()
    private DBStorageType storages;

    private String response;

    private String action;

    private Instant creationDate;

    private Long elapsedTimeMs;

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

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Instant getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Instant creationDate) {
        this.creationDate = creationDate;
    }

    public Long getElapsedTimeMs() {
        return elapsedTimeMs;
    }

    public void setElapsedTimeMs(Long elapsedTimeMs) {
        this.elapsedTimeMs = elapsedTimeMs;
    }

    @Override()
    public String getCetCode() {
        return "ServerAction";
    }
}
