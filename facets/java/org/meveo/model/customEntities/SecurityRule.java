package org.meveo.model.customEntities;

import org.meveo.model.CustomEntity;
import java.util.List;
import org.meveo.model.persistence.DBStorageType;
import org.meveo.model.customEntities.SecurityGroup;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class SecurityRule implements CustomEntity {

    public SecurityRule() {
    }

    public SecurityRule(String uuid) {
        this.uuid = uuid;
    }

    private String uuid;

    @JsonIgnore()
    private DBStorageType storages;

    private Long destPortFrom;

    private Long destPortTo;

    private String providerSideId;

    private String protocol;

    private String zone;

    private Boolean editable;

    private String ipRange;

    private String action;

    private Long position;

    private SecurityGroup securityGroup;

    private String direction;

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

    public Long getDestPortFrom() {
        return destPortFrom;
    }

    public void setDestPortFrom(Long destPortFrom) {
        this.destPortFrom = destPortFrom;
    }

    public Long getDestPortTo() {
        return destPortTo;
    }

    public void setDestPortTo(Long destPortTo) {
        this.destPortTo = destPortTo;
    }

    public String getProviderSideId() {
        return providerSideId;
    }

    public void setProviderSideId(String providerSideId) {
        this.providerSideId = providerSideId;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public Boolean getEditable() {
        return editable;
    }

    public void setEditable(Boolean editable) {
        this.editable = editable;
    }

    public String getIpRange() {
        return ipRange;
    }

    public void setIpRange(String ipRange) {
        this.ipRange = ipRange;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getPosition() {
        return position;
    }

    public void setPosition(Long position) {
        this.position = position;
    }

    public SecurityGroup getSecurityGroup() {
        return securityGroup;
    }

    public void setSecurityGroup(SecurityGroup securityGroup) {
        this.securityGroup = securityGroup;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    @Override()
    public String getCetCode() {
        return "SecurityRule";
    }
}
