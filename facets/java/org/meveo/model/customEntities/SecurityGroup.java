package org.meveo.model.customEntities;

import org.meveo.model.CustomEntity;
import java.util.List;
import org.meveo.model.persistence.DBStorageType;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class SecurityGroup implements CustomEntity {

    public SecurityGroup() {
    }

    public SecurityGroup(String uuid) {
        this.uuid = uuid;
    }

    private String uuid;

    @JsonIgnore()
    private DBStorageType storages;

    private Boolean projectDefault;

    private String state;

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

    public Boolean getProjectDefault() {
        return projectDefault;
    }

    public void setProjectDefault(Boolean projectDefault) {
        this.projectDefault = projectDefault;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @Override()
    public String getCetCode() {
        return "SecurityGroup";
    }
}
