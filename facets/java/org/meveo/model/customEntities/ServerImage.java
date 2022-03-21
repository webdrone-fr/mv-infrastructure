package org.meveo.model.customEntities;

import org.meveo.model.CustomEntity;
import java.util.List;
import org.meveo.model.persistence.DBStorageType;
import java.util.Map;
import org.meveo.model.customEntities.ServerVolume;
import java.util.HashMap;
import java.time.Instant;
import java.util.ArrayList;
import org.meveo.model.customEntities.Bootscript;
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

    private Map<String, ServerVolume> additionalVolumes = new HashMap<>();

    private String project;

    private Instant creationDate;

    private List<String> tags = new ArrayList<>();

    private String zone;

    private Bootscript defaultBootscript;

    private String organization;

    private String name;

    private Boolean isPublic;

    private ServerVolume rootVolume;

    private String arch;

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

    public Map<String, ServerVolume> getAdditionalVolumes() {
        return additionalVolumes;
    }

    public void setAdditionalVolumes(Map<String, ServerVolume> additionalVolumes) {
        this.additionalVolumes = additionalVolumes;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public Instant getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Instant creationDate) {
        this.creationDate = creationDate;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }

    public ServerVolume getRootVolume() {
        return rootVolume;
    }

    public void setRootVolume(ServerVolume rootVolume) {
        this.rootVolume = rootVolume;
    }

    public String getArch() {
        return arch;
    }

    public void setArch(String arch) {
        this.arch = arch;
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
