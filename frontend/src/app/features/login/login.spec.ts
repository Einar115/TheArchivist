import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Login } from './login';

describe('Login', () => {
  const loginUrl = '/api/v1/auth/login';

  let component: Login;
  let fixture: ComponentFixture<Login>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Login],
      // A stub 'chat' route is needed because a successful login navigates there.
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'chat', children: [] }]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    await fixture.whenStable();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('does not call the backend while a field is empty', () => {
    component.form.setValue({ username: 'einar', password: '', rememberMe: false });

    component.submit();

    httpMock.expectNone(loginUrl);
    expect(component.isInvalid('password')).toBe(true);
  });

  it('sends the credentials and leaves the loading state on success', () => {
    component.form.setValue({ username: 'einar', password: 'secret', rememberMe: false });

    component.submit();

    const request = httpMock.expectOne(loginUrl);
    expect(request.request.body).toEqual({ username: 'einar', password: 'secret' });
    expect(component.loading()).toBe(true);

    request.flush({ username: 'einar', roles: ['ROLE_ADMIN_DOCUMENTS'] });

    expect(component.errorMessage()).toBeNull();
  });

  it('reports rejected credentials and clears the password', () => {
    component.form.setValue({ username: 'einar', password: 'wrong', rememberMe: false });

    component.submit();
    httpMock.expectOne(loginUrl).flush(
      { message: 'Invalid username or password' },
      { status: 401, statusText: 'Unauthorized' }
    );

    expect(component.errorMessage()).toBe('Usuario o contraseña incorrectos.');
    expect(component.loading()).toBe(false);
    expect(component.form.enabled).toBe(true);
    expect(component.form.controls.password.value).toBe('');
  });
});
