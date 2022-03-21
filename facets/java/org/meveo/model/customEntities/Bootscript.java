package org.meveo.model.customEntities;

import org.meveo.model.CustomEntity;
import java.util.List;
import org.meveo.model.persistence.DBStorageType;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class Bootscript implements CustomEntity {

    public Bootscript() {
    }

    public Bootscript(String uuid) {
        this.uuid = uuid;
    }

    private String uuid;

    @JsonIgnore()
    private DBStorageType storages;

    private Boolean isDefault;

    private String dtb;

    private String bootcmdargs;

    private String zone;

    private String kernel;

    private String organization;

    private Boolean isPublic;

    private String project;

    private String initrd;

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

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public String getDtb() {
        return dtb;
    }

    public void setDtb(String dtb) {
        this.dtb = dtb;
    }

    public String getBootcmdargs() {
        return bootcmdargs;
    }

    public void setBootcmdargs(String bootcmdargs) {
        this.bootcmdargs = bootcmdargs;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public String getKernel() {
        return kernel;
    }

    public void setKernel(String kernel) {
        this.kernel = kernel;
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

    public String getInitrd() {
        return initrd;
    }

    public void setInitrd(String initrd) {
        this.initrd = initrd;
    }

    @Override()
    public String getCetCode() {
        return "Bootscript";
    }
}
