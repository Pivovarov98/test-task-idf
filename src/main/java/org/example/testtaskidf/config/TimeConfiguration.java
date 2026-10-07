package org.example.testtaskidf.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides the clock used to assign transaction receipt timestamps. */
@Configuration
public class TimeConfiguration {
    /**
     * Supplies a UTC clock that tests can replace with a fixed clock.
     *
     * @return system clock in UTC
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
