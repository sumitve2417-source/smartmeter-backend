package com.smartmeter.mqtt;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@RequiredArgsConstructor
public class MqttStartupRunner implements CommandLineRunner {
    private final MqttGateway gateway;
    @Override public void run(String... args) { gateway.connect(); }
}
