package com.nse.stockfetcher.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * Represents a single day's stock trading data (OHLCV - Open, High, Low, Close, Volume).
 * Used for both real-time quotes and historical data points.
 */
@Schema(description = "Single-day OHLCV trading data for a stock")
public class StockData {

    @Schema(description = "NSE stock symbol", example = "TCS")
    private String symbol;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "Trading date", example = "2024-06-01", type = "string", format = "date")
    private LocalDate date;

    @Schema(description = "Opening price in INR", example = "3750.00")
    private double open;

    @Schema(description = "Day high price in INR", example = "3820.00")
    private double high;

    @Schema(description = "Day low price in INR", example = "3730.00")
    private double low;

    @Schema(description = "Closing price in INR", example = "3800.00")
    private double close;

    @Schema(description = "Previous session's closing price in INR", example = "3740.00")
    private double previousClose;

    @Schema(description = "Total traded volume (number of shares)", example = "1200000")
    private long volume;

    @Schema(description = "Turnover in lakhs or crores (source-dependent)", example = "4560.75")
    private double turnover;       // in lakhs or crores depending on source

    @Schema(description = "Total traded quantity", example = "1200000")
    private long totalTradedQty;

    @Schema(description = "Volume Weighted Average Price (VWAP) in INR", example = "3785.50")
    private double vwap;           // Volume Weighted Average Price

    @Schema(description = "Delivery as a percentage of total traded quantity", example = "38.75")
    private double deliveryPercent;

    public StockData() {}

    public StockData(String symbol, LocalDate date, double open, double high,
                     double low, double close, long volume) {
        this.symbol = symbol;
        this.date = date;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    // --- Derived metrics ---

    /** Daily change = close - previousClose */
    public double getChange() {
        return previousClose > 0 ? close - previousClose : 0;
    }

    /** Percentage change from previous close */
    public double getChangePercent() {
        return previousClose > 0 ? ((close - previousClose) / previousClose) * 100 : 0;
    }

    /** Daily price range */
    public double getDayRange() {
        return high - low;
    }

    // --- Getters and Setters ---

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public double getOpen() { return open; }
    public void setOpen(double open) { this.open = open; }

    public double getHigh() { return high; }
    public void setHigh(double high) { this.high = high; }

    public double getLow() { return low; }
    public void setLow(double low) { this.low = low; }

    public double getClose() { return close; }
    public void setClose(double close) { this.close = close; }

    public double getPreviousClose() { return previousClose; }
    public void setPreviousClose(double previousClose) { this.previousClose = previousClose; }

    public long getVolume() { return volume; }
    public void setVolume(long volume) { this.volume = volume; }

    public double getTurnover() { return turnover; }
    public void setTurnover(double turnover) { this.turnover = turnover; }

    public long getTotalTradedQty() { return totalTradedQty; }
    public void setTotalTradedQty(long totalTradedQty) { this.totalTradedQty = totalTradedQty; }

    public double getVwap() { return vwap; }
    public void setVwap(double vwap) { this.vwap = vwap; }

    public double getDeliveryPercent() { return deliveryPercent; }
    public void setDeliveryPercent(double deliveryPercent) { this.deliveryPercent = deliveryPercent; }

    @Override
    public String toString() {
        return String.format(
            "StockData{symbol='%s', date=%s, O=%.2f, H=%.2f, L=%.2f, C=%.2f, Vol=%,d, Change=%.2f (%.2f%%)}",
            symbol, date, open, high, low, close, volume, getChange(), getChangePercent()
        );
    }
}
