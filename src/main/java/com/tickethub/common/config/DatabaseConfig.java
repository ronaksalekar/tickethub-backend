package com.tickethub.common.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
@Slf4j
public class DatabaseConfig {

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        // 1. Check for standard cloud DATABASE_URL (Railway, Render, Heroku)
        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            databaseUrl = System.getenv("SPRING_DATASOURCE_URL");
        }

        if (databaseUrl != null && !databaseUrl.isBlank() && (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://"))) {
            try {
                URI uri = new URI(databaseUrl);
                String host = uri.getHost();
                int port = uri.getPort() == -1 ? 5432 : uri.getPort();
                String path = uri.getPath(); // e.g. /railway
                String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path;

                HikariConfig config = new HikariConfig();
                config.setJdbcUrl(jdbcUrl);
                config.setDriverClassName("org.postgresql.Driver");

                String user = "postgres";
                if (uri.getUserInfo() != null) {
                    String[] userInfo = uri.getUserInfo().split(":", 2);
                    user = userInfo[0];
                    config.setUsername(user);
                    if (userInfo.length > 1) {
                        config.setPassword(userInfo[1]);
                    }
                }

                config.setMaximumPoolSize(10);
                config.setConnectionTimeout(15000);

                log.info("Connecting to PostgreSQL via cloud URL: {}:{}{} (user: {})", host, port, path, user);
                return new HikariDataSource(config);
            } catch (Exception e) {
                log.warn("Failed to parse DATABASE_URL URI, falling back to standard datasource properties: {}", e.getMessage());
            }
        }

        // 2. Standard fallback to Spring application.yml datasource properties
        log.info("Connecting to PostgreSQL via configured JDBC URL: {}", properties.getUrl());
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }
}
