import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideCrown,
  lucideGem,
  lucideLogOut,
  lucideMenu,
  lucideSparkles,
  lucideX,
} from '@ng-icons/lucide';
import { filter } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { StompService } from '../../../core/services/stomp.service';
import { LEAGUE_LABELS } from '../../utils/league-labels';
import { ArcaneAvatarComponent } from '../arcane-avatar/arcane-avatar.component';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, NgIcon, ArcaneAvatarComponent],
  viewProviders: [
    provideIcons({
      lucideSparkles,
      lucideGem,
      lucideCrown,
      lucideLogOut,
      lucideMenu,
      lucideX,
    }),
  ],
  templateUrl: './app-header.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '(document:keydown.escape)': 'closeMobileMenu()',
  },
})
export class AppHeaderComponent {
  protected readonly auth = inject(AuthService);
  protected readonly stomp = inject(StompService);
  private readonly router = inject(Router);

  protected readonly leagueLabels = LEAGUE_LABELS;
  protected readonly mobileMenuOpen = signal(false);

  constructor() {
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.mobileMenuOpen.set(false));
  }

  protected toggleMobileMenu(): void {
    this.mobileMenuOpen.update((open) => !open);
  }

  protected closeMobileMenu(): void {
    this.mobileMenuOpen.set(false);
  }

  protected async onLogout(): Promise<void> {
    this.closeMobileMenu();
    this.stomp.disconnect();
    await this.auth.logout();
    await this.router.navigateByUrl('/auth');
  }
}
