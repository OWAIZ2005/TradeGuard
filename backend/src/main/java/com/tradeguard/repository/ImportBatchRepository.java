package com.tradeguard.repository;

import com.tradeguard.domain.BatchStatus;
import com.tradeguard.domain.ImportBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    Page<ImportBatch> findByStatus(BatchStatus status, Pageable pageable);
}
