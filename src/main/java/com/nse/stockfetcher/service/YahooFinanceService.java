package com.nse.stockfetcher.service;

import com.google.gson.*;
import com.nse.stockfetcher.model.StockData;
import com.nse.stockfetcher.model.StockQuote;
import okhttp3.*;

import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Reliable fallback service that fetches NSE stock data via Yahoo Finance.
 *
 * <h3>Why Yahoo Finance?</h3>
 * <ul>
 *   <li>No anti-bot protection — works reliably from any HTTP client</li>
 *   <li>No session/cookie management required</li>
 *   <li>Full NSE data available — append {@code .NS} to any NSE symbol</li>
 *   <li>Historical data going back years (not limited to 2 years like NSE)</li>
 *   <li>Supports multiple intervals: 1d, 1wk, 1mo</li>
 * </ul>
 *
 * <h3>Symbol Mapping:</h3>
 * <pre>
 * NSE Symbol     → Yahoo Symbol
 * RELIANCE       → RELIANCE.NS
 * TCS            → TCS.NS
 * NIFTY 50 Index → ^NSEI
 * NIFTY BANK     → ^NSEBANK
 * </pre>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * YahooFinanceService yf = new YahooFinanceService();
 *
 * // Real-time quote
 * StockQuote quote = yf.getQuote("RELIANCE");
 *
 * // Historical data (last 1 year)
 * List<StockData> history = yf.getHistoricalData("TCS",
 *     LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), "1d");
 *
 * // Index data
 * StockQuote nifty = yf.getIndexQuote("NIFTY 50");
 *
 * yf.close();
 * }</pre>
 */
public class YahooFinanceService {

    private static final String CHART_API = "https://query1.finance.yahoo.com/v8/finance/chart/";

    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private final OkHttpClient httpClient;
    private long lastRequestTime = 0;
    private static final long MIN_REQUEST_INTERVAL_MS = 300;

