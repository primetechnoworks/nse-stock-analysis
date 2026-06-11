package com.nse.stockfetcher.controller;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nse.stockfetcher.exception.StockNotFoundException;
import com.nse.stockfetcher.model.StockData;
import com.nse.stockfetcher.model.StockQuote;
import com.nse.stockfetcher.service.StockService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller exposing NSE stock market data endpoints.
 */
@RestController
@RequestMapping("/api/stocks")
@CrossOrigin(origins = "*")
@Tag(name = "NSE Stock Data", description = "Endpoints for fetching real-time and historical NSE stock/index data")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Real-time Quote
    // ─────────────────────────────────────────────────────────────────────────
    @Operation(
            summary = "Get real-time stock quote",
            description = "Fetches the latest real-time quote for the given NSE stock symbol (e.g. `RELIANCE`, `TCS`, `INFY`)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Quote fetched successfully",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = StockQuote.class))),
        @ApiResponse(responseCode = "500", description = "Failed to fetch quote",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(example = "{\"error\":\"Failed to fetch quote for XYZ\",\"message\":\"...\"}")))
    })
    @GetMapping("/quote/{symbol}")
    public ResponseEntity<?> getQuote(
            @Parameter(description = "NSE stock symbol", example = "RELIANCE", required = true)
            @PathVariable String symbol) {
        try {
            StockQuote quote = stockService.getQuote(symbol);
            return ResponseEntity.ok(quote);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to fetch quote for " + symbol,
                            "message", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Historical Data — by date range
    // ─────────────────────────────────────────────────────────────────────────
    @Operation(
            summary = "Get historical OHLCV data by date range",
            description = "Returns historical daily (or intraday) OHLCV data for a stock between two dates."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Historical data fetched successfully",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        array = @ArraySchema(schema = @Schema(implementation = StockData.class)))),
        @ApiResponse(responseCode = "500", description = "Failed to fetch historical data",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(example = "{\"error\":\"Failed to fetch historical data\",\"message\":\"...\"}")))
    })
    @GetMapping("/historical/{symbol}")
    public ResponseEntity<?> getHistorical(
            @Parameter(description = "NSE stock symbol", example = "TCS", required = true)
            @PathVariable String symbol,
            @Parameter(description = "Start date in `yyyy-MM-dd` format", example = "2024-01-01", required = true)
            @RequestParam String from,
            @Parameter(description = "End date in `yyyy-MM-dd` format", example = "2024-06-30", required = true)
            @RequestParam String to,
            @Parameter(description = "Data interval. Supported: `1d` (daily)", example = "1d", schema = @Schema(defaultValue = "1d"))
            @RequestParam(defaultValue = "1d") String interval) {
        try {
            LocalDate fromDate = LocalDate.parse(from);
            LocalDate toDate = LocalDate.parse(to);
            List<StockData> data = stockService.getHistoricalData(symbol, fromDate, toDate, interval);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to fetch historical data",
                            "message", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Historical Data — by range shorthand
    // ─────────────────────────────────────────────────────────────────────────
    @Operation(
            summary = "Get historical OHLCV data by period range",
            description = """
            Returns historical data for a stock using a shorthand period range.
            
            **Supported range values:** `1d`, `5d`, `1mo`, `3mo`, `6mo`, `1y`, `2y`, `5y`, `10y`, `ytd`, `max`
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Historical data fetched successfully",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        array = @ArraySchema(schema = @Schema(implementation = StockData.class)))),
        @ApiResponse(responseCode = "500", description = "Failed to fetch historical data",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(example = "{\"error\":\"Failed to fetch historical data\",\"message\":\"...\"}")))
    })
    @GetMapping("/historical/{symbol}/range")
    public ResponseEntity<?> getHistoricalByRange(
            @Parameter(description = "NSE stock symbol", example = "INFY", required = true)
            @PathVariable String symbol,
            @Parameter(description = "Period range shorthand", example = "6mo",
                    schema = @Schema(defaultValue = "6mo",
                            allowableValues = {"1d", "5d", "1mo", "3mo", "6mo", "1y", "2y", "5y", "10y", "ytd", "max"}))
            @RequestParam(defaultValue = "6mo") String range,
            @Parameter(description = "Data interval", example = "1d", schema = @Schema(defaultValue = "1d"))
            @RequestParam(defaultValue = "1d") String interval) {
        try {
            List<StockData> data = stockService.getHistoricalDataByRange(symbol, range, interval);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to fetch historical data",
                            "message", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Index Quote
    // ─────────────────────────────────────────────────────────────────────────
    @Operation(
            summary = "Get NSE index quote",
            description = """
            Returns the current quote for an NSE index.
            
            Use hyphens or underscores as word separators — they are converted to spaces automatically.
            
            **Examples:** `NIFTY-50`, `NIFTY_BANK`, `NIFTY-IT`
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Index quote fetched successfully",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = StockQuote.class))),
        @ApiResponse(responseCode = "500", description = "Failed to fetch index data",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(example = "{\"error\":\"Failed to fetch index data for XYZ\",\"message\":\"...\"}")))
    })
    @GetMapping("/index/{indexName}")
    public ResponseEntity<?> getIndex(
            @Parameter(description = "Index name (hyphens/underscores converted to spaces)", example = "NIFTY-50", required = true)
            @PathVariable String indexName) {
        try {
            // Replace hyphens or underscores with spaces for convenience
            // e.g., "NIFTY-50" or "NIFTY_50" → "NIFTY 50"
            String normalizedName = indexName.replace("-", " ").replace("_", " ");
            StockQuote quote = stockService.getIndexQuote(normalizedName);
            return ResponseEntity.ok(quote);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to fetch index data for " + indexName,
                            "message", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bulk Quotes
    // ─────────────────────────────────────────────────────────────────────────
    @Operation(
            summary = "Get bulk real-time quotes",
            description = "Fetches real-time quotes for multiple NSE stock symbols in a single request. Symbols are comma-separated."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bulk quotes fetched successfully",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        array = @ArraySchema(schema = @Schema(implementation = StockQuote.class)))),
        @ApiResponse(responseCode = "500", description = "Failed to fetch bulk quotes",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(example = "{\"error\":\"Failed to fetch bulk quotes\",\"message\":\"...\"}")))
    })
    @GetMapping("/bulk-quotes")
    public ResponseEntity<?> getBulkQuotes(
            @Parameter(description = "Comma-separated list of NSE stock symbols", example = "RELIANCE,TCS,INFY", required = true)
            @RequestParam String symbols) {
        try {
            List<String> symbolList = Arrays.asList(symbols.split(","));
            List<StockQuote> quotes = stockService.getMultipleQuotes(symbolList);
            return ResponseEntity.ok(quotes);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to fetch bulk quotes",
                            "message", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Symbol Search
    // ─────────────────────────────────────────────────────────────────────────
    @Operation(
            summary = "Search stock symbols",
            description = "Returns a list of NSE stock symbols and company names matching the search query string."
    )
    @ApiResponse(responseCode = "200", description = "Search results returned",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(example = "[{\"symbol\":\"RELIANCE\",\"name\":\"Reliance Industries Ltd\"}]")))
    @GetMapping("/search")
    public ResponseEntity<?> search(
            @Parameter(description = "Search query string (partial symbol or company name)", example = "REL")
            @RequestParam(defaultValue = "") String q) {
        List<Map<String, String>> results = stockService.searchSymbols(q);
        if (results == null || results.isEmpty()) {
            throw new StockNotFoundException("No results found for query: " + q);
        }
        return ResponseEntity.ok(results);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Market Overview
    // ─────────────────────────────────────────────────────────────────────────
    @Operation(
            summary = "Get market overview",
            description = "Returns a high-level snapshot of the current NSE market, including major index levels and market status."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Market overview fetched successfully",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(type = "object"))),
        @ApiResponse(responseCode = "500", description = "Failed to fetch market overview",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(example = "{\"error\":\"Failed to fetch market overview\",\"message\":\"...\"}")))
    })
    @GetMapping("/market-overview")
    public ResponseEntity<?> getMarketOverview() {
        try {
            Map<String, Object> overview = stockService.getMarketOverview();
            return ResponseEntity.ok(overview);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to fetch market overview",
                            "message", e.getMessage()));
        }
    }
}
