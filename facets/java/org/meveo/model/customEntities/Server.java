package org.meveo.model.customEntities;

import org.meveo.model.CustomEntity;
import java.util.List;
import org.meveo.model.persistence.DBStorageType;
import org.meveo.model.customEntities.ServerImage;
import org.meveo.model.customEntities.SecurityGroup;
import java.util.ArrayList;
import org.meveo.model.customEntities.ServerVolume;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class Server implements CustomEntity {

    public Server() {
    }

    public Server(String uuid) {
        this.uuid = uuid;
    }

    private String uuid;

    @JsonIgnore()
    private DBStorageType storages;

    private ServerImage image;

    private String keyName;

    private String sergentUrl;

    private String publicIp;

    private SecurityGroup securityGroup;

    private String volumeSize;

    private String zone;

    private String domainName;

    private String organization;

    private List<String> serverActions = new ArrayList<>();

    private ServerVolume rootVolume;

    private String backupName;

    private String status;

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

    public ServerImage getImage() {
        return image;
    }

    public void setImage(ServerImage image) {
        this.image = image;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public String getSergentUrl() {
        return sergentUrl;
    }

    public void setSergentUrl(String sergentUrl) {
        this.sergentUrl = sergentUrl;
    }

    public String getPublicIp() {
        return publicIp;
    }

    public void setPublicIp(String publicIp) {
        this.publicIp = publicIp;
    }

    public SecurityGroup getSecurityGroup() {
        return securityGroup;
    }

    public void setSecurityGroup(SecurityGroup securityGroup) {
        this.securityGroup = securityGroup;
    }

    public String getVolumeSize() {
        return volumeSize;
    }

    public void setVolumeSize(String volumeSize) {
        this.volumeSize = volumeSize;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public List<String> getServerActions() {
        return serverActions;
    }

    public void setServerActions(List<String> serverActions) {
        this.serverActions = serverActions;
    }

    public ServerVolume getRootVolume() {
        return rootVolume;
    }

    public void setRootVolume(ServerVolume rootVolume) {
        this.rootVolume = rootVolume;
    }

    public String getBackupName() {
        return backupName;
    }

    public void setBackupName(String backupName) {
        this.backupName = backupName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override()
    public String getCetCode() {
        return "Server";
    }
}
