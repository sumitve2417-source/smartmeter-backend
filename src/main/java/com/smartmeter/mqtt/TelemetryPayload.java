package com.smartmeter.mqtt;

/** Wire format published by real ESP32 devices and by the simulator. */
public record TelemetryPayload(
        String deviceId,
        double voltage,
        double current,
        double powerW,
        double energyKwh,   // cumulative counter, as PZEM reports it
        double frequency,
        double powerFactor,
        long ts             // epoch millis
) {}
