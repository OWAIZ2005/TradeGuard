package com.tradeguard.web.dto;

import java.util.List;

public record TradeDetailDto(TradeDto trade, String rawRecord, List<ErrorDto> errors) {
}
