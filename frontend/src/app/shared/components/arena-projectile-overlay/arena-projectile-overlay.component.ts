import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CombatOutcome, Faction } from '../../../core/models/arcane.models';

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
}

@Component({
  selector: 'app-arena-projectile-overlay',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="arcane-projectile-layer" aria-hidden="true">
      @for (p of projectiles(); track p.uid) {
        <div
          class="arcane-proj-anchor"
          [attr.data-faction]="p.faction"
          [attr.data-outcome]="p.outcome"
          [style.--proj-x.px]="p.startX"
          [style.--proj-y.px]="p.startY"
          [style.--proj-dx.px]="p.dx"
          [style.--proj-dy.px]="p.dy"
          [style.--proj-angle.deg]="p.angleDeg">
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
              </svg>
            } @else {
              <span class="arcane-proj-arrival-core"></span>
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
