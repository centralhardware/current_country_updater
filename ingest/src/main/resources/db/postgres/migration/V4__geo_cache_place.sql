-- The public place (cafe, museum, station...) a cell sits in, or '' when it is none.
-- NULL marks a cell cached before this column existed: it is looked up again once.
ALTER TABLE geo_cache ADD COLUMN place TEXT;
