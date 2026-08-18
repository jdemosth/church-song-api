package com.churchsong;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
class TestDatabaseIsolationConfiguration {

    private final Environment environment;

    TestDatabaseIsolationConfiguration(
            Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void verifyTestDatasourceIsolation() {
        String datasourceUrl =
                environment.getProperty(
                        "spring.datasource.url",
                        ""
                );

        if (datasourceUrl.isBlank()) {
            throw new IllegalStateException(
                    "Test datasource URL is not configured."
            );
        }

        if (datasourceUrl.equals("jdbc:sqlite:churchsongs.db")
                || datasourceUrl.endsWith("/churchsongs.db")
                || datasourceUrl.contains("\\churchsongs.db")) {
            throw new IllegalStateException(
                    "Unsafe test datasource detected: "
                            + datasourceUrl
                            + ". Tests must never use the normal application database."
            );
        }
    }
}
