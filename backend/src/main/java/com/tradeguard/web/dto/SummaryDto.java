package com.tradeguard.web.dto;

import java.util.List;

/** Deterministic, rule-based plain-English summary. Not produced by an LLM. */
public record SummaryDto(String generator, String headline, List<String> points) {
}
