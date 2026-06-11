import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, switchMap, takeUntil, of } from 'rxjs';
import { StockService } from '../../services/stock.service';
import { SearchResult } from '../../models/stock.models';

@Component({
  selector: 'app-search',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="search-wrapper" id="search-wrapper">
      <div class="search-input-row">
        <span class="search-icon">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
        </span>
        <input
          id="stock-search-input"
          class="search-input"
          type="text"
          placeholder="Search stocks…"
          autocomplete="off"
          [value]="query"
          (input)="onInput($event)"
          (focus)="onFocus()"
          (blur)="onBlur()"
          (keydown.Escape)="close()"
        />
        <span *ngIf="query" class="clear-btn" (mousedown)="clearSearch()">✕</span>
      </div>

      <div class="search-dropdown" *ngIf="showDropdown && (results.length > 0 || loading || error)">
        <div *ngIf="loading" class="search-state">
          <div class="mini-spinner"></div>
          <span>Searching…</span>
        </div>
        <div *ngIf="error && !loading" class="search-state error-text">⚠ Failed to search</div>
        <ul *ngIf="!loading && results.length > 0" class="result-list">
          <li
            *ngFor="let r of results; trackBy: trackBySymbol"
            class="result-item"
            (mousedown)="navigate(r)"
            [id]="'result-' + r.symbol">
            <span class="result-symbol">{{ r.symbol }}</span>
            <span class="result-name">{{ r.name }}</span>
          </li>
        </ul>
        <div *ngIf="!loading && !error && results.length === 0 && query.length > 1" class="search-state">
          No results for "{{ query }}"
        </div>
      </div>
    </div>
  `,
  styles: [`
    .search-wrapper {
      position: relative;
      width: 280px;
    }

    .search-input-row {
      display: flex;
      align-items: center;
      background: rgba(255,255,255,0.06);
      border: 1px solid rgba(255,255,255,0.1);
      border-radius: 10px;
      padding: 0 14px;
      gap: 10px;
      transition: border-color 0.2s, background 0.2s;
    }

    .search-input-row:focus-within {
      border-color: var(--accent);
      background: rgba(0, 255, 163, 0.05);
    }

    .search-icon { color: var(--text-muted); display: flex; align-items: center; flex-shrink: 0; }

    .search-input {
      flex: 1;
      background: transparent;
      border: none;
      outline: none;
      color: var(--text-primary);
      font-size: 0.875rem;
      font-family: inherit;
      padding: 10px 0;
    }

    .search-input::placeholder { color: var(--text-muted); }

    .clear-btn {
      color: var(--text-muted);
      cursor: pointer;
      font-size: 0.75rem;
      line-height: 1;
      flex-shrink: 0;
      transition: color 0.15s;
    }
    .clear-btn:hover { color: var(--text-primary); }

    .search-dropdown {
      position: absolute;
      top: calc(100% + 8px);
      left: 0;
      right: 0;
      background: rgba(13, 18, 48, 0.96);
      border: 1px solid var(--border);
      border-radius: 12px;
      box-shadow: var(--shadow-popup);
      backdrop-filter: blur(20px);
      z-index: 200;
      overflow: hidden;
      animation: dropIn 0.15s ease;
    }

    @keyframes dropIn {
      from { opacity: 0; transform: translateY(-6px); }
      to   { opacity: 1; transform: translateY(0); }
    }

    .search-state {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 16px;
      color: var(--text-secondary);
      font-size: 0.85rem;
    }
    .error-text { color: var(--loss); }

    .mini-spinner {
      width: 16px; height: 16px;
      border: 2px solid var(--border);
      border-top-color: var(--accent);
      border-radius: 50%;
      animation: spin 0.6s linear infinite;
      flex-shrink: 0;
    }
    @keyframes spin { to { transform: rotate(360deg); } }

    .result-list { list-style: none; }

    .result-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      cursor: pointer;
      transition: background 0.15s;
      border-bottom: 1px solid rgba(255,255,255,0.04);
    }
    .result-item:last-child { border-bottom: none; }
    .result-item:hover { background: rgba(0, 255, 163, 0.07); }

    .result-symbol {
      font-size: 0.875rem;
      font-weight: 700;
      color: var(--accent);
      min-width: 90px;
      flex-shrink: 0;
    }

    .result-name {
      font-size: 0.8rem;
      color: var(--text-secondary);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    @media (max-width: 600px) {
      .search-wrapper { width: 200px; }
    }
  `]
})
export class SearchComponent implements OnInit, OnDestroy {
  query = '';
  results: SearchResult[] = [];
  loading = false;
  error = false;
  showDropdown = false;
  private focused = false;

  private searchTerms$ = new Subject<string>();
  private destroy$ = new Subject<void>();

  constructor(private stockService: StockService, private router: Router) {}

  ngOnInit() {
    this.searchTerms$.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap(q => {
        if (q.trim().length < 1) { this.results = []; return of([]); }
        this.loading = true;
        this.error = false;
        return this.stockService.search(q);
      }),
      takeUntil(this.destroy$)
    ).subscribe({
      next: res => { this.results = res; this.loading = false; },
      error: () => { this.error = true; this.loading = false; }
    });
  }

  ngOnDestroy() { this.destroy$.next(); this.destroy$.complete(); }

  onInput(event: Event) {
    this.query = (event.target as HTMLInputElement).value;
    this.showDropdown = true;
    this.searchTerms$.next(this.query);
  }

  onFocus() { this.focused = true; if (this.query) this.showDropdown = true; }
  onBlur()  { setTimeout(() => { if (!this.focused) this.showDropdown = false; }, 150); this.focused = false; }

  navigate(r: SearchResult) {
    this.router.navigate(['/stock', r.symbol]);
    this.close();
  }

  close() { this.showDropdown = false; }

  clearSearch() {
    this.query = '';
    this.results = [];
    this.showDropdown = false;
    this.searchTerms$.next('');
  }

  trackBySymbol(_: number, r: SearchResult) { return r.symbol; }
}
