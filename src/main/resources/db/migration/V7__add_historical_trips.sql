INSERT INTO country_days_tracker_bot.country_days_tracker
    (date_time, latitude, longitude, country, tzname, alt, batt, acc, vac, conn, locality, ghash, p, addr, bssid, ssid)
SELECT
    toDateTime(toDate('2011-09-27') + number) AS date_time,
    27.9158 AS latitude,
    34.3300 AS longitude,
    'Egypt' AS country,
    'Africa/Cairo' AS tzname,
    0 AS alt,
    0 AS batt,
    0 AS acc,
    0 AS vac,
    '' AS conn,
    'Sharm el-Sheikh' AS locality,
    '' AS ghash,
    0 AS p,
    '' AS addr,
    '' AS bssid,
    '' AS ssid
FROM numbers(16);

INSERT INTO country_days_tracker_bot.country_days_tracker
    (date_time, latitude, longitude, country, tzname, alt, batt, acc, vac, conn, locality, ghash, p, addr, bssid, ssid)
SELECT
    toDateTime(toDate('2012-10-03') + number) AS date_time,
    27.9158 AS latitude,
    34.3300 AS longitude,
    'Egypt' AS country,
    'Africa/Cairo' AS tzname,
    0 AS alt,
    0 AS batt,
    0 AS acc,
    0 AS vac,
    '' AS conn,
    'Sharm el-Sheikh' AS locality,
    '' AS ghash,
    0 AS p,
    '' AS addr,
    '' AS bssid,
    '' AS ssid
FROM numbers(11);

INSERT INTO country_days_tracker_bot.country_days_tracker
    (date_time, latitude, longitude, country, tzname, alt, batt, acc, vac, conn, locality, ghash, p, addr, bssid, ssid)
SELECT
    toDateTime(toDate('2014-11-27') + number) AS date_time,
    27.9158 AS latitude,
    34.3300 AS longitude,
    'Egypt' AS country,
    'Africa/Cairo' AS tzname,
    0 AS alt,
    0 AS batt,
    0 AS acc,
    0 AS vac,
    '' AS conn,
    'Sharm el-Sheikh' AS locality,
    '' AS ghash,
    0 AS p,
    '' AS addr,
    '' AS bssid,
    '' AS ssid
FROM numbers(12);

INSERT INTO country_days_tracker_bot.country_days_tracker
    (date_time, latitude, longitude, country, tzname, alt, batt, acc, vac, conn, locality, ghash, p, addr, bssid, ssid)
SELECT
    toDateTime(toDate('2015-09-16') + number) AS date_time,
    13.7563 AS latitude,
    100.5018 AS longitude,
    'Thailand' AS country,
    'Asia/Bangkok' AS tzname,
    0 AS alt,
    0 AS batt,
    0 AS acc,
    0 AS vac,
    '' AS conn,
    'Bangkok' AS locality,
    '' AS ghash,
    0 AS p,
    '' AS addr,
    '' AS bssid,
    '' AS ssid
FROM numbers(10);

INSERT INTO country_days_tracker_bot.country_days_tracker
    (date_time, latitude, longitude, country, tzname, alt, batt, acc, vac, conn, locality, ghash, p, addr, bssid, ssid)
SELECT
    toDateTime(toDate('2018-11-29') + number) AS date_time,
    12.9236 AS latitude,
    100.8825 AS longitude,
    'Thailand' AS country,
    'Asia/Bangkok' AS tzname,
    0 AS alt,
    0 AS batt,
    0 AS acc,
    0 AS vac,
    '' AS conn,
    'Pattaya' AS locality,
    '' AS ghash,
    0 AS p,
    '' AS addr,
    '' AS bssid,
    '' AS ssid
FROM numbers(9);

INSERT INTO country_days_tracker_bot.country_days_tracker
    (date_time, latitude, longitude, country, tzname, alt, batt, acc, vac, conn, locality, ghash, p, addr, bssid, ssid)
SELECT
    toDateTime(toDate('2019-10-01') + number) AS date_time,
    36.8969 AS latitude,
    30.7133 AS longitude,
    'Turkey' AS country,
    'Europe/Istanbul' AS tzname,
    0 AS alt,
    0 AS batt,
    0 AS acc,
    0 AS vac,
    '' AS conn,
    'Antalya' AS locality,
    '' AS ghash,
    0 AS p,
    '' AS addr,
    '' AS bssid,
    '' AS ssid
FROM numbers(10);
