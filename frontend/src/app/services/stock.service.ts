import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MarketOverview, SearchResult, StockData, StockQuote } from '../models/stock.models';

@Injectable({ providedIn: 'root' })
export class StockService {
  private readonly base = '/api/stocks';

  constructor(private http: HttpClient) {}

  /** Real-time quote for a single NSE symbol */
  getQuote(symbol: string): Observable<StockQuote> {
    return this.http.get<StockQuote>(`${this.base}/quote/${symbol}`);
  }

  /** Historical OHLCV by explicit date range */
  getHistorical(symbol: string, from: string, to: string, interval = '1d'): Observable<StockData[]> {
    const params = new HttpParams()
      .set('from', from)
      .set('to', to)
      .set('interval', interval);
    return this.http.get<StockData[]>(`${this.base}/historical/${symbol}`, { params });
  }

  /** Historical OHLCV by preset range (e.g. '1mo', '3mo', '6mo', '1y') */
  getHistoricalByRange(symbol: string, range = '6mo', interval = '1d'): Observable<StockData[]> {
    const params = new HttpParams().set('range', range).set('interval', interval);
    return this.http.get<StockData[]>(`${this.base}/historical/${symbol}/range`, { params });
  }

  /** Quote for an index (e.g. 'NIFTY-50', 'NIFTY-BANK') */
  getIndex(indexName: string): Observable<StockQuote> {
    return this.http.get<StockQuote>(`${this.base}/index/${indexName}`);
  }

  /** Bulk quotes for a list of symbols */
  getBulkQuotes(symbols: string[]): Observable<StockQuote[]> {
    const params = new HttpParams().set('symbols', symbols.join(','));
    return this.http.get<StockQuote[]>(`${this.base}/bulk-quotes`, { params });
  }

  /** Symbol / company-name search */
  search(q: string): Observable<SearchResult[]> {
    const params = new HttpParams().set('q', q);
    return this.http.get<SearchResult[]>(`${this.base}/search`, { params });
  }

  /** Market overview: NIFTY 50, NIFTY BANK, top 10 stocks */
  getMarketOverview(): Observable<MarketOverview> {
    return this.http.get<MarketOverview>(`${this.base}/market-overview`);
  }
}
