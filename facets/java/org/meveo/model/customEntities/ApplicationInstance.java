package org.meveo.model.customEntities;

import org.meveo.model.CustomEntity;
import java.util.List;
import org.meveo.model.persistence.DBStorageType;
import java.util.ArrayList;
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

    private List<String> investigationTypes = new ArrayList<>();

    private String application;

    private Boolean isStartServiceSource;

    private Long port;

    private String urlDomain;

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

    public List<String> getInvestigationTypes() {
        return investigationTypes;
    }

    public void setInvestigationTypes(List<String> investigationTypes) {
        this.investigationTypes = investigationTypes;
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

    public Long getPort() {
        return port;
    }

    public void setPort(Long port) {
        this.port = port;
    }

    public String getUrlDomain() {
        return urlDomain;
    }

    public void setUrlDomain(String urlDomain) {
        this.urlDomain = urlDomain;
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
