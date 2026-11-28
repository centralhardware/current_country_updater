-- The OpenCage key's last known state: one row per key (its last four
-- characters, never the key), replaced on every request. Read with FINAL.
-- `outcome` of the last request: ok | quota | rate_limited | invalid_key | error.
-- rate_* are OpenCage's own `rate` block from the last answer (0 when it had
-- none: paid keys, network errors).
CREATE TABLE IF NOT EXISTS country_days_tracker_bot.geo_api_state
(
    `key_id`         String,
    `updated_at`     DateTime64(3, 'UTC') DEFAULT now64(3),
    `outcome`        LowCardinality(String),
    `status`         UInt16,
    `rate_limit`     Int32,
    `rate_remaining` Int32,
    `rate_reset`     DateTime('UTC')
)
ENGINE = ReplacingMergeTree(updated_at)
ORDER BY key_id;
