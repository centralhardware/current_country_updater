ALTER TABLE country_days_tracker_bot.country_days_tracker
    MODIFY COLUMN locality LowCardinality(String),
    MODIFY COLUMN addr LowCardinality(String),
    MODIFY COLUMN ghash LowCardinality(String),
    DROP COLUMN IF EXISTS bs;
