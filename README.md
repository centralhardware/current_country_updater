# Current Country Updater

A Telegram bot that automatically updates a channel's title and adds location tags to messages based on the current country and city information.

## Features

- Automatically updates the channel title with the current country flag emoji
- Adds country and city hashtags to all messages in the channel
- Periodically checks for country changes (every 10 minutes)
- Supports various media types (photos, videos, text messages)

## Requirements

- JDK 24 or higher
- Gradle 8.0 or higher
- ClickHouse database with country tracking data
- Telegram Bot API token

## Configuration

The application uses environment variables for configuration:

| Variable | Description |
|----------|-------------|
| `CHANEL_ID` | The ID of the Telegram channel to monitor and update |
| `MAP_OWNER_ID` | Telegram user id allowed to run /map directly or via guest mode |
| `MAP_GUEST_ID` | Telegram user id allowed to run /map only via guest mode in their DM with the owner |
| `CHANEL_TITLE_PATTERN` | The pattern for the channel title (e.g., "Travel Blog %s") |
| `CLICKHOUSE_URL` | The JDBC URL for the ClickHouse database |
| `PLACE_URL` | Bot: base URL of the place describer (dotclaude `scripts/place/place-server.py`) for the 📍 line; unset turns the line off |
| `CALENDAR_SECRET` | Path secret of the calendar feed (web server) |
| `OPENCAGE_KEY` | OpenCage key for reverse geocoding OwnTracks pings on `POST /pub` (`opencage:` prefix optional) |

## Database Schema

The application expects the following table structure in ClickHouse:

```sql
CREATE TABLE country_days_tracker_bot.country_days_tracker (
    date_time DateTime,
    country String,
    latitude String,
    longitude String
) ENGINE = MergeTree()
ORDER BY date_time;
```

## Modules

Two Gradle modules, two processes, two containers:

- `ingest` -- the web server (`ghcr.io/<repo>-web`, main class `WebMainKt`, port 80): OwnTracks `POST /pub`, the JSON Schema contract, OpenCage reverse geocoding, the calendar feed, `/health`, and the ClickHouse/Postgres storage with its migrations. It reads locality overrides from Postgres (cached for a minute). It has no Telegram dependency. Needs `CLICKHOUSE_*`, `DATABASE_URL`, `LOCATION_USER`/`LOCATION_PASSWORD`, `OPENCAGE_KEY`, `CALENDAR_SECRET`.
- `bot` -- the Telegram bot (`ghcr.io/<repo>`): channel title, commands, `/map`. It uses `ingest` only as a library for storage. The `/map` live location polls ClickHouse for new pings every 15 s. Needs `CLICKHOUSE_*`, `DATABASE_URL`, the Telegram token and the `CHANEL_*`/`MAP_*` variables.

Build the images with `./gradlew :bot:jibDockerBuild` and `./gradlew :ingest:jibDockerBuild`. CI pushes both and triggers `COOLIFY_DEPLOY_HOOK_URL` (bot) and `COOLIFY_WEB_DEPLOY_HOOK_URL` (web).

## Building

### Using Gradle

```bash
./gradlew build
./gradlew installDist
```

The application will be built in `build/install/CurrentCountryUpdater`.

### Using Docker

```bash
docker build -t current-country-updater .
```

## Running

### Using Gradle

```bash
./gradlew run
```

### Using the distribution

```bash
cd build/install/CurrentCountryUpdater
./bin/CurrentCountryUpdater
```

### Using Docker

```bash
docker run -e CHANEL_ID=your_channel_id \
           -e CHANEL_TITLE_PATTERN="Your Title Pattern %s" \
           -e CLICKHOUSE_URL=jdbc:clickhouse://your-clickhouse-host:8123/default \
           current-country-updater
```

## License

This project is licensed under the MIT License - see the LICENSE file for details.
## OwnTracks

Point the OwnTracks app (HTTP mode) at `POST /pub` with the `LOCATION_USER`/`LOCATION_PASSWORD` Basic credentials. It replaces the OwnTracks Recorder: geohash-7, OpenCage reverse geocoding cached per cell in ClickHouse (`geo_cache`), time zone from OpenCage, reply `[]`, and 503 (phone retries) when geocoding or the save fails.

## Geocoding quota

Every OpenCage request is logged to `country_days_tracker_bot.geo_api_usage` (key = last 4 chars, outcome, OpenCage's `rate` block). When the key answers 402/429 or reports `remaining: 0` it is not called again until its reset, and meanwhile pings are stored **without** geocoding (empty country, tzname `Z`) instead of being rejected.

Pings at 250 km/h or faster (`vel`) are treated as in flight and not geocoded: they keep the country and timezone of the last ground ping (no city/address); the backfill skips them.

Both endpoints use the `/pub` Basic auth:

- `GET /geo/usage` -- per key id: requests today / 30 days, quota hits, errors, last reported limit/remaining/reset, blocked-until; plus cached cells and ungeocoded backlog.
- `POST /geo/backfill?limit=100` -- geocodes up to `limit` (max 2500) ungeocoded cells, oldest first, and rewrites their rows (country, tzname, per-row tz_offset, locality, addr). Stops at the first quota exhaustion and reports what is left.
