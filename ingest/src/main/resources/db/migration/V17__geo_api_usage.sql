-- One row per OpenCage request (cache hits are not logged), so each key's
-- daily use and the quota it last reported can be read back: GET /geo/usage
-- and Grafana. `key_id` is the key's last four characters, never the key.
-- `outcome`: ok | quota | rate_limited | invalid_key | error.
-- rate_* are OpenCage's own `rate` block (0 when the answer had none).
CREATE TABLE IF NOT EXISTS country_days_tracker_bot.geo_api_usage
(
    `ts`             DateTime('UTC') DEFAULT now() CODEC(Delta(4), ZSTD(9)),
    `key_id`         LowCardinality(String),
    `outcome`        LowCardinality(String),
    `status`         UInt16,
    `rate_limit`     Int32,
    `rate_remaining` Int32,
    `rate_reset`     DateTime('UTC')
)
ENGINE = MergeTree
ORDER BY (key_id, ts)
TTL ts + INTERVAL 400 DAY;
