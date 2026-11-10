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

Two Gradle modules, one process and one container:

- `ingest` -- OwnTracks intake: `POST /pub`, the JSON Schema contract, OpenCage reverse geocoding, and the ClickHouse/Postgres storage with its migrations. It has no Telegram dependency.
- `bot` -- the Telegram bot (channel title, commands, `/map`) and the calendar feed. It hosts the ingest routes on its HTTP server and builds the image (`./gradlew jibDockerBuild`).

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

Point the OwnTracks app (HTTP mode) at `POST /pub` with the `LOCATION_USER`/`LOCATION_PASSWORD` Basic credentials. It replaces the OwnTracks Recorder: geohash-7, OpenCage reverse geocoding cached per cell in Postgres (`geo_cache`), time zone from OpenCage, reply `[]`, and 503 (phone retries) when geocoding or the save fails.
