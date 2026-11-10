-- Replace note with severity rating
ALTER TABLE country_days_tracker_bot.sick_days DROP COLUMN note;
ALTER TABLE country_days_tracker_bot.sick_days DROP COLUMN created_at;
ALTER TABLE country_days_tracker_bot.sick_days ADD COLUMN severity UInt8 DEFAULT 5;
