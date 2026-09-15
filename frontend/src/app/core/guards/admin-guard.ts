import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../services/auth-service';
import { AuthResponse } from '../models/auth.model';

/**
 * Only ADMIN_DOCUMENTS may manage users. The backend enforces that on every request; this guard only keeps
 * everyone else from landing on a screen whose calls would all come back 403.
 */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const decide = (user: AuthResponse | null) => {
    if (!user) return router.createUrlTree(['/login']);
    return user.roles.includes('ADMIN_DOCUMENTS') ? true : router.createUrlTree(['/chat']);
  };

  // After a reload the guard runs before the shell has restored the session, so ask /me first.
  const current = auth.user();
  return current ? decide(current) : auth.loadCurrentUser().pipe(map(decide));
};
