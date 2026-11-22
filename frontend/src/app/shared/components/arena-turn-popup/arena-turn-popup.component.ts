import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideSwords } from '@ng-icons/lucide';
import { Faction } from '../../../core/models/arcane.models';

export interface TurnAnnouncement {
  readonly key: string;
  readonly turnNumber: number;
  readonly phrase: string;
  readonly opponentName: string | null;
  readonly faction: Faction | null;
}

/**
 * Pop-up centré non-bloquant annonçant en grand le début du tour du joueur face à un adversaire.
 * N'interrompt ni les clics sur le plateau ni le focus clavier ; l'annonce vocale est relayée par AnnouncerService.
 */
@Component({
  selector: 'app-arena-turn-popup',
  standalone: true,
  imports: [NgIcon],
  viewProviders: [provideIcons({ lucideSwords })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [
    `
      .arcane-turn-layer {
        position: absolute;
        inset: 0;
        z-index: 44;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 1rem;
        pointer-events: none;
        overflow: hidden;
      }

      .arcane-turn-scrim {
        --turn-accent: #34d399;
        --turn-glow: rgb(52 211 153 / 0.34);
        display: flex;
        justify-content: center;
        max-inline-size: min(94%, 36rem);
        inline-size: 100%;
        pointer-events: none;
      }

      .arcane-turn-scrim[data-faction='FIRE'] {
        --turn-accent: #fb923c;
        --turn-glow: rgb(249 115 22 / 0.38);
      }

      .arcane-turn-scrim[data-faction='ICE'] {
        --turn-accent: #38bdf8;
        --turn-glow: rgb(56 189 248 / 0.38);
      }

      .arcane-turn-scrim[data-faction='SHADOW'] {
        --turn-accent: #c084fc;
        --turn-glow: rgb(168 85 247 / 0.38);
      }

      .arcane-turn-scrim[data-faction='LIGHT'] {
        --turn-accent: #facc15;
        --turn-glow: rgb(250 204 21 / 0.38);
      }

      .arcane-turn-card {
        position: relative;
        inline-size: 100%;
        border-radius: 1.15rem;
        border: 2px solid var(--turn-accent);
        background:
          radial-gradient(ellipse 90% 70% at 50% 0%, var(--turn-glow), transparent 68%),
          linear-gradient(160deg, rgb(15 22 42 / 0.96), rgb(6 8 17 / 0.98));
        padding: clamp(0.85rem, 2.8vw, 1.35rem) clamp(1rem, 4vw, 2.25rem);
        text-align: center;
        box-shadow:
          0 24px 60px -12px rgb(0 0 0 / 0.96),
          0 0 46px -8px var(--turn-glow),
          inset 0 1px 0 rgb(255 248 230 / 0.24);
      }

      .arcane-turn-eyebrow {
        display: inline-flex;
        align-items: center;
        gap: 0.4rem;
        border-radius: 9999px;
        border: 1px solid rgb(217 164 65 / 0.45);
        background: rgb(8 13 24 / 0.85);
        padding: 0.2rem 0.7rem;
        font-size: 0.72rem;
        font-weight: 700;
        letter-spacing: 0.08em;
        color: var(--arcane-gold-bright, #f0c96e);
        text-transform: uppercase;
      }

      .arcane-turn-phrase {
        margin: 0.45rem 0 0;
        font-family: var(--arcane-font-display);
        font-size: clamp(1.45rem, 4.4vw, 2.65rem);
        font-weight: 700;
        line-height: 1.12;
        letter-spacing: 0.06em;
        color: var(--arcane-ink-primary, #f4efe6);
        text-transform: uppercase;
        text-wrap: balance;
        text-shadow: 0 2px 20px var(--turn-glow);
      }

      .arcane-turn-accent-bar {
        margin: 0.55rem auto 0;
        block-size: 2px;
        inline-size: min(65%, 14rem);
        border-radius: 9999px;
        background: linear-gradient(90deg, transparent, var(--turn-accent), transparent);
      }

      @keyframes arcaneTurnSplash {
        0% {
          opacity: 0;
          transform: scale(0.82) translateY(12px);
        }
        14% {
          opacity: 1;
          transform: scale(1.02) translateY(0);
        }
        20%,
        80% {
          opacity: 1;
          transform: scale(1) translateY(0);
        }
        100% {
          opacity: 0;
          transform: scale(1.04) translateY(-8px);
        }
      }

      @keyframes arcaneTurnFadeReduced {
        0% {
          opacity: 0;
        }
        14%,
        84% {
          opacity: 1;
        }
        100% {
          opacity: 0;
        }
      }

      @media (prefers-reduced-motion: no-preference) {
        .arcane-turn-scrim {
          animation: arcaneTurnSplash 2.35s var(--arcane-spring-easing, cubic-bezier(0.22, 1, 0.36, 1)) forwards;
        }
      }

      @media (prefers-reduced-motion: reduce) {
        .arcane-turn-scrim {
          animation: arcaneTurnFadeReduced 2.1s ease-out forwards;
        }
      }

      @media (forced-colors: active) {
        .arcane-turn-card {
          border: 2px solid Highlight;
          background: Canvas;
          color: CanvasText;
        }
        .arcane-turn-eyebrow,
        .arcane-turn-phrase {
          color: CanvasText;
          text-shadow: none;
        }
      }
    `,
  ],
  template: `
    @if (announcement(); as banner) {
      <div class="arcane-turn-layer" aria-hidden="true">
        @for (item of [banner]; track item.key) {
          <div class="arcane-turn-scrim" [attr.data-faction]="item.faction">
            <div class="arcane-turn-card">
              <div class="arcane-turn-eyebrow">
                <ng-icon name="lucideSwords" size="13px" aria-hidden="true" />
                <span>
                  Tour {{ item.turnNumber }}
                  @if (item.opponentName) {
                    · Contre {{ item.opponentName }}
                  }
                </span>
              </div>
              <p class="arcane-turn-phrase">{{ item.phrase }}</p>
              <div class="arcane-turn-accent-bar"></div>
            </div>
          </div>
        }
      </div>
    }
  `,
})
export class ArenaTurnPopupComponent {
  readonly announcement = input<TurnAnnouncement | null>(null);
}
