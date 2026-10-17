package com.smartmeter.service;

import com.smartmeter.domain.*;
import com.smartmeter.dto.Dto.BillSummary;
import com.smartmeter.repo.*;
import com.smartmeter.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BillService {
    private static final BigDecimal DEFAULT_TARIFF = new BigDecimal("8.00");
    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("MMMM yyyy");

    private final UserLookup lookup;
    private final RoomRepository rooms;
    private final WalletRepository wallets;
    private final WalletTxnRepository walletTxns;
    private final DailyConsumptionRepository dailyConsumption;
    private final LandlordSettingsRepository landlordSettings;

    @Transactional(readOnly = true)
    public BillSummary tenantBill(String username, String yearMonth) {
        AppUser tenant = lookup.byUsername(username);
        Room room = rooms.findByTenantId(tenant.getId()).orElseThrow(() -> ApiException.notFound("No room assigned"));
        return build(tenant, room, parse(yearMonth));
    }

    @Transactional(readOnly = true)
    public BillSummary landlordViewOfBill(String username, Long roomId, String yearMonth) {
        AppUser landlord = lookup.byUsername(username);
        Room room = rooms.findByIdAndLandlordId(roomId, landlord.getId())
                .orElseThrow(() -> ApiException.notFound("Room not found"));
        if (room.getTenant() == null) throw ApiException.conflict("Room has no tenant");
        return build(room.getTenant(), room, parse(yearMonth));
    }

    /** Last 12 months, newest first - the landlord/tenant can request a bill for any of these periods. */
    public List<String> availablePeriods() {
        YearMonth now = YearMonth.now(ZoneOffset.UTC);
        return java.util.stream.IntStream.range(0, 12).mapToObj(now::minusMonths).map(YearMonth::toString).toList();
    }

    private BillSummary build(AppUser tenant, Room room, YearMonth ym) {
        Wallet wallet = wallets.findByTenantId(tenant.getId()).orElseThrow(() -> ApiException.notFound("Wallet not found"));
        var monthStart = ym.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        var monthEnd = ym.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        BigDecimal oldBalance = walletTxns.findLastBefore(wallet.getId(), monthStart)
                .map(WalletTxn::getBalanceAfter).orElse(BigDecimal.ZERO);

        List<WalletTxn> inMonth = walletTxns.findByWalletIdAndCreatedAtBetweenOrderByCreatedAtAsc(wallet.getId(), monthStart, monthEnd);
        BigDecimal recharged = inMonth.stream().filter(t -> t.getType() == TxnType.RECHARGE)
                .map(WalletTxn::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal usageAmount = inMonth.stream().filter(t -> t.getType() == TxnType.USAGE)
                .map(WalletTxn::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add).abs();

        double kwh = dailyConsumption.findByMeterIdAndDayBetweenOrderByDay(room.getMeter().getId(), ym.atDay(1), ym.atEndOfMonth())
                .stream().mapToDouble(DailyConsumption::getKwh).sum();

        BigDecimal newBalance = inMonth.isEmpty() ? oldBalance : inMonth.get(inMonth.size() - 1).getBalanceAfter();
        BigDecimal tariff = landlordSettings.findByLandlordId(room.getLandlord().getId())
                .map(LandlordSettings::getTariffPerKwh).orElse(DEFAULT_TARIFF);

        return new BillSummary(ym.format(PERIOD_FMT), tenant.getFullName(), room.getName(), room.getLandlord().getFullName(),
                oldBalance, recharged, round(kwh), usageAmount, tariff, newBalance);
    }

    public byte[] renderPdf(BillSummary b) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            float margin = 50, y = page.getMediaBox().getHeight() - 60;

            try (PDPageContentStream c = new PDPageContentStream(doc, page)) {
                y = text(c, PDType1Font.HELVETICA_BOLD, 18, margin, y, "Electricity Bill");
                y = text(c, PDType1Font.HELVETICA, 11, margin, y - 6, "Period: " + b.period());
                y -= 14;
                y = text(c, PDType1Font.HELVETICA, 11, margin, y, "Tenant: " + b.tenantName());
                y = text(c, PDType1Font.HELVETICA, 11, margin, y, "Room: " + b.room());
                y = text(c, PDType1Font.HELVETICA, 11, margin, y, "Landlord: " + b.landlordName());
                y -= 16;
                y = line(c, margin, y, page.getMediaBox().getWidth() - margin);
                y -= 16;

                y = row(c, margin, y, "Old Balance", money(b.oldBalance()));
                y = row(c, margin, y, "Recharged this month", money(b.recharged()));
                y = row(c, margin, y, "Units consumed", String.format("%.2f kWh", b.consumedKwh()));
                y = row(c, margin, y, "Tariff", money(b.tariffPerKwh()) + " / kWh");
                y = row(c, margin, y, "Amount for consumption", money(b.consumedAmount()));
                y -= 10;
                y = line(c, margin, y, page.getMediaBox().getWidth() - margin);
                y -= 20;
                text(c, PDType1Font.HELVETICA_BOLD, 13, margin, y, "New Balance: " + money(b.newBalance()));
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Could not generate bill PDF", e);
        }
    }

    private static float text(PDPageContentStream c, PDType1Font font, int size, float x, float y, String s) throws IOException {
        c.beginText();
        c.setFont(font, size);
        c.newLineAtOffset(x, y);
        c.showText(s);
        c.endText();
        return y - (size + 6);
    }

    private static float row(PDPageContentStream c, float x, float y, String label, String value) throws IOException {
        text(c, PDType1Font.HELVETICA, 11, x, y, label);
        text(c, PDType1Font.HELVETICA, 11, x + 260, y, value);
        return y - 20;
    }

    private static float line(PDPageContentStream c, float x1, float y, float x2) throws IOException {
        c.moveTo(x1, y);
        c.lineTo(x2, y);
        c.stroke();
        return y;
    }

    private static String money(BigDecimal v) { return "Rs " + v.setScale(2, RoundingMode.HALF_UP); }
    private static double round(double v) { return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).doubleValue(); }

    private static YearMonth parse(String s) {
        try { return YearMonth.parse(s); } catch (Exception e) { throw ApiException.bad("period must be YYYY-MM"); }
    }
}
