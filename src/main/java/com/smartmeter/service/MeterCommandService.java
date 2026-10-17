package com.smartmeter.service;

import com.smartmeter.domain.Meter;
import com.smartmeter.mqtt.MqttGateway;
import com.smartmeter.mqtt.MqttProperties;
import com.smartmeter.mqtt.RelayCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Single place that changes relay state: updates desired state in the DB and publishes the command over MQTT. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeterCommandService {
    private final MqttGateway mqtt;
    private final MqttProperties props;

    public void sendRelay(Meter meter, boolean on) {
        meter.setRelayOn(on);
        mqtt.publish(props.cmdTopic(meter.getDeviceId()), new RelayCommand(on));
        log.info("Relay command: meter={} on={}", meter.getDeviceId(), on);
    }
}
