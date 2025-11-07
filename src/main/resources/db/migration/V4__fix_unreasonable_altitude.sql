-- Fix unreasonable altitude values (> 12000 meters)
-- Set altitude to 0 for values exceeding 12000 meters (beyond reasonable GPS altitudes)

ALTER TABLE country_days_tracker_bot.country_days_tracker
UPDATE alt = 0
WHERE alt > 12000;
