-- Widen acc and vac from UInt8 to UInt16.
--
-- Both are accuracies in metres, and a fix indoors or from a cell tower is
-- often worse than 255 m. toUInt8 wraps instead of clamping, so a 300 m fix
-- was stored as 44 m: bad points looked precise. Rows already written can't
-- be recovered -- the high byte is gone.
--
-- The Buffer in front (V14) has to change along with the MergeTree under it.
-- ALTER on a Buffer flushes it first, so no buffered row is lost. Both
-- statements are no-ops on a second run, so a half-applied migration that
-- flyway.repair() retries just finishes the job. Data table first: the old
-- UInt8 buffer can still flush into a UInt16 column, but not the other way
-- round.
ALTER TABLE country_days_tracker_bot.country_days_tracker_data
    MODIFY COLUMN `acc` UInt16 CODEC(Delta(2), ZSTD(9)),
    MODIFY COLUMN `vac` UInt16 CODEC(Delta(2), ZSTD(9));
ALTER TABLE country_days_tracker_bot.country_days_tracker
    MODIFY COLUMN `acc` UInt16 CODEC(Delta(2), ZSTD(9)),
    MODIFY COLUMN `vac` UInt16 CODEC(Delta(2), ZSTD(9));
