package com.traceguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ConfigurationPropertiesScan
// Several small repositories are grouped as nested interfaces (OrgRepositories, CatalogRepositories).
@EnableJpaRepositories(considerNestedRepositories = true)
public class TraceGuardApplication {

    public static void main(String[] args) {
        SpringApplication.run(TraceGuardApplication.class, args);
    }
}
