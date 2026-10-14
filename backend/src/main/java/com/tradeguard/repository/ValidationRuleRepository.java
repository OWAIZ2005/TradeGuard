package com.tradeguard.repository;

import com.tradeguard.domain.ValidationRule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ValidationRuleRepository extends JpaRepository<ValidationRule, String> {
}
