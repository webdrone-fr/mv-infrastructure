package org.meveo.model.customEntities;

import org.meveo.model.CustomEntity;
import java.util.List;
import org.meveo.model.persistence.DBStorageType;
import org.meveo.model.customEntities.MeveoMatrix;
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

    private MeveoMatrix<String> providerImages;

    private String code;

    private Map<String, String> organization = new HashMap<>();

    private Map<String, String> serverType = new HashMap<>();

    private MeveoMatrix<String> providerServerTypes;

    private String description;

    private List<String> publicIp = new ArrayList<>();

    private List<String> zones = new ArrayList<>();

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

    public MeveoMatrix<String> getProviderImages() {
        return providerImages;
    }

    public void setProviderImages(MeveoMatrix<String> providerImages) {
        this.providerImages = providerImages;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    public MeveoMatrix<String> getProviderServerTypes() {
        return providerServerTypes;
    }

    public void setProviderServerTypes(MeveoMatrix<String> providerServerTypes) {
        this.providerServerTypes = providerServerTypes;
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

    public List<String> getZones() {
        return zones;
    }

    public void setZones(List<String> zones) {
        this.zones = zones;
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
