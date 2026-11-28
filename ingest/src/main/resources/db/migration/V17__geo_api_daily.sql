-- The OpenCage key's state per UTC day: one row per key (its last four
-- characters, never the key) and day, replaced by every request that day,
-- so the row left at the end of a day says how much of that day's quota was
-- used. Read with FINAL. rate_* are OpenCage's own `rate` block from the
-- last answer (0 when it had none: paid keys, network errors).
CREATE TABLE IF NOT EXISTS country_days_tracker_bot.geo_api_daily
(
    `day`            Date DEFAULT toDate(now()),
    `key_id`         String,
    `updated_at`     DateTime64(3, 'UTC') DEFAULT now64(3),
    `requests`       UInt32,
    `outcome`        LowCardinality(String),
    `status`         UInt16,
    `rate_limit`     Int32,
    `rate_remaining` Int32,
    `rate_reset`     DateTime('UTC')
)
ENGINE = ReplacingMergeTree(updated_at)
ORDER BY (key_id, day);
