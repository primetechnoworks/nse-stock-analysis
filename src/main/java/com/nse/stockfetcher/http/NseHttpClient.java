package com.nse.stockfetcher.http;

import okhttp3.*;
import okhttp3.JavaNetCookieJar;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.concurrent.TimeUnit;

/**
 * HTTP session manager that handles NSE's anti-scraping measures.
 *
 * <h3>How NSE's Protection Works:</h3>
 * NSE uses Akamai Bot Manager which checks:
 * <ul>
 *   <li>TLS fingerprint (JA3) — OkHttp's fingerprint differs from browsers</li>
 *   <li>Session cookies set via JavaScript challenge</li>
 *   <li>Header ordering and values</li>
 *   <li>Request timing patterns</li>
 * </ul>
 *
 * <h3>Mitigation Strategy:</h3>
 * <ol>
 *   <li>Warm up with multiple pre-flight requests to different NSE pages</li>
 *   <li>Use exact Chrome header ordering</li>
 *   <li>Add realistic Sec-Fetch-* headers</li>
 *   <li>Enforce generous rate limiting</li>
 *   <li>Retry with exponential backoff on 403</li>
 * </ol>
 *
 * <p><strong>Note:</strong> If NSE direct access still fails due to TLS fingerprinting,
 * use {@code YahooFinanceService} as a reliable fallback.</p>
 */
public class NseHttpClient {

    private static final String NSE_BASE_URL = "https://www.nseindia.com";
    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private final OkHttpClient httpClient;
    private long lastRequestTime = 0;
    private boolean sessionInitialized = false;

    private static final long MIN_REQUEST_INTERVAL_MS = 500;
    private static final int MAX_RETRIES = 3;

    public NseHttpClient() {
        // In-memory cookie jar to persist session across requests
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        CookieJar cookieJar = new JavaNetCookieJar(cookieManager);

        this.httpClient = new OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            // Add connection pool for keep-alive reuse
            .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
            .build();
    }

    /**
     * Initializes the session by warming up with multiple NSE page hits.
     * This sets the required cookies (nsit, nseappid, bm_sv, ak_bmsc, etc.)
     *
     * @throws IOException if all initialization attempts fail
     */
    public void initSession() throws IOException {
        System.out.println("[Session] Initializing NSE session...");

        // Step 1: Hit the main homepage
        boolean success = warmUpRequest(NSE_BASE_URL,
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "navigate", "none");

        if (!success) {
            // Step 2: Try option chain page (sometimes works when homepage doesn't)
            System.out.println("[Session] Homepage blocked, trying alternate pages...");
            sleep(1000);
            success = warmUpRequest(NSE_BASE_URL + "/get-quotes/equity?symbol=RELIANCE",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "navigate", "none");
        }

        if (!success) {
            // Step 3: Try market data page
            sleep(1500);
            success = warmUpRequest(NSE_BASE_URL + "/market-data/live-equity-market",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "navigate", "none");
        }

        if (success) {
            sessionInitialized = true;
            System.out.println("[Session] ✔ Session initialized successfully.");
        } else {
            sessionInitialized = false;
            System.out.println("[Session] ✖ NSE session could not be established (Akamai bot detection).");
            System.out.println("[Session] ℹ Use YahooFinanceService as a reliable alternative.");
            throw new IOException(
                "NSE blocked the connection (Akamai Bot Manager). " +
                "Use YahooFinanceService instead for reliable data access."
            );
        }
    }

    /**
     * Makes a warm-up request to establish cookies.
     */
    private boolean warmUpRequest(String url, String accept, String fetchMode, String fetchSite) {
        try {
            Request request = buildBrowserRequest(url, accept, fetchMode, fetchSite, true);
            try (Response response = httpClient.newCall(request).execute()) {
                // Consume body fully to get all cookies
                if (response.body() != null) {
                    response.body().string();
                }
                return response.isSuccessful();
            }
        } catch (IOException e) {
            System.out.println("[Session] Warm-up request failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Builds a request with realistic Chrome browser headers.
     * Header ordering matters for bot detection.
     */
    private Request buildBrowserRequest(String url, String accept,
                                         String fetchMode, String fetchSite,
                                         boolean isNavigation) {
        Request.Builder builder = new Request.Builder()
            .url(url)
            // Chrome sends headers in this specific order
            .header("Host", "www.nseindia.com")
            .header("User-Agent", USER_AGENT)
            .header("Accept", accept)
            .header("Accept-Language", "en-US,en;q=0.9,hi;q=0.8")
            .header("Accept-Encoding", "gzip, deflate, br")
            .header("Connection", "keep-alive")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache");

        if (isNavigation) {
            builder.header("Upgrade-Insecure-Requests", "1");
            builder.header("Sec-Fetch-Dest", "document");
        } else {
            builder.header("Sec-Fetch-Dest", "empty");
        }

        builder.header("Sec-Fetch-Mode", fetchMode);
        builder.header("Sec-Fetch-Site", fetchSite);

        if (!isNavigation) {
            builder.header("Referer", NSE_BASE_URL + "/");
            builder.header("X-Requested-With", "XMLHttpRequest");
        }

        return builder.build();
    }

    /**
     * Makes a GET request to the specified NSE API endpoint.
     * Includes retry logic with exponential backoff.
     *
     * @param url Full URL of the API endpoint
     * @return Response body as a String
     * @throws IOException if the request fails after all retries
     */
    public String get(String url) throws IOException {
        IOException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                enforceRateLimit();

                Request request = buildBrowserRequest(url,
                    "application/json, text/plain, */*",
                    "cors", "same-origin",
                    false);

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        ResponseBody body = response.body();
                        return body != null ? body.string() : "";
                    }

                    if (response.code() == 401 || response.code() == 403) {
                        System.out.printf("[HTTP] 403 on attempt %d/%d, re-initializing session...%n",
                            attempt, MAX_RETRIES);
                        sleep(1000 * attempt); // Exponential backoff
                        try {
                            initSession();
                        } catch (IOException e) {
                            // initSession might fail, continue to retry
                        }
                        continue;
                    }

                    throw new IOException("HTTP " + response.code() + " for URL: " + url);
                }
            } catch (IOException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    System.out.printf("[HTTP] Attempt %d failed: %s. Retrying...%n",
                        attempt, e.getMessage());
                    sleep(1000 * attempt);
                }
            }
        }

        throw lastException != null ? lastException :
            new IOException("All retry attempts exhausted for URL: " + url);
    }

    /**
     * Enforces minimum interval between requests to avoid triggering rate limits.
     */
    private void enforceRateLimit() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestTime;
        if (elapsed < MIN_REQUEST_INTERVAL_MS) {
            sleep(MIN_REQUEST_INTERVAL_MS - elapsed);
        }
        lastRequestTime = System.currentTimeMillis();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isSessionInitialized() {
        return sessionInitialized;
    }

    /**
     * Closes the HTTP client and releases resources.
     */
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
