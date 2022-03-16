package org.meveo.model.customEntities;

import org.meveo.model.CustomEntity;
import java.util.List;
import org.meveo.model.persistence.DBStorageType;
import java.util.ArrayList;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class LockdownRule implements CustomEntity {

    public LockdownRule() {
    }

    public LockdownRule(String uuid) {
        this.uuid = uuid;
    }

    private String uuid;

    @JsonIgnore()
    private DBStorageType storages;

    private List<String> ipRanges = new ArrayList<>();

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

    public List<String> getIpRanges() {
        return ipRanges;
    }

    public void setIpRanges(List<String> ipRanges) {
        this.ipRanges = ipRanges;
    }

    @Override()
    public String getCetCode() {
        return "LockdownRule";
    }
}
