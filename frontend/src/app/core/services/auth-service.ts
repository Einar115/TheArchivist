import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of, tap } from 'rxjs';
import { AuthRequest, AuthResponse } from '../models/auth.model';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly apiUrl = `${environment.BACKEND_URL}/api/v1/auth`;

  // Null while nobody is signed in. The session itself lives in the ARCHIVIST_SESSION cookie,
  // so this is only the in-memory copy used to render the UI.
  readonly user = signal<AuthResponse | null>(null);

  constructor(private http: HttpClient) {}

  login(request: AuthRequest): Observable<AuthResponse> {
    // Unlike the SSE chat stream, this goes through HttpClient, whose XSRF interceptor
    // reads the XSRF-TOKEN cookie and sets the header on its own.
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, request).pipe(
      tap(user => this.user.set(user))
    );
  }

  // A page reload drops the in-memory user while the session cookie survives: /me restores it.
  // The backend answers 204 without a session, which HttpClient hands back as a null body.
  loadCurrentUser(): Observable<AuthResponse | null> {
    return this.http.get<AuthResponse | null>(`${this.apiUrl}/me`).pipe(
      catchError(() => of(null)),
      tap(user => this.user.set(user))
    );
  }

  // Handled by Spring Security's LogoutFilter, which invalidates the session and answers 204.
  logout(): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/logout`, null).pipe(
      tap(() => this.user.set(null))
    );
  }
}
