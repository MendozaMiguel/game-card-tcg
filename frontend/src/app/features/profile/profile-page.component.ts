import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideAlertTriangle,
  lucideCheck,
  lucideCrown,
  lucideFlame,
  lucideGem,
  lucideHistory,
  lucideMoon,
  lucideRefreshCw,
  lucideSnowflake,
  lucideSparkles,
  lucideSun,
} from '@ng-icons/lucide';
import {
  CosmeticType,
  Faction,
  MatchParticipantSummaryDto,
  MatchSource,
  PlayerMatchHistoryItemDto,
  PlayerStatsDto,
  UpdateCosmeticsRequest,
  UserProfileDto,
} from '../../core/models/arcane.models';
import { AnnouncerService } from '../../core/services/announcer.service';
import { AuthService, extractApiErrorMessage } from '../../core/services/auth.service';
import { ArcaneAvatarComponent } from '../../shared/components/arcane-avatar/arcane-avatar.component';
import {
  COSMETIC_CATALOG,
  END_REASON_LABELS,
  FACTION_META,
  LEAGUE_LABELS,
  MATCH_SOURCE_META,
  formatDurationCompact,
} from '../../shared/utils/faction-theme';

interface SourceFilterOption {
  value: MatchSource | null;
  label: string;
}

@Component({
  selector: 'app-profile-page',
  standalone: true,
  imports: [NgIcon, ArcaneAvatarComponent],
  viewProviders: [
    provideIcons({
      lucideAlertTriangle,
      lucideCheck,
      lucideCrown,
      lucideFlame,
      lucideGem,
      lucideHistory,
      lucideMoon,
      lucideRefreshCw,
      lucideSnowflake,
      lucideSparkles,
      lucideSun,
    }),
  ],
  templateUrl: './profile-page.component.html',
  styleUrl: './profile-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfilePageComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly announcer = inject(AnnouncerService);

  protected readonly factionMeta = FACTION_META;
  protected readonly leagueLabels = LEAGUE_LABELS;
  protected readonly matchSourceMeta = MATCH_SOURCE_META;
  protected readonly endReasonLabels = END_REASON_LABELS;
  protected readonly factions: readonly Faction[] = ['FIRE', 'ICE', 'SHADOW', 'LIGHT'];
  protected readonly starterAvatarIds: readonly string[] = [
    'avatar_initiate',
    'avatar_pyromancer',
    'avatar_cryomancer',
    'avatar_shadowmage',
    'avatar_luminarch',
  ];
  protected readonly sourceFilters: readonly SourceFilterOption[] = [
    { value: null, label: 'Tous' },
    { value: 'RANKED', label: 'Classé' },
    { value: 'TOURNAMENT', label: 'Tournoi' },
    { value: 'CUSTOM', label: 'Salon' },
    { value: 'CASUAL', label: 'Amical' },
    { value: 'AI', label: 'IA' },
  ];

  protected readonly saving = signal(false);
  protected readonly loadingHistory = signal(false);
  protected readonly historyError = signal<string | null>(null);
  protected readonly selectedSource = signal<MatchSource | null>(null);
  protected readonly stats = signal<PlayerStatsDto | null>(null);
  protected readonly matchHistory = signal<readonly PlayerMatchHistoryItemDto[]>([]);

  private readonly dateFormatter = new Intl.DateTimeFormat('fr-FR', {
    dateStyle: 'short',
    timeStyle: 'short',
  });

  async ngOnInit(): Promise<void> {
    await Promise.all([this.auth.refreshProfile(), this.loadHistory()]);
  }

  protected async loadHistory(source: MatchSource | null = this.selectedSource()): Promise<void> {
    this.loadingHistory.set(true);
    this.historyError.set(null);
    try {
      const response = await this.auth.getMatchHistory(20, source);
      this.stats.set(response.stats);
      this.matchHistory.set(response.matches);
    } catch (err) {
      this.historyError.set(extractApiErrorMessage(err, "Impossible de charger l'historique des duels."));
    } finally {
      this.loadingHistory.set(false);
    }
  }

  protected async onSelectSourceFilter(source: MatchSource | null): Promise<void> {
    if (this.selectedSource() === source && !this.historyError()) {
      return;
    }
    this.selectedSource.set(source);
    await this.loadHistory(source);
  }

  protected formatMatchDate(iso: string | null | undefined): string {
    if (!iso) return '-';
    const date = new Date(iso);
    if (Number.isNaN(date.getTime())) return '-';
    return this.dateFormatter.format(date);
  }

  protected formatDuration(seconds: number | null | undefined): string {
    return formatDurationCompact(seconds);
  }

  protected formatMatchup(item: PlayerMatchHistoryItemDto): string {
    const oppNames =
      item.opponents.length > 0 ? item.opponents.map((o) => o.username).join(' & ') : 'Adversaire inconnu';
    if (item.allies.length > 0) {
      const allyNames = item.allies.map((a) => a.username).join(' & ');
      return `Vous + ${allyNames} vs ${oppNames}`;
    }
    return `Vous vs ${oppNames}`;
  }

  protected formatMmrDelta(delta: number): string {
    if (delta > 0) return `+${delta} MMR`;
    if (delta < 0) return `${delta} MMR`;
    return '';
  }

  protected lpPercent(lp: number | null, startingLp: number): number {
    if (lp == null || startingLp <= 0) return 0;
    return Math.max(0, Math.min(100, Math.round((lp / startingLp) * 100)));
  }

  protected factionShortLabel(faction: Faction | null | undefined): string {
    return faction ? this.factionMeta[faction].shortLabel : 'Multi';
  }

  protected factionIconName(faction: Faction): string {
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

  protected factionWinShare(factionWins: Record<Faction, number>, faction: Faction): number {
    const maxWins = Math.max(1, ...this.factions.map((f) => factionWins[f] ?? 0));
    return Math.round(((factionWins[faction] ?? 0) / maxWins) * 100);
  }

  protected modeStatLabel(source: MatchSource, mode: string): string {
    const srcLabel = this.matchSourceMeta[source]?.shortLabel ?? source;
    return `${srcLabel} ${mode === '1V1' ? '1v1' : '2v2'}`;
  }

  protected participantBadgeText(p: MatchParticipantSummaryDto): string {
    const f = p.primaryFaction ? ` · ${this.factionShortLabel(p.primaryFaction)}` : '';
    const bot = p.bot ? ' (IA)' : '';
    return `${p.username}${bot}${f}`;
  }

  protected cosmeticInfo(itemId: string) {
    return (
      COSMETIC_CATALOG[itemId] ?? {
        itemId,
        label: itemId,
        subtitle: 'Objet arcanique',
        swatchClass: 'from-slate-800 to-slate-900',
      }
    );
  }

  protected isEquipped(profile: UserProfileDto, type: CosmeticType, itemId: string): boolean {
    switch (type) {
      case 'CARD_BACK':
        return profile.equippedCardBack === itemId;
      case 'BOARD_SKIN':
        return profile.equippedBoardSkin === itemId;
      case 'COMPANION_PET':
        return profile.equippedPet === itemId;
      case 'ENERGY_SKIN':
        return profile.equippedEnergySkin === itemId;
      case 'EMOTE_FINISHER':
        return profile.equippedFinisher === itemId;
      case 'MAGE_PROFILE':
        return (
          profile.equippedAvatar === itemId ||
          profile.equippedLeagueFrame === itemId ||
          profile.equippedTitle === itemId
        );
      default:
        return false;
    }
  }

  protected async equipItem(type: CosmeticType, itemId: string): Promise<void> {
    const req: UpdateCosmeticsRequest = {};
    if (type === 'CARD_BACK') req.equippedCardBack = itemId;
    else if (type === 'BOARD_SKIN') req.equippedBoardSkin = itemId;
    else if (type === 'COMPANION_PET') req.equippedPet = itemId;
    else if (type === 'ENERGY_SKIN') req.equippedEnergySkin = itemId;
    else if (type === 'EMOTE_FINISHER') req.equippedFinisher = itemId;
    else if (type === 'MAGE_PROFILE') {
      if (itemId.startsWith('avatar_')) req.equippedAvatar = itemId;
      else if (itemId.startsWith('frame_')) req.equippedLeagueFrame = itemId;
      else req.equippedTitle = itemId;
    }

    this.saving.set(true);
    try {
      await this.auth.equipCosmetics(req);
      this.announcer.pushToast(`Cosmétique « ${this.cosmeticInfo(itemId).label} » équipé.`, 'success');
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    } finally {
      this.saving.set(false);
    }
  }
}
