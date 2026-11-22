import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { ArcaneAudioService } from '../../services/arcane-audio.service';

@Component({
  selector: 'app-arena-audio-control',
  standalone: true,
  template: `
    <div class="aac-root" role="group" aria-label="Contrôles audio">
      <button
        type="button"
        class="aac-btn"
        [class.aac-btn-off]="!audio.musicEnabled()"
        [attr.aria-pressed]="audio.musicEnabled()"
        [attr.aria-label]="audio.musicEnabled() ? 'Couper la musique de fond' : 'Activer la musique de fond'"
        [title]="audio.musicEnabled() ? 'Musique active (cliquer pour couper)' : 'Activer le thème musical'"
        (click)="toggleMusic()"
      >
        <svg class="aac-svg" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M9 18V5l12-2v13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          <circle cx="6" cy="18" r="3" fill="none" stroke="currentColor" stroke-width="2" />
          <circle cx="18" cy="16" r="3" fill="none" stroke="currentColor" stroke-width="2" />
          @if (!audio.musicEnabled()) {
            <path d="M3 3l18 18" stroke="#fb7185" stroke-width="2.2" stroke-linecap="round" />
          }
        </svg>
        @if (!compact()) {
          <span class="aac-label">Musique</span>
        }
      </button>

      <button
        type="button"
        class="aac-btn"
        [class.aac-btn-off]="!audio.sfxEnabled()"
        [attr.aria-pressed]="audio.sfxEnabled()"
        [attr.aria-label]="audio.sfxEnabled() ? 'Couper les bruitages de combat' : 'Activer les bruitages de combat'"
        [title]="audio.sfxEnabled() ? 'Effets sonores actifs' : 'Activer les effets sonores'"
        (click)="toggleSfx()"
      >
        <svg class="aac-svg" viewBox="0 0 24 24" aria-hidden="true">
          <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" />
          @if (audio.sfxEnabled()) {
            <path d="M15.54 8.46a5 5 0 0 1 0 7.07" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
            <path d="M19.07 4.93a10 10 0 0 1 0 14.14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          } @else {
            <path d="M22 9l-6 6M16 9l6 6" fill="none" stroke="#fb7185" stroke-width="2" stroke-linecap="round" />
          }
        </svg>
        @if (!compact()) {
          <span class="aac-label">SFX</span>
        }
      </button>

      <button
        type="button"
        class="aac-btn aac-vol-btn"
        [attr.aria-expanded]="volumeOpen()"
        aria-label="Régler le volume audio"
        title="Régler le volume"
        (click)="toggleVolumePanel()"
      >
        <span class="aac-vol-text">{{ volumePercent() }}%</span>
      </button>

      @if (volumeOpen()) {
        <div class="aac-popover" role="region" aria-label="Volume général">
          <label for="aac-volume-range" class="aac-pop-label">
            <span>Volume</span>
            <strong class="aac-pop-val">{{ volumePercent() }}%</strong>
          </label>
          <input
            id="aac-volume-range"
            type="range"
            min="0"
            max="100"
            step="5"
            class="aac-range"
            [value]="volumePercent()"
            aria-label="Niveau de volume audio en pourcentage"
            (input)="onVolumeInput($event)"
          />
          <button type="button" class="aac-close" (click)="volumeOpen.set(false)">
            Fermer
          </button>
        </div>
      }
    </div>
  `,
  styles: `
    .aac-root {
      position: relative;
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
    }

    .aac-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.25rem;
      min-block-size: 1.85rem;
      min-inline-size: 1.85rem;
      padding: 0 0.45rem;
      border-radius: 0.5rem;
      border: 1px solid rgba(217, 164, 65, 0.45);
      background: rgba(18, 24, 41, 0.92);
      color: #f0c96e;
      font-size: 0.68rem;
      font-weight: 700;
      cursor: pointer;
    }

    .aac-btn:hover {
      border-color: #f0c96e;
      background: rgba(27, 35, 58, 0.96);
    }

    .aac-btn-off {
      border-color: rgba(100, 116, 139, 0.55);
      color: #94a3b8;
    }

    .aac-svg {
      inline-size: 0.9rem;
      block-size: 0.9rem;
      flex-shrink: 0;
    }

    .aac-label {
      display: none;
    }

    @media (min-width: 640px) {
      .aac-label {
        display: inline;
      }
    }

    .aac-vol-text {
      font-variant-numeric: tabular-nums;
    }

    .aac-popover {
      position: absolute;
      inset-block-start: calc(100% + 0.4rem);
      inset-inline-end: 0;
      z-index: 60;
      inline-size: 12.5rem;
      padding: 0.65rem 0.75rem;
      border-radius: 0.75rem;
      border: 1px solid rgba(217, 164, 65, 0.6);
      background: rgba(12, 16, 28, 0.98);
      box-shadow: 0 12px 28px rgba(0, 0, 0, 0.65);
      color: #f4efe6;
      display: flex;
      flex-direction: column;
      gap: 0.45rem;
    }

    .aac-pop-label {
      display: flex;
      align-items: center;
      justify-content: space-between;
      font-size: 0.72rem;
      font-weight: 700;
    }

    .aac-pop-val {
      color: #f0c96e;
      font-variant-numeric: tabular-nums;
    }

    .aac-range {
      inline-size: 100%;
      accent-color: #d9a441;
      cursor: pointer;
    }

    .aac-close {
      align-self: flex-end;
      padding: 0.2rem 0.55rem;
      border-radius: 0.4rem;
      border: 1px solid rgba(100, 116, 139, 0.5);
      background: rgba(27, 35, 58, 0.9);
      color: #cbd5e1;
      font-size: 0.68rem;
      font-weight: 700;
      cursor: pointer;
    }

    .aac-btn:focus-visible,
    .aac-range:focus-visible,
    .aac-close:focus-visible {
      outline: 2px solid #f0c96e;
      outline-offset: 2px;
    }

    @media (pointer: coarse) {
      .aac-btn {
        min-block-size: 2.35rem;
        min-inline-size: 2.35rem;
      }
    }

    @media (forced-colors: active) {
      .aac-btn,
      .aac-popover,
      .aac-close {
        border: 1px solid ButtonText;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '(document:keydown.escape)': 'volumeOpen.set(false)',
  },
})
export class ArenaAudioControlComponent {
  protected readonly audio = inject(ArcaneAudioService);

  readonly compact = input<boolean>(false);
  protected readonly volumeOpen = signal(false);

  protected volumePercent(): number {
    return Math.round(this.audio.masterVolume() * 100);
  }

  protected toggleMusic(): void {
    this.audio.unlockAndResume();
    this.audio.setMusicEnabled(!this.audio.musicEnabled());
  }

  protected toggleSfx(): void {
    this.audio.unlockAndResume();
    const next = !this.audio.sfxEnabled();
    this.audio.setSfxEnabled(next);
    if (next) {
      this.audio.playSfx('POSTURE_DEFENSE');
    }
  }

  protected toggleVolumePanel(): void {
    this.audio.unlockAndResume();
    this.volumeOpen.update((v) => !v);
  }

  protected onVolumeInput(event: Event): void {
    const val = Number((event.target as HTMLInputElement).value);
    if (Number.isFinite(val)) {
      this.audio.setMasterVolume(val / 100);
    }
  }
}
