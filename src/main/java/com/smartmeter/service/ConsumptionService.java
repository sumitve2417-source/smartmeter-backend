package com.smartmeter.service;

import com.smartmeter.domain.*;
import com.smartmeter.dto.Dto.ChartPoint;
import com.smartmeter.dto.Dto.ChartResponse;
import com.smartmeter.repo.*;
import com.smartmeter.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConsumptionService {
    private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

    private final UserLookup lookup;
    private final RoomRepository rooms;
    private final MeterRepository meters;
    private final ReadingRepository readings;
    private final DailyConsumptionRepository dailyConsumption;

    @Transactional(readOnly = true)
    public ChartResponse tenantChart(String username, String range, String dateParam) {
        AppUser tenant = lookup.byUsername(username);
        Room room = rooms.findByTenantId(tenant.getId()).orElseThrow(() -> ApiException.notFound("No room assigned"));
        return forMeter(room.getMeter().getId(), range, dateParam);
    }

    @Transactional(readOnly = true)
    public ChartResponse landlordChart(String username, String range, String dateParam, Long roomId) {
        AppUser landlord = lookup.byUsername(username);
        if (roomId != null) {
            Room room = rooms.findByIdAndLandlordId(roomId, landlord.getId())
                    .orElseThrow(() -> ApiException.notFound("Room not found"));
            return forMeter(room.getMeter().getId(), range, dateParam);
        }
        return forLandlord(landlord.getId(), range, dateParam);
    }

    private ChartResponse forMeter(Long meterId, String range, String dateParam) {
        return switch (range.toLowerCase()) {
            case "day" -> dayChart(meterId, parseDate(dateParam));
            case "year" -> yearChart(meterId, null, parseYear(dateParam));
            default -> monthChart(meterId, null, parseYearMonth(dateParam));
        };
    }

    private ChartResponse dayChart(Long meterId, LocalDate date) {
        Instant from = date.atStartOfDay(INDIA_ZONE).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(INDIA_ZONE).toInstant();
        List<Reading> rows = readings.findBetween(meterId, from, to);

        double[] kwhByHour = new double[24];
        double[] powerSumByHour = new double[24];
        int[] countByHour = new int[24];
        for (Reading r : rows) {
            int h = r.getRecordedAt().getHour(); // Direct local hour matching India time
            kwhByHour[h] += r.getDeltaKwh();
            powerSumByHour[h] += r.getPowerW();
            countByHour[h]++;
        }
        List<ChartPoint> points = new ArrayList<>();
        double totalKwh = 0;
        for (int h = 0; h < 24; h++) {
            double avgPower = countByHour[h] == 0 ? 0 : powerSumByHour[h] / countByHour[h];
            points.add(new ChartPoint(String.format("%02d:00", h), round(kwhByHour[h]), BigDecimal.ZERO, round(avgPower)));
            totalKwh += kwhByHour[h];
        }
        BigDecimal totalRevenue = rows.stream().map(Reading::getCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ChartResponse("day", date.toString(), points, round(totalKwh), totalRevenue);
    }

    private ChartResponse monthChart(Long meterId, Long landlordId, YearMonth ym) {
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        Map<LocalDate, DailyConsumption> byDay = (landlordId == null
                ? dailyConsumption.findByMeterIdAndDayBetweenOrderByDay(meterId, from, to)
                : dailyConsumption.findForLandlordBetween(landlordId, from, to))
                .stream().collect(Collectors.groupingBy(DailyConsumption::getDay,
                        Collectors.collectingAndThen(Collectors.toList(), ConsumptionService::mergeDay)));

        List<ChartPoint> points = new ArrayList<>();
        double totalKwh = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            DailyConsumption dc = byDay.get(d);
            double kwh = dc == null ? 0 : dc.getKwh();
            BigDecimal rev = dc == null ? BigDecimal.ZERO : dc.getRevenue();
            points.add(new ChartPoint(String.valueOf(d.getDayOfMonth()), round(kwh), rev, null));
            totalKwh += kwh;
            totalRevenue = totalRevenue.add(rev);
        }
        return new ChartResponse("month", ym.toString(), points, round(totalKwh), totalRevenue);
    }

    private ChartResponse yearChart(Long meterId, Long landlordId, Year year) {
        LocalDate from = year.atDay(1);
        LocalDate to = year.atMonth(12).atEndOfMonth();
        List<DailyConsumption> rows = landlordId == null
                ? dailyConsumption.findByMeterIdAndDayBetweenOrderByDay(meterId, from, to)
                : dailyConsumption.findForLandlordBetween(landlordId, from, to);

        double[] kwhByMonth = new double[12];
        BigDecimal[] revByMonth = new BigDecimal[12];
        Arrays.fill(revByMonth, BigDecimal.ZERO);
        for (DailyConsumption dc : rows) {
            int m = dc.getDay().getMonthValue() - 1;
            kwhByMonth[m] += dc.getKwh();
            revByMonth[m] = revByMonth[m].add(dc.getRevenue());
        }
        List<ChartPoint> points = new ArrayList<>();
        double totalKwh = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (int m = 0; m < 12; m++) {
            String label = java.time.Month.of(m + 1).getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH);
            points.add(new ChartPoint(label, round(kwhByMonth[m]), revByMonth[m], null));
            totalKwh += kwhByMonth[m];
            totalRevenue = totalRevenue.add(revByMonth[m]);
        }
        return new ChartResponse("year", year.toString(), points, round(totalKwh), totalRevenue);
    }

    private ChartResponse forLandlord(Long landlordId, String range, String dateParam) {
        return switch (range.toLowerCase()) {
            case "day" -> landlordDayChart(landlordId, parseDate(dateParam));
            case "year" -> yearChart(null, landlordId, parseYear(dateParam));
            default -> monthChart(null, landlordId, parseYearMonth(dateParam));
        };
    }

    private ChartResponse landlordDayChart(Long landlordId, LocalDate date) {
        List<Room> allRooms = rooms.findByLandlordIdOrderByName(landlordId);
        Instant from = date.atStartOfDay(INDIA_ZONE).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(INDIA_ZONE).toInstant();
        double[] kwhByHour = new double[24];
        BigDecimal[] revByHour = new BigDecimal[24];
        Arrays.fill(revByHour, BigDecimal.ZERO);
        for (Room room : allRooms) {
            for (Reading r : readings.findBetween(room.getMeter().getId(), from, to)) {
                int h = r.getRecordedAt().getHour();
                kwhByHour[h] += r.getDeltaKwh();
                revByHour[h] = revByHour[h].add(r.getCost());
            }
        }
        List<ChartPoint> points = new ArrayList<>();
        double totalKwh = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (int h = 0; h < 24; h++) {
            points.add(new ChartPoint(String.format("%02d:00", h), round(kwhByHour[h]), revByHour[h], null));
            totalKwh += kwhByHour[h];
            totalRevenue = totalRevenue.add(revByHour[h]);
        }
        return new ChartResponse("day", date.toString(), points, round(totalKwh), totalRevenue);
    }

    private static DailyConsumption mergeDay(List<DailyConsumption> rowsForDay) {
        DailyConsumption merged = new DailyConsumption();
        merged.setDay(rowsForDay.get(0).getDay());
        double kwh = rowsForDay.stream().mapToDouble(DailyConsumption::getKwh).sum();
        BigDecimal rev = rowsForDay.stream().map(DailyConsumption::getRevenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        merged.setKwh(kwh);
        merged.setRevenue(rev);
        return merged;
    }

    private static double round(double v) { return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).doubleValue(); }

    private static LocalDate parseDate(String s) {
        try { return s == null ? LocalDate.now(INDIA_ZONE) : LocalDate.parse(s); }
        catch (Exception e) { throw ApiException.bad("date must be YYYY-MM-DD"); }
    }
    private static YearMonth parseYearMonth(String s) {
        try { return s == null ? YearMonth.now(INDIA_ZONE) : YearMonth.parse(s); }
        catch (Exception e) { throw ApiException.bad("date must be YYYY-MM"); }
    }
    private static Year parseYear(String s) {
        try { return s == null ? Year.now(INDIA_ZONE) : Year.parse(s); }
        catch (Exception e) { throw ApiException.bad("date must be YYYY"); }
    }
}
