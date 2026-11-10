-- Create database and table for country days tracker
CREATE DATABASE IF NOT EXISTS country_days_tracker_bot;

CREATE TABLE IF NOT EXISTS country_days_tracker_bot.country_days_tracker
(
    date_time DateTime,
    latitude Float32,
    longitude Float32,
    country String,
    tzname String,
    alt UInt16,
    batt UInt8,
    acc UInt8,
    vac UInt8,
    conn String,
    locality String,
    ghash String,
    p Float64,
    addr String,
    bssid String,
    ssid String
)
ENGINE = MergeTree
ORDER BY (toDate(date_time), country);
