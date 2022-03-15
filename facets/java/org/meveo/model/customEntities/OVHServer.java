package org.meveo.model.customEntities;

import org.meveo.model.CustomEntity;
import java.util.List;
import org.meveo.model.persistence.DBStorageType;
import org.meveo.model.customEntities.ServerNetwork;
import java.util.ArrayList;
import org.meveo.model.customEntities.Server;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class OVHServer extends Server implements CustomEntity {

    public OVHServer() {
    }

    public OVHServer(String uuid) {
        this.uuid = uuid;
    }

    private String uuid;

    @JsonIgnore()
    private DBStorageType storages;

    private String keyName;

    private List<ServerNetwork> network = new ArrayList<>();

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

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public List<ServerNetwork> getNetwork() {
        return network;
    }

    public void setNetwork(List<ServerNetwork> network) {
        this.network = network;
    }

    @Override()
    public String getCetCode() {
        return "OVHServer";
    }
}
