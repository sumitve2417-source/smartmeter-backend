package com.smartmeter.web;

import com.smartmeter.simulator.SimulatorRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Testing-only: flips one virtual meter offline/online so you can watch the buffer-and-flush behaviour live.
 * No auth on purpose (it only controls the simulator, never real data) - remove this controller before deploying publicly.
 */
@RestController
@RequestMapping("/api/dev/simulator")
@RequiredArgsConstructor
public class SimulatorController {
    private final SimulatorRunner simulator;

    @PostMapping("/{deviceId}/offline")
    public Map<String, Object> goOffline(@PathVariable String deviceId) {
        boolean ok = simulator.setOnline(deviceId, false);
        return Map.of("deviceId", deviceId, "online", false, "found", ok);
    }

    @PostMapping("/{deviceId}/online")
    public Map<String, Object> goOnline(@PathVariable String deviceId) {
        boolean ok = simulator.setOnline(deviceId, true);
        return Map.of("deviceId", deviceId, "online", true, "found", ok);
    }
}
