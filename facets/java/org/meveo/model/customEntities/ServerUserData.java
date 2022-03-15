package org.meveo.model.customEntities;

import org.meveo.model.CustomEntity;
import java.util.List;
import org.meveo.model.persistence.DBStorageType;
import org.meveo.model.customEntities.Server;
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

    private Server server;

    private String zone;

    private String name;

    private String contentType;

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

    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
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
