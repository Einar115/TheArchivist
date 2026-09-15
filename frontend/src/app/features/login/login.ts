import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth-service';

@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-login',
  styleUrl: './login.css',
  templateUrl: './login.html',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly passwordVisible = signal(false);

  readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
    rememberMe: [false],
  });

  isInvalid(control: 'username' | 'password'): boolean {
    const field = this.form.controls[control];
    return field.invalid && field.touched;
  }

  togglePasswordVisibility(): void {
    this.passwordVisible.update(visible => !visible);
  }

  submit(): void {
    if (this.loading()) return;

    this.errorMessage.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { username, password } = this.form.getRawValue();

    this.loading.set(true);
    // Disabling the group is what greys out the fields while the request is in flight.
    this.form.disable({ emitEvent: false });

    this.auth.login({ username, password }).subscribe({
      next: () => this.router.navigateByUrl('/chat'),
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        this.form.enable({ emitEvent: false });
        this.form.controls.password.reset();
        this.errorMessage.set(this.messageFor(error));
      },
    });
  }

  private messageFor(error: HttpErrorResponse): string {
    // The backend answers every failed attempt with the same 401 on purpose, so that
    // a disabled or locked account cannot be told apart from a wrong password.
    if (error.status === 401) return 'Usuario o contraseña incorrectos.';
    if (error.status === 0) return 'No se pudo conectar con el servidor. Revisa tu conexión.';
    return 'Ha ocurrido un error inesperado. Inténtalo de nuevo.';
  }
}
