import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthPageComponent } from './auth-page.component';

describe('AuthPageComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuthPageComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
  });

  it('renders accessible login and registration tabs with proper autocomplete attributes', () => {
    const fixture = TestBed.createComponent(AuthPageComponent);
    const httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    httpMock.expectOne('/api/health').flush({
      status: 'UP',
      application: 'arcane-online-backend',
      virtualThreadsEnabled: true,
      javaVersion: '21',
    });

    const el = fixture.nativeElement as HTMLElement;
    const idInput = el.querySelector('#login-identifier') as HTMLInputElement;
    const pwdInput = el.querySelector('#login-password') as HTMLInputElement;
    expect(idInput.getAttribute('autocomplete')).toBe('username');
    expect(pwdInput.getAttribute('autocomplete')).toBe('current-password');

    // Switch to register tab
    (el.querySelector('#tab-register') as HTMLButtonElement).click();
    fixture.detectChanges();

    const newPwd = el.querySelector('#reg-password') as HTMLInputElement;
    expect(newPwd.getAttribute('autocomplete')).toBe('new-password');
    httpMock.verify();
  });
});
