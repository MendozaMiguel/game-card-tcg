import { ChangeDetectionStrategy, Component, OnInit, WritableSignal, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideAlertTriangle,
  lucideEye,
  lucideEyeOff,
  lucideShieldCheck,
  lucideSparkles,
  lucideSwords,
  lucideUsers,
} from '@ng-icons/lucide';
import { AuthService, extractApiErrorMessage } from '../../core/services/auth.service';
import { LobbyApiService } from '../../core/services/lobby-api.service';
import { StompService } from '../../core/services/stomp.service';
import { FormAriaSyncDirective } from '../../shared/directives/form-aria-sync.directive';

@Component({
  selector: 'app-auth-page',
  standalone: true,
  imports: [FormAriaSyncDirective, NgIcon],
  viewProviders: [
    provideIcons({
      lucideAlertTriangle,
      lucideEye,
      lucideEyeOff,
      lucideShieldCheck,
      lucideSparkles,
      lucideSwords,
      lucideUsers,
    }),
  ],
  templateUrl: './auth-page.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuthPageComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly lobbyApi = inject(LobbyApiService);
  private readonly stomp = inject(StompService);
  private readonly router = inject(Router);

  protected readonly activeTab = signal<'LOGIN' | 'REGISTER'>('LOGIN');
  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly loginIdentifier = signal('');
  protected readonly loginPassword = signal('');
  protected readonly showLoginPwd = signal(false);

  protected readonly regUsername = signal('');
  protected readonly regEmail = signal('');
  protected readonly regPassword = signal('');
  protected readonly showRegPwd = signal(false);

  ngOnInit(): void {
    this.auth.checkHealth();
  }

  protected switchTab(tab: 'LOGIN' | 'REGISTER'): void {
    this.activeTab.set(tab);
    this.errorMessage.set(null);
  }

  protected onInputValue(target: WritableSignal<string>, event: Event): void {
    target.set((event.target as HTMLInputElement).value);
  }

  protected async onLoginSubmit(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    const form = event.target as HTMLFormElement;
    if (!form.reportValidity()) {
      return;
    }
    this.errorMessage.set(null);
    this.submitting.set(true);
    try {
      await this.auth.login({
        identifier: this.loginIdentifier().trim(),
        password: this.loginPassword(),
      });
      this.stomp.connect();
      await this.navigateAfterAuth();
    } catch (err) {
      this.errorMessage.set(extractApiErrorMessage(err, 'Connexion impossible.'));
    } finally {
      this.submitting.set(false);
    }
  }

  protected async onRegisterSubmit(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    const form = event.target as HTMLFormElement;
    if (!form.reportValidity()) {
      return;
    }
    this.errorMessage.set(null);
    this.submitting.set(true);
    try {
      await this.auth.register({
        username: this.regUsername().trim(),
        email: this.regEmail().trim(),
        password: this.regPassword(),
      });
      this.stomp.connect();
      await this.navigateAfterAuth();
    } catch (err) {
      this.errorMessage.set(extractApiErrorMessage(err, "Impossible de créer le compte."));
    } finally {
      this.submitting.set(false);
    }
  }

  private async navigateAfterAuth(): Promise<void> {
    try {
      const active = await this.lobbyApi.getActiveMatch();
      if (active.activeMatchId) {
        await this.router.navigate(['/arena', active.activeMatchId]);
        return;
      }
    } catch {
      // Fallback vers le lobby si pas de match actif
    }
    await this.router.navigateByUrl('/lobby');
  }
}
