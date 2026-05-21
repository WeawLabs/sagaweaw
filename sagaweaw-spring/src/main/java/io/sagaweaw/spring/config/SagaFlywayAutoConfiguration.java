package io.sagaweaw.spring.config;

import org.flywaydb.core.Flyway;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Locale;

@AutoConfiguration(before = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass(Flyway.class)
@ConditionalOnProperty(prefix = "sagaweaw.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SagaFlywayAutoConfiguration {

    // Separate Flyway instance — uses its own history table so it never conflicts
    // with the application's own Flyway migrations.
    @Bean(name = "sagaweawFlyway", initMethod = "migrate")
    @ConditionalOnMissingBean(name = "sagaweawFlyway")
    public Flyway sagaweawFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/sagaweaw/" + detectVendor(dataSource))
                .table("sagaweaw_schema_history")
                .load();
    }

    // Flyway 10 does not resolve {vendor} in programmatic location paths.
    private static String detectVendor(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String name = meta.getDatabaseProductName().toLowerCase(Locale.ROOT);
            if (name.contains("postgresql"))                         return "postgresql";
            if (name.contains("h2"))                                 return "h2";
            if (name.contains("mysql") || name.contains("mariadb")) return "mysql";
        } catch (Exception e) {
            LoggerFactory.getLogger(SagaFlywayAutoConfiguration.class)
                    .warn("Could not detect database vendor, defaulting to postgresql", e);
        }
        return "postgresql";
    }
}
