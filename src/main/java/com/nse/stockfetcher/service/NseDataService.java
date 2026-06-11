package com.nse.stockfetcher.service;

import com.google.gson.*;
import com.nse.stockfetcher.http.NseHttpClient;
import com.nse.stockfetcher.model.IndexData;
import com.nse.stockfetcher.model.StockData;
import com.nse.stockfetcher.model.StockQuote;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Core service to fetch stock and index data from NSE India's internal APIs.
 *
 * <h3>API Endpoints Used:</h3>
 * <ul>
 *   <li>{@code /api/quote-equity?symbol=SYMBOL} — Real-time equity quote</li>
 *   <li>{@code /api/historical/cm/equity?symbol=SYMBOL&from=DD-MM-YYYY&to=DD-MM-YYYY} — Historical OHLCV</li>
 *   <li>{@code /api/allIndices} — All index data</li>
 *   <li>{@code /api/equity-stockIndices?index=INDEXNAME} — Stocks in an index</li>
 *   <li>{@code /api/market-data-pre-open?key=NIFTY} — Pre-open market data</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * NseHttpClient client = new NseHttpClient();
 * client.initSession();
 * NseDataService service = new NseDataService(client);
 *
 * StockQuote quote = service.getQuote("RELIANCE");
 * List<StockData> history = service.getHistoricalData("TCS",
 *     LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 1));
 * }</pre>
 */
public class NseDataService {

    private static final String BASE_API = "https://www.nseindia.com/api";
    private static final DateTimeFormatter NSE_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter PARSE_DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final NseHttpClient httpClient;

