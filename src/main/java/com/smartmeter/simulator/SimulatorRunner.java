package com.smartmeter.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeter.mqtt.MqttProperties;
import com.smartmeter.mqtt.RelayCommand;
import com.smartmeter.mqtt.TelemetryPayload;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Simulates N virtual ESP32+PZEM+relay meters over MQTT, using its own connection so it behaves like a
 * separate device fleet talking to the same broker your real ESP32 hardware will use.
 *
 * Enable with simulator.enabled=true (env SIMULATOR_ENABLED=true). Publishes meters/{deviceId}/telemetry
 * every simulator.interval-ms and reacts to meters/{deviceId}/cmd the same way your firmware should.
 *
 * Register rooms in the app with deviceId = "{devicePrefix}-001" ... "{devicePrefix}-0NN" (e.g. SIM-001)
 * so the backend's billing pipeline recognises and bills these meters.
 */
@Slf4j
@Component
@Order(2) // after MqttStartupRunner connects the backend's own client
public class SimulatorRunner implements CommandLineRunner {
    private final SimulatorProperties props;
    private final MqttProperties mqttProps;
    private final ObjectMapper mapper;

    private MqttAsyncClient client;
    private ScheduledExecutorService scheduler;
    private Map<String, VirtualMeter> meters;

    public SimulatorRunner(SimulatorProperties props, MqttProperties mqttProps, ObjectMapper mapper) {
        this.props = props;
        this.mqttProps = mqttProps;
        this.mapper = mapper;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!props.isEnabled()) return;
        if (mqttProps.getBrokerUrl() == null || mqttProps.getBrokerUrl().contains("YOUR-CLOUD-BROKER-HOST")) {
            log.warn("Simulator enabled but MQTT_BROKER_URL is not configured - simulator will not start.");
            return;
        }

        List<String> deviceIds = IntStream.rangeClosed(1, props.getMeterCount())
                .mapToObj(i -> String.format("%s-%03d", props.getDevicePrefix(), i))
                .toList();
        meters = deviceIds.stream().collect(Collectors.toMap(id -> id, VirtualMeter::new));

        client = new MqttAsyncClient(mqttProps.getBrokerUrl(), "smartmeter-simulator-" + System.currentTimeMillis(),
                new MemoryPersistence());
        client.setCallback(new MqttCallbackExtended() {
            @Override public void connectComplete(boolean reconnect, String uri) {
                log.info("Simulator MQTT connected to {} ({} virtual meters)", uri, meters.size());
                meters.keySet().forEach(id -> {
                    try { client.subscribe(mqttProps.cmdTopic(id), mqttProps.getQos()); }
                    catch (MqttException e) { log.warn("Simulator subscribe failed for {}", id); }
                });
            }
            @Override public void connectionLost(Throwable cause) {
                log.warn("Simulator MQTT connection lost: {}", cause == null ? "" : cause.getMessage());
            }
            @Override public void messageArrived(String topic, MqttMessage message) {
                String deviceId = mqttProps.deviceIdFromTopic(topic);
                VirtualMeter m = meters.get(deviceId);
                if (m == null) return;
                try {
                    RelayCommand cmd = mapper.readValue(message.getPayload(), RelayCommand.class);
                    m.relayOn = cmd.on();
                    log.info("Simulator: {} relay -> {}", deviceId, cmd.on() ? "ON" : "OFF");
                } catch (Exception e) {
                    log.warn("Simulator: bad relay command on {}", topic);
                }
            }
            @Override public void deliveryComplete(IMqttDeliveryToken token) { }
        });

        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setCleanSession(true);
        opts.setAutomaticReconnect(true);
        if (mqttProps.getUsername() != null && !mqttProps.getUsername().isBlank()) {
            opts.setUserName(mqttProps.getUsername());
            opts.setPassword(mqttProps.getPassword() == null ? new char[0] : mqttProps.getPassword().toCharArray());
        }
        client.connect(opts).waitForCompletion(15_000);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "meter-simulator");
            t.setDaemon(true);
            return t;
        });
        double intervalSeconds = props.getIntervalMs() / 1000.0;
        scheduler.scheduleAtFixedRate(() -> tick(intervalSeconds), props.getIntervalMs(), props.getIntervalMs(), TimeUnit.MILLISECONDS);
    }

    private void tick(double intervalSeconds) {
        for (VirtualMeter m : meters.values()) {
            double[] s = m.nextSample(intervalSeconds);
            if (!m.online) {
                // Buffer locally instead of publishing, exactly like the ESP32 would while WiFi/MQTT is down.
                m.offlineQueue.add(s);
                continue;
            }
            publish(m.deviceId, s);
            if (!m.offlineQueue.isEmpty()) {
                // Just came back online: flush the buffered backlog, oldest first, same as the real firmware's resend.
                log.info("{} back online, flushing {} buffered readings", m.deviceId, m.offlineQueue.size());
                m.offlineQueue.forEach(q -> publish(m.deviceId, q));
                m.offlineQueue.clear();
            }
        }
    }

    private void publish(String deviceId, double[] s) {
        TelemetryPayload payload = new TelemetryPayload(deviceId, round(s[0]), round(s[1]), round(s[2]),
                round(s[3]), round(s[4]), round(s[5]), (long) s[6]);
        try {
            byte[] bytes = mapper.writeValueAsBytes(payload);
            client.publish(mqttProps.telemetryTopic(deviceId), new MqttMessage(bytes) {{ setQos(mqttProps.getQos()); }});
        } catch (Exception e) {
            log.warn("Simulator publish failed for {}: {}", deviceId, e.toString());
        }
    }

    /** Toggle a specific virtual meter offline/online at runtime (e.g. from a test endpoint or a debugger). */
    public boolean setOnline(String deviceId, boolean online) {
        VirtualMeter m = meters == null ? null : meters.get(deviceId);
        if (m == null) return false;
        m.online = online;
        return true;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    @PreDestroy
    void shutdown() {
        if (scheduler != null) scheduler.shutdownNow();
        try { if (client != null && client.isConnected()) client.disconnect(); } catch (MqttException ignored) { }
    }
}
