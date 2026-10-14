package com.tradeguard.web;

import com.tradeguard.service.AnalyticsService;
import com.tradeguard.service.TradeFilter;
import com.tradeguard.web.dto.AnalyticsDtos.DailyVolume;
import com.tradeguard.web.dto.AnalyticsDtos.InstrumentStat;
import com.tradeguard.web.dto.AnalyticsDtos.Overview;
import com.tradeguard.web.dto.AnalyticsDtos.SideStat;
import com.tradeguard.web.dto.AnalyticsDtos.ValidationStats;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** All endpoints accept the same from/to/instrument/side/batchId filter as the trade explorer and reports. */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/overview")
    public Overview overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String instrument, @RequestParam(required = false) String side,
            @RequestParam(required = false) Long batchId) {
        return analytics.overview(TradeFilter.parse(from, to, instrument, side, null, batchId, null));
    }

    @GetMapping("/daily-volume")
    public List<DailyVolume> dailyVolume(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String instrument, @RequestParam(required = false) String side,
            @RequestParam(required = false) Long batchId) {
        return analytics.dailyVolume(TradeFilter.parse(from, to, instrument, side, null, batchId, null));
    }

    @GetMapping("/instruments")
    public List<InstrumentStat> instruments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String instrument, @RequestParam(required = false) String side,
            @RequestParam(required = false) Long batchId) {
        return analytics.instruments(TradeFilter.parse(from, to, instrument, side, null, batchId, null));
    }

    @GetMapping("/sides")
    public List<SideStat> sides(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String instrument, @RequestParam(required = false) String side,
            @RequestParam(required = false) Long batchId) {
        return analytics.sides(TradeFilter.parse(from, to, instrument, side, null, batchId, null));
    }

    @GetMapping("/validation")
    public ValidationStats validation(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String instrument, @RequestParam(required = false) String side,
            @RequestParam(required = false) Long batchId) {
        return analytics.validation(TradeFilter.parse(from, to, instrument, side, null, batchId, null));
    }
}
