import {
  AfterViewInit, Component, ElementRef, OnDestroy, OnInit, ViewChild
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, switchMap, takeUntil, catchError, of, forkJoin } from 'rxjs';
import { Chart, registerables } from 'chart.js';
import { StockService } from '../../services/stock.service';
import { StockData, StockQuote } from '../../models/stock.models';

Chart.register(...registerables);

type Range = '1W' | '1M' | '3M' | '6M' | '1Y';
const RANGE_MAP: Record<Range, string> = { '1W': '5d', '1M': '1mo', '3M': '3mo', '6M': '6mo', '1Y': '1y' };

@Component({
  selector: 'app-stock-detail',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="page">
      <div class="container">

        <!-- Back button -->
        <button class="back-btn" id="back-btn" (click)="goBack()">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/>
          </svg>
          Back
        </button>

        <!-- Loading -->
        <div *ngIf="loading" class="spinner"></div>

        <!-- Error -->
        <div *ngIf="error && !quote" class="error-state">
          <div class="icon">⚠️</div>
          <h3>Could not load {{ symbol }}</h3>
          <p>Check that the symbol is valid and the backend is running.</p>
          <button class="retry-btn" id="detail-retry-btn" (click)="load()">Retry</button>
        </div>

        <ng-container *ngIf="quote && !loading">

          <!-- Stock header -->
          <div class="stock-header">
            <div class="stock-identity">
              <h1 class="stock-symbol">{{ quote.symbol }}</h1>
              <p class="stock-company">{{ quote.companyName }}</p>
              <div class="stock-meta-tags" *ngIf="quote.series || quote.industry">
                <span class="meta-tag" *ngIf="quote.series">{{ quote.series }}</span>
                <span class="meta-tag" *ngIf="quote.industry">{{ quote.industry }}</span>
                <span class="meta-tag muted" *ngIf="quote.isin">{{ quote.isin }}</span>
              </div>
            </div>

            <div class="stock-price-block">
              <div class="stock-last-price">₹{{ quote.lastPrice | number:'1.2-2' }}</div>
              <div [class]="quote.change >= 0 ? 'badge-gain price-badge' : 'badge-loss price-badge'">
                {{ quote.change >= 0 ? '▲' : '▼' }}
                {{ quote.change | number:'1.2-2' }}
                &nbsp;({{ quote.changePercent | number:'1.2-2' }}%)
              </div>
              <div class="prev-close muted">Prev Close ₹{{ quote.previousClose | number:'1.2-2' }}</div>
            </div>
          </div>

          <!-- Stats grid -->
          <div class="stats-grid">
            <div class="stat-card card">
              <div class="stat-label">Open</div>
              <div class="stat-value">₹{{ quote.open | number:'1.2-2' }}</div>
            </div>
            <div class="stat-card card">
              <div class="stat-label">High</div>
              <div class="stat-value gain">₹{{ quote.high | number:'1.2-2' }}</div>
            </div>
            <div class="stat-card card">
              <div class="stat-label">Low</div>
              <div class="stat-value loss">₹{{ quote.low | number:'1.2-2' }}</div>
            </div>
            <div class="stat-card card">
              <div class="stat-label">Volume</div>
              <div class="stat-value">{{ formatVol(quote.totalTradedVolume) }}</div>
            </div>
            <div class="stat-card card">
              <div class="stat-label">52W High</div>
              <div class="stat-value gain">₹{{ quote.yearHigh | number:'1.2-2' }}</div>
            </div>
            <div class="stat-card card">
              <div class="stat-label">52W Low</div>
              <div class="stat-value loss">₹{{ quote.yearLow | number:'1.2-2' }}</div>
            </div>
            <div class="stat-card card">
              <div class="stat-label">Upper Circuit</div>
              <div class="stat-value">₹{{ quote.upperCircuitLimit | number:'1.2-2' }}</div>
            </div>
            <div class="stat-card card">
              <div class="stat-label">Lower Circuit</div>
              <div class="stat-value">₹{{ quote.lowerCircuitLimit | number:'1.2-2' }}</div>
            </div>
            <div class="stat-card card" *ngIf="quote.bestBidPrice">
              <div class="stat-label">Best Bid</div>
              <div class="stat-value">₹{{ quote.bestBidPrice | number:'1.2-2' }} × {{ quote.bestBidQty | number }}</div>
            </div>
            <div class="stat-card card" *ngIf="quote.bestAskPrice">
              <div class="stat-label">Best Ask</div>
              <div class="stat-value">₹{{ quote.bestAskPrice | number:'1.2-2' }} × {{ quote.bestAskQty | number }}</div>
            </div>
            <div class="stat-card card" *ngIf="quote.deliveryToTradedQty">
              <div class="stat-label">Delivery %</div>
              <div class="stat-value">{{ quote.deliveryToTradedQty | number:'1.2-2' }}%</div>
            </div>
            <div class="stat-card card" *ngIf="quote.totalTradedValue">
              <div class="stat-label">Turnover (L)</div>
              <div class="stat-value">₹{{ quote.totalTradedValue | number:'1.2-2' }}</div>
            </div>
          </div>

          <!-- 52W range indicator -->
          <div class="year-range card">
            <div class="yr-labels">
              <span class="yr-key">52W Low</span>
              <span class="yr-key">Current</span>
              <span class="yr-key">52W High</span>
            </div>
            <div class="yr-track">
              <div class="yr-fill" [style.width]="rangePercent() + '%'"></div>
              <div class="yr-thumb" [style.left]="rangePercent() + '%'"></div>
            </div>
            <div class="yr-vals">
              <span>₹{{ quote.yearLow | number:'1.2-2' }}</span>
              <span class="yr-current">₹{{ quote.lastPrice | number:'1.2-2' }}</span>
              <span>₹{{ quote.yearHigh | number:'1.2-2' }}</span>
            </div>
          </div>

          <!-- Chart section -->
          <div class="chart-section card">
            <div class="chart-toolbar">
              <h2 class="chart-title">Price History</h2>
              <div class="range-btns" role="group">
                <button
                  *ngFor="let r of ranges"
                  [id]="'range-btn-' + r"
                  class="range-btn"
                  [class.active]="activeRange === r"
                  (click)="setRange(r)">
                  {{ r }}
                </button>
              </div>
            </div>

            <div *ngIf="chartLoading" class="chart-loading">
              <div class="mini-spinner"></div>
            </div>

            <div class="chart-wrap" [class.hidden]="chartLoading">
              <canvas #chartCanvas id="price-chart"></canvas>
            </div>

            <div *ngIf="chartError" class="chart-error">
              Could not load historical data
            </div>
          </div>

        </ng-container>
      </div>
    </div>
  `,
  styles: [`
    /* --- Back button --- */
    .back-btn {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 8px 16px;
      background: transparent;
      border: 1px solid var(--border);
      border-radius: 8px;
      color: var(--text-secondary);
      font-size: 0.85rem;
      font-weight: 500;
      margin-bottom: 24px;
      transition: border-color 0.2s, color 0.2s, background 0.2s;
    }
    .back-btn:hover {
      border-color: var(--border-accent);
      color: var(--accent);
      background: var(--accent-dim);
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

    /* --- Stock header --- */
    .stock-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 24px;
      margin-bottom: 28px;
      flex-wrap: wrap;
    }

    .stock-symbol {
      font-size: 2rem;
      font-weight: 900;
      letter-spacing: -0.04em;
      color: var(--text-primary);
    }

    .stock-company {
      font-size: 0.9rem;
      color: var(--text-secondary);
      margin-top: 4px;
      font-weight: 400;
    }

    .stock-meta-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      margin-top: 10px;
    }

    .meta-tag {
      font-size: 0.7rem;
      font-weight: 600;
      padding: 3px 10px;
      background: rgba(255,255,255,0.06);
      border: 1px solid var(--border);
      border-radius: 100px;
      color: var(--text-secondary);
      letter-spacing: 0.05em;
    }

    .stock-price-block {
      text-align: right;
    }

    .stock-last-price {
      font-size: 2.5rem;
      font-weight: 900;
      letter-spacing: -0.05em;
      color: var(--text-primary);
      line-height: 1;
      margin-bottom: 10px;
    }

    .price-badge { font-size: 0.9rem; padding: 4px 14px; }

    .prev-close {
      margin-top: 8px;
      font-size: 0.78rem;
    }

    /* --- Stats grid --- */
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
      gap: 12px;
      margin-bottom: 20px;
    }

    .stat-card {
      padding: 14px 18px;
    }

    .stat-label {
      font-size: 0.68rem;
      font-weight: 700;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--text-muted);
      margin-bottom: 6px;
    }

    .stat-value {
      font-size: 1rem;
      font-weight: 700;
      color: var(--text-primary);
    }

    /* --- 52W Range --- */
    .year-range {
      margin-bottom: 20px;
      padding: 18px 24px;
    }

    .yr-labels {
      display: flex;
      justify-content: space-between;
      font-size: 0.68rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      color: var(--text-muted);
      margin-bottom: 10px;
    }

    .yr-track {
      position: relative;
      height: 6px;
      background: rgba(255,255,255,0.08);
      border-radius: 3px;
      margin-bottom: 10px;
      overflow: visible;
    }

    .yr-fill {
      height: 100%;
      background: linear-gradient(90deg, var(--loss) 0%, var(--accent) 60%, var(--gain) 100%);
      border-radius: 3px;
      transition: width 0.8s cubic-bezier(0.4,0,0.2,1);
    }

    .yr-thumb {
      position: absolute;
      top: 50%;
      transform: translate(-50%, -50%);
      width: 14px;
      height: 14px;
      background: white;
      border-radius: 50%;
      border: 2px solid var(--accent);
      box-shadow: 0 0 8px rgba(0,255,163,0.5);
      transition: left 0.8s cubic-bezier(0.4,0,0.2,1);
    }

    .yr-vals {
      display: flex;
      justify-content: space-between;
      font-size: 0.78rem;
      color: var(--text-secondary);
    }

    .yr-current {
      font-weight: 700;
      color: var(--accent);
    }

    /* --- Chart --- */
    .chart-section {
      padding: 24px;
    }

    .chart-toolbar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 20px;
      flex-wrap: wrap;
      gap: 12px;
    }

    .chart-title {
      font-size: 1rem;
      font-weight: 700;
    }

    .range-btns {
      display: flex;
      gap: 4px;
      background: rgba(255,255,255,0.04);
      border: 1px solid var(--border);
      border-radius: 8px;
      padding: 3px;
    }

    .range-btn {
      padding: 5px 14px;
      border: none;
      border-radius: 6px;
      background: transparent;
      color: var(--text-secondary);
      font-size: 0.8rem;
      font-weight: 600;
      transition: background 0.15s, color 0.15s;
    }

    .range-btn.active {
      background: var(--accent-dim);
      color: var(--accent);
      border: 1px solid var(--border-accent);
    }

    .range-btn:hover:not(.active) {
      background: rgba(255,255,255,0.06);
      color: var(--text-primary);
    }

    .chart-wrap { position: relative; height: 360px; }
    .chart-wrap.hidden { opacity: 0; height: 0; overflow: hidden; }

    .chart-loading {
      display: flex;
      justify-content: center;
      padding: 80px;
    }

    .mini-spinner {
      width: 28px; height: 28px;
      border: 3px solid var(--border);
      border-top-color: var(--accent);
      border-radius: 50%;
      animation: spin 0.6s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }

    .chart-error {
      text-align: center;
      padding: 40px;
      color: var(--text-muted);
      font-size: 0.85rem;
    }

    @media (max-width: 640px) {
      .stock-header { flex-direction: column; }
      .stock-price-block { text-align: left; }
      .stock-last-price { font-size: 1.8rem; }
      .stats-grid { grid-template-columns: 1fr 1fr; }
      .chart-wrap { height: 260px; }
    }
  `]
})
export class StockDetailComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild('chartCanvas') chartCanvas!: ElementRef<HTMLCanvasElement>;

  symbol = '';
  quote: StockQuote | null = null;
  history: StockData[] = [];
  loading = false;
  error = false;
  chartLoading = false;
  chartError = false;
  activeRange: Range = '6M';
  readonly ranges: Range[] = ['1W', '1M', '3M', '6M', '1Y'];

  private chart: Chart | null = null;
  private destroy$ = new Subject<void>();
  private rangeChange$ = new Subject<Range>();

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private stockService: StockService
  ) {}

  ngOnInit() {
    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe(params => {
      this.symbol = params.get('symbol') ?? '';
      this.load();
    });
  }

  ngAfterViewInit() {
    this.rangeChange$.pipe(
      switchMap(range =>
        this.stockService.getHistoricalByRange(this.symbol, RANGE_MAP[range]).pipe(
          catchError(() => { this.chartError = true; return of([]); })
        )
      ),
      takeUntil(this.destroy$)
    ).subscribe(data => {
      this.chartLoading = false;
      this.history = data;
      this.drawChart(data);
    });

    // Trigger initial chart load once view ready
    setTimeout(() => this.rangeChange$.next(this.activeRange), 0);
  }

  ngOnDestroy() {
    this.chart?.destroy();
    this.destroy$.next();
    this.destroy$.complete();
  }

  load() {
    this.loading = true;
    this.error = false;
    this.stockService.getQuote(this.symbol).pipe(
      catchError(() => { this.error = true; this.loading = false; return of(null); })
    ).subscribe(q => {
      this.loading = false;
      this.quote = q;
    });
  }

  setRange(r: Range) {
    this.activeRange = r;
    this.chartLoading = true;
    this.chartError = false;
    this.rangeChange$.next(r);
  }

  goBack() { this.router.navigate(['/']); }

  rangePercent(): number {
    if (!this.quote) return 50;
    const { lastPrice, yearLow, yearHigh } = this.quote;
    if (!yearHigh || !yearLow || yearHigh === yearLow) return 50;
    return Math.min(100, Math.max(0, ((lastPrice - yearLow) / (yearHigh - yearLow)) * 100));
  }

  formatVol(v: number): string {
    if (!v) return '—';
    if (v >= 1e7) return (v / 1e7).toFixed(2) + ' Cr';
    if (v >= 1e5) return (v / 1e5).toFixed(2) + ' L';
    return v.toLocaleString('en-IN');
  }

  private drawChart(data: StockData[]) {
    if (!this.chartCanvas) return;

    const canvas = this.chartCanvas.nativeElement;
    const labels = data.map(d => d.date);
    const prices = data.map(d => d.close);

    const isPositive = prices.length > 1 && prices[prices.length - 1] >= prices[0];
    const lineColor = isPositive ? '#00e676' : '#ff5252';

    const ctx = canvas.getContext('2d')!;
    const gradient = ctx.createLinearGradient(0, 0, 0, canvas.offsetHeight || 360);
    gradient.addColorStop(0, isPositive ? 'rgba(0,230,118,0.25)' : 'rgba(255,82,82,0.25)');
    gradient.addColorStop(1, 'rgba(0,0,0,0)');

    this.chart?.destroy();
    this.chart = new Chart(canvas, {
      type: 'line',
      data: {
        labels,
        datasets: [{
          label: this.symbol,
          data: prices,
          borderColor: lineColor,
          backgroundColor: gradient,
          borderWidth: 2,
          pointRadius: 0,
          pointHoverRadius: 5,
          pointHoverBackgroundColor: lineColor,
          tension: 0.3,
          fill: true,
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: { duration: 600, easing: 'easeInOutQuart' },
        interaction: { mode: 'index', intersect: false },
        plugins: {
          legend: { display: false },
          tooltip: {
            backgroundColor: 'rgba(13,18,48,0.96)',
            borderColor: 'rgba(255,255,255,0.1)',
            borderWidth: 1,
            titleColor: '#8896a8',
            bodyColor: '#e8edf5',
            bodyFont: { weight: 700 as const, size: 14 },
            padding: 12,
            callbacks: {
              label: ctx => `  ₹${(ctx.parsed.y ?? 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`
            }
          }
        },
        scales: {
          x: {
            grid: { color: 'rgba(255,255,255,0.04)' },
            ticks: {
              color: '#566474',
              font: { size: 11 },
              maxTicksLimit: 8,
              maxRotation: 0,
            },
            border: { color: 'rgba(255,255,255,0.06)' }
          },
          y: {
            position: 'right',
            grid: { color: 'rgba(255,255,255,0.04)' },
            ticks: {
              color: '#566474',
              font: { size: 11 },
              callback: v => '₹' + Number(v).toLocaleString('en-IN')
            },
            border: { color: 'rgba(255,255,255,0.06)' }
          }
        }
      }
    });
  }
}
