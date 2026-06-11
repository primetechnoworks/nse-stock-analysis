package com.nse.stockfetcher.service;

import com.nse.stockfetcher.model.StockData;
import com.nse.stockfetcher.model.StockQuote;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Spring-managed service wrapping YahooFinanceService for the REST API.
 */
@Service
public class StockService {

    private YahooFinanceService yahooFinance;

    /** Popular NSE symbols for search autocomplete */
    private static final List<Map<String, String>> NSE_SYMBOLS = List.of(
        Map.of("symbol", "RELIANCE",    "name", "Reliance Industries Ltd"),
        Map.of("symbol", "TCS",         "name", "Tata Consultancy Services Ltd"),
        Map.of("symbol", "INFY",        "name", "Infosys Ltd"),
        Map.of("symbol", "HDFCBANK",    "name", "HDFC Bank Ltd"),
        Map.of("symbol", "ICICIBANK",   "name", "ICICI Bank Ltd"),
        Map.of("symbol", "SBIN",        "name", "State Bank of India"),
        Map.of("symbol", "HINDUNILVR",  "name", "Hindustan Unilever Ltd"),
        Map.of("symbol", "ITC",         "name", "ITC Ltd"),
        Map.of("symbol", "BHARTIARTL",  "name", "Bharti Airtel Ltd"),
        Map.of("symbol", "KOTAKBANK",   "name", "Kotak Mahindra Bank Ltd"),
        Map.of("symbol", "LT",          "name", "Larsen & Toubro Ltd"),
        Map.of("symbol", "AXISBANK",    "name", "Axis Bank Ltd"),
        Map.of("symbol", "MARUTI",      "name", "Maruti Suzuki India Ltd"),
        Map.of("symbol", "HCLTECH",     "name", "HCL Technologies Ltd"),
        Map.of("symbol", "WIPRO",       "name", "Wipro Ltd"),
        Map.of("symbol", "SUNPHARMA",   "name", "Sun Pharmaceutical Industries Ltd"),
        Map.of("symbol", "TITAN",       "name", "Titan Company Ltd"),
        Map.of("symbol", "BAJFINANCE",  "name", "Bajaj Finance Ltd"),
        Map.of("symbol", "ASIANPAINT",  "name", "Asian Paints Ltd"),
        Map.of("symbol", "NESTLEIND",   "name", "Nestle India Ltd"),
        Map.of("symbol", "ULTRACEMCO",  "name", "UltraTech Cement Ltd"),
        Map.of("symbol", "ADANIENT",    "name", "Adani Enterprises Ltd"),
        Map.of("symbol", "ADANIPORTS",  "name", "Adani Ports and SEZ Ltd"),
        Map.of("symbol", "TECHM",       "name", "Tech Mahindra Ltd"),
        Map.of("symbol", "ONGC",        "name", "Oil and Natural Gas Corporation Ltd"),
        Map.of("symbol", "NTPC",        "name", "NTPC Ltd"),
        Map.of("symbol", "POWERGRID",   "name", "Power Grid Corporation of India Ltd"),
        Map.of("symbol", "COALINDIA",   "name", "Coal India Ltd"),
        Map.of("symbol", "TATAMOTORS",  "name", "Tata Motors Ltd"),
        Map.of("symbol", "TATASTEEL",   "name", "Tata Steel Ltd"),
        Map.of("symbol", "JSWSTEEL",    "name", "JSW Steel Ltd"),
        Map.of("symbol", "HINDALCO",    "name", "Hindalco Industries Ltd"),
        Map.of("symbol", "DRREDDY",     "name", "Dr. Reddy's Laboratories Ltd"),
        Map.of("symbol", "CIPLA",       "name", "Cipla Ltd"),
        Map.of("symbol", "HEROMOTOCO",  "name", "Hero MotoCorp Ltd"),
        Map.of("symbol", "EICHERMOT",   "name", "Eicher Motors Ltd"),
        Map.of("symbol", "M&M",         "name", "Mahindra & Mahindra Ltd"),
        Map.of("symbol", "DIVISLAB",    "name", "Divi's Laboratories Ltd"),
        Map.of("symbol", "APOLLOHOSP",  "name", "Apollo Hospitals Enterprise Ltd"),
        Map.of("symbol", "BPCL",        "name", "Bharat Petroleum Corporation Ltd"),
        Map.of("symbol", "GRASIM",      "name", "Grasim Industries Ltd"),
        Map.of("symbol", "BRITANNIA",   "name", "Britannia Industries Ltd"),
        Map.of("symbol", "TATACONSUM",  "name", "Tata Consumer Products Ltd"),
        Map.of("symbol", "SBILIFE",     "name", "SBI Life Insurance Company Ltd"),
        Map.of("symbol", "HDFCLIFE",    "name", "HDFC Life Insurance Company Ltd"),
        Map.of("symbol", "BAJAJFINSV",  "name", "Bajaj Finserv Ltd"),
        Map.of("symbol", "INDUSINDBK",  "name", "IndusInd Bank Ltd"),
        Map.of("symbol", "SHRIRAMFIN",  "name", "Shriram Finance Ltd"),
        Map.of("symbol", "LTIM",        "name", "LTIMindtree Ltd"),
        Map.of("symbol", "UPL",         "name", "UPL Ltd")
    );

