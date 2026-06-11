package com.nse.stockfetcher.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configures the OpenAPI 3.0 specification metadata used by Swagger UI.
 * Swagger UI is available at: /swagger-ui.html
 * Raw OpenAPI JSON is at:     /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI nseStockFetcherOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("NSE Stock Fetcher API")
                        .description("""
                                REST API for fetching real-time and historical stock market data \
                                from the National Stock Exchange of India (NSE).
                                
                                **Features:**
                                - Real-time stock quotes
                                - Historical OHLCV data (by date range or period)
                                - NSE index quotes (NIFTY 50, NIFTY BANK, etc.)
                                - Bulk quote fetching
                                - Symbol search
                                - Market overview
                                """)
                        .version("2.0.0")
                        .contact(new Contact()
                                .name("NSE Stock Fetcher")
                                .url("https://github.com/nse-stock-fetcher"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development Server")
                ));
    }
}
