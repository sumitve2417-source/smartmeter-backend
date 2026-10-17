package com.smartmeter.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "meters")
@Getter @Setter @NoArgsConstructor
public class Meter {

    @OneToOne(mappedBy = "meter", fetch = FetchType.LAZY)
    private Room room;
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String deviceId;

    /** Desired relay state (true = power ON). */
    @Column(nullable = false)
    private boolean relayOn = true;

    /** True when the landlord switched power off by hand; auto-restore must not override it. */
    @Column(nullable = false)
    private boolean manualLock = false;

    private Instant lastSeen;

    /** Cumulative energy (kWh) as of the last reading that was billed. Used to compute delta usage. */
    @Column(nullable = false)
    private double lastEnergyKwh = 0.0;

    // Latest live snapshot, shown on dashboards; not historical (see Reading for history).
    private Double lastVoltage;
    private Double lastCurrent;
    private Double lastPowerW;
    private Double lastFrequency;
    private Double lastPowerFactor;

    /** Set when a tenant is onboarded into this meter's room; null again after vacate. No billing happens while null. */
    private Instant activatedAt;
}
