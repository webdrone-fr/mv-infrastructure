package org.meveo.model.customEntities;

import org.meveo.model.CustomEntity;
import java.util.List;
import org.meveo.model.persistence.DBStorageType;
import org.meveo.model.customEntities.ServerNetwork;
import org.meveo.model.customEntities.Server;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class ServerOVH extends Server implements CustomEntity {

    public ServerOVH() {
    }

    public ServerOVH(String uuid) {
        this.uuid = uuid;
    }

    private String uuid;

    @JsonIgnore()
    private DBStorageType storages;

    private ServerNetwork network;

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

    public ServerNetwork getNetwork() {
        return network;
    }

    public void setNetwork(ServerNetwork network) {
        this.network = network;
    }

    @Override()
    public String getCetCode() {
        return "ServerOVH";
    }
}
