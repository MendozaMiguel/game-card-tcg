import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideRouter, withViewTransitions } from '@angular/router';
import { provideNgIconsConfig, withContentSecurityPolicy } from '@ng-icons/core';
import { routes } from './app.routes';
import { credentialsInterceptor } from './core/interceptors/credentials.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideHttpClient(withFetch(), withInterceptors([credentialsInterceptor])),
    provideRouter(routes, withViewTransitions()),
    provideNgIconsConfig({ strokeWidth: 1.75 }, withContentSecurityPolicy()),
  ],
};
