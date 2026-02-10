ALTER TABLE country_days_tracker_bot.country_days_tracker
    ADD COLUMN IF NOT EXISTS bs LowCardinality(UInt8) DEFAULT 0;
