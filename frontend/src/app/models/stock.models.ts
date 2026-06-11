/**
 * Mirrors com.nse.stockfetcher.model.StockQuote
 */
export interface StockQuote {
  symbol: string;
  companyName: string;
  series: string;
  industry: string;
  isin: string;

  // Price data
  lastPrice: number;
  open: number;
  high: number;
  low: number;
  previousClose: number;
  change: number;
  changePercent: number;

  // Volume and turnover
  totalTradedVolume: number;
  totalTradedValue: number;
  deliveryToTradedQty: number;

  // 52-week range
  yearHigh: number;
  yearLow: number;

  // Market depth
  bestBidPrice: number;
  bestBidQty: number;
  bestAskPrice: number;
  bestAskQty: number;

  // Circuit limits
  upperCircuitLimit: number;
  lowerCircuitLimit: number;
}

/**
 * Mirrors com.nse.stockfetcher.model.StockData (historical OHLCV)
 */
export interface StockData {
  symbol: string;
  date: string; // yyyy-MM-dd from @JsonFormat
  open: number;
  high: number;
  low: number;
  close: number;
  previousClose: number;
  volume: number;
  turnover: number;
  totalTradedQty: number;
  vwap: number;
  deliveryPercent: number;
  // Computed by backend
  change: number;
  changePercent: number;
  dayRange: number;
}

/**
 * Mirrors the market overview response shape from StockService.getMarketOverview()
 */
export interface MarketOverview {
  nifty50: StockQuote | null;
  niftyBank: StockQuote | null;
  topStocks: StockQuote[];
}

/**
 * Mirrors the search result shape from StockService.searchSymbols()
 */
export interface SearchResult {
  symbol: string;
  name: string;
}
