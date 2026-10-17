package com.smartmeter.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.stereotype.Component;

import java.util.function.BiConsumer;

/**
 * One shared MQTT connection to the cloud broker (EMQX Cloud / HiveMQ Cloud etc. over TLS on :8883).
 * Cloud brokers use publicly trusted certificates, so the JVM's default trust store works without extra setup.
 */
@Slf4j
@Component
public class MqttGateway {
    private final MqttProperties props;
    private final ObjectMapper mapper;
    private MqttAsyncClient client;
    private volatile BiConsumer<String, byte[]> messageHandler;

    public MqttGateway(MqttProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    /** Called once by whoever wants to receive messages (TelemetryListener). Only one handler is supported. */
    public void onMessage(BiConsumer<String, byte[]> handler) {
        this.messageHandler = handler;
    }

    public synchronized void connect() {
        if (props.getBrokerUrl() == null || props.getBrokerUrl().contains("YOUR-CLOUD-BROKER-HOST")) {
            log.warn("MQTT broker URL not configured (set MQTT_BROKER_URL) - skipping MQTT connect.");
            return;
        }
        try {
            client = new MqttAsyncClient(props.getBrokerUrl(), props.getClientId() + "-" + System.currentTimeMillis(),
                    new MemoryPersistence());
            client.setCallback(new MqttCallbackExtended() {
                @Override public void connectComplete(boolean reconnect, String uri) {
                    log.info("MQTT connected (reconnect={}) to {}", reconnect, uri);
                    subscribeAll();
                }
                @Override public void connectionLost(Throwable cause) {
                    log.warn("MQTT connection lost: {}", cause == null ? "unknown" : cause.getMessage());
                }
                @Override public void messageArrived(String topic, MqttMessage message) {
                    BiConsumer<String, byte[]> h = messageHandler;
                    if (h != null) h.accept(topic, message.getPayload());
                }
                @Override public void deliveryComplete(IMqttDeliveryToken token) { }
            });

            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setCleanSession(true);
            opts.setAutomaticReconnect(true);
            opts.setConnectionTimeout(15);
            opts.setKeepAliveInterval(30);
            if (props.getUsername() != null && !props.getUsername().isBlank()) {
                opts.setUserName(props.getUsername());
                opts.setPassword(props.getPassword() == null ? new char[0] : props.getPassword().toCharArray());
            }
            client.connect(opts).waitForCompletion(15_000);
        } catch (MqttException e) {
            log.error("Could not connect to MQTT broker: {}", e.getMessage());
        }
    }

    private void subscribeAll() {
        try {
            client.subscribe(props.telemetryWildcard(), props.getQos());
            client.subscribe(props.statusWildcard(), props.getQos());
        } catch (MqttException e) {
            log.error("MQTT subscribe failed: {}", e.getMessage());
        }
    }

    public void publish(String topic, Object payload) {
        if (client == null || !client.isConnected()) {
            log.warn("MQTT not connected - dropped publish to {}", topic);
            return;
        }
        try {
            byte[] bytes = mapper.writeValueAsBytes(payload);
            client.publish(topic, new MqttMessage(bytes) {{ setQos(props.getQos()); }});
        } catch (Exception e) {
            log.error("MQTT publish failed on {}: {}", topic, e.getMessage());
        }
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    @PreDestroy
    void shutdown() {
        try { if (client != null && client.isConnected()) client.disconnect(); } catch (MqttException ignored) { }
    }
}
