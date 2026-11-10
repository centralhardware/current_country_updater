INSERT INTO country_days_tracker_bot.country_days_tracker
    (date_time, latitude, longitude, country, tzname, alt, batt, acc, vac, conn, locality, ghash, p, addr, bssid, ssid)
SELECT
    toDateTime(toDate('2011-10-04') + number) AS date_time,
    29.5581 AS latitude,
    34.9482 AS longitude,
    'Israel' AS country,
    'Asia/Jerusalem' AS tzname,
    0 AS alt,
    0 AS batt,
    0 AS acc,
    0 AS vac,
    '' AS conn,
    'Eilat' AS locality,
    '' AS ghash,
    0 AS p,
    '' AS addr,
    '' AS bssid,
    '' AS ssid
FROM numbers(3);
