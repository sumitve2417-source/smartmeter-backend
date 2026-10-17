package com.smartmeter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** The product maker's own support contact - set once via env vars, shown on both dashboards' Customer Care tab. */
@Component
@ConfigurationProperties(prefix = "app.support")
public class SupportProperties {
    private String productMakerName;
    private String productMakerEmail;
    private String productMakerPhone;

    public String getProductMakerName() { return productMakerName; }
    public void setProductMakerName(String v) { this.productMakerName = v; }
    public String getProductMakerEmail() { return productMakerEmail; }
    public void setProductMakerEmail(String v) { this.productMakerEmail = v; }
    public String getProductMakerPhone() { return productMakerPhone; }
    public void setProductMakerPhone(String v) { this.productMakerPhone = v; }
}
