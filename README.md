# Current Country Updater

A Telegram bot that automatically updates a channel's title and adds location tags to messages based on the current country and city information.

## Features

- Automatically updates the channel title with the current country flag emoji
- Adds country and city hashtags to all messages in the channel
- Periodically checks for country changes (every 10 minutes)
- Supports various media types (photos, videos, text messages)

## Requirements

- JDK 22 or higher
- Gradle 8.0 or higher
- ClickHouse database with country tracking data
- Telegram Bot API token

## Configuration

The application uses environment variables for configuration:

| Variable | Description |
|----------|-------------|
| `CHANEL_ID` | The ID of the Telegram channel to monitor and update |
| `CHANEL_TITLE_PATTERN` | The pattern for the channel title (e.g., "Travel Blog %s") |
| `CLICKHOUSE_URL` | The JDBC URL for the ClickHouse database |

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