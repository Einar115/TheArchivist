import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RegisterUser } from './register-user';
import { Role } from '../../../core/models/auth.model';

describe('RegisterUser', () => {
  const registerUrl = '/api/v1/auth/register';

  let component: RegisterUser;
  let fixture: ComponentFixture<RegisterUser>;
  let httpMock: HttpTestingController;

  const fill = (overrides: Partial<{ username: string; password: string; confirmPassword: string; role: Role }> = {}) =>
    component.form.setValue({
      username: 'maria.lopez',
      password: 'secreto-largo',
      confirmPassword: 'secreto-largo',
      role: 'UPLOADER',
      ...overrides,
    });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RegisterUser],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(RegisterUser);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    await fixture.whenStable();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('does not call the backend while the passwords differ', () => {
    fill({ confirmPassword: 'otra-cosa' });

    component.submit();

    httpMock.expectNone(registerUrl);
    expect(component.matchState()).toBe('failed');
    expect(component.isInvalid('confirmPassword')).toBe(true);
  });

  it('tracks the minimum password length while typing', () => {
    expect(component.lengthState()).toBe('pending');

    fill({ password: 'corta', confirmPassword: 'corta' });
    expect(component.lengthState()).toBe('failed');

    fill({ password: '12345678', confirmPassword: '12345678' });
    expect(component.lengthState()).toBe('met');
  });

  it('sends the trimmed username with the chosen role and resets the form on success', () => {
    fill({ username: '  maria.lopez  ', role: 'ADMIN_DOCUMENTS' });

    component.submit();

    const request = httpMock.expectOne(registerUrl);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ username: 'maria.lopez', password: 'secreto-largo', role: 'ADMIN_DOCUMENTS' });
    expect(component.loading()).toBe(true);

    request.flush({ id: 7, username: 'maria.lopez', role: 'ADMIN_DOCUMENTS' });

    expect(component.created()?.username).toBe('maria.lopez');
    expect(component.loading()).toBe(false);
    expect(component.form.getRawValue()).toEqual({ username: '', password: '', confirmPassword: '', role: 'UPLOADER' });
  });

  it('flags the username when the backend reports it is already taken', () => {
    fill();

    component.submit();
    httpMock.expectOne(registerUrl).flush(
      { message: 'Username already exists: maria.lopez' },
      { status: 409, statusText: 'Conflict' }
    );

    expect(component.errorMessage()).toContain('«maria.lopez» ya está registrado');
    expect(component.form.controls.username.hasError('taken')).toBe(true);
    expect(component.form.enabled).toBe(true);
    expect(component.loading()).toBe(false);
  });

  it('previews the extra permissions that ADMIN_DOCUMENTS grants', () => {
    const granted = (label: string) => component.permissions().find(permission => permission.label === label)?.granted;

    expect(granted('Registrar usuarios')).toBe(false);

    fill({ role: 'ADMIN_DOCUMENTS' });

    expect(granted('Registrar usuarios')).toBe(true);
  });
});
