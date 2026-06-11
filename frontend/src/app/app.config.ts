import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { StockDetailComponent } from './components/stock-detail/stock-detail.component';

export const appConfig: ApplicationConfig = {
  providers: [
    provideHttpClient(),
    provideRouter([
      { path: '',            component: DashboardComponent },
      { path: 'stock/:symbol', component: StockDetailComponent },
      { path: '**',          redirectTo: '' }
    ])
  ]
};
