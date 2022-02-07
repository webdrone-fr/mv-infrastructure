package org.meveo.model.customEntities;

import org.meveo.model.CustomEntity;
import java.util.List;
import org.meveo.model.persistence.DBStorageType;
import java.util.ArrayList;
import org.meveo.model.customEntities.ServiceProvider;
import org.meveo.model.customEntities.ServerImage;
import java.util.Map;
import org.meveo.model.customEntities.ServerVolume;
import java.util.HashMap;
import org.meveo.model.customEntities.SecurityGroup;
import java.time.Instant;
import org.meveo.model.customEntities.Server;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class ScalewayServer extends Server implements CustomEntity {

    public ScalewayServer() {
    }

    public ScalewayServer(String uuid) {
        this.uuid = uuid;
    }

    private String uuid;

    @JsonIgnore()
    private DBStorageType storages;

    private String providerSideId;

    private String instanceName;

    private Boolean dynamicIpRequired;

    private String privateIp;

    private String sergentUrl;

    private String project;

    private String locationDefinition;

    private String volumeSize;

    private List<String> privateNics = new ArrayList<>();

    private Boolean enableIPvSix;

    private Boolean isProtected;

    private ServiceProvider provider;

    private String zone;

    private List<String> serverActions = new ArrayList<>();

    private String placementGroup;

    private ServerImage image;

    private Map<String, ServerVolume> additionalVolumes = new HashMap<>();

    private String publicIp;

    private SecurityGroup securityGroup;

    private Instant creationDate;

    private String ipVSix;

    private List<String> maintenances = new ArrayList<>();

    private String bootType;

    private String domainName;

    private Instant lastUpdate;

    private String organization;

    private String serverType;

    private ServerVolume rootVolume;

    private String location;

    private String arch;

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

    public String getProviderSideId() {
        return providerSideId;
    }

    public void setProviderSideId(String providerSideId) {
        this.providerSideId = providerSideId;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public Boolean getDynamicIpRequired() {
        return dynamicIpRequired;
    }

    public void setDynamicIpRequired(Boolean dynamicIpRequired) {
        this.dynamicIpRequired = dynamicIpRequired;
    }

    public String getPrivateIp() {
        return privateIp;
    }

    public void setPrivateIp(String privateIp) {
        this.privateIp = privateIp;
    }

    public String getSergentUrl() {
        return sergentUrl;
    }

    public void setSergentUrl(String sergentUrl) {
        this.sergentUrl = sergentUrl;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getLocationDefinition() {
        return locationDefinition;
    }

    public void setLocationDefinition(String locationDefinition) {
        this.locationDefinition = locationDefinition;
    }

    public String getVolumeSize() {
        return volumeSize;
    }

    public void setVolumeSize(String volumeSize) {
        this.volumeSize = volumeSize;
    }

    public List<String> getPrivateNics() {
        return privateNics;
    }

    public void setPrivateNics(List<String> privateNics) {
        this.privateNics = privateNics;
    }

    public Boolean getEnableIPvSix() {
        return enableIPvSix;
    }

    public void setEnableIPvSix(Boolean enableIPvSix) {
        this.enableIPvSix = enableIPvSix;
    }

    public Boolean getIsProtected() {
        return isProtected;
    }

    public void setIsProtected(Boolean isProtected) {
        this.isProtected = isProtected;
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

    public List<String> getServerActions() {
        return serverActions;
    }

    public void setServerActions(List<String> serverActions) {
        this.serverActions = serverActions;
    }

    public String getPlacementGroup() {
        return placementGroup;
    }

    public void setPlacementGroup(String placementGroup) {
        this.placementGroup = placementGroup;
    }

    public ServerImage getImage() {
        return image;
    }

    public void setImage(ServerImage image) {
        this.image = image;
    }

    public Map<String, ServerVolume> getAdditionalVolumes() {
        return additionalVolumes;
    }

    public void setAdditionalVolumes(Map<String, ServerVolume> additionalVolumes) {
        this.additionalVolumes = additionalVolumes;
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

    public String getIpVSix() {
        return ipVSix;
    }

    public void setIpVSix(String ipVSix) {
        this.ipVSix = ipVSix;
    }

    public List<String> getMaintenances() {
        return maintenances;
    }

    public void setMaintenances(List<String> maintenances) {
        this.maintenances = maintenances;
    }

    public String getBootType() {
        return bootType;
    }

    public void setBootType(String bootType) {
        this.bootType = bootType;
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

    public String getServerType() {
        return serverType;
    }

    public void setServerType(String serverType) {
        this.serverType = serverType;
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

    public String getArch() {
        return arch;
    }

    public void setArch(String arch) {
        this.arch = arch;
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
        return "ScalewayServer";
    }
}
