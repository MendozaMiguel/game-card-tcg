import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  HealthResponse,
  MatchSource,
  PlayerMatchHistoryResponse,
  UpdateCosmeticsRequest,
  UserProfileDto,
} from '../models/arcane.models';

export interface LoginPayload {
  identifier: string;
  password: string;
}

export interface RegisterPayload {
  username: string;
  email: string;
  password: string;
}

export function extractApiErrorMessage(err: unknown, fallback = 'Une erreur serveur est survenue.'): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error;
    if (body && typeof body === 'object') {
      const record = body as Record<string, unknown>;
      const details = record['details'];
      if (Array.isArray(details) && details.length > 0 && typeof details[0] === 'string') {
        return details.join(' — ');
      }
      const validation = record['validation'] as Record<string, unknown> | undefined;
      if (validation && Array.isArray(validation['violations']) && validation['violations'].length > 0) {
        return (validation['violations'] as string[]).join(' ');
      }
      if (typeof record['message'] === 'string' && record['message'].trim().length > 0) {
        return record['message'];
      }
    }
    if (err.status === 0) {
      return 'Impossible de joindre le serveur Java Back-End.';
    }
    if (err.status === 401) {
      return 'Session expirée ou identifiants invalides.';
    }
  }
  return fallback;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private initPromise: Promise<UserProfileDto | null> | null = null;

  readonly user = signal<UserProfileDto | null>(null);
  readonly initialized = signal(false);
  readonly health = signal<HealthResponse | null>(null);
  readonly healthState = signal<'LOADING' | 'ONLINE' | 'OFFLINE'>('LOADING');

  readonly isAuthenticated = computed(() => this.user() !== null);

  initSession(): Promise<UserProfileDto | null> {
    if (this.initialized()) {
      return Promise.resolve(this.user());
    }
    if (this.initPromise) {
      return this.initPromise;
    }

    this.checkHealth();
    this.initPromise = firstValueFrom(this.http.get<UserProfileDto>('/api/auth/me'))
      .then((profile) => {
        this.user.set(profile);
        this.initialized.set(true);
        return profile;
      })
      .catch(() => {
        this.user.set(null);
        this.initialized.set(true);
        return null;
      })
      .finally(() => {
        this.initPromise = null;
      });

    return this.initPromise;
  }

  checkHealth(): void {
    this.http.get<HealthResponse>('/api/health').subscribe({
      next: (res) => {
        this.health.set(res);
        this.healthState.set('ONLINE');
      },
      error: () => {
        this.healthState.set('OFFLINE');
      },
    });
  }

  async login(payload: LoginPayload): Promise<UserProfileDto> {
    const profile = await firstValueFrom(this.http.post<UserProfileDto>('/api/auth/login', payload));
    this.user.set(profile);
    this.initialized.set(true);
    return profile;
  }

  async register(payload: RegisterPayload): Promise<UserProfileDto> {
    const profile = await firstValueFrom(this.http.post<UserProfileDto>('/api/auth/register', payload));
    this.user.set(profile);
    this.initialized.set(true);
    return profile;
  }

  async refreshProfile(): Promise<UserProfileDto | null> {
    try {
      const profile = await firstValueFrom(this.http.get<UserProfileDto>('/api/profile'));
      this.user.set(profile);
      return profile;
    } catch {
      return null;
    }
  }

  async equipCosmetics(request: UpdateCosmeticsRequest): Promise<UserProfileDto> {
    const updated = await firstValueFrom(
      this.http.put<UserProfileDto>('/api/profile/cosmetics', request),
    );
    this.user.set(updated);
    return updated;
  }

  /** Marks the onboarding interactive tutorial as completed for the signed-in user. */
  async completeTutorial(): Promise<UserProfileDto> {
    const updated = await firstValueFrom(
      this.http.post<UserProfileDto>('/api/profile/tutorial/complete', {}),
    );
    this.user.set(updated);
    return updated;
  }

  getMatchHistory(limit = 20, source?: MatchSource | null): Promise<PlayerMatchHistoryResponse> {
    let params = new HttpParams().set('limit', String(limit));
    if (source) {
      params = params.set('source', source);
    }
    return firstValueFrom(
      this.http.get<PlayerMatchHistoryResponse>('/api/profile/matches', { params }),
    );
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.http.post<void>('/api/auth/logout', {}));
    } finally {
      this.user.set(null);
      this.initialized.set(true);
    }
  }
}
