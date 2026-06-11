package com.nse.stockfetcher.model;

import java.util.List;

/**
 * Holds index data (NIFTY 50, NIFTY BANK, etc.) including advances/declines
 * and constituent stock performance.
 */
public class IndexData {

    private String indexName;
    private double lastPrice;
    private double open;
    private double high;
    private double low;
    private double previousClose;
    private double change;
    private double changePercent;
    private int advances;
    private int declines;
    private int unchanged;

    private List<String> topGainers;
    private List<String> topLosers;

    public IndexData() {}

    // --- Getters and Setters ---

    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }

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

    public int getAdvances() { return advances; }
    public void setAdvances(int advances) { this.advances = advances; }

    public int getDeclines() { return declines; }
    public void setDeclines(int declines) { this.declines = declines; }

    public int getUnchanged() { return unchanged; }
    public void setUnchanged(int unchanged) { this.unchanged = unchanged; }

    public List<String> getTopGainers() { return topGainers; }
    public void setTopGainers(List<String> topGainers) { this.topGainers = topGainers; }

    public List<String> getTopLosers() { return topLosers; }
    public void setTopLosers(List<String> topLosers) { this.topLosers = topLosers; }

    @Override
    public String toString() {
        return String.format("""
            ╔══════════════════════════════════════════════════════════╗
            ║  INDEX: %s
            ╠══════════════════════════════════════════════════════════╣
            ║  Last      : %,.2f  (%+.2f | %+.2f%%)
            ║  Open      : %,.2f
            ║  High      : %,.2f
            ║  Low       : %,.2f
            ║  Prev Close: %,.2f
            ╠══════════════════════════════════════════════════════════╣
            ║  Advances: %d  |  Declines: %d  |  Unchanged: %d
            ╚══════════════════════════════════════════════════════════╝""",
            indexName,
            lastPrice, change, changePercent,
            open, high, low, previousClose,
            advances, declines, unchanged
        );
    }
}
