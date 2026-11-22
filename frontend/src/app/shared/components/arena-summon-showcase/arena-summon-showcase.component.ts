import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MythicShowcaseBanner } from '../../utils/arena-vfx';
import { resolveCardArtworkUrl } from '../../utils/card-artwork';

/**
 * Banderole cinématique non-bloquante (`pointer-events: none`) lors de l'arrivée sur la table
 * d'une carte Épique, Coût >= 5 ou Sort Ultime : met en valeur l'illustration et les stats
 * sans interrompre la saisie ni voler le focus clavier.
 */
@Component({
  selector: 'app-arena-summon-showcase',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [
    `
      .arcane-showcase-stage {
        position: absolute;
        inset-inline: 0;
        inset-block-start: 16%;
        z-index: 44;
        display: flex;
        justify-content: center;
        padding-inline: 0.75rem;
        pointer-events: none;
      }

      .arcane-showcase-card {
        position: relative;
        display: flex;
        align-items: center;
        gap: 0.85rem;
        max-inline-size: min(92vw, 27.5rem);
        padding: 0.65rem 1rem 0.65rem 0.75rem;
        border-radius: 1rem;
        border: 1.5px solid rgb(240 201 110 / 0.8);
        background:
          radial-gradient(circle at 18% 50%, rgb(240 201 110 / 0.18), transparent 62%),
          linear-gradient(135deg, rgb(12 16 29 / 0.96), rgb(7 10 19 / 0.97));
        box-shadow:
          0 16px 40px -12px rgb(0 0 0 / 0.86),
          0 0 30px -6px var(--showcase-glow, rgb(240 201 110 / 0.48));
        overflow: hidden;
      }

      .arcane-showcase-card[data-faction='FIRE'] {
        --showcase-accent: #fb923c;
        --showcase-glow: rgb(249 115 22 / 0.56);
      }
      .arcane-showcase-card[data-faction='ICE'] {
        --showcase-accent: #38bdf8;
        --showcase-glow: rgb(56 189 248 / 0.56);
      }
      .arcane-showcase-card[data-faction='SHADOW'] {
        --showcase-accent: #c084fc;
        --showcase-glow: rgb(168 85 247 / 0.56);
      }
      .arcane-showcase-card[data-faction='LIGHT'] {
        --showcase-accent: #fde047;
        --showcase-glow: rgb(250 204 21 / 0.56);
      }

      .arcane-showcase-thumb-wrap {
        position: relative;
        flex: 0 0 auto;
        inline-size: 5.5rem;
        block-size: 3.1rem;
        border-radius: 0.55rem;
        border: 1px solid var(--showcase-accent, #f0c96e);
        overflow: hidden;
        background: rgb(9 13 24);
        box-shadow: 0 0 14px -2px var(--showcase-glow, rgb(240 201 110 / 0.5));
      }

      .arcane-showcase-thumb {
        display: block;
        inline-size: 100%;
        block-size: 100%;
        object-fit: cover;
      }

      .arcane-showcase-sigil {
        display: flex;
        align-items: center;
        justify-content: center;
        inline-size: 100%;
        block-size: 100%;
        color: var(--showcase-accent, #f0c96e);
      }

      .arcane-showcase-meta {
        min-inline-size: 0;
        display: flex;
        flex-direction: column;
        gap: 0.18rem;
      }

      .arcane-showcase-kicker {
        font-size: 0.625rem;
        font-weight: 800;
        letter-spacing: 0.14em;
        text-transform: uppercase;
        color: var(--showcase-accent, #f0c96e);
      }

      .arcane-showcase-name {
        font-family: var(--arcane-font-display, Cinzel, Georgia, serif);
        font-size: clamp(0.95rem, 2.2vw, 1.28rem);
        font-weight: 800;
        line-height: 1.15;
        color: #f8fafc;
        margin: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .arcane-showcase-sub {
        display: flex;
        align-items: center;
        gap: 0.45rem;
        font-size: 0.6875rem;
        font-weight: 600;
        color: #cbd5e1;
      }

      .arcane-showcase-stat {
        display: inline-flex;
        align-items: center;
        gap: 0.2rem;
        border-radius: 0.35rem;
        border: 1px solid rgb(217 164 65 / 0.45);
        background: rgb(8 12 22 / 0.9);
        padding: 0.05rem 0.35rem;
        font-size: 0.6875rem;
        font-weight: 800;
        font-variant-numeric: tabular-nums;
        color: #f0c96e;
      }

      .arcane-showcase-sheen {
        position: absolute;
        inset-block: 0;
        inset-inline-start: -45%;
        inline-size: 40%;
        background: linear-gradient(90deg, transparent, rgb(255 255 255 / 0.2), transparent);
        pointer-events: none;
      }

      @media (prefers-reduced-motion: no-preference) {
        .arcane-showcase-card {
          animation: arcaneShowcasePop 1.45s cubic-bezier(0.22, 1, 0.36, 1) forwards;
        }
        .arcane-showcase-sheen {
          animation: arcaneShowcaseSheen 0.85s ease-out 0.12s forwards;
        }
        @keyframes arcaneShowcasePop {
          0% {
            opacity: 0;
            transform: translate3d(0, 18px, 0) scale(0.84);
          }
          16% {
            opacity: 1;
            transform: translate3d(0, -2px, 0) scale(1.03);
          }
          26%,
          78% {
            opacity: 1;
            transform: translate3d(0, 0, 0) scale(1);
          }
          100% {
            opacity: 0;
            transform: translate3d(0, -10px, 0) scale(0.96);
          }
        }
        @keyframes arcaneShowcaseSheen {
          0% {
            transform: translate3d(0, 0, 0) skewX(-20deg);
          }
          100% {
            transform: translate3d(380%, 0, 0) skewX(-20deg);
          }
        }
      }

      @media (prefers-reduced-motion: reduce) {
        .arcane-showcase-sheen {
          display: none;
        }
        .arcane-showcase-card {
          animation: arcaneShowcaseFade 1.2s ease-out forwards;
        }
        @keyframes arcaneShowcaseFade {
          0% {
            opacity: 0;
          }
          18%,
          80% {
            opacity: 1;
          }
          100% {
            opacity: 0;
          }
        }
      }

      @media (forced-colors: active) {
        .arcane-showcase-card {
          border: 2px solid Highlight;
          background: Canvas;
          color: CanvasText;
        }
      }
    `,
  ],
  template: `
    @if (showcase(); as s) {
      <div class="arcane-showcase-stage" aria-hidden="true">
        <div class="arcane-showcase-card" [attr.data-faction]="s.faction">
          <div class="arcane-showcase-sheen"></div>
          <div class="arcane-showcase-thumb-wrap">
            @if (artworkUrl(); as src) {
              <img [src]="src" alt="" width="640" height="360" decoding="async" class="arcane-showcase-thumb" />
            } @else {
              <div class="arcane-showcase-sigil">
                <svg viewBox="0 0 64 36" width="52" height="30" fill="none">
                  <polygon points="32,3 44,24 20,24" stroke="currentColor" stroke-width="1.8" />
                  <polygon points="32,33 44,12 20,12" stroke="currentColor" stroke-width="1.8" />
                  <circle cx="32" cy="18" r="14" stroke="#f0c96e" stroke-width="1.2" stroke-dasharray="3 2" />
                </svg>
              </div>
            }
          </div>
          <div class="arcane-showcase-meta">
            <span class="arcane-showcase-kicker">{{ s.title }}</span>
            <p class="arcane-showcase-name">{{ s.cardName }}</p>
            <div class="arcane-showcase-sub">
              <span>{{ s.subtitle }}</span>
              @if (s.atk !== null && s.def !== null) {
                <span class="arcane-showcase-stat">{{ s.atk }}/{{ s.def }}</span>
              }
            </div>
          </div>
        </div>
      </div>
    }
  `,
})
export class ArenaSummonShowcaseComponent {
  readonly showcase = input<MythicShowcaseBanner | null>(null);
  protected readonly artworkUrl = computed(() => resolveCardArtworkUrl(this.showcase()?.cardId ?? null));
}