    public NseDataService(NseHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    // ═══════════════════════════════════════════════════════════════
    //  REAL-TIME QUOTE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Fetches real-time quote for an equity symbol.
     *
     * @param symbol NSE symbol (e.g., "RELIANCE", "TCS", "INFY")
     * @return StockQuote with current market data
     * @throws IOException if the API call fails
     */
    public StockQuote getQuote(String symbol) throws IOException {
        String url = BASE_API + "/quote-equity?symbol=" + encodeSymbol(symbol);
        String json = httpClient.get(url);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject priceInfo = root.getAsJsonObject("priceInfo");
        JsonObject info = root.getAsJsonObject("info");

        StockQuote quote = new StockQuote();
        quote.setSymbol(symbol.toUpperCase());

        // Company info
        if (info != null) {
            quote.setCompanyName(getStringOrDefault(info, "companyName", ""));
            quote.setIndustry(getStringOrDefault(info, "industry", ""));
            quote.setIsin(getStringOrDefault(info, "isin", ""));
        }

        // Series from metadata
        JsonObject metadata = root.getAsJsonObject("metadata");
        if (metadata != null) {
            quote.setSeries(getStringOrDefault(metadata, "series", "EQ"));
        }

        // Price data
        if (priceInfo != null) {
            quote.setLastPrice(getDoubleOrDefault(priceInfo, "lastPrice", 0));
            quote.setOpen(getDoubleOrDefault(priceInfo, "open", 0));
            quote.setHigh(getDoubleOrDefault(priceInfo, "intraDayHighLow", "max", 0));
            quote.setLow(getDoubleOrDefault(priceInfo, "intraDayHighLow", "min", 0));
            quote.setPreviousClose(getDoubleOrDefault(priceInfo, "previousClose", 0));
            quote.setChange(getDoubleOrDefault(priceInfo, "change", 0));
            quote.setChangePercent(getDoubleOrDefault(priceInfo, "pChange", 0));

            // 52 week range
            JsonObject weekHL = priceInfo.getAsJsonObject("weekHighLow");
            if (weekHL != null) {
                quote.setYearHigh(getDoubleOrDefault(weekHL, "max", 0));
                quote.setYearLow(getDoubleOrDefault(weekHL, "min", 0));
            }

            // Circuit limits
            quote.setUpperCircuitLimit(getDoubleOrDefault(priceInfo, "upperCP", 0));
            quote.setLowerCircuitLimit(getDoubleOrDefault(priceInfo, "lowerCP", 0));
        }

        // Security-wise delivery (volume data)
        JsonObject securityWise = root.getAsJsonObject("securityWiseDP");
        if (securityWise != null) {
            quote.setTotalTradedVolume(getLongOrDefault(securityWise, "quantityTraded", 0));
            quote.setDeliveryToTradedQty(getDoubleOrDefault(securityWise, "deliveryToTradedQuantity", 0));
        }

        return quote;
    }

    // ═══════════════════════════════════════════════════════════════
    //  HISTORICAL DATA
    // ═══════════════════════════════════════════════════════════════

    /**
     * Fetches historical OHLCV data for a stock within a date range.
     *
     * <p><strong>Note:</strong> NSE limits historical data to ~90 days per request.
     * This method automatically splits larger date ranges into chunks and
     * merges the results.</p>
     *
     * @param symbol NSE symbol (e.g., "RELIANCE")
     * @param from   Start date (inclusive)
     * @param to     End date (inclusive)
     * @return List of StockData sorted by date ascending
     * @throws IOException if any API call fails
     */
    public List<StockData> getHistoricalData(String symbol, LocalDate from, LocalDate to)
            throws IOException {

        List<StockData> allData = new ArrayList<>();

        // NSE limits to ~90 days per request, so we chunk larger ranges
        LocalDate chunkStart = from;
        while (!chunkStart.isAfter(to)) {
            LocalDate chunkEnd = chunkStart.plusDays(89);
            if (chunkEnd.isAfter(to)) {
                chunkEnd = to;
            }

            String url = String.format(
                "%s/historical/cm/equity?symbol=%s&series=[\"EQ\"]&from=%s&to=%s",
                BASE_API, encodeSymbol(symbol),
                chunkStart.format(NSE_DATE_FMT),
                chunkEnd.format(NSE_DATE_FMT)
            );

            System.out.printf("[Historical] Fetching %s: %s to %s%n",
                symbol, chunkStart, chunkEnd);

            String json = httpClient.get(url);
            List<StockData> chunk = parseHistoricalResponse(json, symbol);
            allData.addAll(chunk);

            chunkStart = chunkEnd.plusDays(1);
        }

        // Sort by date ascending
        allData.sort((a, b) -> a.getDate().compareTo(b.getDate()));
        return allData;
    }

    /**
     * Parses the JSON response from the historical data API.
     */
    private List<StockData> parseHistoricalResponse(String json, String symbol) {
        List<StockData> dataList = new ArrayList<>();

        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray dataArray = root.getAsJsonArray("data");

            if (dataArray == null || dataArray.isEmpty()) {
                System.out.println("[Historical] No data found in response.");
                return dataList;
            }

            for (JsonElement element : dataArray) {
                JsonObject record = element.getAsJsonObject();

                StockData sd = new StockData();
                sd.setSymbol(symbol.toUpperCase());

                // Parse date — NSE returns "20-Mar-2024" format
                String dateStr = getStringOrDefault(record, "CH_TIMESTAMP", "");
                if (!dateStr.isEmpty()) {
                    try {
                        sd.setDate(LocalDate.parse(dateStr, PARSE_DATE_FMT));
                    } catch (Exception e) {
                        // Try alternate format
                        sd.setDate(LocalDate.parse(dateStr));
                    }
                }

                sd.setOpen(getDoubleOrDefault(record, "CH_OPENING_PRICE", 0));
                sd.setHigh(getDoubleOrDefault(record, "CH_TRADE_HIGH_PRICE", 0));
                sd.setLow(getDoubleOrDefault(record, "CH_TRADE_LOW_PRICE", 0));
                sd.setClose(getDoubleOrDefault(record, "CH_CLOSING_PRICE", 0));
                sd.setPreviousClose(getDoubleOrDefault(record, "CH_PREVIOUS_CLS_PRICE", 0));
                sd.setVolume(getLongOrDefault(record, "CH_TOT_TRADED_QTY", 0));
                sd.setTurnover(getDoubleOrDefault(record, "CH_TOT_TRADED_VAL", 0));
                sd.setVwap(getDoubleOrDefault(record, "VWAP", 0));

                // CHS_52_WEEK_HIGH/LOW may exist
                // CH_52WEEK_HIGH_PRICE / CH_52WEEK_LOW_PRICE as well

                dataList.add(sd);
            }
        } catch (JsonSyntaxException e) {
            System.err.println("[Historical] Failed to parse JSON: " + e.getMessage());
        }

        return dataList;
    }

