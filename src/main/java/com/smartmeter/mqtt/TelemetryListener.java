package com.smartmeter.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeter.service.TelemetryService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Bridges raw MQTT bytes to TelemetryService. Registered once at startup as the gateway's single message handler. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryListener {
    private final MqttGateway gateway;
    private final MqttProperties props;
    private final ObjectMapper mapper;
    private final TelemetryService telemetryService;

    @PostConstruct
    void register() {
        gateway.onMessage((topic, payload) -> {
            try {
                if (topic.endsWith("/telemetry")) {
                    TelemetryPayload p = mapper.readValue(payload, TelemetryPayload.class);
                    telemetryService.handle(p);
                }
                // /status messages (online/offline via LWT) are available here too if you want to persist them later.
            } catch (Exception e) {
                log.warn("Bad message on {}: {}", topic, e.toString());
            }
        });
    }
}
