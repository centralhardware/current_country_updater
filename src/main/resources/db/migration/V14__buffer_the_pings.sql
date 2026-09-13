-- Put a Buffer table in front of the tracker, so a ping is no longer a part.
--
-- WHAT WAS WRONG
--
-- Every ping is one INSERT, and in ClickHouse one INSERT is one part. The
-- tracker logs ~4,400 rows a day arriving in bursts -- 24 rows inside the same
-- minute is normal -- so the table takes ~4,400 parts a day and the background
-- merges spend the day collapsing them back into the single active part the
-- data actually occupies. Nothing is broken by this; it is simply work the
-- server does over and over for rows that could have arrived together.
--
-- WHAT THIS DOES
--
-- `country_days_tracker` becomes a Buffer table in front of the real MergeTree,
-- which is renamed `country_days_tracker_data`. The Buffer keeps the rows in
-- server memory and writes them down as one part when either 60 seconds pass
-- or 10,000 rows pile up -- at this volume, one part a minute during a burst
-- and nothing at all the rest of the time.
--
-- The name is what makes this invisible. Every reader -- the bot's own
-- getLastLocation / getCountryStats / getCountrySessions / getCurrentCountryLength,
-- every Grafana panel -- goes on querying `country_days_tracker`, and a SELECT
-- on a Buffer table reads the buffer AND the table underneath, so a ping logged
-- five seconds ago still answers. Not one query in this repo or on the
-- dashboards changes, and neither does the INSERT in DatabaseService.
--
-- WHY IT IS BUILT IN THIS ORDER
--
-- The new tables are created and proved BEFORE anything is renamed, and the
-- swap is a single atomic EXCHANGE. The failure to design against is a CREATE
-- the server rejects AFTER a rename has already happened: that would leave
-- `country_days_tracker` not existing at all, and every reader broken, in the
-- gap between two statements ClickHouse will not run in a transaction. Built
-- this way, the worst case is that step 2 fails, the deploy stops, and the live
-- table has not been touched.
--
-- The one thing that genuinely needs proving that way: `date_time` is an ALIAS
-- column -- `ts + tz_offset` since V13 -- and the documentation does not say
-- what a Buffer table does with an ALIAS. If it will not take one, this
-- migration fails on its own CREATE, with the live table still in place.
--
-- WHAT IT COSTS
--
-- Up to a minute of pings lives in ClickHouse's memory rather than on disk, so
-- a hard crash of the SERVER can lose them; an ordinary restart flushes
-- cleanly. That is a trade the location log can take -- a lost minute is one or
-- two points on a map, and the alternative was ~4,400 parts a day to merge
-- away.
--
-- Two Buffer limitations, neither of which this table runs into: FINAL and
-- SAMPLE are not applied to buffered rows (nothing here uses either -- this is
-- a plain MergeTree, not Replacing), and a Buffer has no index, so its rows are
-- always scanned in full (at a minute of pings, a few hundred rows).

-- 1. The real table, exactly as it stands today. `country_days_tracker` still
--    exists and still serves every reader while this runs.
CREATE TABLE IF NOT EXISTS country_days_tracker_bot.country_days_tracker_data
(
    `ts` DateTime('UTC') CODEC(Delta(4), ZSTD(9)),
    `tz_offset` Int32 CODEC(Delta(4), ZSTD(9)),
    `date_time` DateTime ALIAS ts + tz_offset,
    `country` LowCardinality(String),
    `longitude` Float32 DEFAULT 0 CODEC(Delta(4), ZSTD(9)),
    `latitude` Float32 DEFAULT 0 CODEC(Delta(4), ZSTD(9)),
    `tzname` LowCardinality(String),
    `alt` UInt16 CODEC(Delta(2), ZSTD(9)),
    `batt` UInt8,
    `acc` UInt8 CODEC(Delta(1), ZSTD(9)),
    `vac` UInt8 CODEC(Delta(1), ZSTD(9)),
    `conn` LowCardinality(String),
    `locality` LowCardinality(String),
    `ghash` LowCardinality(String),
    `p` Float64 CODEC(Delta(8), ZSTD(9)),
    `addr` LowCardinality(String),
    `bssid` LowCardinality(String),
    `ssid` LowCardinality(String),
    `bs` UInt8 DEFAULT 0 CODEC(ZSTD(9)),
    `vel` UInt16 DEFAULT 0 CODEC(Delta(2), ZSTD(9)),
    `cog` UInt16 DEFAULT 0 CODEC(Delta(2), ZSTD(9)),
    `m` Int8 DEFAULT 0
)
ENGINE = MergeTree
ORDER BY ts
SETTINGS index_granularity = 8192;

-- 2. The Buffer, still under a working name -- this is the statement that has
--    to survive for the rest to be safe.
--
--    Buffer(database, table, num_layers, min_time, max_time, min_rows, max_rows, min_bytes, max_bytes)
--    One layer: the writer is a single bot, and layers only buy parallel
--    inserts. Flush at 60s / 10,000 rows / 10 MB, whichever comes first, and
--    never sooner than 10s / 100 rows / 10 KB.
CREATE TABLE IF NOT EXISTS country_days_tracker_bot.country_days_tracker_buffer
AS country_days_tracker_bot.country_days_tracker_data
ENGINE = Buffer(
    country_days_tracker_bot,
    country_days_tracker_data,
    1,
    10, 60,
    100, 10000,
    10000, 10000000
);

-- 3. The swap, in one atomic statement, and BEFORE the history is copied.
--
--    The order matters and is not the obvious one. Copying first and swapping
--    second leaves a gap: the bot keeps logging throughout, and a ping that
--    lands in the old table between the copy and the swap belongs to neither
--    side -- it is not in the copy, and the table holding it is about to be
--    dropped. There is no transaction to close that window. Swapping first
--    closes it instead: after this statement the old MergeTree is named
--    `country_days_tracker_buffer`, nothing writes to it any more, and its
--    contents are final. Then the copy cannot miss a row and cannot duplicate
--    one either.
--
--    The cost of this order is the other way round: between this statement and
--    step 4, `country_days_tracker` is a Buffer over an empty table, so a
--    reader sees only the pings of the last few seconds. That lasts as long as
--    a ~1M-row single-part copy takes -- a second or two of a thin dashboard,
--    against a permanently lost ping the other way.
EXCHANGE TABLES country_days_tracker_bot.country_days_tracker
            AND country_days_tracker_bot.country_days_tracker_buffer;

-- 4. Move the history across, out of the old table which no longer receives
--    anything. `date_time` is an alias and is not copied -- it is recomputed
--    from ts + tz_offset on the other side, which is why this can be a plain
--    SELECT *.
INSERT INTO country_days_tracker_bot.country_days_tracker_data
SELECT * FROM country_days_tracker_bot.country_days_tracker_buffer;

-- 5. Drop it. Every row it held is in country_days_tracker_data, and from here
--    on the Buffer writes there.
DROP TABLE IF EXISTS country_days_tracker_bot.country_days_tracker_buffer;