    // ═══════════════════════════════════════════════════════════════
    //  INDEX DATA
    // ═══════════════════════════════════════════════════════════════

    /**
     * Fetches data for all NSE indices.
     *
     * @return List of IndexData for all active indices
     * @throws IOException if the API call fails
     */
    public List<IndexData> getAllIndices() throws IOException {
        String url = BASE_API + "/allIndices";
        String json = httpClient.get(url);

        List<IndexData> indices = new ArrayList<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray dataArray = root.getAsJsonArray("data");

        if (dataArray != null) {
            for (JsonElement element : dataArray) {
                JsonObject record = element.getAsJsonObject();
                IndexData idx = new IndexData();
                idx.setIndexName(getStringOrDefault(record, "index", ""));
                idx.setLastPrice(getDoubleOrDefault(record, "last", 0));
                idx.setOpen(getDoubleOrDefault(record, "open", 0));
                idx.setHigh(getDoubleOrDefault(record, "high", 0));
                idx.setLow(getDoubleOrDefault(record, "low", 0));
                idx.setPreviousClose(getDoubleOrDefault(record, "previousClose", 0));
                idx.setChange(getDoubleOrDefault(record, "variation", 0));
                idx.setChangePercent(getDoubleOrDefault(record, "percentChange", 0));
                idx.setAdvances(getIntOrDefault(record, "advances", 0));
                idx.setDeclines(getIntOrDefault(record, "declines", 0));
                idx.setUnchanged(getIntOrDefault(record, "unchanged", 0));
                indices.add(idx);
            }
        }

        return indices;
    }

    /**
     * Fetches a specific index by name (e.g., "NIFTY 50", "NIFTY BANK").
     *
     * @param indexName Exact name of the index
     * @return IndexData or null if not found
     * @throws IOException if the API call fails
     */
    public IndexData getIndex(String indexName) throws IOException {
        List<IndexData> all = getAllIndices();
        return all.stream()
            .filter(i -> i.getIndexName().equalsIgnoreCase(indexName))
            .findFirst()
            .orElse(null);
    }

    /**
     * Fetches all stocks in a given index.
     *
     * @param indexName Exact index name (e.g., "NIFTY 50")
     * @return List of StockQuote for index constituents
     * @throws IOException if the API call fails
     */
    public List<StockQuote> getIndexStocks(String indexName) throws IOException {
        String url = BASE_API + "/equity-stockIndices?index=" +
            encodeSymbol(indexName);
        String json = httpClient.get(url);

        List<StockQuote> stocks = new ArrayList<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray dataArray = root.getAsJsonArray("data");

        if (dataArray != null) {
            for (JsonElement element : dataArray) {
                JsonObject record = element.getAsJsonObject();
                StockQuote q = new StockQuote();
                q.setSymbol(getStringOrDefault(record, "symbol", ""));
                q.setCompanyName(getStringOrDefault(record, "meta", "companyName", ""));
                q.setLastPrice(getDoubleOrDefault(record, "lastPrice", 0));
                q.setOpen(getDoubleOrDefault(record, "open", 0));
                q.setHigh(getDoubleOrDefault(record, "dayHigh", 0));
                q.setLow(getDoubleOrDefault(record, "dayLow", 0));
                q.setPreviousClose(getDoubleOrDefault(record, "previousClose", 0));
                q.setChange(getDoubleOrDefault(record, "change", 0));
                q.setChangePercent(getDoubleOrDefault(record, "pChange", 0));
                q.setTotalTradedVolume(getLongOrDefault(record, "totalTradedVolume", 0));
                q.setYearHigh(getDoubleOrDefault(record, "yearHigh", 0));
                q.setYearLow(getDoubleOrDefault(record, "yearLow", 0));
                stocks.add(q);
            }
        }

        return stocks;
    }

