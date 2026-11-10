-- Reverse-geocoding cache per geohash-7 cell, replacing the OwnTracks Recorder's LMDB ghash store.
CREATE TABLE geo_cache (
    ghash      TEXT PRIMARY KEY,
    cc         TEXT NOT NULL,
    addr       TEXT NOT NULL,
    locality   TEXT NOT NULL,
    tzname     TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
