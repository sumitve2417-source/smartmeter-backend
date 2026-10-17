package com.smartmeter.simulator;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "simulator")
public class SimulatorProperties {
    private boolean enabled = false;
    private int meterCount = 20;
    private long intervalMs = 3000;
    private String devicePrefix = "SIM";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public int getMeterCount() { return meterCount; }
    public void setMeterCount(int v) { this.meterCount = v; }
    public long getIntervalMs() { return intervalMs; }
    public void setIntervalMs(long v) { this.intervalMs = v; }
    public String getDevicePrefix() { return devicePrefix; }
    public void setDevicePrefix(String v) { this.devicePrefix = v; }
}
