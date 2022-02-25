package org.meveo.model.customEntities;

import org.meveo.model.CustomEntity;
import java.util.List;
import org.meveo.model.persistence.DBStorageType;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class ServiceProvider implements CustomEntity {

    public ServiceProvider() {
    }

    public ServiceProvider(String uuid) {
        this.uuid = uuid;
    }

    private String uuid;

    @JsonIgnore()
    private DBStorageType storages;

    private String apiBaseUrl;

    private Map<String, String> image = new HashMap<>();

    private String code;

    private Map<String, String> zone = new HashMap<>();

    private Map<String, String> organization = new HashMap<>();

    private Map<String, String> serverType = new HashMap<>();

    private String description;

    private List<String> publicIp = new ArrayList<>();

    private List<String> status = new ArrayList<>();

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

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public Map<String, String> getImage() {
        return image;
    }

    public void setImage(Map<String, String> image) {
        this.image = image;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Map<String, String> getZone() {
        return zone;
    }

    public void setZone(Map<String, String> zone) {
        this.zone = zone;
    }

    public Map<String, String> getOrganization() {
        return organization;
    }

    public void setOrganization(Map<String, String> organization) {
        this.organization = organization;
    }

    public Map<String, String> getServerType() {
        return serverType;
    }

    public void setServerType(Map<String, String> serverType) {
        this.serverType = serverType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getPublicIp() {
        return publicIp;
    }

    public void setPublicIp(List<String> publicIp) {
        this.publicIp = publicIp;
    }

    public List<String> getStatus() {
        return status;
    }

    public void setStatus(List<String> status) {
        this.status = status;
    }

    @Override()
    public String getCetCode() {
        return "ServiceProvider";
    }
}
