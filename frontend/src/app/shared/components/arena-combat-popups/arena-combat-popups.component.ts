import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Faction } from '../../../core/models/arcane.models';

export type CombatPopupTone =
  | 'CRIT'
  | 'DAMAGE'
  | 'DIRECT'
  | 'DESTROY'
  | 'BLOCK'
  | 'HEAL'
  | 'BUFF'
  | 'DEBUFF';

export interface FloatingCombatPopup {
  id: string;
  x: number;
  y: number;
  headline: string;
  sublabel?: string | null;
  tone: CombatPopupTone;
  faction: Faction;
  tiltDeg?: number;
}

/**
 * Calque d'annotations de combat façon Cross Blitz (chiffres arcade rebondissants,
 * appels CRITIQUE / K.O. / BRISÉ / PARÉ / FRAPPE DIRECTE) non rogné par le cadre des cartes.
 */
@Component({
  selector: 'app-arena-combat-popups',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [
    `
      .arcane-popups-layer {
        position: absolute;
        inset: 0;
        z-index: 46;
        overflow: visible;
        pointer-events: none;
      }

      .arcane-popup-item {
        position: absolute;
        left: 0;
        top: 0;
        display: inline-flex;
        flex-direction: column;
        align-items: center;
        gap: 0.08rem;
        transform: translate3d(var(--pop-x, 0px), var(--pop-y, 0px), 0) translate(-50%, -50%);
        pointer-events: none;
        user-select: none;
        will-change: transform, opacity;
        --pop-color: #fde047;
        --pop-edge: #92400e;
        --pop-tag-bg: rgb(12 17 30 / 0.94);
      }

      .arcane-popup-item[data-tone='DAMAGE'] {
        --pop-color: #fda4af;
        --pop-edge: #9f1239;
      }

      .arcane-popup-item[data-tone='CRIT'],
      .arcane-popup-item[data-tone='DIRECT'] {
        --pop-color: #fef08a;
        --pop-edge: #be123c;
      }

      .arcane-popup-item[data-tone='DESTROY'] {
        --pop-color: #fb7185;
        --pop-edge: #881337;
      }

      .arcane-popup-item[data-tone='BLOCK'] {
        --pop-color: #93c5fd;
        --pop-edge: #1e3a8a;
      }

      .arcane-popup-item[data-tone='HEAL'] {
        --pop-color: #6ee7b7;
        --pop-edge: #065f46;
      }

      .arcane-popup-item[data-tone='BUFF'] {
        --pop-color: #fcd34d;
        --pop-edge: #78350f;
      }

      .arcane-popup-item[data-tone='DEBUFF'] {
        --pop-color: #d8b4fe;
        --pop-edge: #581c87;
      }

      .arcane-popup-core {
        display: inline-flex;
        flex-direction: column;
        align-items: center;
        transform: rotate(var(--pop-tilt, 0deg));
      }

      .arcane-popup-headline {
        font-family: var(--arcane-font-display, Cinzel, Georgia, serif);
        font-size: clamp(1.1rem, 2.2vw, 1.55rem);
        font-weight: 900;
        line-height: 1;
        letter-spacing: 0.03em;
        font-variant-numeric: tabular-nums;
        white-space: nowrap;
        color: var(--pop-color);
        text-shadow:
          0 2px 0 #050811,
          -1.5px -1.5px 0 #050811,
          1.5px -1.5px 0 #050811,
          -1.5px 1.5px 0 #050811,
          1.5px 1.5px 0 #050811,
          0 0 16px var(--pop-edge);
      }

      .arcane-popup-item[data-tone='CRIT'] .arcane-popup-headline,
      .arcane-popup-item[data-tone='DIRECT'] .arcane-popup-headline,
      .arcane-popup-item[data-tone='DESTROY'] .arcane-popup-headline {
        font-size: clamp(1.3rem, 2.7vw, 1.85rem);
      }

      .arcane-popup-sub {
        border-radius: 0.35rem;
        border: 1px solid color-mix(in srgb, var(--pop-color) 75%, transparent);
        background: var(--pop-tag-bg);
        padding: 0.06rem 0.38rem;
        font-size: 0.58rem;
        font-weight: 800;
        letter-spacing: 0.1em;
        line-height: 1.15;
        text-transform: uppercase;
        white-space: nowrap;
        color: #f8fafc;
        box-shadow: 0 4px 12px rgb(0 0 0 / 0.65);
      }

      @media (prefers-reduced-motion: no-preference) {
        .arcane-popup-core {
          animation: arcaneCrossBlitzPopMotion 0.96s forwards;
          animation-timing-function: cubic-bezier(0.34, 1.56, 0.64, 1);
          animation-timing-function: linear(
            0,
            0.34 11%,
            0.94 24%,
            1.14 34%,
            1.02 48%,
            0.99 66%,
            1
          );
        }

        .arcane-popup-item {
          animation: arcaneCrossBlitzPopOpacity 0.96s ease-out forwards;
        }

        @keyframes arcaneCrossBlitzPopMotion {
          0% {
            transform: translate3d(0, 14px, 0) scale(0.45) rotate(calc(var(--pop-tilt, 0deg) * 1.4));
          }
          28% {
            transform: translate3d(0, -18px, 0) scale(1.18) rotate(var(--pop-tilt, 0deg));
          }
          64% {
            transform: translate3d(0, -24px, 0) scale(1) rotate(var(--pop-tilt, 0deg));
          }
          100% {
            transform: translate3d(0, -34px, 0) scale(0.92) rotate(var(--pop-tilt, 0deg));
          }
        }

        @keyframes arcaneCrossBlitzPopOpacity {
          0% {
            opacity: 0;
          }
          14%,
          74% {
            opacity: 1;
          }
          100% {
            opacity: 0;
          }
        }
      }

      @media (prefers-reduced-motion: reduce) {
        .arcane-popup-item {
          animation: arcaneCrossBlitzReducedFade 1.1s ease-out forwards;
        }

        @keyframes arcaneCrossBlitzReducedFade {
          0% {
            opacity: 0;
          }
          12%,
          82% {
            opacity: 1;
          }
          100% {
            opacity: 0;
          }
        }
      }

      @media (forced-colors: active) {
        .arcane-popup-headline {
          color: CanvasText;
          text-shadow: none;
          background: Canvas;
          padding-inline: 0.25rem;
          border: 1px solid CanvasText;
        }
      }
    `,
  ],
  template: `
    <div class="arcane-popups-layer" aria-hidden="true">
      @for (pop of popups(); track pop.id) {
        <div
          class="arcane-popup-item"
          [attr.data-tone]="pop.tone"
          [attr.data-faction]="pop.faction"
          [style.--pop-x.px]="pop.x"
          [style.--pop-y.px]="pop.y"
          [style.--pop-tilt.deg]="pop.tiltDeg ?? 0">
          <div class="arcane-popup-core">
            <span class="arcane-popup-headline">{{ pop.headline }}</span>
            @if (pop.sublabel) {
              <span class="arcane-popup-sub">{{ pop.sublabel }}</span>
            }
          </div>
        </div>
      }
    </div>
  `,
})
export class ArenaCombatPopupsComponent {
  readonly popups = input<readonly FloatingCombatPopup[]>([]);
}
