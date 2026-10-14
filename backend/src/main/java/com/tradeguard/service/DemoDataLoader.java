package com.tradeguard.service;

import com.tradeguard.repository.ImportBatchRepository;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Optional demo seeding (TRADEGUARD_DEMO_LOAD=true). Imports the bundled SYNTHETIC sample CSV through the normal
 * import pipeline when - and only when - the database has no import batches yet.
 */
@Component
public class DemoDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);
    private static final String RESOURCE = "demo/sample_trades.csv";

    private final boolean enabled;
    private final ImportBatchRepository batches;
    private final ImportService importService;

    public DemoDataLoader(@Value("${tradeguard.demo.load-on-startup:false}") boolean enabled,
                          ImportBatchRepository batches, ImportService importService) {
        this.enabled = enabled;
        this.batches = batches;
        this.importService = importService;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!enabled) {
            return;
        }
        if (batches.count() > 0) {
            log.info("Demo data not loaded: the database already contains import batches.");
            return;
        }
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        long size = resource.contentLength();
        importService.importCsv("sample_trades.csv", size, "text/csv", () -> {
            InputStream in = resource.getInputStream();
            return in;
        });
        log.info("Synthetic demo data loaded from {}", RESOURCE);
    }
}
