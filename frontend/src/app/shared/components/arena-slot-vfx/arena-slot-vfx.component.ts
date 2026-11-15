import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { SlotVfxEvent } from '../../utils/arena-vfx';

@Component({
  selector: 'app-arena-slot-vfx',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (event(); as vfx) {
      <div
        class="arcane-vfx-slot"
        [attr.data-kind]="vfx.kind"
        [attr.data-faction]="vfx.faction"
        aria-hidden="true">
        <div class="arcane-vfx-ring"></div>
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
