import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { SearchComponent } from '../search/search.component';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, SearchComponent],
  template: `
    <header class="navbar" id="main-navbar">
      <div class="nav-inner container">
        <!-- Logo -->
        <a routerLink="/" class="nav-logo" id="nav-logo">
          <div class="logo-icon">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/>
              <polyline points="16 7 22 7 22 13"/>
            </svg>
          </div>
          <div class="logo-text">
            <span class="logo-title">NSE Stocks</span>
            <span class="logo-sub">Market Dashboard</span>
          </div>
        </a>

        <!-- Nav links -->
        <nav class="nav-links" role="navigation">
          <a
            id="nav-dashboard"
            routerLink="/"
            routerLinkActive="active"
            [routerLinkActiveOptions]="{exact: true}"
            class="nav-link">
            Dashboard
          </a>
        </nav>

        <!-- Search -->
        <div class="nav-search">
          <app-search></app-search>
        </div>

        <!-- Market status indicator -->
        <div class="market-status" id="market-status">
          <span class="status-dot"></span>
          <span class="status-label">Live</span>
        </div>
      </div>
    </header>
  `,
  styles: [`
    .navbar {
      position: sticky;
      top: 0;
      z-index: 100;
      height: 64px;
      background: rgba(8, 12, 30, 0.85);
      backdrop-filter: blur(20px);
      border-bottom: 1px solid var(--border);
    }

    .nav-inner {
      display: flex;
      align-items: center;
      gap: 24px;
      height: 100%;
    }

    /* --- Logo --- */
    .nav-logo {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-shrink: 0;
    }

    .logo-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 38px;
      height: 38px;
      background: linear-gradient(135deg, rgba(0,255,163,0.2), rgba(68,138,255,0.2));
      border: 1px solid var(--border-accent);
      border-radius: 10px;
      color: var(--accent);
      flex-shrink: 0;
    }

    .logo-text {
      display: flex;
      flex-direction: column;
      line-height: 1.1;
    }

    .logo-title {
      font-size: 0.95rem;
      font-weight: 700;
      color: var(--text-primary);
      letter-spacing: -0.02em;
    }

    .logo-sub {
      font-size: 0.65rem;
      color: var(--text-muted);
      font-weight: 400;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }

    /* --- Nav links --- */
    .nav-links {
      display: flex;
      align-items: center;
      gap: 4px;
    }

    .nav-link {
      padding: 6px 14px;
      border-radius: 8px;
      font-size: 0.875rem;
      font-weight: 500;
      color: var(--text-secondary);
      transition: color 0.2s, background 0.2s;
    }

    .nav-link:hover, .nav-link.active {
      color: var(--text-primary);
      background: rgba(255,255,255,0.06);
    }

    .nav-link.active {
      color: var(--accent);
    }

    /* --- Search --- */
    .nav-search { margin-left: auto; }

    /* --- Market status --- */
    .market-status {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 4px 12px;
      background: rgba(0, 230, 118, 0.08);
      border: 1px solid rgba(0, 230, 118, 0.2);
      border-radius: 100px;
      flex-shrink: 0;
    }

    .status-dot {
      width: 7px;
      height: 7px;
      background: var(--gain);
      border-radius: 50%;
      animation: pulse 2s ease-in-out infinite;
    }

    @keyframes pulse {
      0%, 100% { opacity: 1; transform: scale(1); }
      50%       { opacity: 0.5; transform: scale(0.8); }
    }

    .status-label {
      font-size: 0.72rem;
      font-weight: 600;
      color: var(--gain);
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }

    @media (max-width: 600px) {
      .nav-links, .logo-text, .market-status { display: none; }
      .nav-search { margin-left: auto; }
    }
  `]
})
export class NavbarComponent {}
