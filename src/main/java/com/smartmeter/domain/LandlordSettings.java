package com.smartmeter.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "landlord_settings")
@Getter @Setter @NoArgsConstructor
public class LandlordSettings {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "landlord_id", unique = true)
    private AppUser landlord;

    @Column(length = 100)
    private String upiId;

    @Column(length = 100)
    private String payeeName;

    @Column(columnDefinition = "MEDIUMBLOB")
    private byte[] qrImage;

    @Column(length = 30)
    private String qrContentType;

    /** Price per kWh. */
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal tariffPerKwh = new BigDecimal("8.00");

    /** Relay is cut automatically when a tenant's balance falls to or below this. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal minBalance = new BigDecimal("50.00");
}
