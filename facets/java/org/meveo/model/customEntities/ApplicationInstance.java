package org.meveo.model.customEntities;

import org.meveo.model.CustomEntity;
import java.util.List;
import org.meveo.model.persistence.DBStorageType;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class ApplicationInstance implements CustomEntity {

    public ApplicationInstance() {
    }

    public ApplicationInstance(String uuid) {
        this.uuid = uuid;
    }

    private String uuid;

    @JsonIgnore()
    private DBStorageType storages;

    private String application;

    private Boolean isStartServiceSource;

    private String repoName;

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

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public Boolean getIsStartServiceSource() {
        return isStartServiceSource;
    }

    public void setIsStartServiceSource(Boolean isStartServiceSource) {
        this.isStartServiceSource = isStartServiceSource;
    }

    public String getRepoName() {
        return repoName;
    }

    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }

    @Override()
    public String getCetCode() {
        return "ApplicationInstance";
    }
}
