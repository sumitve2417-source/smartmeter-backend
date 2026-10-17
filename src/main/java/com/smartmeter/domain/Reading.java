package com.smartmeter.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime; // <-- Change from Instant

/** One telemetry sample from a meter, kept for consumption history/charts. */
@Entity
@Table(name = "readings", indexes = @Index(columnList = "meter_id, recordedAt"))
@Getter @Setter @NoArgsConstructor
public class Reading {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meter_id")
    private Meter meter;

    private double voltage;
    private double current;
    private double powerW;
    /** Cumulative energy counter reported by the device (kWh), not a delta. */
    private double energyKwh;
    private double frequency;
    private double powerFactor;

    /** kWh billed for this sample (energyKwh - previous energyKwh), never negative. */
    @Column(nullable = false)
    private double deltaKwh;

    @Column(nullable = false, precision = 12, scale = 2)
    private java.math.BigDecimal cost;

    @Column(nullable = false)
    private LocalDateTime recordedAt; // <-- Changed to LocalDateTime
}
