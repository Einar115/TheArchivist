import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { UserRequest, UserResponse } from '../models/auth.model';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class UserService {
  // Registration is mapped under /auth in the backend, but SecurityConfig only lets ADMIN_DOCUMENTS call it.
  private readonly apiUrl = `${environment.BACKEND_URL}/api/v1/auth`;

  constructor(private http: HttpClient) {}

  create(request: UserRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>(`${this.apiUrl}/register`, request);
  }
}
