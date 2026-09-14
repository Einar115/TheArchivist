import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { Observable, firstValueFrom, isObservable, of } from 'rxjs';
import { adminGuard } from './admin-guard';
import { AuthService } from '../services/auth-service';
import { AuthResponse } from '../models/auth.model';

describe('adminGuard', () => {
  const user = signal<AuthResponse | null>(null);
  let sessionOnServer: AuthResponse | null = null;

  const run = async () => {
    const result = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));
    return isObservable(result) ? firstValueFrom(result as Observable<unknown>) : result;
  };

  const urlOf = (result: unknown) => TestBed.inject(Router).serializeUrl(result as UrlTree);

  beforeEach(() => {
    user.set(null);
    sessionOnServer = null;

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { user, loadCurrentUser: () => of(sessionOnServer) } },
      ],
    });
  });

  it('lets an ADMIN_DOCUMENTS user through', async () => {
    user.set({ username: 'admin', roles: ['ADMIN_DOCUMENTS'] });

    expect(await run()).toBe(true);
  });

  it('sends an UPLOADER back to the chat', async () => {
    user.set({ username: 'maria.lopez', roles: ['UPLOADER'] });

    expect(urlOf(await run())).toBe('/chat');
  });

  it('restores the session from /me before deciding after a reload', async () => {
    sessionOnServer = { username: 'admin', roles: ['ADMIN_DOCUMENTS'] };

    expect(await run()).toBe(true);
  });

  it('redirects to the login when there is no session', async () => {
    expect(urlOf(await run())).toBe('/login');
  });
});
