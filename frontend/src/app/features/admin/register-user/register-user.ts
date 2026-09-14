import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { map } from 'rxjs';
import { UserService } from '../../../core/services/user-service';
import { Role, UserResponse } from '../../../core/models/auth.model';

// Mirrors @Size(min = 8) on the backend's UserRequest.password.
const PASSWORD_MIN_LENGTH = 8;

type RequirementState = 'pending' | 'met' | 'failed';

interface RoleOption {
  value: Role;
  title: string;
  description: string;
  icon: string;
}

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  return group.get('password')?.value === group.get('confirmPassword')?.value ? null : { passwordMismatch: true };
}

@Component({
  imports: [ReactiveFormsModule],
  selector: 'app-register-user',
  styleUrl: './register-user.css',
  templateUrl: './register-user.html',
})
export class RegisterUser {
  private readonly users = inject(UserService);

  readonly passwordMinLength = PASSWORD_MIN_LENGTH;

  readonly roles: RoleOption[] = [
    {
      value: 'UPLOADER',
      title: 'Uploader',
      description: 'Consulta el chat y sube documentos al archivo.',
      icon: 'bi-cloud-arrow-up',
    },
    {
      value: 'ADMIN_DOCUMENTS',
      title: 'Administrador de documentos',
      description: 'Todo lo de Uploader, y además elimina documentos y registra usuarios.',
      icon: 'bi-shield-check',
    },
  ];

  readonly loading = signal(false);
  readonly submitted = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly created = signal<UserResponse | null>(null);
  readonly passwordVisible = signal(false);
  readonly confirmVisible = signal(false);

  readonly form = inject(FormBuilder).nonNullable.group(
    {
      // @NotBlank on the backend rejects whitespace-only names, which Validators.required lets through.
      username: ['', [Validators.required, Validators.pattern(/\S/)]],
      password: ['', [Validators.required, Validators.minLength(PASSWORD_MIN_LENGTH)]],
      confirmPassword: ['', Validators.required],
      role: ['UPLOADER' as Role, Validators.required],
    },
    { validators: passwordsMatch }
  );

  // getRawValue keeps disabled controls, so the preview does not blank out while the request is in flight.
  private readonly value = toSignal(this.form.valueChanges.pipe(map(() => this.form.getRawValue())), {
    initialValue: this.form.getRawValue(),
  });

  readonly lengthState = computed<RequirementState>(() => {
    const { password } = this.value();
    if (!password) return 'pending';
    return password.length >= PASSWORD_MIN_LENGTH ? 'met' : 'failed';
  });

  readonly matchState = computed<RequirementState>(() => {
    const { password, confirmPassword } = this.value();
    if (!confirmPassword) return 'pending';
    return password === confirmPassword ? 'met' : 'failed';
  });

  readonly previewName = computed(() => this.value().username.trim());
  readonly initials = computed(() => this.previewName().slice(0, 2).toUpperCase() || '?');
  readonly selectedRole = computed(() => this.value().role);

  // Mirrors authorizeHttpRequests in SecurityConfig, where ADMIN_DOCUMENTS implies UPLOADER.
  readonly permissions = computed(() => {
    const admin = this.selectedRole() === 'ADMIN_DOCUMENTS';
    return [
      { label: 'Consultar el chat', granted: true },
      { label: 'Subir documentos', granted: true },
      { label: 'Eliminar documentos', granted: admin },
      { label: 'Registrar usuarios', granted: admin },
      { label: 'Ver métricas del sistema', granted: admin },
    ];
  });

  isInvalid(control: 'username' | 'password' | 'confirmPassword'): boolean {
    const field = this.form.controls[control];
    const shown = field.touched || this.submitted();
    if (control === 'confirmPassword') {
      return shown && (field.invalid || this.form.hasError('passwordMismatch'));
    }
    return shown && field.invalid;
  }

  requirementIcon(state: RequirementState): string {
    return { pending: 'bi-circle', met: 'bi-check-circle-fill', failed: 'bi-x-circle-fill' }[state];
  }

  roleTitle(role: Role): string {
    return this.roles.find(option => option.value === role)?.title ?? role;
  }

  togglePasswordVisibility(): void {
    this.passwordVisible.update(visible => !visible);
  }

  toggleConfirmVisibility(): void {
    this.confirmVisible.update(visible => !visible);
  }

  clear(): void {
    this.form.reset();
    this.submitted.set(false);
    this.errorMessage.set(null);
    this.created.set(null);
    this.passwordVisible.set(false);
    this.confirmVisible.set(false);
  }

  submit(): void {
    if (this.loading()) return;

    this.submitted.set(true);
    this.errorMessage.set(null);
    this.created.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { username, password, role } = this.form.getRawValue();
    const request = { username: username.trim(), password, role };

    this.loading.set(true);
    // Disabling the group is what greys out the fields while the request is in flight.
    this.form.disable({ emitEvent: false });

    this.users.create(request).subscribe({
      next: user => {
        this.loading.set(false);
        this.form.enable({ emitEvent: false });
        this.clear();
        this.created.set(user);
      },
      error: (error: HttpErrorResponse) => {
        this.loading.set(false);
        this.form.enable({ emitEvent: false });
        this.errorMessage.set(this.messageFor(error, request.username));

        // Set after enable(), which re-runs the validators. The next keystroke clears them again.
        if (error.status === 409) {
          this.form.controls.username.setErrors({ taken: true });
        }
        if (error.status === 400) {
          const fields: Record<string, string> = error.error?.validationErrors ?? {};
          for (const [name, message] of Object.entries(fields)) {
            this.form.get(name)?.setErrors({ server: message });
          }
        }
      },
    });
  }

  private messageFor(error: HttpErrorResponse, username: string): string {
    switch (error.status) {
      case 409:
        return `No se pudo crear el usuario: «${username}» ya está registrado.`;
      case 400:
        return 'El servidor rechazó algunos campos. Revisa los valores marcados.';
      case 401:
        return 'Tu sesión ha caducado. Vuelve a iniciar sesión.';
      case 403:
        return 'Tu cuenta no tiene permiso para registrar usuarios.';
      case 0:
        return 'No se pudo conectar con el servidor. Revisa tu conexión.';
      default:
        return 'Ha ocurrido un error inesperado. Inténtalo de nuevo.';
    }
  }
}
