import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { interval } from 'rxjs';
import { switchMap, catchError } from 'rxjs/operators';
import { of } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface HealthStatus {
  status: 'UP' | 'DOWN';
  timestamp: Date;
}

@Injectable({ providedIn: 'root' })
export class HealthService {
  private readonly healthUrl = `${environment.BACKEND_URL}/actuator/health`;

  backendHealth = signal<HealthStatus>({
    status: 'DOWN',
    timestamp: new Date()
  });

  isHealthy = signal(false);

  constructor(private http: HttpClient) {
    this.startHealthCheck();
  }

  private startHealthCheck() {
    interval(5000)
      .pipe(
        switchMap(() =>
          this.http.get<{ status: string }>(this.healthUrl).pipe(
            catchError(() => of({ status: 'DOWN' }))
          )
        )
      )
      .subscribe(response => {
        const status = (response.status === 'UP' ? 'UP' : 'DOWN') as 'UP' | 'DOWN';
        this.backendHealth.set({
          status,
          timestamp: new Date()
        });
        this.isHealthy.set(status === 'UP');
      });

    // Initial check
    this.checkHealth();
  }

  private checkHealth() {
    this.http.get<{ status: string }>(this.healthUrl).pipe(
      catchError(() => of({ status: 'DOWN' }))
    ).subscribe(response => {
      const status = (response.status === 'UP' ? 'UP' : 'DOWN') as 'UP' | 'DOWN';
      this.backendHealth.set({
        status,
        timestamp: new Date()
      });
      this.isHealthy.set(status === 'UP');
    });
  }
}
