-- Codecs for the tracker's numeric columns: Delta + ZSTD(9) instead of LZ4.
--
-- The table has never had a codec on anything, so every column is on
-- ClickHouse's default LZ4. It is 14.57 MiB for a million pings, and almost
-- all of that is numbers written in time order -- exactly what Delta is for:
-- it stores each value as its difference from the one before, and consecutive
-- pings differ by seconds of time and metres of distance.
--
-- Measured over each column as it stands today, in the 1 MiB blocks ClickHouse
-- compresses in, with the table read in its sort order (`ORDER BY date_time`).
-- `now` is what the column actually occupies on disk under LZ4:
--
--                    now      ZSTD(9)   Delta+ZSTD(9)
--     date_time    4089912    2819434       380124
--     latitude     3296457    2129754      1228244
--     longitude    2759390    1402343       724926
--     p            2028773     868075       654860
--     alt           847249     370636       324498
--     vac           456092     255017       244361
--     acc           379576     254027       238002
--     cog           262591     161365       149952
--     vel           239307     109079       101099
--
-- Together: 14.36 MB today, 4.05 MB with Delta+ZSTD(9) -- the table goes to
-- roughly 5 MiB, under a third of its size, and Delta beats plain ZSTD on
-- every one of these columns.
--
-- `date_time` is the extreme case. LZ4 gets a compression ratio of exactly
-- 1.00 on it -- four bytes per row, none of them saved -- because a rising
-- 32-bit timestamp has no repeated byte sequence to find. Its deltas are tiny
-- integers, and 4 MB becomes 380 KB.
--
-- Gorilla, the codec ClickHouse offers for floats, was measured on the three
-- float columns and loses to Delta on all of them (latitude 1505770 against
-- 1228244). Gorilla assumes neighbouring values share their high bits and
-- differ in the low ones; slow movement across the Earth gives it the leading
-- bytes but leaves the trailing ones unrelated, while the difference between
-- two nearby coordinates is small in the ordinary arithmetic sense, which is
-- what Delta encodes. DoubleDelta was measured too and loses to Delta
-- everywhere here (440338 on `date_time`): the ping interval is not constant
-- enough for a second derivative to pay for itself.
--
-- ZSTD(9) rather than a lower level: above level 1 zstd's decompression speed
-- barely moves, so the level is paid for in insert CPU only, and this table
-- takes one small row per ping.
--
-- Not touched:
--
--   * every `LowCardinality` column (`ghash`, `addr`, `locality`, `conn`,
--     `country`, `tzname`, `bssid`, `ssid`, `bs`). The dictionary already does
--     the deduplication a codec would be looking for.
--   * `batt`, `m` -- a few tens of KB between them.
--
-- MODIFY COLUMN changes metadata only: existing parts keep their LZ4 bytes
-- until something rewrites them, and the OPTIMIZE FINAL below is that
-- something -- one pass over 14 MiB, seconds.
--
-- Nothing in the application changes. A codec is how a column is stored, not
-- what it holds: inserts and queries are unaffected, and the column types are
-- deliberately not restated below so that nothing else about them can shift.
-- Should this table ever be recreated, these codecs belong in the CREATE TABLE.

ALTER TABLE country_days_tracker_bot.country_days_tracker
    MODIFY COLUMN date_time CODEC(Delta(4), ZSTD(9)),
    MODIFY COLUMN latitude  CODEC(Delta(4), ZSTD(9)),
    MODIFY COLUMN longitude CODEC(Delta(4), ZSTD(9)),
    MODIFY COLUMN p         CODEC(Delta(8), ZSTD(9)),
    MODIFY COLUMN alt       CODEC(Delta(2), ZSTD(9)),
    MODIFY COLUMN vac       CODEC(Delta(1), ZSTD(9)),
    MODIFY COLUMN acc       CODEC(Delta(1), ZSTD(9)),
    MODIFY COLUMN cog       CODEC(Delta(2), ZSTD(9)),
    MODIFY COLUMN vel       CODEC(Delta(2), ZSTD(9));

OPTIMIZE TABLE country_days_tracker_bot.country_days_tracker FINAL;
