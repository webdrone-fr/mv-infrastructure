package org.meveo.model.customEntities;

import org.meveo.model.CustomEntity;
import java.util.List;
import org.meveo.model.persistence.DBStorageType;
import org.meveo.model.customEntities.Bootscript;
import org.meveo.model.customEntities.ServerVolume;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class ServerImage implements CustomEntity {

    public ServerImage() {
    }

    public ServerImage(String uuid) {
        this.uuid = uuid;
    }

    private String uuid;

    @JsonIgnore()
    private DBStorageType storages;

    private String fromServer;

    private String zone;

    private Bootscript defaultBootscript;

    private String organization;

    private Boolean isPublic;

    private String project;

    private ServerVolume rootVolume;

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

    public String getFromServer() {
        return fromServer;
    }

    public void setFromServer(String fromServer) {
        this.fromServer = fromServer;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public Bootscript getDefaultBootscript() {
        return defaultBootscript;
    }

    public void setDefaultBootscript(Bootscript defaultBootscript) {
        this.defaultBootscript = defaultBootscript;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public Boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public ServerVolume getRootVolume() {
        return rootVolume;
    }

    public void setRootVolume(ServerVolume rootVolume) {
        this.rootVolume = rootVolume;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @Override()
    public String getCetCode() {
        return "ServerImage";
    }
}
