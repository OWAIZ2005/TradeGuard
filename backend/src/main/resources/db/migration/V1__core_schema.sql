-- TradeGuard core schema.
-- Design decision: every uploaded row is stored in `trades` (auditability) with a
-- validation_status. Analytics only ever read VALID rows. CHECK constraints make it
-- impossible for a VALID row to carry missing/non-positive values, and a partial unique
-- index guarantees one VALID trade per trade_id across all batches.

CREATE SEQUENCE trade_import_batches_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE trades_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE trade_validation_errors_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE trade_import_batches (
    id              BIGINT PRIMARY KEY,
    filename        VARCHAR(255)  NOT NULL,
    file_size_bytes BIGINT        NOT NULL CHECK (file_size_bytes >= 0),
    file_sha256     VARCHAR(64)   NOT NULL,
    status          VARCHAR(20)   NOT NULL CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    total_rows      INTEGER       NOT NULL DEFAULT 0 CHECK (total_rows >= 0),
    valid_rows      INTEGER       NOT NULL DEFAULT 0 CHECK (valid_rows >= 0),
    invalid_rows    INTEGER       NOT NULL DEFAULT 0 CHECK (invalid_rows >= 0),
    duplicate_rows  INTEGER       NOT NULL DEFAULT 0 CHECK (duplicate_rows >= 0),
    error_count     INTEGER       NOT NULL DEFAULT 0 CHECK (error_count >= 0),
    rules_snapshot  TEXT          NOT NULL,
    failure_reason  VARCHAR(1000),
    uploaded_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_batch_row_counts CHECK (status <> 'COMPLETED' OR valid_rows + invalid_rows = total_rows)
);
CREATE INDEX idx_batches_uploaded_at ON trade_import_batches (uploaded_at DESC);
CREATE INDEX idx_batches_sha256 ON trade_import_batches (file_sha256);

CREATE TABLE trades (
    id                BIGINT PRIMARY KEY,
    batch_id          BIGINT         NOT NULL REFERENCES trade_import_batches (id) ON DELETE CASCADE,
    row_number        INTEGER        NOT NULL CHECK (row_number >= 2),
    trade_id          VARCHAR(64),
    instrument        VARCHAR(32),
    trade_timestamp   TIMESTAMPTZ,
    side              VARCHAR(4),
    price             NUMERIC(19, 6),
    quantity          NUMERIC(19, 6),
    currency          VARCHAR(3),
    trader_id         VARCHAR(64),
    raw_record        TEXT           NOT NULL,
    validation_status VARCHAR(7)     NOT NULL CHECK (validation_status IN ('VALID', 'INVALID')),
    -- Date used by every date-range filter: trade_timestamp, or the upload time when the
    -- timestamp of a rejected row is missing/unparseable.
    effective_at      TIMESTAMPTZ    NOT NULL,
    notional          NUMERIC(38, 12) GENERATED ALWAYS AS (price * quantity) STORED,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_trades_batch_row UNIQUE (batch_id, row_number),
    CONSTRAINT chk_valid_trade_complete CHECK (
        validation_status = 'INVALID' OR (
            trade_id IS NOT NULL AND length(trim(trade_id)) > 0
            AND instrument IS NOT NULL AND trade_timestamp IS NOT NULL
            AND side IN ('BUY', 'SELL')
            AND price IS NOT NULL AND price > 0
            AND quantity IS NOT NULL AND quantity > 0
            AND currency IS NOT NULL AND trader_id IS NOT NULL)
    )
);
-- A trade id may be VALID only once, across all batches (documented duplicate policy).
CREATE UNIQUE INDEX uq_trades_valid_trade_id ON trades (trade_id) WHERE validation_status = 'VALID';
CREATE INDEX idx_trades_batch ON trades (batch_id, row_number);
CREATE INDEX idx_trades_effective_at ON trades (effective_at);
CREATE INDEX idx_trades_valid_instrument_ts ON trades (instrument, trade_timestamp) WHERE validation_status = 'VALID';
CREATE INDEX idx_trades_status ON trades (validation_status);
CREATE INDEX idx_trades_trade_id_prefix ON trades (lower(trade_id) text_pattern_ops);

CREATE TABLE trade_validation_errors (
    id             BIGINT PRIMARY KEY,
    batch_id       BIGINT       NOT NULL REFERENCES trade_import_batches (id) ON DELETE CASCADE,
    trade_pk       BIGINT       NOT NULL REFERENCES trades (id) ON DELETE CASCADE,
    row_number     INTEGER      NOT NULL,
    field_name     VARCHAR(40)  NOT NULL,
    error_code     VARCHAR(40)  NOT NULL,
    message        VARCHAR(500) NOT NULL,
    rejected_value VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_errors_batch ON trade_validation_errors (batch_id, row_number);
CREATE INDEX idx_errors_trade ON trade_validation_errors (trade_pk);
CREATE INDEX idx_errors_code ON trade_validation_errors (error_code);

CREATE TABLE validation_rules (
    rule_key    VARCHAR(60)  PRIMARY KEY,
    rule_value  VARCHAR(2000) NOT NULL,
    description VARCHAR(300) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
