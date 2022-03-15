package org.meveo.model.customEntities;

import org.meveo.model.CustomEntity;
import java.util.List;
import org.meveo.model.persistence.DBStorageType;
import org.meveo.model.customEntities.ServerImage;
import java.util.Map;
import org.meveo.model.customEntities.ServerVolume;
import java.util.HashMap;
import org.meveo.model.customEntities.SecurityGroup;
import java.time.Instant;
import org.meveo.model.customEntities.ServiceProvider;
import java.util.ArrayList;
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

    private String providerSideId;

    private Map<String, ServerVolume> additionalVolumes = new HashMap<>();

    private String instanceName;

    private String sergentUrl;

    private String locationDefinition;

    private String publicIp;

    private SecurityGroup securityGroup;

    private Instant creationDate;

    private String volumeSize;

    private ServiceProvider provider;

    private String zone;

    private String domainName;

    private Instant lastUpdate;

    private String organization;

    private List<String> serverActions = new ArrayList<>();

    private ServerVolume rootVolume;

    private String location;

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

    public String getProviderSideId() {
        return providerSideId;
    }

    public void setProviderSideId(String providerSideId) {
        this.providerSideId = providerSideId;
    }

    public Map<String, ServerVolume> getAdditionalVolumes() {
        return additionalVolumes;
    }

    public void setAdditionalVolumes(Map<String, ServerVolume> additionalVolumes) {
        this.additionalVolumes = additionalVolumes;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public String getSergentUrl() {
        return sergentUrl;
    }

    public void setSergentUrl(String sergentUrl) {
        this.sergentUrl = sergentUrl;
    }

    public String getLocationDefinition() {
        return locationDefinition;
    }

    public void setLocationDefinition(String locationDefinition) {
        this.locationDefinition = locationDefinition;
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

    public Instant getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Instant creationDate) {
        this.creationDate = creationDate;
    }

    public String getVolumeSize() {
        return volumeSize;
    }

    public void setVolumeSize(String volumeSize) {
        this.volumeSize = volumeSize;
    }

    public ServiceProvider getProvider() {
        return provider;
    }

    public void setProvider(ServiceProvider provider) {
        this.provider = provider;
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

    public Instant getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(Instant lastUpdate) {
        this.lastUpdate = lastUpdate;
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

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
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
