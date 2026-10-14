package com.tradeguard.repository;

import com.tradeguard.domain.TradeValidationError;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeValidationErrorRepository extends JpaRepository<TradeValidationError, Long> {

    @Query("""
            select e.errorCode as code, count(e) as occurrences, count(distinct e.tradePk) as affectedRows
            from TradeValidationError e
            where e.batchId = :batchId
            group by e.errorCode
            order by count(e) desc, e.errorCode
            """)
    List<ErrorCodeCount> breakdownForBatch(@Param("batchId") Long batchId);
}
