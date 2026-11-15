import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Faction } from '../../../core/models/arcane.models';
import { AvatarImpactKind } from '../../utils/arena-vfx';

export type AvatarSize = 'xs' | 'sm' | 'md' | 'lg';

export type KnownAvatarId =
  | 'avatar_initiate'
  | 'avatar_automaton'
  | 'avatar_pyromancer'
  | 'avatar_cryomancer'
  | 'avatar_shadowmage'
  | 'avatar_luminarch';

function resolveAvatarKey(avatarId: string | null | undefined, bot: boolean): KnownAvatarId {
  if (avatarId === 'avatar_automaton' || (bot && !avatarId)) {
    return 'avatar_automaton';
  }
  switch (avatarId) {
    case 'avatar_pyromancer':
    case 'avatar_cryomancer':
    case 'avatar_shadowmage':
    case 'avatar_luminarch':
    case 'avatar_automaton':
      return avatarId;
    case 'avatar_initiate':
    default:
      return 'avatar_initiate';
  }
}

function defaultFactionForAvatar(key: KnownAvatarId): Faction {
  switch (key) {
    case 'avatar_pyromancer':
      return 'FIRE';
    case 'avatar_cryomancer':
      return 'ICE';
    case 'avatar_shadowmage':
      return 'SHADOW';
    case 'avatar_luminarch':
      return 'LIGHT';
    case 'avatar_automaton':
      return 'ICE';
    case 'avatar_initiate':
    default:
      return 'LIGHT';
  }
}

