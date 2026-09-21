import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Shell } from './shell';
import { HealthService } from '../core/services/health-service';
import { AuthService } from '../core/services/auth-service';
import { ConversationService } from '../core/services/conversation-service';
import { AuthResponse } from '../core/models/auth.model';
import { Conversation } from '../core/models/chat.model';

describe('Shell', () => {
  const isHealthy = signal(true);
  const user = signal<AuthResponse | null>(null);
  const conversations = signal<Conversation[]>([]);

  let fixture: ComponentFixture<Shell>;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  beforeEach(async () => {
    isHealthy.set(true);
    user.set({ username: 'admin', roles: ['ADMIN_DOCUMENTS'] });
    conversations.set([{ id: 'c1', title: 'La caída del reino de Hyrule', updatedAt: Date.now(), messages: [] }]);

    await TestBed.configureTestingModule({
      imports: [Shell],
      providers: [
        // Logging out navigates to /login, so the router needs a route to land on.
        provideRouter([{ path: 'login', children: [] }]),
        // The real HealthService polls /actuator/health every 5 s; the indicator only reads the signal.
        { provide: HealthService, useValue: { isHealthy } },
        { provide: AuthService, useValue: { user, loadCurrentUser: () => of(null), logout: () => of(undefined) } },
        { provide: ConversationService, useValue: { conversations, clear: () => conversations.set([]) } },
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

  it("lists this session's conversations under Hoy and drops them on logout", () => {
    expect(text()).toContain('Hoy');
    expect(text()).toContain('La caída del reino de Hyrule');

    fixture.componentInstance.logout();
    fixture.detectChanges();

    expect(text()).not.toContain('La caída del reino de Hyrule');
  });
});
