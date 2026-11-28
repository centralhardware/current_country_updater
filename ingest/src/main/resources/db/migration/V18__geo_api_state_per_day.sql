-- The OpenCage key's state per day instead of only the latest: one row per
-- key and UTC day, replaced by every request that day, so the row left at the
-- end of a day says how much of that day's quota was used. Read with FINAL.
-- geo_api_state (V17) only ever held the latest row; it is dropped.
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

DROP TABLE IF EXISTS country_days_tracker_bot.geo_api_state;
