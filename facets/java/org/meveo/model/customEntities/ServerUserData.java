package org.meveo.model.customEntities;

import org.meveo.model.CustomEntity;
import java.util.List;
import org.meveo.model.persistence.DBStorageType;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class ServerUserData implements CustomEntity {

    public ServerUserData() {
    }

    public ServerUserData(String uuid) {
        this.uuid = uuid;
    }

    private String uuid;

    @JsonIgnore()
    private DBStorageType storages;

    private String content;

    private String serverSideKey;

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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getServerSideKey() {
        return serverSideKey;
    }

    public void setServerSideKey(String serverSideKey) {
        this.serverSideKey = serverSideKey;
    }

    @Override()
    public String getCetCode() {
        return "ServerUserData";
    }
}
