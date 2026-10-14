package com.tradeguard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real PostgreSQL (Testcontainers) + the full Spring context, so Flyway migrations, constraints, SQL aggregations
 * and HTTP behaviour are all exercised together. One shared container per JVM keeps the suite fast.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    static final String HEADER = "trade_id,instrument,trade_timestamp,side,price,quantity,currency,trader_id";

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE trade_validation_errors, trades, trade_import_batches RESTART IDENTITY CASCADE");
        jdbc.execute("ALTER SEQUENCE trade_import_batches_seq RESTART WITH 1");
        jdbc.update("UPDATE validation_rules SET rule_value = '10' WHERE rule_key = 'MAX_UPLOAD_SIZE_MB'");
        jdbc.update("UPDATE validation_rules SET rule_value = 'USD,EUR,GBP,JPY,INR,CHF' WHERE rule_key = 'ALLOWED_CURRENCIES'");
        jdbc.update("UPDATE validation_rules SET rule_value = 'ES,NQ,YM,CL,NG,GC,SI,ZN,ZB,6E' WHERE rule_key = 'ALLOWED_INSTRUMENTS'");
        jdbc.update("UPDATE validation_rules SET rule_value = '10000' WHERE rule_key = 'MAX_TRADE_QUANTITY'");
    }

    protected ResultActions upload(String filename, String contentType, String csv) throws Exception {
        return upload(filename, contentType, csv.getBytes(StandardCharsets.UTF_8));
    }

    protected ResultActions upload(String filename, String contentType, byte[] content) throws Exception {
        return mvc.perform(multipart("/api/imports").file(new MockMultipartFile("file", filename, contentType, content)));
    }

    protected ResultActions uploadCsv(String csvBody) throws Exception {
        return upload("trades.csv", "text/csv", HEADER + "\n" + csvBody);
    }

    /** Performs a request whose body is streamed (CSV reports): waits for the async dispatch to complete. */
    protected ResultActions report(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        org.springframework.test.web.servlet.MvcResult first = mvc.perform(request).andReturn();
        if (first.getRequest().isAsyncStarted()) {
            first.getAsyncResult(10_000);
            return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(first));
        }
        return mvc.perform(request);
    }

    protected long countRows(String table) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
        return n == null ? 0 : n;
    }
}
