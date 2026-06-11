# NSE Stock Data Fetcher (Java)

A developer-friendly Java library to fetch **real-time quotes** and **historical OHLCV data** from the National Stock Exchange of India (NSE).

## Features

| Feature | Description |
|---|---|
| **Real-Time Quotes** | Live price, OHLCV, 52-week range, circuit limits, delivery % |
| **Historical Data** | OHLCV data for any date range (auto-chunked for large ranges) |
| **Index Data** | NIFTY 50, NIFTY BANK, and all NSE indices with advance/decline |
| **Index Constituents** | All stocks in an index with top gainers/losers |
| **Pre-Open Data** | Pre-open session prices (9:00–9:15 AM IST) |
| **CSV/JSON Export** | Export historical data to CSV or JSON for further analysis |
| **Session Management** | Automatic cookie handling, anti-scraping headers, rate limiting |

## Prerequisites

- **Java 17+**
- **Maven 3.6+**

## Quick Start

### 1. Build the project

```bash
mvn clean package
```

### 2. Run the demo

```bash
# Default (fetches RELIANCE data)
java -jar target/nse-stock-fetcher-1.0.0.jar

# With a specific symbol
java -jar target/nse-stock-fetcher-1.0.0.jar TCS
```

### 3. Or use Maven exec plugin

```bash
mvn clean compile exec:java -Dexec.mainClass="com.nse.stockfetcher.Main"
```

## Usage in Your Code

### Fetch Real-Time Quote

```java
NseHttpClient client = new NseHttpClient();
client.initSession();
NseDataService service = new NseDataService(client);

StockQuote quote = service.getQuote("RELIANCE");
System.out.println("Price: ₹" + quote.getLastPrice());
System.out.println("Change: " + quote.getChangePercent() + "%");
System.out.println("52W High: ₹" + quote.getYearHigh());

client.close();
```

### Fetch Historical Data

```java
NseHttpClient client = new NseHttpClient();
client.initSession();
NseDataService service = new NseDataService(client);

LocalDate from = LocalDate.of(2024, 1, 1);
LocalDate to = LocalDate.of(2024, 6, 30);

List<StockData> history = service.getHistoricalData("TCS", from, to);

// Print to console
DataExporter.printTable(history);

// Export to CSV
DataExporter.exportToCsv(history, "output/TCS_history.csv");

// Export to JSON
DataExporter.exportToJson(history, "output/TCS_history.json");

client.close();
```

### Fetch Index Data

```java
NseHttpClient client = new NseHttpClient();
client.initSession();
NseDataService service = new NseDataService(client);

// Get NIFTY 50 index data
IndexData nifty = service.getIndex("NIFTY 50");
System.out.println("NIFTY 50: " + nifty.getLastPrice());
System.out.println("Advances: " + nifty.getAdvances());
System.out.println("Declines: " + nifty.getDeclines());

// Get all stocks in NIFTY 50
List<StockQuote> stocks = service.getIndexStocks("NIFTY 50");
stocks.forEach(s -> System.out.printf("%-12s ₹%,.2f (%+.2f%%)%n",
    s.getSymbol(), s.getLastPrice(), s.getChangePercent()));

client.close();
```

### Fetch Pre-Open Market Data

```java
NseHttpClient client = new NseHttpClient();
client.initSession();
NseDataService service = new NseDataService(client);

// Available keys: "NIFTY", "BANKNIFTY", "SME", "FO", "OTHERS", "ALL"
List<StockQuote> preOpen = service.getPreOpenData("NIFTY");
preOpen.forEach(s -> System.out.printf("%-12s ₹%,.2f (%+.2f%%)%n",
    s.getSymbol(), s.getLastPrice(), s.getChangePercent()));

client.close();
```

## Project Structure

```
nse-stock-fetcher/
├── pom.xml                          # Maven configuration
├── src/main/java/com/nse/stockfetcher/
│   ├── Main.java                    # Demo entry point
│   ├── http/
│   │   └── NseHttpClient.java       # HTTP session & cookie manager
│   ├── model/
│   │   ├── StockData.java           # OHLCV data model
│   │   ├── StockQuote.java          # Real-time quote model
│   │   └── IndexData.java           # Index data model
│   ├── service/
│   │   └── NseDataService.java      # Core API service
│   └── export/
│       └── DataExporter.java        # CSV/JSON/Table exporter
└── output/                          # Generated export files
```

## API Endpoints Used

| Endpoint | Purpose |
|---|---|
| `/api/quote-equity?symbol=X` | Real-time equity quote |
| `/api/historical/cm/equity?symbol=X&from=DD-MM-YYYY&to=DD-MM-YYYY` | Historical OHLCV data |
| `/api/allIndices` | All index data |
| `/api/equity-stockIndices?index=X` | Stocks in an index |
| `/api/market-data-pre-open?key=X` | Pre-open market data |

## Important Notes

1. **Session Required**: Always call `client.initSession()` before making API calls. The client handles cookie management automatically.

2. **Rate Limiting**: Built-in 350ms delay between requests to avoid being blocked by NSE.

3. **Auto-Retry**: If a session expires (HTTP 401/403), the client automatically re-initializes and retries.

4. **Historical Data Chunking**: NSE limits historical data to ~90 days per request. The service automatically splits larger ranges into chunks.

5. **Market Hours**: Real-time data is available during market hours (9:15 AM – 3:30 PM IST, Mon-Fri). Pre-open data is available 9:00–9:15 AM IST.

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| OkHttp | 4.12.0 | HTTP client with cookie management |
| Gson | 2.11.0 | JSON parsing |
| OpenCSV | 5.9 | CSV file export |

## License

MIT License — Free for personal and commercial use.
