package com.tradeguard.web;

import com.tradeguard.service.TradeFilter;
import com.tradeguard.service.TradeQueryService;
import com.tradeguard.web.dto.PageResponse;
import com.tradeguard.web.dto.TradeDetailDto;
import com.tradeguard.web.dto.TradeDto;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trades")
public class TradeController {

    private final TradeQueryService trades;

    public TradeController(TradeQueryService trades) {
        this.trades = trades;
    }

    @GetMapping
    public PageResponse<TradeDto> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String instrument,
            @RequestParam(required = false) String side,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        TradeFilter filter = TradeFilter.parse(from, to, instrument, side, status, batchId, q);
        return trades.search(filter, sort, direction, Paging.of(page, size));
    }

    @GetMapping("/instruments")
    public List<String> instruments() {
        return trades.instruments();
    }

    @GetMapping("/{id}")
    public TradeDetailDto get(@PathVariable long id) {
        return trades.get(id);
    }
}
