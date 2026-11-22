import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideArrowUp,
  lucideCheck,
  lucideClock,
  lucideCrown,
  lucideEyeOff,
  lucideFlame,
  lucideGem,
  lucideMoon,
  lucideOrbit,
  lucideRotateCw,
  lucideShield,
  lucideSkull,
  lucideSnowflake,
  lucideSparkles,
  lucideSun,
  lucideSwords,
  lucideWand2,
  lucideZap,
} from '@ng-icons/lucide';
import { CardDefinition, CardType, CardViewDto, Faction } from '../../../core/models/arcane.models';
import {
  CardEffectVisualKind,
  buildCardIllustrationSpec,
  getProceduralArtTheme,
  resolveCardArtworkUrl,
} from '../../utils/card-artwork';
import { CARD_TYPE_LABELS, FACTION_META } from '../../utils/faction-theme';
import { prefersReducedMotion } from '../../utils/motion-presets';
import { ArcaneCardArtComponent } from '../arcane-card-art/arcane-card-art.component';

const EFFECT_ICON_MAP: Record<CardEffectVisualKind, string> = {
  DAMAGE: 'lucideFlame',
  DESTROY: 'lucideSkull',
  SHIELD: 'lucideShield',
  ATK_BUFF: 'lucideSwords',
  HEAL: 'lucideSparkles',
  SLOW: 'lucideSnowflake',
  STEALTH: 'lucideEyeOff',
  ENERGY: 'lucideZap',
  SURVOL: 'lucideArrowUp',
  PEEK: 'lucideOrbit',
  REACTIVE: 'lucideClock',
  CURSE: 'lucideSkull',
};

@Component({
  selector: 'app-arcane-card',
  standalone: true,
  imports: [NgIcon, ArcaneCardArtComponent],
  viewProviders: [
    provideIcons({
      lucideArrowUp,
      lucideCheck,
      lucideClock,
      lucideCrown,
      lucideEyeOff,
      lucideFlame,
      lucideGem,
      lucideMoon,
      lucideOrbit,
      lucideRotateCw,
      lucideShield,
      lucideSkull,
      lucideSnowflake,
      lucideSparkles,
      lucideSun,
      lucideSwords,
      lucideWand2,
      lucideZap,
    }),
  ],
  templateUrl: './arcane-card.component.html',
  styleUrl: './arcane-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class.arcane-card-playable]': 'playable()',
    '[class.arcane-card-ready-attacker]': 'attackerReady()',
    '[class.arcane-card-tabletop-host]': 'tabletop()',
    '[attr.data-card-faction]': 'card()?.faction ?? catalogCard()?.faction ?? null',
  },
})
export class ArcaneCardComponent {
  readonly card = input<CardViewDto | null>(null);
  readonly catalogCard = input<CardDefinition | null>(null);
  readonly compact = input(false);
  readonly tabletop = input(false);
  readonly showCompactText = input(false);
  readonly selected = input(false);
  readonly highlightedTarget = input(false);
  readonly playable = input(false);
  readonly attackerReady = input(false);
  readonly artworkUrl = input<string | null>(null);
  readonly showArtwork = input<boolean | null>(null);
  readonly interactiveTilt = input(false);
  readonly showPosture = input(true);
  readonly unclampDescription = input(false);
  readonly foilOverride = input<boolean | null>(null);

  protected readonly factionMeta = FACTION_META;
  protected readonly typeLabels = CARD_TYPE_LABELS;

  protected readonly tiltX = signal(0);
  protected readonly tiltY = signal(0);
  protected readonly foilX = signal(50);
  protected readonly foilY = signal(50);

  protected readonly shouldShowArtwork = computed(() => {
    const explicit = this.showArtwork();
    return explicit !== null ? explicit : (!this.compact() || this.tabletop());
  });

  protected readonly isPowerhouse = computed(() => {
    const view = this.card();
    const def = this.catalogCard();
    const isEpic = view ? !!view.epic : !!def?.isEpic;
    const type = view?.cardType ?? def?.cardType ?? null;
    const cost = view?.energyCost ?? def?.energyCost ?? 0;
    return isEpic || type === 'SPELL_ULTIMATE' || cost >= 5;
  });

  protected readonly resolvedCardId = computed(
    () => this.card()?.cardId ?? this.catalogCard()?.cardId ?? null,
  );

  protected readonly resolvedArtworkSrc = computed(() =>
    resolveCardArtworkUrl(this.resolvedCardId(), this.artworkUrl()),
  );

  protected readonly artTheme = computed(() => {
    const faction = this.card()?.faction ?? this.catalogCard()?.faction ?? null;
    const cardType = this.card()?.cardType ?? this.catalogCard()?.cardType ?? null;
    return getProceduralArtTheme(faction, cardType);
  });

  protected readonly artSpec = computed(() => {
    return buildCardIllustrationSpec(this.card() ?? this.catalogCard());
  });

  protected effectIconName(effect: CardEffectVisualKind): string {
    return EFFECT_ICON_MAP[effect];
  }

  protected factionIconName(faction: Faction | null): string {
    switch (faction) {
      case 'FIRE':
        return 'lucideFlame';
      case 'ICE':
        return 'lucideSnowflake';
      case 'SHADOW':
        return 'lucideMoon';
      case 'LIGHT':
      default:
        return 'lucideSun';
    }
  }

  protected typeIconName(cardType: CardType | null): string {
    switch (cardType) {
      case 'MONSTER':
        return 'lucideSwords';
      case 'FAMILIAR':
        return 'lucideSparkles';
      case 'SPELL_NORMAL':
        return 'lucideWand2';
      case 'SPELL_REACTIVE':
        return 'lucideShield';
      case 'SPELL_ULTIMATE':
        return 'lucideOrbit';
      case 'CURSE':
        return 'lucideSkull';
      case 'ARTIFACT':
        return 'lucideGem';
      default:
        return 'lucideSparkles';
    }
  }

  protected onPointerMove(event: PointerEvent): void {
    if (!this.interactiveTilt() || prefersReducedMotion()) {
      return;
    }
    const target = event.currentTarget as HTMLElement | null;
    if (!target) return;
    const rect = target.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return;

    const px = Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width));
    const py = Math.max(0, Math.min(1, (event.clientY - rect.top) / rect.height));
    const maxDeg = 7;

    this.tiltY.set(Number(((px - 0.5) * 2 * maxDeg).toFixed(2)));
    this.tiltX.set(Number(((0.5 - py) * 2 * maxDeg).toFixed(2)));
    this.foilX.set(Math.round(px * 100));
    this.foilY.set(Math.round(py * 100));
  }

  protected onPointerLeave(): void {
    this.tiltX.set(0);
    this.tiltY.set(0);
    this.foilX.set(50);
    this.foilY.set(50);
  }
}
