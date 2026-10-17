package com.smartmeter.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row per meter per calendar day (UTC): running total for that day, updated incrementally as
 * telemetry arrives. This is what powers the day/month/year charts without scanning millions of
 * raw Reading rows every time someone opens a dashboard.
 */
@Entity
@Table(name = "daily_consumption", uniqueConstraints = @UniqueConstraint(columnNames = {"meter_id", "day"}))
@Getter @Setter @NoArgsConstructor
public class DailyConsumption {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meter_id")
    private Meter meter;

    @Column(name = "day", nullable = false)
    private LocalDate day;

    @Column(nullable = false)
    private double kwh = 0.0;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal revenue = BigDecimal.ZERO;
}
