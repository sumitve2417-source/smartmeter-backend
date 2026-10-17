package com.smartmeter.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "recharge_requests", indexes = @Index(columnList = "landlord_id, status"))
@Getter @Setter @NoArgsConstructor
public class RechargeRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id")
    private AppUser tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "landlord_id")
    private AppUser landlord;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** UPI transaction reference; unique so one payment can never be credited twice. */
    @Column(nullable = false, unique = true, length = 30)
    private String utr;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RechargeStatus status = RechargeStatus.PENDING;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant decidedAt;
}
