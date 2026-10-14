package com.tradeguard.web.dto;

import java.util.List;

public record BatchDetailDto(ImportBatchDto batch, List<ErrorBreakdownItem> errorBreakdown, SummaryDto summary) {
}
