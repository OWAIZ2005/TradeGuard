package com.tradeguard.web;

import com.tradeguard.service.RulesService;
import com.tradeguard.web.dto.RulesDto;
import com.tradeguard.web.dto.RulesUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rules")
public class RulesController {

    private final RulesService rules;

    public RulesController(RulesService rules) {
        this.rules = rules;
    }

    @GetMapping
    public RulesDto get() {
        return rules.get();
    }

    @PutMapping
    public RulesDto update(@Valid @RequestBody RulesUpdateRequest request) {
        return rules.update(request);
    }
}
