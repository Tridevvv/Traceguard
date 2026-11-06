package com.traceguard.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AppConfig {

    /** One shared clock (UTC) so date windows in analytics and reports are consistent and replaceable in tests. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
