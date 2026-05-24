import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import javax.sql.DataSource

object PostgresService {

    val dataSource: DataSource = initialize()

    private fun initialize(): DataSource {
        val hikari = HikariConfig().apply {
            jdbcUrl = Config.DATABASE_URL
            maximumPoolSize = 2
            driverClassName = "org.postgresql.Driver"
        }
        val ds = HikariDataSource(hikari)

        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/postgres/migration")
            .baselineOnMigrate(true)
            .load()
            .migrate()

        return ds
    }
}
