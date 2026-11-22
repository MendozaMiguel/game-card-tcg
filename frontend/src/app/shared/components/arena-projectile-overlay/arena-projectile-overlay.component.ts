import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CombatOutcome, Faction } from '../../../core/models/arcane.models';
import { ImpactHitStrength } from '../../utils/arena-vfx';

export interface ActiveProjectile {
  uid: string;
  faction: Faction;
  startX: number;
  startY: number;
  dx: number;
  dy: number;
  angleDeg: number;
  distance: number;
  outcome: CombatOutcome;
  directAttack: boolean;
  hitStrength?: ImpactHitStrength;
}

@Component({
  selector: 'app-arena-projectile-overlay',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [
    `
      .arcane-projectile-layer {
        position: absolute;
        inset: 0;
        z-index: 42;
        overflow: hidden;
        pointer-events: none;
      }

      .arcane-proj-anchor {
        position: absolute;
        left: 0;
        top: 0;
        transform: translate3d(var(--proj-x, 0px), var(--proj-y, 0px), 0);
        pointer-events: none;
      }

      .arcane-proj-anchor[data-faction='FIRE'] {
        color: #fb923c;
      }
      .arcane-proj-anchor[data-faction='ICE'] {
        color: #38bdf8;
      }
      .arcane-proj-anchor[data-faction='SHADOW'] {
        color: #c084fc;
      }
      .arcane-proj-anchor[data-faction='LIGHT'] {
        color: #facc15;
      }

      .arcane-proj-bolt {
        position: absolute;
        left: -48px;
        top: -22px;
        width: 96px;
        height: 44px;
        transform-origin: center;
        opacity: 0;
        will-change: transform, opacity;
      }

      .arcane-proj-anchor[data-strength='CRITICAL'] .arcane-proj-bolt,
      .arcane-proj-anchor[data-direct='true'] .arcane-proj-bolt {
        left: -58px;
        top: -26px;
        width: 116px;
        height: 52px;
      }

      .arcane-proj-svg {
        width: 100%;
        height: 100%;
        overflow: visible;
        filter: drop-shadow(0 0 10px currentColor);
      }

      .arcane-proj-arrival {
        position: absolute;
        left: -44px;
        top: -44px;
        width: 88px;
        height: 88px;
        display: flex;
        align-items: center;
        justify-content: center;
        transform: translate3d(var(--proj-dx, 0px), var(--proj-dy, 0px), 0);
        opacity: 0;
        will-change: transform, opacity;
      }

      .arcane-proj-anchor[data-strength='CRITICAL'] .arcane-proj-arrival,
      .arcane-proj-anchor[data-direct='true'] .arcane-proj-arrival {
        left: -56px;
        top: -56px;
        width: 112px;
        height: 112px;
      }

      .arcane-proj-arrival-ring {
        position: absolute;
        inset: 6px;
        border-radius: 999px;
        border: 2.5px solid currentColor;
        background: radial-gradient(circle, currentColor 0%, transparent 65%);
        opacity: 0.72;
      }

      .arcane-proj-origin {
        position: absolute;
        left: -28px;
        top: -28px;
        width: 56px;
        height: 56px;
        border-radius: 999px;
        border: 2px solid currentColor;
        background: radial-gradient(circle, currentColor 0%, transparent 66%);
        opacity: 0;
        will-change: transform, opacity;
      }

      .arcane-proj-star-svg {
        position: relative;
        width: 68px;
        height: 68px;
        overflow: visible;
        filter: drop-shadow(0 0 12px currentColor);
      }

      .arcane-proj-deflect-arc {
        width: 56px;
        height: 56px;
      }

      @media (prefers-reduced-motion: no-preference) {
        .arcane-proj-origin {
          animation: arcaneProjectileOriginFlare 0.2s ease-out forwards;
        }
        .arcane-proj-bolt {
          animation: arcaneProjectileFlight 0.22s cubic-bezier(0.22, 1, 0.36, 1) forwards;
        }
        .arcane-proj-arrival {
          animation: arcaneProjectileArrival 0.36s cubic-bezier(0.16, 1, 0.3, 1) 0.16s both;
        }
        @keyframes arcaneProjectileOriginFlare {
          0% {
            opacity: 0.92;
            transform: scale(0.35);
          }
          100% {
            opacity: 0;
            transform: scale(1.38);
          }
        }
        @keyframes arcaneProjectileFlight {
          0% {
            opacity: 0;
            transform: translate3d(0, 0, 0) rotate(var(--proj-angle, 0deg)) scale(0.75, 0.85);
          }
          18% {
            opacity: 1;
            transform: translate3d(calc(var(--proj-dx, 0px) * 0.18), calc(var(--proj-dy, 0px) * 0.18), 0)
              rotate(var(--proj-angle, 0deg)) scale(1.26, 0.88);
          }
          84% {
            opacity: 1;
            transform: translate3d(calc(var(--proj-dx, 0px) * 0.95), calc(var(--proj-dy, 0px) * 0.95), 0)
              rotate(var(--proj-angle, 0deg)) scale(1.18, 0.94);
          }
          100% {
            opacity: 0;
            transform: translate3d(var(--proj-dx, 0px), var(--proj-dy, 0px), 0) rotate(var(--proj-angle, 0deg)) scale(0.65);
          }
        }
        @keyframes arcaneProjectileArrival {
          0% {
            opacity: 0;
            transform: translate3d(var(--proj-dx, 0px), var(--proj-dy, 0px), 0) scale(0.4);
          }
          28% {
            opacity: 1;
            transform: translate3d(var(--proj-dx, 0px), var(--proj-dy, 0px), 0) scale(1.22);
          }
          100% {
            opacity: 0;
            transform: translate3d(var(--proj-dx, 0px), var(--proj-dy, 0px), 0) scale(1.52);
          }
        }
      }

      @media (prefers-reduced-motion: reduce) {
        .arcane-proj-arrival {
          opacity: 0.85;
        }
      }
    `,
  ],
  template: `
    <div class="arcane-projectile-layer" aria-hidden="true">
      @for (p of projectiles(); track p.uid) {
        <div
          class="arcane-proj-anchor"
          [attr.data-faction]="p.faction"
          [attr.data-outcome]="p.outcome"
          [attr.data-direct]="p.directAttack ? 'true' : null"
          [attr.data-strength]="p.hitStrength ?? null"
          [style.--proj-x.px]="p.startX"
          [style.--proj-y.px]="p.startY"
          [style.--proj-dx.px]="p.dx"
          [style.--proj-dy.px]="p.dy"
          [style.--proj-angle.deg]="p.angleDeg">
          <!-- Charge / Launch Flare at Source -->
          <span class="arcane-proj-origin"></span>

          <!-- Elemental Projectile Bolt -->
          <div class="arcane-proj-bolt">
            @switch (p.faction) {
              @case ('FIRE') {
                <svg class="arcane-proj-svg" viewBox="0 0 88 40" fill="none">
                  <path d="M6 20 Q28 8 46 20 Q28 32 6 20 Z" fill="#f97316" fill-opacity="0.45" />
                  <path d="M16 20 Q42 5 76 20 Q42 35 16 20 Z" fill="#fb923c" fill-opacity="0.88" />
                  <path d="M40 20 Q58 11 82 20 Q58 29 40 20 Z" fill="#fef08a" />
                  <circle cx="72" cy="20" r="6.5" fill="#fff7ed" />
                  <circle cx="24" cy="11" r="2.2" fill="#fdba74" />
                  <circle cx="19" cy="29" r="2.5" fill="#fb923c" />
                  <circle cx="38" cy="9" r="1.8" fill="#fef08a" />
                </svg>
              }
              @case ('ICE') {
                <svg class="arcane-proj-svg" viewBox="0 0 88 40" fill="none">
                  <path d="M6 20 L38 11 L28 20 L38 29 Z" fill="#0284c7" fill-opacity="0.45" />
                  <polygon points="22,20 56,9 84,20 56,31" fill="#38bdf8" fill-opacity="0.92" />
                  <polygon points="44,20 64,13 84,20 64,27" fill="#e0f2fe" />
                  <path d="M16 12 L26 15 M14 28 L25 25 M32 7 L40 12" stroke="#bae6fd" stroke-width="1.8" stroke-linecap="round" />
                </svg>
              }
              @case ('SHADOW') {
                <svg class="arcane-proj-svg" viewBox="0 0 88 40" fill="none">
                  <path d="M8 24 C26 4, 48 36, 72 20" stroke="#9333ea" stroke-width="4" stroke-linecap="round" stroke-opacity="0.65" />
                  <path d="M10 16 C28 36, 50 4, 74 20" stroke="#c084fc" stroke-width="3.2" stroke-linecap="round" />
                  <path d="M46 20 L66 11 L84 20 L66 29 Z" fill="#f3e8ff" />
                  <circle cx="71" cy="20" r="5" fill="#3b0764" stroke="#e9d5ff" stroke-width="2" />
                </svg>
              }
              @case ('LIGHT') {
                <svg class="arcane-proj-svg" viewBox="0 0 88 40" fill="none">
                  <path d="M6 20 H76" stroke="#fde047" stroke-width="5" stroke-opacity="0.48" stroke-linecap="round" />
                  <polygon points="28,20 62,12 85,20 62,28" fill="#facc15" />
                  <polygon points="48,20 68,15 85,20 68,25" fill="#fefce8" />
                  <path d="M68 8 V32 M56 20 H80" stroke="#fef9c3" stroke-width="1.8" stroke-linecap="round" />
                </svg>
              }
            }
          </div>

          <!-- Arrival Impact Flash / Defense Shockwave -->
          <div class="arcane-proj-arrival">
            <span class="arcane-proj-arrival-ring"></span>
            @if (p.outcome === 'DEFENSE_BLOCKED' || p.outcome === 'DIRECT_BLOCKED' || p.outcome === 'DEFENSE_BROKEN') {
              <svg class="arcane-proj-deflect-arc" viewBox="0 0 64 64" fill="none">
                <path
                  d="M14 48 C18 22, 46 22, 50 48"
                  stroke="#93c5fd"
                  stroke-width="3.5"
                  stroke-linecap="round" />
                <path
                  d="M19 42 C24 27, 40 27, 45 42"
                  stroke="#e0f2fe"
                  stroke-width="2"
                  stroke-linecap="round" />
                @if (p.outcome === 'DEFENSE_BROKEN') {
                  <path
                    d="M34 14 L27 32 L38 40 L28 54"
                    stroke="#fda4af"
                    stroke-width="3"
                    stroke-linecap="round"
                    stroke-linejoin="round" />
                }
              </svg>
            } @else {
              <svg class="arcane-proj-star-svg" viewBox="0 0 64 64" fill="none">
                <polygon
                  points="32,4 37,24 58,18 40,32 56,50 35,39 30,60 27,39 6,46 24,31 10,14 28,25"
                  fill="currentColor"
                  fill-opacity="0.88" />
                <polygon
                  points="32,12 35,27 49,23 37,32 47,44 34,36 31,51 28,36 16,41 26,31 18,20 29,27"
                  fill="#ffffff" />
              </svg>
            }
          </div>
        </div>
      }
    </div>
  `,
})
export class ArenaProjectileOverlayComponent {
  readonly projectiles = input<readonly ActiveProjectile[]>([]);
}
