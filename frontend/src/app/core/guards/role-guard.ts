import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../services/auth-service';
import { AuthResponse, Role } from '../models/auth.model';

/**
 * Lets the route through when the user holds any of the given roles. The backend enforces the same rules on every
 * request; the guard only keeps people off screens whose calls would all come back 401 or 403.
 */
export function roleGuard(...allowed: Role[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    const decide = (user: AuthResponse | null) => {
      if (!user) return router.createUrlTree(['/login']);
      return user.roles.some(role => allowed.includes(role)) ? true : router.createUrlTree(['/chat']);
    };

    // After a reload the guard runs before the shell has restored the session, so ask /me first.
    const current = auth.user();
    return current ? decide(current) : auth.loadCurrentUser().pipe(map(decide));
  };
}

export const adminGuard = roleGuard('ADMIN_DOCUMENTS');

// SecurityConfig's RoleHierarchy makes ADMIN_DOCUMENTS imply UPLOADER, but /me only lists the assigned role.
export const uploaderGuard = roleGuard('UPLOADER', 'ADMIN_DOCUMENTS');
