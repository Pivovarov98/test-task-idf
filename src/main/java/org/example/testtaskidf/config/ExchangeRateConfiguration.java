package org.example.testtaskidf.config;

import java.time.Duration;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/** The HTTP client is used by background workers, never by transaction reception. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ExchangeRateConfiguration {
    @Bean
    public WebClient exchangeRateWebClient(@Value("${exchange-rates.base-url}") String baseUrl) {
        var http = HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                .responseTimeout(Duration.ofSeconds(5));
        return WebClient.builder().baseUrl(baseUrl).clientConnector(new ReactorClientHttpConnector(http))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(1024 * 1024)).build();
    }
}
