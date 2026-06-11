package com.nse.stockfetcher.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Holds real-time quote information for a stock including market depth,
 * company metadata, and 52-week range.
 */
@Schema(description = "Real-time stock quote with price, volume, circuit limits and market depth")
public class StockQuote {

    @Schema(description = "NSE stock symbol", example = "RELIANCE")
    private String symbol;

    @Schema(description = "Full company name", example = "Reliance Industries Ltd")
    private String companyName;

    @Schema(description = "Trading series (e.g. EQ, BE)", example = "EQ")
    private String series;           // EQ, BE, etc.

    @Schema(description = "Industry/sector classification", example = "Oil & Gas")
    private String industry;

    @Schema(description = "ISIN code", example = "INE002A01018")
    private String isin;

    // Price data
    @Schema(description = "Last traded price (LTP) in INR", example = "2950.50")
    private double lastPrice;

    @Schema(description = "Day's opening price in INR", example = "2930.00")
    private double open;

    @Schema(description = "Day's highest price in INR", example = "2975.00")
    private double high;

    @Schema(description = "Day's lowest price in INR", example = "2920.00")
    private double low;

    @Schema(description = "Previous session's closing price in INR", example = "2915.00")
    private double previousClose;

    @Schema(description = "Absolute change from previous close", example = "35.50")
    private double change;

    @Schema(description = "Percentage change from previous close", example = "1.22")
    private double changePercent;

    // Volume and turnover
    @Schema(description = "Total number of shares traded today", example = "4500000")
    private long totalTradedVolume;

    @Schema(description = "Total traded value in lakhs (INR)", example = "132750.50")
    private double totalTradedValue;  // in lakhs

    @Schema(description = "Delivery quantity as percentage of total traded quantity", example = "45.30")
    private double deliveryToTradedQty;

    // 52-week range
    @Schema(description = "52-week high price in INR", example = "3217.90")
    private double yearHigh;

    @Schema(description = "52-week low price in INR", example = "2220.30")
    private double yearLow;

    // Market depth (top bid/ask)
    @Schema(description = "Best bid (buy) price in INR", example = "2950.00")
    private double bestBidPrice;

    @Schema(description = "Quantity available at best bid price", example = "150")
    private long bestBidQty;

    @Schema(description = "Best ask (sell) price in INR", example = "2950.50")
    private double bestAskPrice;

    @Schema(description = "Quantity available at best ask price", example = "200")
    private long bestAskQty;

    // Upper and Lower circuit limits
    @Schema(description = "Upper circuit limit price for the day", example = "3206.85")
    private double upperCircuitLimit;

    @Schema(description = "Lower circuit limit price for the day", example = "2623.15")
    private double lowerCircuitLimit;

    public StockQuote() {}

    // --- Getters and Setters ---

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getSeries() { return series; }
    public void setSeries(String series) { this.series = series; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public String getIsin() { return isin; }
    public void setIsin(String isin) { this.isin = isin; }

    public double getLastPrice() { return lastPrice; }
    public void setLastPrice(double lastPrice) { this.lastPrice = lastPrice; }

    public double getOpen() { return open; }
    public void setOpen(double open) { this.open = open; }

    public double getHigh() { return high; }
    public void setHigh(double high) { this.high = high; }

    public double getLow() { return low; }
    public void setLow(double low) { this.low = low; }

    public double getPreviousClose() { return previousClose; }
    public void setPreviousClose(double previousClose) { this.previousClose = previousClose; }

    public double getChange() { return change; }
    public void setChange(double change) { this.change = change; }

    public double getChangePercent() { return changePercent; }
    public void setChangePercent(double changePercent) { this.changePercent = changePercent; }

    public long getTotalTradedVolume() { return totalTradedVolume; }
    public void setTotalTradedVolume(long totalTradedVolume) { this.totalTradedVolume = totalTradedVolume; }

    public double getTotalTradedValue() { return totalTradedValue; }
    public void setTotalTradedValue(double totalTradedValue) { this.totalTradedValue = totalTradedValue; }

    public double getDeliveryToTradedQty() { return deliveryToTradedQty; }
    public void setDeliveryToTradedQty(double deliveryToTradedQty) { this.deliveryToTradedQty = deliveryToTradedQty; }

    public double getYearHigh() { return yearHigh; }
    public void setYearHigh(double yearHigh) { this.yearHigh = yearHigh; }

    public double getYearLow() { return yearLow; }
    public void setYearLow(double yearLow) { this.yearLow = yearLow; }

    public double getBestBidPrice() { return bestBidPrice; }
    public void setBestBidPrice(double bestBidPrice) { this.bestBidPrice = bestBidPrice; }

    public long getBestBidQty() { return bestBidQty; }
    public void setBestBidQty(long bestBidQty) { this.bestBidQty = bestBidQty; }

    public double getBestAskPrice() { return bestAskPrice; }
    public void setBestAskPrice(double bestAskPrice) { this.bestAskPrice = bestAskPrice; }

    public long getBestAskQty() { return bestAskQty; }
    public void setBestAskQty(long bestAskQty) { this.bestAskQty = bestAskQty; }

    public double getUpperCircuitLimit() { return upperCircuitLimit; }
    public void setUpperCircuitLimit(double upperCircuitLimit) { this.upperCircuitLimit = upperCircuitLimit; }

    public double getLowerCircuitLimit() { return lowerCircuitLimit; }
    public void setLowerCircuitLimit(double lowerCircuitLimit) { this.lowerCircuitLimit = lowerCircuitLimit; }

    @Override
    public String toString() {
        return String.format("""
            ╔══════════════════════════════════════════════════════════╗
            ║  %s - %s
            ║  Series: %s  |  ISIN: %s
            ╠══════════════════════════════════════════════════════════╣
            ║  Last Price : ₹%,.2f  (%+.2f | %+.2f%%)
            ║  Open       : ₹%,.2f
            ║  High       : ₹%,.2f
            ║  Low        : ₹%,.2f
            ║  Prev Close : ₹%,.2f
            ╠══════════════════════════════════════════════════════════╣
            ║  Volume     : %,d
            ║  52W High   : ₹%,.2f
            ║  52W Low    : ₹%,.2f
            ║  Upper CL   : ₹%,.2f
            ║  Lower CL   : ₹%,.2f
            ╚══════════════════════════════════════════════════════════╝""",
            symbol, companyName != null ? companyName : "",
            series != null ? series : "N/A", isin != null ? isin : "N/A",
            lastPrice, change, changePercent,
            open, high, low, previousClose,
            totalTradedVolume,
            yearHigh, yearLow,
            upperCircuitLimit, lowerCircuitLimit
        );
    }
}