@Component({
  selector: 'app-arcane-avatar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let key = avatarKey();
    <div
      class="arcane-avatar-slot"
      [attr.data-size]="size()"
      [attr.data-avatar]="key"
      [attr.data-frame]="frameTier()"
      [attr.data-faction]="resolvedFaction()"
      [attr.data-impact]="impactKind() ?? null"
      [attr.data-bot]="bot() ? 'true' : null"
      [attr.role]="decorative() ? null : 'img'"
      [attr.aria-label]="decorative() ? null : computedAriaLabel()">
      <span class="arcane-avatar-aura" aria-hidden="true"></span>
      <span class="arcane-avatar-ring" aria-hidden="true"></span>

      <div class="arcane-avatar-core" aria-hidden="true">
        <svg class="arcane-avatar-svg" viewBox="0 0 64 64" fill="none" focusable="false">
          <defs>
            <radialGradient [attr.id]="gradId()" cx="50%" cy="34%" r="70%">
              <stop offset="0%" stop-color="currentColor" stop-opacity="0.34" />
              <stop offset="68%" stop-color="#0b1020" stop-opacity="0.94" />
              <stop offset="100%" stop-color="#060913" stop-opacity="1" />
            </radialGradient>
          </defs>

          <!-- Runic medallion backdrop -->
          <circle cx="32" cy="32" r="30" [attr.fill]="'url(#' + gradId() + ')'" />
          <circle cx="32" cy="32" r="26.5" stroke="currentColor" stroke-opacity="0.28" stroke-width="1" stroke-dasharray="3 3" />

          @switch (key) {
            @case ('avatar_automaton') {
              <!-- Mage Automate clockwork helm + optic core -->
              <path d="M15 56 C16 44, 23 39, 32 39 C41 39, 48 44, 49 56 Z" fill="#162235" stroke="#5eead4" stroke-opacity="0.55" stroke-width="1.5" />
              <path d="M21 26 L24 14 L32 10 L40 14 L43 26 L41 37 L32 41 L23 37 Z" fill="#0f172a" stroke="#7dd3fc" stroke-width="1.8" stroke-linejoin="round" />
              <path d="M26 12 L32 6 L38 12" stroke="#d4a94e" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
              <rect x="23" y="23" width="18" height="6" rx="3" fill="#082f49" stroke="#38bdf8" stroke-width="1.3" />
              <circle cx="28" cy="26" r="2.1" fill="#34d399" />
              <circle cx="36" cy="26" r="2.1" fill="#34d399" />
              <path d="M27 33 H37 M29 36 H35" stroke="#94a3b8" stroke-width="1.4" stroke-linecap="round" />
            }
            @case ('avatar_pyromancer') {
              <!-- Pyromancer ember mantle & flame crown -->
              <path d="M13 56 C16 43, 23 38, 32 38 C41 38, 48 43, 51 56 Z" fill="#2a120a" stroke="#fb923c" stroke-opacity="0.65" stroke-width="1.5" />
              <path d="M20 35 C18 23, 23 13, 32 10 C41 13, 46 23, 44 35 L32 42 Z" fill="#1a0d0a" stroke="#f97316" stroke-width="1.8" stroke-linejoin="round" />
              <path d="M24 14 L21 7 L29 12 M32 10 L32 4 L35 12 M40 14 L43 7 L36 12" stroke="#fdba74" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" />
              <path d="M24 25 L29 27 L25 29 Z M40 25 L35 27 L39 29 Z" fill="#fde047" />
              <path d="M29 36 L32 39 L35 36" stroke="#fb923c" stroke-width="1.6" stroke-linecap="round" />
            }
            @case ('avatar_cryomancer') {
              <!-- Cryomancer glacial tiara & frosted visor -->
              <path d="M13 56 C16 43, 23 38, 32 38 C41 38, 48 43, 51 56 Z" fill="#0d2238" stroke="#38bdf8" stroke-opacity="0.65" stroke-width="1.5" />
              <path d="M20 34 C19 22, 24 13, 32 11 C40 13, 45 22, 44 34 L32 42 Z" fill="#081426" stroke="#7dd3fc" stroke-width="1.8" stroke-linejoin="round" />
              <path d="M23 15 L25 7 L32 12 L39 7 L41 15" stroke="#bae6fd" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" />
              <path d="M24 26 H30 M34 26 H40" stroke="#e0f2fe" stroke-width="2" stroke-linecap="round" />
              <path d="M32 19 L34.5 23 L32 27 L29.5 23 Z" fill="#38bdf8" />
            }
            @case ('avatar_shadowmage') {
              <!-- Shadowmage veiled umbral hood & astral eyes -->
              <path d="M13 56 C16 43, 23 38, 32 38 C41 38, 48 43, 51 56 Z" fill="#1e1030" stroke="#c084fc" stroke-opacity="0.65" stroke-width="1.5" />
              <path d="M18 39 C17 21, 24 10, 32 8 C40 10, 47 21, 46 39 C41 35, 36 42, 32 42 C28 42, 23 35, 18 39 Z" fill="#0d0818" stroke="#a855f7" stroke-width="1.8" stroke-linejoin="round" />
              <circle cx="27" cy="26" r="2.1" fill="#e9d5ff" />
              <circle cx="37" cy="26" r="2.1" fill="#e9d5ff" />
              <path d="M28 16 C30 14, 34 14, 36 16" stroke="#c084fc" stroke-width="1.5" stroke-linecap="round" />
              <path d="M29 33 L32 36 L35 33" stroke="#a855f7" stroke-width="1.4" stroke-linecap="round" />
            }
            @case ('avatar_luminarch') {
              <!-- Luminarch solar helm & winged halo -->
              <path d="M13 56 C16 43, 23 38, 32 38 C41 38, 48 43, 51 56 Z" fill="#291f0b" stroke="#facc15" stroke-opacity="0.7" stroke-width="1.5" />
              <circle cx="32" cy="18" r="9.5" stroke="#fef08a" stroke-opacity="0.55" stroke-width="1.3" stroke-dasharray="2.5 2.5" />
              <path d="M21 34 L22 17 L32 11 L42 17 L43 34 L32 42 Z" fill="#181207" stroke="#facc15" stroke-width="1.8" stroke-linejoin="round" />
              <path d="M21 22 L13 17 M43 22 L51 17" stroke="#fde047" stroke-width="1.8" stroke-linecap="round" />
              <path d="M26 26 H30 M34 26 H38" stroke="#fef9c3" stroke-width="2" stroke-linecap="round" />
              <path d="M32 14 V22" stroke="#facc15" stroke-width="1.8" stroke-linecap="round" />
            }
            @default {
              <!-- Initiate hooded arcane robe & Order crest -->
              <path d="M14 56 C16 43, 23 38, 32 38 C41 38, 48 43, 50 56 Z" fill="#171f33" stroke="#d4a94e" stroke-opacity="0.6" stroke-width="1.5" />
              <path d="M19 37 C19 20, 25 11, 32 9 C39 11, 45 20, 45 37 C40 34, 36 40, 32 40 C28 40, 24 34, 19 37 Z" fill="#0d1424" stroke="#d4a94e" stroke-width="1.7" stroke-linejoin="round" />
              <circle cx="27.5" cy="25.5" r="1.9" fill="#fde68a" />
              <circle cx="36.5" cy="25.5" r="1.9" fill="#fde68a" />
              <path d="M32 14 L34.5 18.5 L32 22 L29.5 18.5 Z" fill="#f0c96e" />
            }
          }
        </svg>
      </div>

      @if (!connected()) {
        <span class="arcane-avatar-status arcane-avatar-status--offline" aria-hidden="true" title="Déconnecté"></span>
      } @else if (bot()) {
        <span class="arcane-avatar-status arcane-avatar-status--bot" aria-hidden="true" title="Mage Automate">IA</span>
      }

      @if (impactKind(); as kind) {
        <div class="arcane-avatar-impact-fx" [attr.data-kind]="kind" aria-hidden="true">
          @if (kind === 'DEFENSE_BLOCK') {
            <svg class="arcane-avatar-shield-svg" viewBox="0 0 64 64" fill="none">
              <path
                d="M32 5 L53 14 V31 C53 45 43 54 32 59 C21 54 11 45 11 31 V14 Z"
                stroke="#60a5fa"
                stroke-width="3"
                fill="rgba(59, 130, 246, 0.24)" />
              <path
                d="M32 12 L46 18.5 V30 C46 40 39 47 32 51 C25 47 18 40 18 30 V18.5 Z"
                stroke="#bfdbfe"
                stroke-width="1.6"
                stroke-dasharray="4 3" />
            </svg>
          } @else {
            <span class="arcane-avatar-hit-ring"></span>
          }
          @if (impactBadgeText(); as badge) {
            <span class="arcane-avatar-impact-badge">{{ badge }}</span>
          }
        </div>
      }
    </div>
  `,
})
export class ArcaneAvatarComponent {
  readonly avatarId = input<string | null | undefined>('avatar_initiate');
  readonly frameId = input<string | null | undefined>('frame_apprenti');
  readonly faction = input<Faction | null | undefined>(null);
  readonly size = input<AvatarSize>('sm');
  readonly bot = input<boolean>(false);
  readonly connected = input<boolean>(true);
  readonly impactKind = input<AvatarImpactKind | null>(null);
  readonly impactBadgeText = input<string | null>(null);
  readonly decorative = input<boolean>(true);
  readonly label = input<string>('Avatar du Mage');

  protected readonly avatarKey = computed(() => resolveAvatarKey(this.avatarId(), this.bot()));
  protected readonly resolvedFaction = computed<Faction>(() => this.faction() ?? defaultFactionForAvatar(this.avatarKey()));
  protected readonly frameTier = computed(() => {
    const f = this.frameId() ?? 'frame_apprenti';
    if (f.includes('grand_maitre')) return 'grand_maitre';
    if (f.includes('archimage')) return 'archimage';
    if (f.includes('mage')) return 'mage';
    return 'apprenti';
  });
  protected readonly gradId = computed(
    () => `avGrad-${this.avatarKey()}-${this.resolvedFaction()}-${this.size()}`,
  );
  protected readonly computedAriaLabel = computed(() => this.label() || 'Avatar du Mage');
}
