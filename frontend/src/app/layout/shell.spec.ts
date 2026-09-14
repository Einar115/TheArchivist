import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Shell } from './shell';
import { HealthService } from '../core/services/health-service';
import { AuthService } from '../core/services/auth-service';
import { AuthResponse } from '../core/models/auth.model';

describe('Shell', () => {
  const isHealthy = signal(true);
  const user = signal<AuthResponse | null>(null);

  let fixture: ComponentFixture<Shell>;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  beforeEach(async () => {
    isHealthy.set(true);
    user.set({ username: 'admin', roles: ['ADMIN_DOCUMENTS'] });

    await TestBed.configureTestingModule({
      imports: [Shell],
      providers: [
        provideRouter([]),
        // The real HealthService polls /actuator/health every 5 s; the indicator only reads the signal.
        { provide: HealthService, useValue: { isHealthy } },
        { provide: AuthService, useValue: { user, loadCurrentUser: () => of(null), logout: () => of(undefined) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
  });

  it('shows the backend as available while the health check reports UP', () => {
    expect(text()).toContain('Backend conectado');
  });

  it('warns when the health check fails', () => {
    isHealthy.set(false);
    fixture.detectChanges();

    expect(text()).toContain('Sin conexión con el backend');
  });

  it('shows the signed-in user and their role', () => {
    expect(text()).toContain('admin');
    expect(text()).toContain('ADMIN_DOCUMENTS');
  });

  it('offers the login link when there is no session', () => {
    user.set(null);
    fixture.detectChanges();

    expect(text()).toContain('Iniciar sesión');
  });

  // Bootstrap's JavaScript is not loaded in unit tests, so only the wiring of the mobile panel can be checked.
  it('points the mobile menu button at the offcanvas sidebar', () => {
    const root = fixture.nativeElement as HTMLElement;
    const panel = root.querySelector('aside.offcanvas-lg');
    const toggle = root.querySelector('[data-bs-toggle="offcanvas"]');

    expect(panel?.id).toBeTruthy();
    expect(toggle?.getAttribute('data-bs-target')).toBe(`#${panel?.id}`);
  });

  it('shows the Administración section only to ADMIN_DOCUMENTS users', () => {
    expect(text()).toContain('Administración');

    user.set({ username: 'maria.lopez', roles: ['UPLOADER'] });
    fixture.detectChanges();

    expect(text()).not.toContain('Administración');
  });
});