    public YahooFinanceService() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();
    }

    // ═══════════════════════════════════════════════════════════════
    //  REAL-TIME QUOTE
    // ═══════════════════════════════════════════════════════════════

    /**
     * Fetches the current quote for an NSE equity symbol.
     *
     * @param nseSymbol NSE symbol without suffix (e.g., "RELIANCE", "TCS")
     * @return StockQuote with latest market data
     * @throws IOException if the API call fails
     */
    public StockQuote getQuote(String nseSymbol) throws IOException {
        String yahooSymbol = toYahooSymbol(nseSymbol);
        String url = CHART_API + yahooSymbol + "?range=5d&interval=1d&includePrePost=false";

        String json = doGet(url);
        JsonObject chart = parseChartResult(json);

        JsonObject meta = chart.getAsJsonObject("meta");
        StockQuote quote = new StockQuote();
        quote.setSymbol(nseSymbol.toUpperCase());
        quote.setCompanyName(getStringOrDefault(meta, "shortName",
            getStringOrDefault(meta, "longName", nseSymbol)));

        quote.setLastPrice(getDoubleOrDefault(meta, "regularMarketPrice", 0));
        quote.setPreviousClose(getDoubleOrDefault(meta, "chartPreviousClose",
            getDoubleOrDefault(meta, "previousClose", 0)));
        quote.setChange(quote.getLastPrice() - quote.getPreviousClose());
        quote.setChangePercent(quote.getPreviousClose() > 0
            ? (quote.getChange() / quote.getPreviousClose()) * 100 : 0);

        // Get today's OHLCV from the last data point
        JsonArray timestamps = chart.getAsJsonArray("timestamp");
        JsonObject indicators = chart.getAsJsonObject("indicators");
        if (indicators != null && timestamps != null && timestamps.size() > 0) {
            JsonArray quoteArr = indicators.getAsJsonArray("quote");
            if (quoteArr != null && quoteArr.size() > 0) {
                JsonObject q = quoteArr.get(0).getAsJsonObject();
                int lastIdx = timestamps.size() - 1;

                quote.setOpen(getArrayDouble(q.getAsJsonArray("open"), lastIdx));
                quote.setHigh(getArrayDouble(q.getAsJsonArray("high"), lastIdx));
                quote.setLow(getArrayDouble(q.getAsJsonArray("low"), lastIdx));
                quote.setTotalTradedVolume(getArrayLong(q.getAsJsonArray("volume"), lastIdx));
            }
        }

        // 52-week high/low
        quote.setYearHigh(getDoubleOrDefault(meta, "fiftyTwoWeekHigh", 0));
        quote.setYearLow(getDoubleOrDefault(meta, "fiftyTwoWeekLow", 0));

        quote.setSeries("EQ");
        quote.setIsin(""); // Not available from Yahoo Finance

        return quote;
    }

    // ═══════════════════════════════════════════════════════════════
    //  HISTORICAL DATA
    // ═══════════════════════════════════════════════════════════════

    /**
     * Fetches historical OHLCV data for an NSE stock.
     *
     * @param nseSymbol NSE symbol (e.g., "RELIANCE")
     * @param from      Start date (inclusive)
     * @param to        End date (inclusive)
     * @param interval  Data interval: "1d" (daily), "1wk" (weekly), "1mo" (monthly)
     * @return List of StockData sorted by date ascending
     * @throws IOException if the API call fails
     */
    public List<StockData> getHistoricalData(String nseSymbol, LocalDate from,
                                              LocalDate to, String interval) throws IOException {
        String yahooSymbol = toYahooSymbol(nseSymbol);

        // Convert dates to Unix timestamps (IST timezone)
        long period1 = from.atStartOfDay(ZoneId.of("Asia/Kolkata")).toEpochSecond();
        long period2 = to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toEpochSecond();

        String url = String.format(
            "%s%s?period1=%d&period2=%d&interval=%s&includePrePost=false&events=div,splits",
            CHART_API, yahooSymbol, period1, period2, interval
        );

        System.out.printf("[Yahoo] Fetching %s historical data: %s to %s (%s interval)%n",
            nseSymbol, from, to, interval);

        String json = doGet(url);
        return parseHistoricalData(json, nseSymbol);
    }

    /**
     * Convenience overload using daily interval.
     */
    public List<StockData> getHistoricalData(String nseSymbol, LocalDate from,
                                              LocalDate to) throws IOException {
        return getHistoricalData(nseSymbol, from, to, "1d");
    }

    /**
     * Fetches historical data using Yahoo's range shortcuts.
     *
     * @param nseSymbol NSE symbol
     * @param range     One of: "1d", "5d", "1mo", "3mo", "6mo", "1y", "2y", "5y", "10y", "max"
     * @param interval  One of: "1d", "1wk", "1mo"
     * @return List of StockData
     * @throws IOException if the API call fails
     */
    public List<StockData> getHistoricalDataByRange(String nseSymbol, String range,
                                                     String interval) throws IOException {
        String yahooSymbol = toYahooSymbol(nseSymbol);
        String url = String.format(
            "%s%s?range=%s&interval=%s&includePrePost=false&events=div,splits",
            CHART_API, yahooSymbol, range, interval
        );

        System.out.printf("[Yahoo] Fetching %s: range=%s, interval=%s%n",
            nseSymbol, range, interval);

        String json = doGet(url);
        return parseHistoricalData(json, nseSymbol);
    }

    // ═══════════════════════════════════════════════════════════════
    //  INDEX DATA
    // ═══════════════════════════════════════════════════════════════

    /**
     * Fetches current data for an NSE index.
     *
     * @param indexName Index name: "NIFTY 50", "NIFTY BANK", "NIFTY IT", etc.
     * @return StockQuote with index data
     * @throws IOException if the API call fails
     */
    public StockQuote getIndexQuote(String indexName) throws IOException {
        String yahooSymbol = toYahooIndexSymbol(indexName);
        if (yahooSymbol == null) {
            throw new IOException("Unknown index: " + indexName +
                ". Supported: NIFTY 50, NIFTY BANK, NIFTY IT, NIFTY NEXT 50, NIFTY MIDCAP 50");
        }

        String url = CHART_API + yahooSymbol + "?range=5d&interval=1d";
        String json = doGet(url);
        JsonObject chart = parseChartResult(json);
        JsonObject meta = chart.getAsJsonObject("meta");

        StockQuote quote = new StockQuote();
        quote.setSymbol(indexName);
        quote.setCompanyName(indexName);
        quote.setLastPrice(getDoubleOrDefault(meta, "regularMarketPrice", 0));
        quote.setPreviousClose(getDoubleOrDefault(meta, "chartPreviousClose",
            getDoubleOrDefault(meta, "previousClose", 0)));
        quote.setChange(quote.getLastPrice() - quote.getPreviousClose());
        quote.setChangePercent(quote.getPreviousClose() > 0
            ? (quote.getChange() / quote.getPreviousClose()) * 100 : 0);

        // OHLCV from last trading day
        JsonArray timestamps = chart.getAsJsonArray("timestamp");
        JsonObject indicators = chart.getAsJsonObject("indicators");
        if (indicators != null && timestamps != null && timestamps.size() > 0) {
            JsonArray quoteArr = indicators.getAsJsonArray("quote");
            if (quoteArr != null && quoteArr.size() > 0) {
                JsonObject q = quoteArr.get(0).getAsJsonObject();
                int lastIdx = timestamps.size() - 1;
                quote.setOpen(getArrayDouble(q.getAsJsonArray("open"), lastIdx));
                quote.setHigh(getArrayDouble(q.getAsJsonArray("high"), lastIdx));
                quote.setLow(getArrayDouble(q.getAsJsonArray("low"), lastIdx));
                quote.setTotalTradedVolume(getArrayLong(q.getAsJsonArray("volume"), lastIdx));
            }
        }

        quote.setYearHigh(getDoubleOrDefault(meta, "fiftyTwoWeekHigh", 0));
        quote.setYearLow(getDoubleOrDefault(meta, "fiftyTwoWeekLow", 0));

        return quote;
    }

    /**
     * Fetches historical data for an NSE index.
     *
     * @param indexName Index name (e.g., "NIFTY 50")
     * @param from      Start date
     * @param to        End date
     * @param interval  "1d", "1wk", or "1mo"
     * @return List of StockData
     * @throws IOException if the API call fails
     */
    public List<StockData> getIndexHistoricalData(String indexName, LocalDate from,
                                                   LocalDate to, String interval) throws IOException {
        String yahooSymbol = toYahooIndexSymbol(indexName);
        if (yahooSymbol == null) {
            throw new IOException("Unknown index: " + indexName);
        }

        long period1 = from.atStartOfDay(ZoneId.of("Asia/Kolkata")).toEpochSecond();
        long period2 = to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toEpochSecond();

        String url = String.format(
            "%s%s?period1=%d&period2=%d&interval=%s&includePrePost=false",
            CHART_API, yahooSymbol, period1, period2, interval
        );

        System.out.printf("[Yahoo] Fetching %s index history: %s to %s%n", indexName, from, to);

        String json = doGet(url);
        return parseHistoricalData(json, indexName);
    }

    // ═══════════════════════════════════════════════════════════════
    //  BULK QUOTES (Multiple symbols at once)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Fetches quotes for multiple NSE symbols.
     *
     * @param symbols List of NSE symbols (e.g., ["RELIANCE", "TCS", "INFY"])
     * @return List of StockQuote for each symbol
     * @throws IOException if any API call fails
     */
    public List<StockQuote> getMultipleQuotes(List<String> symbols) throws IOException {
        List<StockQuote> quotes = new ArrayList<>();
        for (String symbol : symbols) {
            try {
                quotes.add(getQuote(symbol));
                System.out.printf("[Yahoo] ✔ %s fetched%n", symbol);
            } catch (IOException e) {
                System.err.printf("[Yahoo] ✖ Failed to fetch %s: %s%n", symbol, e.getMessage());
            }
        }
        return quotes;
    }

    // ═══════════════════════════════════════════════════════════════
    //  INTERNAL — HTTP & PARSING
    // ═══════════════════════════════════════════════════════════════

    private String doGet(String url) throws IOException {
        enforceRateLimit();

        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Yahoo Finance API error. HTTP " + response.code() +
                    " for URL: " + url);
            }
            ResponseBody body = response.body();
            return body != null ? body.string() : "";
        }
    }

    /**
     * Parses the Yahoo Finance chart API response and extracts the result object.
     */
    private JsonObject parseChartResult(String json) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject chart = root.getAsJsonObject("chart");

            // Check for errors
            JsonElement error = chart.get("error");
            if (error != null && !error.isJsonNull()) {
                String errMsg = error.getAsJsonObject().has("description")
                    ? error.getAsJsonObject().get("description").getAsString()
                    : "Unknown error";
                throw new IOException("Yahoo Finance API error: " + errMsg);
            }

            JsonArray resultArray = chart.getAsJsonArray("result");
            if (resultArray == null || resultArray.isEmpty()) {
                throw new IOException("No data in Yahoo Finance response");
            }

            return resultArray.get(0).getAsJsonObject();
        } catch (JsonSyntaxException e) {
            throw new IOException("Invalid JSON from Yahoo Finance: " + e.getMessage());
        }
    }

    /**
     * Parses historical OHLCV data from the chart API response.
     */
    private List<StockData> parseHistoricalData(String json, String symbol) throws IOException {
        List<StockData> dataList = new ArrayList<>();
        JsonObject chart = parseChartResult(json);

        JsonArray timestamps = chart.getAsJsonArray("timestamp");
        JsonObject indicators = chart.getAsJsonObject("indicators");

        if (timestamps == null || indicators == null) {
            System.out.println("[Yahoo] No timestamp/indicator data in response.");
            return dataList;
        }

        JsonArray quoteArr = indicators.getAsJsonArray("quote");
        if (quoteArr == null || quoteArr.isEmpty()) {
            return dataList;
        }

        JsonObject quote = quoteArr.get(0).getAsJsonObject();
        JsonArray opens = quote.getAsJsonArray("open");
        JsonArray highs = quote.getAsJsonArray("high");
        JsonArray lows = quote.getAsJsonArray("low");
        JsonArray closes = quote.getAsJsonArray("close");
        JsonArray volumes = quote.getAsJsonArray("volume");

        // Get adjusted close if available
        JsonArray adjCloses = null;
        JsonArray adjCloseArr = indicators.getAsJsonArray("adjclose");
        if (adjCloseArr != null && adjCloseArr.size() > 0) {
            adjCloses = adjCloseArr.get(0).getAsJsonObject().getAsJsonArray("adjclose");
        }

        for (int i = 0; i < timestamps.size(); i++) {
            long epoch = timestamps.get(i).getAsLong();
            LocalDate date = Instant.ofEpochSecond(epoch)
                .atZone(ZoneId.of("Asia/Kolkata"))
                .toLocalDate();

            double open = getArrayDouble(opens, i);
            double high = getArrayDouble(highs, i);
            double low = getArrayDouble(lows, i);
            double close = getArrayDouble(closes, i);
            long volume = getArrayLong(volumes, i);

            // Skip days with no data (market holidays that Yahoo includes)
            if (open == 0 && high == 0 && low == 0 && close == 0) {
                continue;
            }

            StockData sd = new StockData(symbol.toUpperCase(), date, open, high, low, close, volume);

            // Set previous close from previous day's close
            if (!dataList.isEmpty()) {
                sd.setPreviousClose(dataList.get(dataList.size() - 1).getClose());
            }

            dataList.add(sd);
        }

        return dataList;
    }

    // ═══════════════════════════════════════════════════════════════
    //  SYMBOL MAPPING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Converts an NSE equity symbol to Yahoo Finance format.
     * Example: "RELIANCE" → "RELIANCE.NS"
     */
    private String toYahooSymbol(String nseSymbol) {
        String symbol = nseSymbol.trim().toUpperCase();
        // Handle special characters in symbols like M&M
        symbol = symbol.replace("&", "%26");
        if (!symbol.endsWith(".NS") && !symbol.startsWith("^")) {
            symbol = symbol + ".NS";
        }
        return symbol;
    }

    /**
     * Maps NSE index names to Yahoo Finance symbols.
     */
    private String toYahooIndexSymbol(String indexName) {
        return switch (indexName.toUpperCase().trim()) {
            case "NIFTY 50", "NIFTY50" -> "^NSEI";
            case "NIFTY BANK", "BANKNIFTY", "NIFTY BANK INDEX" -> "^NSEBANK";
            case "NIFTY IT" -> "^CNXIT";
            case "NIFTY NEXT 50" -> "^NSMIDCP";
            case "NIFTY MIDCAP 50" -> "^NSEMDCP50";
            case "INDIA VIX" -> "^INDIAVIX";
            case "NIFTY 100" -> "^CNX100";
            case "NIFTY 500" -> "^CRSLDX";
            case "NIFTY PHARMA" -> "^CNXPHARMA";
            case "NIFTY AUTO" -> "^CNXAUTO";
            case "NIFTY METAL" -> "^CNXMETAL";
            case "NIFTY FMCG" -> "^CNXFMCG";
            case "NIFTY ENERGY" -> "^CNXENERGY";
            case "NIFTY REALTY" -> "^CNXREALTY";
            case "NIFTY INFRA" -> "^CNXINFRA";
            default -> null;
        };
    }

    // ═══════════════════════════════════════════════════════════════
    //  JSON HELPERS
    // ═══════════════════════════════════════════════════════════════

    private double getArrayDouble(JsonArray arr, int index) {
        if (arr == null || index >= arr.size() || arr.get(index).isJsonNull()) {
            return 0;
        }
        return arr.get(index).getAsDouble();
    }

    private long getArrayLong(JsonArray arr, int index) {
        if (arr == null || index >= arr.size() || arr.get(index).isJsonNull()) {
            return 0;
        }
        return arr.get(index).getAsLong();
    }

    private String getStringOrDefault(JsonObject obj, String key, String defaultVal) {
        JsonElement el = obj.get(key);
        return (el != null && !el.isJsonNull()) ? el.getAsString() : defaultVal;
    }

    private double getDoubleOrDefault(JsonObject obj, String key, double defaultVal) {
        JsonElement el = obj.get(key);
        if (el != null && !el.isJsonNull()) {
            try { return el.getAsDouble(); } catch (Exception e) { return defaultVal; }
        }
        return defaultVal;
    }

    private void enforceRateLimit() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestTime;
        if (elapsed < MIN_REQUEST_INTERVAL_MS) {
            try { Thread.sleep(MIN_REQUEST_INTERVAL_MS - elapsed); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        lastRequestTime = System.currentTimeMillis();
    }

    /**
     * Closes the HTTP client and releases resources.
     */
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
