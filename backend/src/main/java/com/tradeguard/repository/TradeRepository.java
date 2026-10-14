package com.tradeguard.repository;

import com.tradeguard.domain.Trade;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    /** One query per chunk of ids, so duplicate detection against history is never N+1. */
    @Query("""
            select new com.tradeguard.repository.ExistingTradeRef(t.tradeId, t.batchId, t.rowNumber)
            from Trade t
            where t.validationStatus = com.tradeguard.domain.ValidationStatus.VALID and t.tradeId in :ids
            """)
    List<ExistingTradeRef> findValidByTradeIds(@Param("ids") Collection<String> ids);
}
