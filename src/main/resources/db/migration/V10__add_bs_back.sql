SET allow_suspicious_low_cardinality_types = 1;

ALTER TABLE country_days_tracker_bot.country_days_tracker
    ADD COLUMN IF NOT EXISTS bs LowCardinality(UInt8) DEFAULT 0;
