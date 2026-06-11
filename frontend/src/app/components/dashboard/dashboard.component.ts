import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { interval, Subject, switchMap, takeUntil, startWith, catchError, of } from 'rxjs';
import { StockService } from '../../services/stock.service';
import { MarketOverview, StockQuote } from '../../models/stock.models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="page">
      <div class="container">

        <!-- Page header -->
        <div class="page-header">
          <div>
            <h1 class="page-title">Market Overview</h1>
            <p class="page-sub">NSE real-time data · Auto-refreshes every 60s</p>
          </div>
          <div class="refresh-info" *ngIf="lastUpdated">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/>
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
            </svg>
            Updated {{ lastUpdated | date:'HH:mm:ss' }}
          </div>
        </div>

        <!-- Loading state -->
        <div *ngIf="loading && !overview" class="spinner"></div>

        <!-- Error state -->
        <div *ngIf="error && !overview" class="error-state">
          <div class="icon">⚠️</div>
          <h3>Backend Unreachable</h3>
          <p>Make sure the Spring Boot server is running on port 8080</p>
          <button class="retry-btn" id="retry-btn" (click)="refresh()">Retry</button>
        </div>

        <!-- Index cards -->
        <div class="index-strip" *ngIf="overview">
          <div class="index-card card" id="nifty50-card" *ngIf="overview.nifty50" (click)="goToStock('NIFTY-50')">
            <div class="index-label">NIFTY 50</div>
            <div class="index-price">{{ overview.nifty50.lastPrice | number:'1.2-2' }}</div>
            <div [class]="overview.nifty50.change >= 0 ? 'badge-gain' : 'badge-loss'">
              {{ overview.nifty50.change >= 0 ? '▲' : '▼' }}
              {{ overview.nifty50.change | number:'1.2-2' }}
              ({{ overview.nifty50.changePercent | number:'1.2-2' }}%)
            </div>
            <div class="index-meta">
              <span>H {{ overview.nifty50.high | number:'1.2-2' }}</span>
              <span>L {{ overview.nifty50.low | number:'1.2-2' }}</span>
            </div>
          </div>

          <div class="index-card card" id="niftybank-card" *ngIf="overview.niftyBank" (click)="goToStock('NIFTY-BANK')">
            <div class="index-label">NIFTY BANK</div>
            <div class="index-price">{{ overview.niftyBank.lastPrice | number:'1.2-2' }}</div>
            <div [class]="overview.niftyBank.change >= 0 ? 'badge-gain' : 'badge-loss'">
              {{ overview.niftyBank.change >= 0 ? '▲' : '▼' }}
              {{ overview.niftyBank.change | number:'1.2-2' }}
              ({{ overview.niftyBank.changePercent | number:'1.2-2' }}%)
            </div>
            <div class="index-meta">
              <span>H {{ overview.niftyBank.high | number:'1.2-2' }}</span>
              <span>L {{ overview.niftyBank.low | number:'1.2-2' }}</span>
            </div>
          </div>

          <!-- Summary bar -->
          <div class="market-summary-card card" *ngIf="overview.topStocks.length">
            <div class="summary-stat">
              <span class="summary-label">Advances</span>
              <span class="gain summary-val">{{ gainCount }}</span>
            </div>
            <div class="summary-divider"></div>
            <div class="summary-stat">
              <span class="summary-label">Declines</span>
              <span class="loss summary-val">{{ lossCount }}</span>
            </div>
            <div class="summary-divider"></div>
            <div class="summary-stat">
              <span class="summary-label">Top Gainer</span>
              <span class="gain summary-val">{{ topGainer?.symbol }}</span>
            </div>
            <div class="summary-divider"></div>
            <div class="summary-stat">
              <span class="summary-label">Top Loser</span>
              <span class="loss summary-val">{{ topLoser?.symbol }}</span>
            </div>
          </div>
        </div>

        <!-- Top stocks grid -->
        <div class="section-header" *ngIf="overview">
          <h2 class="section-title">Top Stocks</h2>
          <span class="section-count">{{ overview.topStocks.length }} stocks</span>
        </div>

        <div class="stocks-grid" *ngIf="overview">
          <div
            class="stock-card card"
            *ngFor="let s of overview.topStocks; trackBy: trackBySymbol; let i = index"
            [id]="'stock-card-' + s.symbol"
            (click)="goToStock(s.symbol)"
            [style.animation-delay]="(i * 40) + 'ms'">

            <!-- Header -->
            <div class="sc-header">
              <div>
                <div class="sc-symbol">{{ s.symbol }}</div>
                <div class="sc-name">{{ s.companyName || '—' }}</div>
              </div>
              <div [class]="s.changePercent >= 0 ? 'badge-gain' : 'badge-loss'">
                {{ s.changePercent >= 0 ? '▲' : '▼' }}
                {{ s.changePercent | number:'1.2-2' }}%
              </div>
            </div>

            <!-- Price -->
            <div class="sc-price">₹{{ s.lastPrice | number:'1.2-2' }}</div>

            <!-- OHLV row -->
            <div class="sc-ohlv">
              <span><span class="muted">O</span> {{ s.open | number:'1.2-2' }}</span>
              <span><span class="muted">H</span> {{ s.high | number:'1.2-2' }}</span>
              <span><span class="muted">L</span> {{ s.low | number:'1.2-2' }}</span>
            </div>

            <!-- 52W range bar -->
            <div class="range-bar-wrap">
              <span class="range-label muted">{{ s.yearLow | number:'1.0-0' }}</span>
              <div class="range-bar">
                <div class="range-fill" [style.width]="rangePercent(s) + '%'"></div>
              </div>
              <span class="range-label muted">{{ s.yearHigh | number:'1.0-0' }}</span>
            </div>

            <!-- Volume -->
            <div class="sc-vol muted">
              Vol {{ formatVol(s.totalTradedVolume) }}
            </div>

            <div class="sc-arrow">→</div>
          </div>
        </div>

      </div>
    </div>
  `,
  styles: [`
    /* --- Page header --- */
    .page-header {
      display: flex;
      align-items: flex-end;
      justify-content: space-between;
      margin-bottom: 28px;
      flex-wrap: wrap;
      gap: 12px;
    }

    .page-title {
      font-size: 1.75rem;
      font-weight: 800;
      letter-spacing: -0.03em;
      background: linear-gradient(135deg, #e8edf5 30%, var(--accent));
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
    }

    .page-sub {
      font-size: 0.8rem;
      color: var(--text-muted);
      margin-top: 4px;
    }

    .refresh-info {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 0.78rem;
      color: var(--text-muted);
    }

    .retry-btn {
      margin-top: 12px;
      padding: 10px 24px;
      background: var(--accent-dim);
      border: 1px solid var(--border-accent);
      border-radius: 8px;
      color: var(--accent);
      font-weight: 600;
      font-size: 0.875rem;
      transition: background 0.2s;
    }
    .retry-btn:hover { background: rgba(0,255,163,0.25); }

    /* --- Index strip --- */
    .index-strip {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 16px;
      margin-bottom: 32px;
    }

    .index-card {
      cursor: pointer;
    }

    .index-label {
      font-size: 0.7rem;
      font-weight: 700;
      letter-spacing: 0.1em;
      text-transform: uppercase;
      color: var(--text-muted);
      margin-bottom: 6px;
    }

    .index-price {
      font-size: 1.6rem;
      font-weight: 800;
      letter-spacing: -0.03em;
      color: var(--text-primary);
      margin-bottom: 8px;
    }

    .index-meta {
      display: flex;
      gap: 12px;
      font-size: 0.78rem;
      color: var(--text-secondary);
      margin-top: 12px;
    }

    /* --- Market summary card --- */
    .market-summary-card {
      display: flex;
      align-items: center;
      justify-content: space-around;
      flex-wrap: wrap;
      gap: 8px;
    }

    .summary-stat {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;
    }

    .summary-label {
      font-size: 0.68rem;
      font-weight: 600;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      color: var(--text-muted);
    }

    .summary-val {
      font-size: 1.2rem;
      font-weight: 800;
    }

    .summary-divider {
      width: 1px;
      height: 36px;
      background: var(--border);
    }

    /* --- Section header --- */
    .section-header {
      display: flex;
      align-items: baseline;
      gap: 12px;
      margin-bottom: 16px;
    }

    .section-title {
      font-size: 1.1rem;
      font-weight: 700;
    }

    .section-count {
      font-size: 0.78rem;
      color: var(--text-muted);
    }

    /* --- Stocks grid --- */
    .stocks-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
      gap: 16px;
    }

    .stock-card {
      cursor: pointer;
      position: relative;
      animation: fadeUp 0.4s both;
    }

    @keyframes fadeUp {
      from { opacity: 0; transform: translateY(16px); }
      to   { opacity: 1; transform: translateY(0); }
    }

    .sc-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 8px;
      margin-bottom: 12px;
    }

    .sc-symbol {
      font-size: 0.95rem;
      font-weight: 800;
      color: var(--text-primary);
      letter-spacing: -0.01em;
    }

    .sc-name {
      font-size: 0.72rem;
      color: var(--text-muted);
      margin-top: 2px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 160px;
    }

    .sc-price {
      font-size: 1.4rem;
      font-weight: 800;
      letter-spacing: -0.03em;
      color: var(--text-primary);
      margin-bottom: 10px;
    }

    .sc-ohlv {
      display: flex;
      gap: 12px;
      font-size: 0.75rem;
      color: var(--text-secondary);
      margin-bottom: 10px;
    }

    /* --- 52W Range bar --- */
    .range-bar-wrap {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 10px;
    }

    .range-label { font-size: 0.68rem; flex-shrink: 0; }

    .range-bar {
      flex: 1;
      height: 4px;
      background: rgba(255,255,255,0.08);
      border-radius: 2px;
      overflow: hidden;
    }

    .range-fill {
      height: 100%;
      background: linear-gradient(90deg, var(--gain), var(--accent));
      border-radius: 2px;
      transition: width 0.6s ease;
    }

    .sc-vol {
      font-size: 0.72rem;
    }

    .sc-arrow {
      position: absolute;
      bottom: 16px;
      right: 20px;
      font-size: 0.9rem;
      color: var(--text-muted);
      opacity: 0;
      transform: translateX(-4px);
      transition: opacity 0.2s, transform 0.2s;
    }

    .stock-card:hover .sc-arrow {
      opacity: 1;
      transform: translateX(0);
      color: var(--accent);
    }

    @media (max-width: 600px) {
      .stocks-grid { grid-template-columns: 1fr 1fr; }
      .index-strip { grid-template-columns: 1fr 1fr; }
    }
  `]
})
export class DashboardComponent implements OnInit, OnDestroy {
  overview: MarketOverview | null = null;
  loading = false;
  error = false;
  lastUpdated: Date | null = null;
  private destroy$ = new Subject<void>();

  get gainCount() {
    return this.overview?.topStocks.filter(s => s.changePercent >= 0).length ?? 0;
  }
  get lossCount() {
    return this.overview?.topStocks.filter(s => s.changePercent < 0).length ?? 0;
  }
  get topGainer() {
    return this.overview?.topStocks.reduce((a, b) => (a.changePercent > b.changePercent ? a : b), this.overview.topStocks[0]);
  }
  get topLoser() {
    return this.overview?.topStocks.reduce((a, b) => (a.changePercent < b.changePercent ? a : b), this.overview.topStocks[0]);
  }

  constructor(private stockService: StockService, private router: Router) {}

  ngOnInit() {
    this.loading = true;
    interval(60_000).pipe(
      startWith(0),
      switchMap(() => this.stockService.getMarketOverview().pipe(
        catchError(() => { this.error = true; return of(null); })
      )),
      takeUntil(this.destroy$)
    ).subscribe(data => {
      this.loading = false;
      if (data) {
        this.overview = data;
        this.error = false;
        this.lastUpdated = new Date();
      }
    });
  }

  ngOnDestroy() { this.destroy$.next(); this.destroy$.complete(); }

  refresh() {
    this.loading = true;
    this.error = false;
    this.stockService.getMarketOverview().pipe(
      catchError(() => { this.error = true; this.loading = false; return of(null); })
    ).subscribe(data => {
      this.loading = false;
      if (data) { this.overview = data; this.lastUpdated = new Date(); }
    });
  }

  goToStock(symbol: string) { this.router.navigate(['/stock', symbol]); }

  rangePercent(s: StockQuote): number {
    if (!s.yearHigh || !s.yearLow || s.yearHigh === s.yearLow) return 50;
    return Math.min(100, Math.max(0, ((s.lastPrice - s.yearLow) / (s.yearHigh - s.yearLow)) * 100));
  }

  formatVol(v: number): string {
    if (!v) return '—';
    if (v >= 1e7) return (v / 1e7).toFixed(2) + ' Cr';
    if (v >= 1e5) return (v / 1e5).toFixed(2) + ' L';
    return v.toLocaleString('en-IN');
  }

  trackBySymbol(_: number, s: StockQuote) { return s.symbol; }
}