    @PostConstruct
    public void init() {
        this.yahooFinance = new YahooFinanceService();
    }

    @PreDestroy
    public void cleanup() {
        if (yahooFinance != null) {
            yahooFinance.close();
        }
    }

    public StockQuote getQuote(String symbol) throws IOException {
        return yahooFinance.getQuote(symbol);
    }

    public List<StockData> getHistoricalData(String symbol, LocalDate from,
                                              LocalDate to, String interval) throws IOException {
        return yahooFinance.getHistoricalData(symbol, from, to, interval);
    }

    public List<StockData> getHistoricalDataByRange(String symbol, String range,
                                                     String interval) throws IOException {
        return yahooFinance.getHistoricalDataByRange(symbol, range, interval);
    }

    public StockQuote getIndexQuote(String indexName) throws IOException {
        return yahooFinance.getIndexQuote(indexName);
    }

    public List<StockQuote> getMultipleQuotes(List<String> symbols) throws IOException {
        return yahooFinance.getMultipleQuotes(symbols);
    }

    /**
     * Searches the hardcoded symbol list by symbol or company name (case-insensitive).
     */
    public List<Map<String, String>> searchSymbols(String query) {
        if (query == null || query.isBlank()) {
            return NSE_SYMBOLS.stream().limit(10).collect(Collectors.toList());
        }
        String q = query.toUpperCase().trim();
        return NSE_SYMBOLS.stream()
            .filter(entry ->
                entry.get("symbol").toUpperCase().contains(q) ||
                entry.get("name").toUpperCase().contains(q))
            .limit(10)
            .collect(Collectors.toList());
    }

    /**
     * Fetches a market overview: NIFTY 50, NIFTY BANK, and top 10 stocks.
     */
    public Map<String, Object> getMarketOverview() throws IOException {
        Map<String, Object> overview = new LinkedHashMap<>();

        try { overview.put("nifty50", yahooFinance.getIndexQuote("NIFTY 50")); }
        catch (Exception e) { overview.put("nifty50", null); }

        try { overview.put("niftyBank", yahooFinance.getIndexQuote("NIFTY BANK")); }
        catch (Exception e) { overview.put("niftyBank", null); }

        List<String> topSymbols = List.of(
            "RELIANCE", "TCS", "HDFCBANK", "INFY", "ICICIBANK",
            "HINDUNILVR", "ITC", "SBIN", "BHARTIARTL", "KOTAKBANK"
        );
        overview.put("topStocks", yahooFinance.getMultipleQuotes(topSymbols));

        return overview;
    }
}
