package org.meveo.model.customEntities;

import org.meveo.model.CustomEntity;
import java.util.List;
import org.meveo.model.persistence.DBStorageType;
import org.meveo.model.customEntities.Bootscript;
import java.util.ArrayList;
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

    private Boolean enableIPvSix;

    private String bootType;

    private Boolean dynamicIpRequired;

    private String privateIp;

    private Bootscript bootscript;

    private String project;

    private String placementGroup;

    private String arch;

    private String ipVSix;

    private String totalLocalVolumesSize;

    private List<String> maintenances = new ArrayList<>();

    private List<String> privateNics = new ArrayList<>();

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

    public Boolean getEnableIPvSix() {
        return enableIPvSix;
    }

    public void setEnableIPvSix(Boolean enableIPvSix) {
        this.enableIPvSix = enableIPvSix;
    }

    public String getBootType() {
        return bootType;
    }

    public void setBootType(String bootType) {
        this.bootType = bootType;
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

    public Bootscript getBootscript() {
        return bootscript;
    }

    public void setBootscript(Bootscript bootscript) {
        this.bootscript = bootscript;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getPlacementGroup() {
        return placementGroup;
    }

    public void setPlacementGroup(String placementGroup) {
        this.placementGroup = placementGroup;
    }

    public String getArch() {
        return arch;
    }

    public void setArch(String arch) {
        this.arch = arch;
    }

    public String getIpVSix() {
        return ipVSix;
    }

    public void setIpVSix(String ipVSix) {
        this.ipVSix = ipVSix;
    }

    public String getTotalLocalVolumesSize() {
        return totalLocalVolumesSize;
    }

    public void setTotalLocalVolumesSize(String totalLocalVolumesSize) {
        this.totalLocalVolumesSize = totalLocalVolumesSize;
    }

    public List<String> getMaintenances() {
        return maintenances;
    }

    public void setMaintenances(List<String> maintenances) {
        this.maintenances = maintenances;
    }

    public List<String> getPrivateNics() {
        return privateNics;
    }

    public void setPrivateNics(List<String> privateNics) {
        this.privateNics = privateNics;
    }

    @Override()
    public String getCetCode() {
        return "ScalewayServer";
    }
}
