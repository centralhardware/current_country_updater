-- Reverse-geocoding cache per geohash-7 cell, moved here from Postgres.
--
-- Rows are only ever added and never updated, so ReplacingMergeTree keyed by
-- the geohash fits: a cell written twice by two racing pings collapses on
-- merge, and either copy is a correct answer until then. The table starts
-- empty -- cells are geocoded again the first time a ping lands in them.
CREATE TABLE IF NOT EXISTS country_days_tracker_bot.geo_cache
(
    `ghash`      String CODEC(ZSTD(9)),
    `cc`         LowCardinality(String),
    `addr`       String CODEC(ZSTD(9)),
    `locality`   LowCardinality(String),
    `tzname`     LowCardinality(String),
    `created_at` DateTime DEFAULT now() CODEC(Delta(4), ZSTD(9))
)
ENGINE = ReplacingMergeTree
ORDER BY ghash;
