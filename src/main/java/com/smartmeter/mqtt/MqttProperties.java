package com.smartmeter.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties {
    private String brokerUrl;
    private String username;
    private String password;
    private String clientId;
    private String topicPrefix = "meters";
    private int qos = 1;

    public String getBrokerUrl() { return brokerUrl; }
    public void setBrokerUrl(String v) { this.brokerUrl = v; }
    public String getUsername() { return username; }
    public void setUsername(String v) { this.username = v; }
    public String getPassword() { return password; }
    public void setPassword(String v) { this.password = v; }
    public String getClientId() { return clientId; }
    public void setClientId(String v) { this.clientId = v; }
    public String getTopicPrefix() { return topicPrefix; }
    public void setTopicPrefix(String v) { this.topicPrefix = v; }
    public int getQos() { return qos; }
    public void setQos(int v) { this.qos = v; }

    public String telemetryTopic(String deviceId) { return topicPrefix + "/" + deviceId + "/telemetry"; }
    public String cmdTopic(String deviceId) { return topicPrefix + "/" + deviceId + "/cmd"; }
    public String statusTopic(String deviceId) { return topicPrefix + "/" + deviceId + "/status"; }
    public String telemetryWildcard() { return topicPrefix + "/+/telemetry"; }
    public String statusWildcard() { return topicPrefix + "/+/status"; }

    /** meters/{deviceId}/telemetry -> deviceId */
    public String deviceIdFromTopic(String topic) {
        String[] parts = topic.split("/");
        return parts.length >= 2 ? parts[1] : null;
    }
}
