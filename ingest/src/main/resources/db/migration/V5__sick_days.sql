-- Create table for tracking sick days
CREATE TABLE IF NOT EXISTS country_days_tracker_bot.sick_days
(
    sick_date Date,
    note String DEFAULT '',
    created_at DateTime DEFAULT now()
)
ENGINE = MergeTree
ORDER BY sick_date;
