package org.meveo.model.customEntities;

import org.meveo.model.CustomEntity;
import java.util.List;
import org.meveo.model.persistence.DBStorageType;
import java.time.Instant;
import java.util.ArrayList;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class SecurityGroup implements CustomEntity {

    public SecurityGroup() {
    }

    public SecurityGroup(String uuid) {
        this.uuid = uuid;
    }

    private String uuid;

    @JsonIgnore()
    private DBStorageType storages;

    private Boolean enableDefaultSecurity;

    private String description;

    private String project;

    private Instant creationDate;

    private List<String> tags = new ArrayList<>();

    private List<String> servers = new ArrayList<>();

    private String zone;

    private String inboundDefaultPolicy;

    private String outboundDefaultPolicy;

    private Boolean projectDefault;

    private String name;

    private String state;

    private Boolean stateful;

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

    public Boolean getEnableDefaultSecurity() {
        return enableDefaultSecurity;
    }

    public void setEnableDefaultSecurity(Boolean enableDefaultSecurity) {
        this.enableDefaultSecurity = enableDefaultSecurity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public List<String> getServers() {
        return servers;
    }

    public void setServers(List<String> servers) {
        this.servers = servers;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public String getInboundDefaultPolicy() {
        return inboundDefaultPolicy;
    }

    public void setInboundDefaultPolicy(String inboundDefaultPolicy) {
        this.inboundDefaultPolicy = inboundDefaultPolicy;
    }

    public String getOutboundDefaultPolicy() {
        return outboundDefaultPolicy;
    }

    public void setOutboundDefaultPolicy(String outboundDefaultPolicy) {
        this.outboundDefaultPolicy = outboundDefaultPolicy;
    }

    public Boolean getProjectDefault() {
        return projectDefault;
    }

    public void setProjectDefault(Boolean projectDefault) {
        this.projectDefault = projectDefault;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Boolean getStateful() {
        return stateful;
    }

    public void setStateful(Boolean stateful) {
        this.stateful = stateful;
    }

    @Override()
    public String getCetCode() {
        return "SecurityGroup";
    }
}
