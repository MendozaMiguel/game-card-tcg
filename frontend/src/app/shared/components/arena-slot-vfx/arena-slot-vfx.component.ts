import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { SlotVfxEvent } from '../../utils/arena-vfx';

@Component({
  selector: 'app-arena-slot-vfx',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [
    `
      .arcane-vfx-hit-flash {
        position: absolute;
        inset: 0;
        border-radius: inherit;
        background: radial-gradient(circle, rgb(255 255 255 / 0.85) 0%, var(--vfx-tint, rgb(251 113 133 / 0.45)) 72%, transparent 100%);
        opacity: 0;
        pointer-events: none;
        will-change: transform, opacity;
      }

      .arcane-vfx-cross-svg {
        position: absolute;
        inset: -12%;
        width: 124%;
        height: 124%;
        pointer-events: none;
        opacity: 0;
        filter: drop-shadow(0 0 8px var(--vfx-accent, #fb7185));
        will-change: transform, opacity;
      }

      @media (prefers-reduced-motion: no-preference) {
        .arcane-vfx-hit-flash {
          animation: arcaneSlotHitFlash 0.22s ease-out forwards;
        }
        .arcane-vfx-cross-svg {
          animation: arcaneSlotCrossSlash 0.36s cubic-bezier(0.16, 1, 0.3, 1) forwards;
        }
        @keyframes arcaneSlotHitFlash {
          0% {
            opacity: 0.82;
            transform: scale(0.96);
          }
          100% {
            opacity: 0;
            transform: scale(1.04);
          }
        }
        @keyframes arcaneSlotCrossSlash {
          0% {
            opacity: 0;
            transform: scale(0.45) rotate(-14deg);
          }
          28% {
            opacity: 1;
            transform: scale(1.08) rotate(2deg);
          }
          100% {
            opacity: 0;
            transform: scale(1.18) rotate(6deg);
          }
        }
      }
    `,
  ],
  template: `
    @if (event(); as vfx) {
      <div
        class="arcane-vfx-slot"
        [attr.data-kind]="vfx.kind"
        [attr.data-faction]="vfx.faction"
        aria-hidden="true">
        <div class="arcane-vfx-ring"></div>
        @if (vfx.kind === 'DAMAGE' || vfx.kind === 'DESTROY' || vfx.kind === 'DEFENSE_BROKEN') {
          <div class="arcane-vfx-hit-flash"></div>
          <svg class="arcane-vfx-cross-svg" viewBox="0 0 100 100" fill="none">
            <path d="M16 20 Q52 44 86 82 Q48 54 16 20 Z" fill="var(--vfx-accent, #fb7185)" fill-opacity="0.9" />
            <path d="M22 25 Q52 46 80 76 Q50 51 22 25 Z" fill="#ffffff" />
            @if (vfx.kind === 'DESTROY' || vfx.kind === 'DEFENSE_BROKEN') {
              <path d="M84 18 Q50 48 16 84 Q53 52 84 18 Z" fill="var(--vfx-accent, #fda4af)" fill-opacity="0.88" />
              <path d="M77 24 Q50 49 23 77 Q51 51 77 24 Z" fill="#ffffff" />
            }
          </svg>
        }
        @if (vfx.kind === 'DAMAGE' || vfx.kind === 'DESTROY' || vfx.kind === 'ATK_BUFF') {
          <div class="arcane-vfx-cleave"></div>
        }
        @if (vfx.kind === 'SHIELD' || vfx.kind === 'HEAL' || vfx.kind === 'REACTIVE') {
          <div class="arcane-vfx-dome"></div>
        }
        @if (vfx.kind === 'DEFENSE_BLOCK' || vfx.kind === 'DEFENSE_BROKEN') {
          <svg class="arcane-vfx-hex-shield" viewBox="0 0 100 100" fill="none">
            <polygon
              points="50,8 86,27 86,68 50,92 14,68 14,27"
              stroke="var(--vfx-shield-stroke, #60a5fa)"
              stroke-width="3.2"
              fill="var(--vfx-shield-fill, rgb(59 130 246 / 0.22))" />
            <polygon
              points="50,18 76,32 76,63 50,81 24,63 24,32"
              stroke="#e0f2fe"
              stroke-opacity="0.78"
              stroke-width="1.6"
              stroke-dasharray="6 4" />
            <path
              d="M28 24 L50 13 L72 24"
              stroke="#fde68a"
              stroke-width="2.2"
              stroke-linecap="round" />
            @if (vfx.kind === 'DEFENSE_BROKEN') {
              <path
                d="M52 20 L43 44 L59 54 L45 78"
                stroke="#fda4af"
                stroke-width="3"
                stroke-linecap="round"
                stroke-linejoin="round" />
            }
          </svg>
          <div class="arcane-vfx-deflect-rays"></div>
        }
        @if (vfx.kind === 'SLOW') {
          <div class="arcane-vfx-frost-bars"></div>
        }
        <span class="arcane-vfx-badge">{{ vfx.label }}</span>
      </div>
    }
  `,
})
export class ArenaSlotVfxComponent {
  readonly event = input<SlotVfxEvent | null>(null);
}
