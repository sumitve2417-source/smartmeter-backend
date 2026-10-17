package com.smartmeter.simulator;

import java.util.concurrent.ThreadLocalRandom;

/** In-memory state for one simulated meter. Mirrors what a real ESP32+PZEM would track on-device. */
class VirtualMeter {
    final String deviceId;
    volatile boolean relayOn = true;
    volatile boolean online = true;
    double energyKwh;          // cumulative counter, like the real PZEM
    private double baseLoadW;  // this room's typical load, so meters aren't identical

    // Offline-buffer simulation: readings queued here while "offline", flushed on reconnect - like the ESP32's LittleFS queue.
    final java.util.List<double[]> offlineQueue = new java.util.ArrayList<>(); // [voltage,current,powerW,energyKwh,freq,pf,epochMillis]

    VirtualMeter(String deviceId) {
        this.deviceId = deviceId;
        this.baseLoadW = 150 + ThreadLocalRandom.current().nextDouble(0, 900); // 150W-1050W typical room load
        this.energyKwh = 0.0; // <-- Change this from a random number to 0.0 so it starts fresh!
    }

    /** Produces the next sample and advances the cumulative energy counter, honouring relay state. */
    double[] nextSample(double intervalSeconds) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double voltage = relayOn ? 225 + r.nextDouble(-5, 5) : 0;
        double powerW = relayOn ? Math.max(0, baseLoadW + r.nextDouble(-40, 60)) : 0;
        double current = voltage > 0 ? powerW / voltage : 0;
        double frequency = relayOn ? 50 + r.nextDouble(-0.15, 0.15) : 0;
        double pf = relayOn ? 0.9 + r.nextDouble(-0.05, 0.08) : 0;

        if (relayOn) {
            energyKwh += (powerW * intervalSeconds) / 3600.0 / 1000.0;
        }
        return new double[] { voltage, current, powerW, energyKwh, frequency, Math.min(pf, 1.0), System.currentTimeMillis() };
    }
}