    // ═══════════════════════════════════════════════════════════════
    //  PRE-OPEN MARKET DATA
    // ═══════════════════════════════════════════════════════════════

    /**
     * Fetches pre-open market data (available 9:00 AM - 9:15 AM IST).
     *
     * @param key One of: "NIFTY", "BANKNIFTY", "SME", "FO", "OTHERS", "ALL"
     * @return List of StockQuote with pre-open prices
     * @throws IOException if the API call fails
     */
    public List<StockQuote> getPreOpenData(String key) throws IOException {
        String url = BASE_API + "/market-data-pre-open?key=" + encodeSymbol(key);
        String json = httpClient.get(url);

        List<StockQuote> stocks = new ArrayList<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray dataArray = root.getAsJsonArray("data");

        if (dataArray != null) {
            for (JsonElement element : dataArray) {
                JsonObject wrapper = element.getAsJsonObject();
                JsonObject metadata = wrapper.getAsJsonObject("metadata");

                if (metadata != null) {
                    StockQuote q = new StockQuote();
                    q.setSymbol(getStringOrDefault(metadata, "symbol", ""));
                    q.setLastPrice(getDoubleOrDefault(metadata, "lastPrice", 0));
                    q.setChange(getDoubleOrDefault(metadata, "change", 0));
                    q.setChangePercent(getDoubleOrDefault(metadata, "pChange", 0));
                    q.setPreviousClose(getDoubleOrDefault(metadata, "previousClose", 0));
                    stocks.add(q);
                }
            }
        }

        return stocks;
    }

    // ═══════════════════════════════════════════════════════════════
    //  UTILITY / JSON HELPERS
    // ═══════════════════════════════════════════════════════════════

    private String encodeSymbol(String symbol) {
        return symbol.replace("&", "%26").replace(" ", "%20");
    }

    private String getStringOrDefault(JsonObject obj, String key, String defaultVal) {
        JsonElement el = obj.get(key);
        return (el != null && !el.isJsonNull()) ? el.getAsString() : defaultVal;
    }

    private String getStringOrDefault(JsonObject obj, String outerKey, String innerKey, String defaultVal) {
        JsonElement outer = obj.get(outerKey);
        if (outer != null && outer.isJsonObject()) {
            return getStringOrDefault(outer.getAsJsonObject(), innerKey, defaultVal);
        }
        return defaultVal;
    }

    private double getDoubleOrDefault(JsonObject obj, String key, double defaultVal) {
        JsonElement el = obj.get(key);
        if (el != null && !el.isJsonNull()) {
            try {
                return el.getAsDouble();
            } catch (NumberFormatException e) {
                // Try parsing the string value (NSE sometimes returns "1,234.56")
                try {
                    return Double.parseDouble(el.getAsString().replace(",", ""));
                } catch (Exception ex) {
                    return defaultVal;
                }
            }
        }
        return defaultVal;
    }

    private double getDoubleOrDefault(JsonObject obj, String outerKey, String innerKey, double defaultVal) {
        JsonElement outer = obj.get(outerKey);
        if (outer != null && outer.isJsonObject()) {
            return getDoubleOrDefault(outer.getAsJsonObject(), innerKey, defaultVal);
        }
        return defaultVal;
    }

    private long getLongOrDefault(JsonObject obj, String key, long defaultVal) {
        JsonElement el = obj.get(key);
        if (el != null && !el.isJsonNull()) {
            try {
                return el.getAsLong();
            } catch (NumberFormatException e) {
                try {
                    return Long.parseLong(el.getAsString().replace(",", ""));
                } catch (Exception ex) {
                    return defaultVal;
                }
            }
        }
        return defaultVal;
    }

    private int getIntOrDefault(JsonObject obj, String key, int defaultVal) {
        JsonElement el = obj.get(key);
        if (el != null && !el.isJsonNull()) {
            try {
                return el.getAsInt();
            } catch (NumberFormatException e) {
                return defaultVal;
            }
        }
        return defaultVal;
    }
}
