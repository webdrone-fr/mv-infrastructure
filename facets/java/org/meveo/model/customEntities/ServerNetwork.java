package org.meveo.model.customEntities;

import org.meveo.model.CustomEntity;
import java.util.List;
import org.meveo.model.persistence.DBStorageType;
import java.util.ArrayList;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class ServerNetwork implements CustomEntity {

    public ServerNetwork() {
    }

    public ServerNetwork(String uuid) {
        this.uuid = uuid;
    }

    private String uuid;

    @JsonIgnore()
    private DBStorageType storages;

    private List<String> subnet = new ArrayList<>();

    private String name;

    private Instant creationDate;

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

    public List<String> getSubnet() {
        return subnet;
    }

    public void setSubnet(List<String> subnet) {
        this.subnet = subnet;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Instant creationDate) {
        this.creationDate = creationDate;
    }

    @Override()
    public String getCetCode() {
        return "ServerNetwork";
    }
}
