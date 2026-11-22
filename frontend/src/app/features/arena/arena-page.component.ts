import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  computed,
  effect,
  inject,
  signal,
  untracked,
  viewChild,
  viewChildren,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideArrowUp,
  lucideBookOpen,
  lucideCheckCircle,
  lucideChevronLeft,
  lucideClock,
  lucideCrosshair,
  lucideEye,
  lucideFeather,
  lucideFlame,
  lucideLayers,
  lucideMenu,
  lucideMessageSquare,
  lucideMoon,
  lucidePlay,
  lucideRotateCw,
  lucideScrollText,
  lucideSend,
  lucideShield,
  lucideSkull,
  lucideSnowflake,
  lucideSparkles,
  lucideSun,
  lucideSwords,
  lucideTrash2,
  lucideTrophy,
  lucideWand2,
  lucideX,
  lucideZap,
} from '@ng-icons/lucide';
import { gsap } from 'gsap';
import {
  AbilityOption,
  CardDefinition,
  CardViewDto,
  Faction,
  GameActionType,
  GamePhase,
  PlayerBoardViewDto,
  PlayerGameView,
  Posture,
  TeamId,
} from '../../core/models/arcane.models';
import { AnnouncerService } from '../../core/services/announcer.service';
import { AuthService, extractApiErrorMessage } from '../../core/services/auth.service';
import { LobbyApiService } from '../../core/services/lobby-api.service';
import { StompService } from '../../core/services/stomp.service';
import { ArcaneAvatarComponent } from '../../shared/components/arcane-avatar/arcane-avatar.component';
import { ArcaneCardComponent } from '../../shared/components/arcane-card/arcane-card.component';
import {
  ActiveProjectile,
  ArenaProjectileOverlayComponent,
} from '../../shared/components/arena-projectile-overlay/arena-projectile-overlay.component';
import {
  ArenaCombatPopupsComponent,
  FloatingCombatPopup,
} from '../../shared/components/arena-combat-popups/arena-combat-popups.component';
import {
  ArenaImpactCanvasComponent,
  CanvasImpactBurst,
} from '../../shared/components/arena-impact-canvas/arena-impact-canvas.component';
import { ArenaSlotVfxComponent } from '../../shared/components/arena-slot-vfx/arena-slot-vfx.component';
import { ArenaSummonShowcaseComponent } from '../../shared/components/arena-summon-showcase/arena-summon-showcase.component';
import {
  ArenaThreeBackdropComponent,
  ThreeCombatPulse,
} from '../../shared/components/arena-three-backdrop/arena-three-backdrop.component';
import { ArenaAudioControlComponent } from '../../shared/components/arena-audio-control/arena-audio-control.component';
import { ArenaTutorialCoachComponent } from '../../shared/components/arena-tutorial-coach/arena-tutorial-coach.component';
import {
  ArenaTurnPopupComponent,
  TurnAnnouncement,
} from '../../shared/components/arena-turn-popup/arena-turn-popup.component';
import { ImeEnterSubmitDirective } from '../../shared/directives/ime-enter-submit.directive';
import { ArcaneAudioService } from '../../shared/services/arcane-audio.service';
import {
  HandCardPlayability,
  TacticalGuidance,
  buildTacticalGuidance,
  evaluateHandCardPlayability,
} from '../../shared/utils/arena-tactical-guide';
import {
  ArenaVfxBatch,
  AvatarImpactEvent,
  CombatPopupRequest,
  CombatShatterEvent,
  LpVfxEvent,
  MythicShowcaseBanner,
  PROJECTILE_IMPACT_DELAY_MS,
  SlotVfxEvent,
  SlotZone,
  SpellWaveBanner,
  SummonImpactEvent,
  VisualProjectileRequest,
  extractArenaVfxEvents,
  slotBurstKey,
} from '../../shared/utils/arena-vfx';
import { CardEffectVisualKind } from '../../shared/utils/card-artwork';
import {
  PresentedCombatLogEntry,
  presentCombatLogEntries,
} from '../../shared/utils/combat-log-presenter';
import {
  AI_DIFFICULTY_META,
  FACTION_META,
  TargetRequirement,
  findEligibleTargetsForRequirement,
  getTargetRequirementForTargetString,
  getTargetRequirementForTrigger,
} from '../../shared/utils/faction-theme';
import {
  animateBoardShake,
  animateCardAttackLunge,
  animateCardSummonSlam,
  animateMulliganCardIn,
  animateMulliganCardOut,
  launchVictoryConfetti,
  prefersReducedMotion,
  pulseElementImpact,
} from '../../shared/utils/motion-presets';

const ARENA_PHASE_LABELS: Record<GamePhase, { full: string; short: string }> = {
  MULLIGAN: { full: 'Mulligan', short: 'Mulligan' },
  MAIN_PHASE: { full: 'Phase Principale', short: 'Principale' },
  COMBAT_PHASE: { full: 'Phase de Combat', short: 'Combat' },
  WAITING_FOR_REACTION: { full: 'Réaction (12s)', short: 'Réaction' },
  WAITING_FOR_MODAL_CHOICE: { full: 'Choix d’Effet', short: 'Choix' },
  WAITING_FOR_DISCARD: { full: 'Défausse', short: 'Défausse' },
  FINISHED: { full: 'Terminé', short: 'Fin' },
};

type PendingInteraction =
  | { kind: 'PICK_SLOT'; mode: 'MONSTER' | 'FAMILIAR' | 'SPELL' | 'REACTIVE'; card: CardViewDto }
  | {
    kind: 'PICK_TARGET';
    actionType: 'PLAY_CARD' | 'ACTIVATE_EFFECT' | 'DECLARE_ATTACK';
    sourceInstanceId: string;
    targetSlot: number | null;
    posture: Posture | null;
    requirement: TargetRequirement;
    maxTargets: 1 | 2;
    eligibleIds: Set<string>;
    originX: number;
    originY: number;
  };

@Component({
  selector: 'app-arena-page',
  standalone: true,
  imports: [
    ArcaneAvatarComponent,
    ArcaneCardComponent,
    ArenaAudioControlComponent,
    ArenaCombatPopupsComponent,
    ArenaImpactCanvasComponent,
    ArenaProjectileOverlayComponent,
    ArenaSlotVfxComponent,
    ArenaSummonShowcaseComponent,
    ArenaThreeBackdropComponent,
    ArenaTutorialCoachComponent,
    ArenaTurnPopupComponent,
    ImeEnterSubmitDirective,
    NgIcon,
  ],
  viewProviders: [
    provideIcons({
      lucideArrowUp,
      lucideBookOpen,
      lucideCheckCircle,
      lucideChevronLeft,
      lucideClock,
      lucideCrosshair,
      lucideEye,
      lucideFeather,
      lucideFlame,
      lucideLayers,
      lucideMenu,
      lucideMessageSquare,
      lucideMoon,
      lucidePlay,
      lucideRotateCw,
      lucideScrollText,
      lucideSend,
      lucideShield,
      lucideSkull,
      lucideSnowflake,
      lucideSparkles,
      lucideSun,
      lucideSwords,
      lucideTrash2,
      lucideTrophy,
      lucideWand2,
      lucideX,
      lucideZap,
    }),
  ],
  templateUrl: './arena-page.component.html',
  styleUrl: './arena-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '(document:keydown.escape)': 'onEscapeKey()',
  },
})
export class ArenaPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly lobbyApi = inject(LobbyApiService);
  private readonly announcer = inject(AnnouncerService);
  private readonly audio = inject(ArcaneAudioService);
  private readonly auth = inject(AuthService, { optional: true });
  private readonly destroyRef = inject(DestroyRef);
  protected readonly stomp = inject(StompService);

  private readonly arenaGridRef = viewChild<ElementRef<HTMLElement>>('arenaGrid');
  private readonly arenaPlayColRef = viewChild<ElementRef<HTMLElement>>('arenaPlayCol');
  private readonly enemyLpBoxRef = viewChild<ElementRef<HTMLElement>>('enemyLpBox');
  private readonly allyLpBoxRef = viewChild<ElementRef<HTMLElement>>('allyLpBox');
  private readonly mulliganDialogRef = viewChild<ElementRef<HTMLDialogElement>>('mulliganDialog');
  private readonly mulliganCardSlots = viewChildren<ElementRef<HTMLElement>>('mulliganCardSlot');
  private readonly reactionDialogRef = viewChild<ElementRef<HTMLDialogElement>>('reactionDialog');
  private readonly choiceDialogRef = viewChild<ElementRef<HTMLDialogElement>>('choiceDialog');
  private readonly discardDialogRef = viewChild<ElementRef<HTMLDialogElement>>('discardDialog');
  private readonly finishedDialogRef = viewChild<ElementRef<HTMLDialogElement>>('finishedDialog');
  private readonly forfeitDialogRef = viewChild<ElementRef<HTMLDialogElement>>('forfeitDialog');
  private readonly discardViewerDialogRef = viewChild<ElementRef<HTMLDialogElement>>('discardViewerDialog');
  private readonly sidebarLogTabRef = viewChild<ElementRef<HTMLButtonElement>>('sidebarLogTab');
  private readonly sidebarChatTabRef = viewChild<ElementRef<HTMLButtonElement>>('sidebarChatTab');

  private matchId = '';
  private lastAnimatedLogSeq = 0;
  private lastSeenChatCount = 0;
  private prevEnemyLp: number | null = null;
  private prevAllyLp: number | null = null;
  private prevVfxGameView: PlayerGameView | null = null;
  private catalogByName = new Map<string, CardDefinition>();
  private readonly activeVfxTimers = new Set<number>();
  private readonly activeAnimationFrames = new Set<number>();
  private readonly activeTimelineCleanups = new Set<() => void>();
  private celebratedMatchFinish = false;
  private finishedAudioPlayed = false;
  private destroyed = false;
  private stopVictoryConfetti: (() => void) | null = null;

  protected readonly nowMs = signal(Date.now());
  protected readonly gameView = this.stomp.gameState;
  private readonly tutorialQueryActive = signal(false);
  private readonly tutorialDismissedLocally = signal(false);
  protected readonly tutorialSaving = signal(false);
  protected readonly tutorialActive = computed(() => {
    const view = this.gameView();
    if (!view || view.spectator || this.tutorialDismissedLocally()) {
      return false;
    }
    return this.tutorialQueryActive() || this.auth?.user()?.tutorialCompleted === false;
  });
  protected readonly slotBursts = signal<Readonly<Record<string, SlotVfxEvent>>>({});
  protected readonly avatarBursts = signal<Readonly<Record<string, AvatarImpactEvent>>>({});
  protected readonly activeProjectiles = signal<readonly ActiveProjectile[]>([]);
  protected readonly canvasBursts = signal<readonly CanvasImpactBurst[]>([]);
  protected readonly floatingPopups = signal<readonly FloatingCombatPopup[]>([]);
  protected readonly mythicShowcase = signal<MythicShowcaseBanner | null>(null);
  protected readonly threeCombatPulse = signal<ThreeCombatPulse | null>(null);
  protected readonly boardImpactTint = signal<Faction | 'CRITICAL_HIT' | null>(null);
  protected readonly enemyLpBurst = signal<LpVfxEvent | null>(null);
  protected readonly allyLpBurst = signal<LpVfxEvent | null>(null);
  protected readonly spellWave = signal<SpellWaveBanner | null>(null);
  protected readonly aiDifficultyMeta = AI_DIFFICULTY_META;
  protected readonly factionMeta = FACTION_META;
  protected readonly logFilterMode = signal<'HIGHLIGHTS' | 'ALL'>('HIGHLIGHTS');
  protected readonly turnAnnouncement = signal<TurnAnnouncement | null>(null);
  protected readonly presentedLogs = computed<PresentedCombatLogEntry[]>(() => {
    const view = this.gameView();
    if (!view) return [];
    const teamId = view.spectator ? null : this.myTeamId();
    return presentCombatLogEntries(
      [...view.combatLog].reverse(),
      view.viewerUserId,
      teamId,
      view.players,
    );
  });
  protected readonly visibleLogs = computed<PresentedCombatLogEntry[]>(() => {
    const all = this.presentedLogs();
    return this.logFilterMode() === 'HIGHLIGHTS' ? all.filter((entry) => entry.isHighlight) : all;
  });
  protected readonly tacticalGuidance = computed<TacticalGuidance>(() =>
    buildTacticalGuidance(
      this.gameView(),
      this.activeControlledBoard(),
      this.isMyTurn(),
      this.activeTurnPlayerName(),
    ),
  );
  private lastTurnBeaconKey: string | null = null;
  private turnBeaconTimer: ReturnType<typeof setTimeout> | null = null;
  protected readonly monsterSlots = [0, 1, 2, 3] as const;
  protected readonly familiarSlots = [0, 1, 2] as const;
  protected readonly spellSlots = [0, 1] as const;
  protected readonly handSlotIndices = [0, 1, 2, 3, 4, 5] as const;

  protected readonly relayTargetUserId = signal<string | null>(null);
  protected readonly summonPosture = signal<Posture>('ATTACK');
  protected readonly pendingAction = signal<PendingInteraction | null>(null);
  protected readonly selectedTargetIds = signal<string[]>([]);
  protected readonly pointerPos = signal<{ x: number; y: number } | null>(null);
  protected readonly inspectedCard = signal<CardViewDto | null>(null);
  protected readonly hoveredCard = signal<CardViewDto | null>(null);
  protected readonly displayedInspectorCard = computed<CardViewDto | null>(
    () => this.hoveredCard() ?? this.inspectedCard(),
  );
  protected readonly sidebarTab = signal<'LOG' | 'CHAT'>('LOG');
  protected readonly unreadChatCount = signal(0);
  protected readonly viewedDiscardBoard = signal<PlayerBoardViewDto | null>(null);
  protected readonly mobileDrawerOpen = signal(false);
  protected readonly mobileReaderOpen = signal(false);

  protected readonly mulliganSwappingSlots = signal<ReadonlySet<number>>(new Set());
  private readonly mulliganPendingPreviousIds = new Map<number, string>();
  private readonly mulliganLastKnownIds: string[] = [];
  protected readonly discardSelectedIds = signal<string[]>([]);
  protected readonly modalChoiceIndex = signal<number | null>(null);
  protected readonly modalChoiceTargetId = signal<string | null>(null);

  protected readonly gameChatInput = signal('');
  protected readonly teamOnlyChat = signal(false);

  protected readonly myTeamId = computed<TeamId>(() => {
    const view = this.gameView();
    if (!view || view.spectator) return 'TEAM_A';
    return view.players.find((p) => p.userId === view.viewerUserId)?.teamId ?? 'TEAM_A';
  });

  protected readonly activeControlledBoard = computed<PlayerBoardViewDto | null>(() => {
    const view = this.gameView();
    if (!view) return null;
    if (view.spectator) {
      return view.players.find((p) => p.teamId === 'TEAM_A') ?? view.players[0] ?? null;
    }
    const relay = this.relayTargetUserId();
    if (relay && view.controlledRelays.includes(relay)) {
      return view.players.find((p) => p.userId === relay) ?? null;
    }
    if (view.controlledRelays.includes(view.activeTurnPlayerId)) {
      return view.players.find((p) => p.userId === view.activeTurnPlayerId) ?? null;
    }
    return view.players.find((p) => p.userId === view.viewerUserId) ?? view.players[0] ?? null;
  });

  protected readonly partnerBoard = computed<PlayerBoardViewDto | null>(() => {
    const view = this.gameView();
    const controlled = this.activeControlledBoard();
    if (!view || !controlled) return null;
    return (
      view.players.find(
        (p) => p.teamId === controlled.teamId && p.userId !== controlled.userId,
      ) ?? null
    );
  });

  protected readonly opponentBoards = computed<PlayerBoardViewDto[]>(() => {
    const view = this.gameView();
    if (!view) return [];
    const team = this.myTeamId();
    return view.players.filter((p) => p.teamId !== team);
  });

  protected readonly activeTurnPlayerName = computed(() => {
    const view = this.gameView();
    if (!view) return '-';
    return view.players.find((p) => p.userId === view.activeTurnPlayerId)?.username ?? '-';
  });

  protected readonly isMyTurn = computed(() => {
    const view = this.gameView();
    const controlled = this.activeControlledBoard();
    if (!view || !controlled || view.spectator) return false;
    return view.activeTurnPlayerId === controlled.userId;
  });

  protected readonly remainingSeconds = computed(() => {
    const view = this.gameView();
    if (!view?.phaseDeadline) return 0;
    const diff = new Date(view.phaseDeadline).getTime() - this.nowMs();
    return Math.max(0, Math.ceil(diff / 1000));
  });

  protected readonly ropePercent = computed(() => {
    const view = this.gameView();
    if (!view) return 100;
    const maxSec =
      view.phase === 'WAITING_FOR_REACTION'
        ? 12
        : view.phase === 'MULLIGAN'
          ? 20
          : view.phase === 'WAITING_FOR_DISCARD'
            ? 15
            : view.mode === 'TEAM_2V2'
              ? 75
              : 60;
    return Math.min(100, Math.max(0, (this.remainingSeconds() / maxSec) * 100));
  });

  protected readonly reversedLog = computed(() => {
    const log = this.gameView()?.combatLog ?? [];
    return [...log].reverse();
  });

  protected readonly eligibleReactionCards = computed<CardViewDto[]>(() => {
    const view = this.gameView();
    const rw = view?.pendingReaction;
    if (!view || !rw) return [];
    const responderBoard = view.players.find((p) => p.userId === rw.currentResponderPlayerId);
    if (!responderBoard) return [];
    const ids = new Set(rw.eligibleReactiveInstanceIds);
    return [...responderBoard.spellArtifacts, ...responderBoard.familiars]
      .filter((c): c is CardViewDto => c !== null && ids.has(c.instanceId));
  });

  protected readonly modalChoiceTargets = computed<CardViewDto[]>(() => {
    const view = this.gameView();
    const pc = view?.pendingChoice;
    const idx = this.modalChoiceIndex();
    if (!view || !pc || idx === null) return [];
    const opt = pc.options[idx];
    const req = getTargetRequirementForTargetString(opt?.target);
    if (!req) return [];
    const ownerBoard = view.players.find((p) => p.userId === pc.ownerPlayerId);
    if (!ownerBoard) return [];
    return findEligibleTargetsForRequirement(view, ownerBoard, pc.sourceInstanceId, req.targetType);
  });

  protected readonly interactionPrompt = computed<string | null>(() => {
    const pending = this.pendingAction();
    if (!pending) return null;
    if (pending.kind === 'PICK_SLOT') {
      return `Choisissez un emplacement libre pour « ${pending.card.name} ».`;
    }
    return pending.requirement.label;
  });

  protected readonly pickingTarget = computed(() => {
    const pending = this.pendingAction();
    return pending?.kind === 'PICK_TARGET' ? pending : null;
  });

  protected readonly activeSourceInstanceId = computed<string | null>(() => {
    const pending = this.pendingAction();
    if (!pending) return null;
    return pending.kind === 'PICK_SLOT' ? pending.card.instanceId : pending.sourceInstanceId;
  });

  protected readonly inspectedBoardCreatureActions = computed<{
    card: CardViewDto;
    canAttack: boolean;
    canChangePosture: boolean;
    canActivateEffect: boolean;
  } | null>(() => {
    const view = this.gameView();
    const me = this.activeControlledBoard();
    const card = this.inspectedCard();
    if (!view || !me || !card || view.spectator || !this.isMyTurn() || this.pendingAction()) {
      return null;
    }
    const onMonster = me.monsters.some((m) => m?.instanceId === card.instanceId);
    const onFamiliar = me.familiars.some((f) => f?.instanceId === card.instanceId);
    const onArtifact =
      card.cardType === 'ARTIFACT' &&
      me.spellArtifacts.some((s) => s?.instanceId === card.instanceId);
    if (!onMonster && !onFamiliar && !onArtifact) {
      return null;
    }
    const canAttack =
      (onMonster || onFamiliar) && card.canAttackNow && !view.turn1TruceActive;
    const canChangePosture =
      (onMonster || onFamiliar) &&
      !card.changedPostureThisTurn &&
      view.phase === 'MAIN_PHASE';
    const canActivateEffect =
      this.hasActivatedAbility(card) && !card.activatedThisTurn && view.phase === 'MAIN_PHASE';
    return { card, canAttack, canChangePosture, canActivateEffect };
  });

  protected readonly directAttackStatus = computed<{ allowed: boolean; reason: string | null }>(() => {
    const view = this.gameView();
    const pending = this.pendingAction();
    if (!view || !pending || pending.kind !== 'PICK_TARGET' || pending.actionType !== 'DECLARE_ATTACK') {
      return { allowed: false, reason: null };
    }
    if (view.turn1TruceActive) {
      return { allowed: false, reason: 'Trêve du Tour 1 active' };
    }
    const me = this.activeControlledBoard();
    const attacker = me
      ? ([...me.monsters, ...me.familiars].find((c) => c?.instanceId === pending.sourceInstanceId) ?? null)
      : null;
    const hasSurvol = !!attacker?.keywords?.some((k) => k === 'SURVOL' || k === 'VOL');

    let guardianName: string | null = null;
    let blockingMonsterName: string | null = null;
    let hasFamiliar = false;
    for (const opp of this.opponentBoards()) {
      for (const m of opp.monsters) {
        if (m && !m.hidden && !m.camouflaged) {
          blockingMonsterName ??= m.name;
          if (m.keywords?.includes('GARDIEN')) {
            guardianName ??= m.name;
          }
        }
      }
      for (const f of opp.familiars) {
        if (f && !f.hidden && !f.camouflaged) {
          hasFamiliar = true;
          if (f.keywords?.includes('GARDIEN')) {
            guardianName ??= f.name;
          }
        }
      }
    }
    if (guardianName) {
      return { allowed: false, reason: `Gardien prioritaire : « ${guardianName} »` };
    }
    if (blockingMonsterName) {
      return {
        allowed: false,
        reason: hasSurvol
          ? `Bloqué par le Monstre « ${blockingMonsterName} » (Vol contourne uniquement les Familiers)`
          : `Bloqué par le Monstre « ${blockingMonsterName} » (Règle du Rempart)`,
      };
    }
    if (hasFamiliar && !hasSurvol) {
      return { allowed: false, reason: 'Bloqué par la Règle du Rempart (Familier adverse — requiert Survol)' };
    }
    return { allowed: true, reason: null };
  });

  protected readonly arrowPoints = computed<{ path: string } | null>(() => {
    const pending = this.pendingAction();
    const ptr = this.pointerPos();
    if (!pending || pending.kind !== 'PICK_TARGET' || !ptr) return null;
    const x1 = pending.originX;
    const y1 = pending.originY;
    const x2 = ptr.x;
    const y2 = ptr.y;
    const cx = (x1 + x2) / 2;
    const cy = Math.min(y1, y2) - 55;
    return { path: `M ${x1} ${y1} Q ${cx} ${cy} ${x2} ${y2}` };
  });

  constructor() {
    effect(() => {
      const view = this.gameView();
      const isModalWaitPhase =
        !view ||
        view.phase === 'MULLIGAN' ||
        view.phase === 'FINISHED' ||
        view.phase === 'WAITING_FOR_REACTION' ||
        view.phase === 'WAITING_FOR_MODAL_CHOICE' ||
        view.phase === 'WAITING_FOR_DISCARD';
      if (!view || view.spectator || isModalWaitPhase || !this.isMyTurn()) {
        if (this.turnBeaconTimer !== null && typeof window !== 'undefined') {
          window.clearTimeout(this.turnBeaconTimer);
          this.turnBeaconTimer = null;
        }
        this.turnAnnouncement.set(null);
        return;
      }
      if (view.phase !== 'MAIN_PHASE') {
        return;
      }
      const opponents = this.opponentBoards();
      if (opponents.length === 0) {
        return;
      }
      const turnKey = `${view.matchId}:${view.turnNumber}:${view.activeTurnPlayerId}`;
      if (turnKey === this.lastTurnBeaconKey) {
        return;
      }
      this.lastTurnBeaconKey = turnKey;
      if (this.turnBeaconTimer !== null && typeof window !== 'undefined') {
        window.clearTimeout(this.turnBeaconTimer);
        this.turnBeaconTimer = null;
      }
      const faction = this.activeControlledBoard()?.primaryFaction ?? null;
      const opponentName =
        opponents.length === 1 ? opponents[0].username : `${opponents.length} adversaires`;
      this.turnAnnouncement.set({
        key: turnKey,
        turnNumber: view.turnNumber,
        phrase: 'C’est à vous de jouer !',
        opponentName,
        faction,
      });
      untracked(() => {
        this.audio.playSfx('YOUR_TURN', faction);
        this.announcer.announce(`Tour ${view.turnNumber} : c’est à vous de jouer.`, 'polite');
      });
      if (typeof window !== 'undefined') {
        this.turnBeaconTimer = window.setTimeout(() => {
          this.turnAnnouncement.set(null);
          this.turnBeaconTimer = null;
        }, 2400);
      }
    });

    effect(() => {
      const view = this.gameView();
      const controlled = this.activeControlledBoard();
      if (!view) return;

      // Sync mandatory native top-layer <dialog>s cleanly
      const showMulligan =
        !view.spectator &&
        view.phase === 'MULLIGAN' &&
        !!controlled &&
        !controlled.mulliganCompleted &&
        (controlled.userId === view.viewerUserId ||
          view.controlledRelays.includes(controlled.userId));
      this.syncDialog(this.mulliganDialogRef()?.nativeElement, showMulligan);
      untracked(() => this.syncMulliganCardAnimations(controlled, view.phase));

      const rw = view.pendingReaction;
      const showReaction =
        !view.spectator &&
        view.phase === 'WAITING_FOR_REACTION' &&
        !!rw &&
        (rw.currentResponderPlayerId === view.viewerUserId ||
          view.controlledRelays.includes(rw.currentResponderPlayerId));
      this.syncDialog(this.reactionDialogRef()?.nativeElement, showReaction);

      const pc = view.pendingChoice;
      const showChoice =
        !view.spectator &&
        view.phase === 'WAITING_FOR_MODAL_CHOICE' &&
        !!pc &&
        (pc.ownerPlayerId === view.viewerUserId ||
          view.controlledRelays.includes(pc.ownerPlayerId));
      this.syncDialog(this.choiceDialogRef()?.nativeElement, showChoice);

      const showDiscard =
        !view.spectator &&
        view.phase === 'WAITING_FOR_DISCARD' &&
        !!controlled &&
        controlled.userId === view.activeTurnPlayerId &&
        controlled.hand.length > 6;
      this.syncDialog(this.discardDialogRef()?.nativeElement, showDiscard);

      this.syncDialog(this.finishedDialogRef()?.nativeElement, view.phase === 'FINISHED');

      const myTeam = this.myTeamId();
      const enemyTeam = myTeam === 'TEAM_A' ? view.teamB : view.teamA;
      const allyTeam = myTeam === 'TEAM_A' ? view.teamA : view.teamB;

      const prevVfxView = this.prevVfxGameView;
      this.prevVfxGameView = view;
      untracked(() => this.triggerArenaVfx(prevVfxView, view, myTeam));

      if (this.prevEnemyLp !== null && enemyTeam.lp !== this.prevEnemyLp) {
        pulseElementImpact(
          this.enemyLpBoxRef()?.nativeElement,
          enemyTeam.lp < this.prevEnemyLp ? 'damage' : 'heal',
        );
      }
      this.prevEnemyLp = enemyTeam.lp;

      if (this.prevAllyLp !== null && allyTeam.lp !== this.prevAllyLp) {
        pulseElementImpact(
          this.allyLpBoxRef()?.nativeElement,
          allyTeam.lp < this.prevAllyLp ? 'damage' : 'heal',
        );
      }
      this.prevAllyLp = allyTeam.lp;

      if (view.phase === 'FINISHED' && !view.spectator && !this.finishedAudioPlayed) {
        this.finishedAudioPlayed = true;
        untracked(() => {
          this.audio.stopMusic();
          this.audio.playSfx(view.winnerTeam === myTeam ? 'VICTORY' : 'DEFEAT');
        });
      }

      if (
        view.phase === 'FINISHED' &&
        !view.spectator &&
        view.winnerTeam === myTeam &&
        !this.celebratedMatchFinish
      ) {
        this.celebratedMatchFinish = true;
        void launchVictoryConfetti().then((stop) => {
          if (this.destroyed) {
            stop();
            return;
          }
          this.stopVictoryConfetti = stop;
        });
      }

      // Subtle GSAP kinetic feedback on board updates (only if reduced-motion not preferred)
      const latestSeq = view.combatLog.at(-1)?.sequence ?? 0;
      if (latestSeq > this.lastAnimatedLogSeq) {
        this.lastAnimatedLogSeq = latestSeq;
        this.animateLatestCombatPulse();
      }
    });

    effect(() => {
      const count = this.stomp.gameMessages().length;
      if (count < this.lastSeenChatCount) {
        this.lastSeenChatCount = count;
        this.unreadChatCount.set(0);
        return;
      }
      if (count > this.lastSeenChatCount) {
        const delta = count - this.lastSeenChatCount;
        this.lastSeenChatCount = count;
        if (this.sidebarTab() === 'CHAT') {
          this.unreadChatCount.set(0);
        } else {
          this.unreadChatCount.update((prev) => prev + delta);
        }
      }
    });
  }

  async ngOnInit(): Promise<void> {
    this.matchId = this.route.snapshot.paramMap.get('matchId') ?? '';
    const spectate = this.route.snapshot.queryParamMap.get('spectate') === '1';
    this.tutorialQueryActive.set(
      !spectate && this.route.snapshot.queryParamMap.get('tutorial') === '1',
    );
    this.audio.startMusic('COMBAT');

    const interval = setInterval(() => this.nowMs.set(Date.now()), 250);
    this.destroyRef.onDestroy(() => {
      this.destroyed = true;
      clearInterval(interval);
      this.clearVfxTimers();
      this.stopVictoryConfetti?.();
      this.audio.stopMusic();
      this.stomp.leaveMatchSubscription();
    });

    void this.loadCardCatalogForVfx();

    if (!this.matchId) return;
    this.stomp.clearLobbyNotification();
    this.stomp.connect();
    this.stomp.subscribeMatch(this.matchId, spectate);

    try {
      const state = await this.lobbyApi.getGameState(this.matchId);
      this.stomp.gameState.set(state);
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    }
  }

  protected phaseLabel(phase: GamePhase): string {
    return ARENA_PHASE_LABELS[phase].full;
  }

  protected async onCompleteTutorial(): Promise<void> {
    this.tutorialDismissedLocally.set(true);
    this.tutorialQueryActive.set(false);
    if (!this.auth || this.auth.user()?.tutorialCompleted === true) {
      this.announcer.pushToast('Didacticiel terminé ! Bonne conquête dans l’Arène.', 'success');
      return;
    }
    this.tutorialSaving.set(true);
    try {
      await this.auth.completeTutorial();
      this.announcer.pushToast('Initiation validée ! Bonne conquête dans l’Arène.', 'success');
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    } finally {
      this.tutorialSaving.set(false);
    }
  }

  protected async onDismissTutorial(): Promise<void> {
    this.tutorialDismissedLocally.set(true);
    this.tutorialQueryActive.set(false);
    if (!this.auth || this.auth.user()?.tutorialCompleted === true) {
      return;
    }
    try {
      await this.auth.completeTutorial();
      this.announcer.pushToast(
        'Didacticiel ignoré : vous pouvez le relancer depuis le Sanctuaire.',
        'info',
      );
    } catch {
      // Allow exiting tutorial cleanly even if offline persistence call fails
    }
  }

  protected async onFinishTutorialAndReturn(): Promise<void> {
    await this.onCompleteTutorial();
    this.returnToLobby();
  }

  protected shortPhaseLabel(phase: GamePhase): string {
    return ARENA_PHASE_LABELS[phase].short;
  }

  protected switchRelayControl(userId: string): void {
    this.relayTargetUserId.set(userId);
    this.cancelInteraction();
  }

  protected slotAt(cards: (CardViewDto | null)[], slot: number): CardViewDto | null {
    return (
      cards.find((card, idx) => card !== null && ((card.slot >= 0 ? card.slot : idx) === slot)) ?? null
    );
  }

  protected onBoardPointerMove(event: PointerEvent): void {
    if (this.pendingAction()?.kind === 'PICK_TARGET') {
      this.pointerPos.set({ x: event.clientX, y: event.clientY });
    }
  }

  protected inspectBoardCard(card: CardViewDto): void {
    if (!card.hidden) {
      this.inspectedCard.set(card);
      this.hoveredCard.set(null);
    }
  }

  protected previewCard(card: CardViewDto | null): void {
    if (!card) {
      this.hoveredCard.set(null);
      return;
    }
    if (!card.hidden) {
      this.hoveredCard.set(card);
    }
  }

  protected clearInspector(): void {
    this.hoveredCard.set(null);
    this.inspectedCard.set(null);
    this.mobileReaderOpen.set(false);
  }

  protected openMobileDrawer(tab?: 'LOG' | 'CHAT'): void {
    if (tab) {
      this.selectSidebarTab(tab);
    }
    this.mobileDrawerOpen.set(true);
  }

  protected closeMobileDrawer(): void {
    this.mobileDrawerOpen.set(false);
  }

  protected openMobileReader(card?: CardViewDto | null): void {
    if (card && !card.hidden) {
      this.inspectedCard.set(card);
    }
    this.mobileReaderOpen.set(true);
  }

  protected closeMobileReader(): void {
    this.mobileReaderOpen.set(false);
  }

  protected onEscapeKey(): void {
    if (this.mobileReaderOpen()) {
      this.mobileReaderOpen.set(false);
      return;
    }
    if (this.mobileDrawerOpen()) {
      this.mobileDrawerOpen.set(false);
      return;
    }
    if (this.pendingAction()) {
      this.cancelInteraction();
      return;
    }
    if (this.inspectedCard()) {
      this.clearInspector();
    }
  }

  protected selectSidebarTab(tab: 'LOG' | 'CHAT'): void {
    this.sidebarTab.set(tab);
    if (tab === 'CHAT') {
      this.unreadChatCount.set(0);
      this.lastSeenChatCount = this.stomp.gameMessages().length;
    }
  }

  protected onSidebarTabKeydown(event: KeyboardEvent): void {
    let nextTab: 'LOG' | 'CHAT' | null = null;
    if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') {
      nextTab = this.sidebarTab() === 'LOG' ? 'CHAT' : 'LOG';
    } else if (event.key === 'Home') {
      nextTab = 'LOG';
    } else if (event.key === 'End') {
      nextTab = 'CHAT';
    }
    if (!nextTab) return;
    event.preventDefault();
    this.selectSidebarTab(nextTab);
    const targetRef = nextTab === 'LOG' ? this.sidebarLogTabRef() : this.sidebarChatTabRef();
    targetRef?.nativeElement.focus();
  }

  protected cancelInteraction(): void {
    this.pendingAction.set(null);
    this.selectedTargetIds.set([]);
    this.pointerPos.set(null);
  }

  protected canPickBoardSlot(zone: 'MONSTER' | 'FAMILIAR' | 'SPELL'): boolean {
    const pending = this.pendingAction();
    if (!pending || pending.kind !== 'PICK_SLOT') return false;
    if (zone === 'MONSTER') return pending.mode === 'MONSTER';
    if (zone === 'FAMILIAR') return pending.mode === 'FAMILIAR';
    return pending.mode === 'SPELL' || pending.mode === 'REACTIVE';
  }

  protected setLogFilterMode(mode: 'HIGHLIGHTS' | 'ALL'): void {
    this.logFilterMode.set(mode);
  }

  protected factionIconName(faction: Faction | null | undefined): string {
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

  protected handCardPlayability(card: CardViewDto): HandCardPlayability {
    return evaluateHandCardPlayability(this.gameView(), this.activeControlledBoard(), card);
  }

  protected onOwnBoardCreatureClick(card: CardViewDto, event: MouseEvent): void {
    if (this.isAllyCreatureClickableTarget(card)) {
      this.onAllyCreatureTargetClick(card);
      return;
    }
    this.inspectBoardCard(card);
    const view = this.gameView();
    if (
      view &&
      !view.spectator &&
      this.isMyTurn() &&
      view.phase === 'COMBAT_PHASE' &&
      card.canAttackNow &&
      !view.turn1TruceActive
    ) {
      this.startAttackSelection(card, event);
    }
  }

  protected hasActivatedAbility(card: CardViewDto): boolean {
    return !!card.abilitiesScript?.some((a) => a.trigger === 'ACTIVATED');
  }

  protected onHandCardClick(card: CardViewDto, event: MouseEvent): void {
    this.inspectedCard.set(card);
    this.hoveredCard.set(null);
    const view = this.gameView();
    const me = this.activeControlledBoard();
    if (!view || !me || view.spectator || !this.isMyTurn() || view.phase !== 'MAIN_PHASE') {
      return;
    }
    const playability = this.handCardPlayability(card);
    if (!playability.playable) {
      if (playability.reason) {
        this.announcer.pushToast(playability.reason, 'error');
      }
      return;
    }

    const origin = this.elementCenter(event.currentTarget as HTMLElement);

    if (card.cardType === 'MONSTER') {
      if (card.epic && me.monsters.some((m) => m?.epic)) {
        this.announcer.pushToast("Maximum 1 Monstre Épique ★ sur votre terrain.", 'error');
        return;
      }
      this.pendingAction.set({ kind: 'PICK_SLOT', mode: 'MONSTER', card });
    } else if (card.cardType === 'FAMILIAR') {
      this.pendingAction.set({ kind: 'PICK_SLOT', mode: 'FAMILIAR', card });
    } else if (card.cardType === 'SPELL_REACTIVE') {
      this.pendingAction.set({ kind: 'PICK_SLOT', mode: 'REACTIVE', card });
    } else if (card.cardType === 'CURSE' || card.cardType === 'ARTIFACT') {
      this.pendingAction.set({ kind: 'PICK_SLOT', mode: 'SPELL', card });
    } else {
      // SPELL_NORMAL or SPELL_ULTIMATE
      const req = getTargetRequirementForTrigger(card.abilitiesScript, 'ON_PLAY');
      if (req) {
        const eligible = findEligibleTargetsForRequirement(view, me, card.instanceId, req.targetType);
        if (eligible.length > 0) {
          this.selectedTargetIds.set([]);
          this.pendingAction.set({
            kind: 'PICK_TARGET',
            actionType: 'PLAY_CARD',
            sourceInstanceId: card.instanceId,
            targetSlot: null,
            posture: null,
            requirement: req,
            maxTargets: req.maxTargets,
            eligibleIds: new Set(eligible.map((c) => c.instanceId)),
            originX: origin.x,
            originY: origin.y,
          });
          return;
        }
      }
      this.dispatchAction({
        actionType: 'PLAY_CARD',
        cardInstanceId: card.instanceId,
      });
    }
  }

  protected onSelectOwnSlot(zone: 'MONSTER' | 'FAMILIAR' | 'SPELL', slot: number): void {
    const pending = this.pendingAction();
    const view = this.gameView();
    const me = this.activeControlledBoard();
    if (!pending || pending.kind !== 'PICK_SLOT' || !view || !me) return;

    const card = pending.card;
    if (pending.mode === 'REACTIVE') {
      this.cancelInteraction();
      this.dispatchAction({
        actionType: 'SET_REACTIVE_SPELL',
        cardInstanceId: card.instanceId,
        targetSlot: slot,
      });
      return;
    }

    const posture: Posture | null =
      zone === 'MONSTER' || zone === 'FAMILIAR' ? this.summonPosture() : null;
    const req = getTargetRequirementForTrigger(card.abilitiesScript, 'ON_PLAY');
    if (req) {
      const eligible = findEligibleTargetsForRequirement(view, me, card.instanceId, req.targetType);
      if (eligible.length > 0) {
        this.selectedTargetIds.set([]);
        this.pendingAction.set({
          kind: 'PICK_TARGET',
          actionType: 'PLAY_CARD',
          sourceInstanceId: card.instanceId,
          targetSlot: slot,
          posture,
          requirement: req,
          maxTargets: req.maxTargets,
          eligibleIds: new Set(eligible.map((c) => c.instanceId)),
          originX: window.innerWidth / 2,
          originY: window.innerHeight * 0.72,
        });
        return;
      }
    }

    this.cancelInteraction();
    this.dispatchAction({
      actionType: 'PLAY_CARD',
      cardInstanceId: card.instanceId,
      targetSlot: slot,
      posture,
    });
  }

  protected setSummonPosture(posture: Posture): void {
    this.summonPosture.set(posture);
    this.audio.playSfx(posture === 'DEFENSE' ? 'POSTURE_DEFENSE' : 'POSTURE_ATTACK');
  }

  protected onChangePosture(card: CardViewDto): void {
    this.audio.playSfx(card.posture === 'ATTACK' ? 'POSTURE_DEFENSE' : 'POSTURE_ATTACK', card.faction);
    this.dispatchAction({
      actionType: 'CHANGE_POSTURE',
      cardInstanceId: card.instanceId,
    });
  }

  protected onActivateBoardEffect(card: CardViewDto, event: MouseEvent): void {
    const view = this.gameView();
    const me = this.activeControlledBoard();
    if (!view || !me) return;
    const req = getTargetRequirementForTrigger(card.abilitiesScript, 'ACTIVATED');
    if (req) {
      const eligible = findEligibleTargetsForRequirement(view, me, card.instanceId, req.targetType);
      if (eligible.length > 0) {
        const origin = this.elementCenter(event.currentTarget as HTMLElement);
        this.selectedTargetIds.set([]);
        this.pendingAction.set({
          kind: 'PICK_TARGET',
          actionType: 'ACTIVATE_EFFECT',
          sourceInstanceId: card.instanceId,
          targetSlot: null,
          posture: null,
          requirement: req,
          maxTargets: req.maxTargets,
          eligibleIds: new Set(eligible.map((c) => c.instanceId)),
          originX: origin.x,
          originY: origin.y,
        });
        return;
      }
    }
    this.dispatchAction({
      actionType: 'ACTIVATE_EFFECT',
      cardInstanceId: card.instanceId,
    });
  }

  protected startAttackSelection(attacker: CardViewDto, event: MouseEvent): void {
    const view = this.gameView();
    if (!view) return;
    const origin = this.elementCenter(event.currentTarget as HTMLElement);

    const oppCreatures: CardViewDto[] = [];
    for (const opp of this.opponentBoards()) {
      for (const m of opp.monsters) {
        if (m && !m.hidden && !m.camouflaged) oppCreatures.push(m);
      }
      for (const f of opp.familiars) {
        if (f && !f.hidden && !f.camouflaged) oppCreatures.push(f);
      }
    }
    const guardians = oppCreatures.filter((c) => c.keywords?.includes('GARDIEN'));
    const eligible = guardians.length > 0 ? guardians : oppCreatures;

    this.selectedTargetIds.set([]);
    this.pendingAction.set({
      kind: 'PICK_TARGET',
      actionType: 'DECLARE_ATTACK',
      sourceInstanceId: attacker.instanceId,
      targetSlot: null,
      posture: null,
      requirement: {
        targetType: 'COMBAT',
        maxTargets: 1,
        optional: false,
        label: `Attaque de ${attacker.name} : cliquez une créature cible ou Attaque Directe`,
      },
      maxTargets: 1,
      eligibleIds: new Set(eligible.map((c) => c.instanceId)),
      originX: origin.x,
      originY: origin.y,
    });
  }

  protected isCreatureClickableTarget(card: CardViewDto): boolean {
    const p = this.pendingAction();
    return !!p && p.kind === 'PICK_TARGET' && p.eligibleIds.has(card.instanceId);
  }

  protected isAllyCreatureClickableTarget(card: CardViewDto): boolean {
    const p = this.pendingAction();
    return !!p && p.kind === 'PICK_TARGET' && p.eligibleIds.has(card.instanceId);
  }

  protected onOpponentCreatureClick(card: CardViewDto): void {
    this.handleTargetCardClick(card);
  }

  protected onAllyCreatureTargetClick(card: CardViewDto): void {
    this.handleTargetCardClick(card);
  }

  protected onSelectDirectAttack(): void {
    const p = this.pendingAction();
    if (!p || p.kind !== 'PICK_TARGET' || p.actionType !== 'DECLARE_ATTACK') return;
    this.cancelInteraction();
    this.dispatchAction({
      actionType: 'DECLARE_ATTACK',
      cardInstanceId: p.sourceInstanceId,
      targetId: 'DIRECT_PLAYER',
    });
  }

  protected confirmMultiTarget(): void {
    const p = this.pendingAction();
    const chosen = this.selectedTargetIds();
    if (!p || p.kind !== 'PICK_TARGET' || chosen.length === 0) return;
    this.cancelInteraction();
    this.dispatchAction({
      actionType: p.actionType,
      cardInstanceId: p.sourceInstanceId,
      targetSlot: p.targetSlot,
      posture: p.posture,
      targetId: chosen[0] ?? null,
      secondaryTargetId: chosen[1] ?? null,
    });
  }

  protected isMulliganSlotReplaced(slotIdx: number): boolean {
    const slots = this.activeControlledBoard()?.mulliganReplacedSlots;
    return !!slots && slots.includes(slotIdx);
  }

  protected replaceMulliganCard(slotIdx: number, card: CardViewDto): void {
    const view = this.gameView();
    const me = this.activeControlledBoard();
    if (!view || !me || view.phase !== 'MULLIGAN' || me.mulliganCompleted) {
      return;
    }
    if (this.isMulliganSlotReplaced(slotIdx) || this.mulliganSwappingSlots().has(slotIdx)) {
      return;
    }
    this.mulliganPendingPreviousIds.set(slotIdx, card.instanceId);
    this.mulliganSwappingSlots.update((prev) => {
      const next = new Set(prev);
      next.add(slotIdx);
      return next;
    });
    const slotEl = this.mulliganCardSlots()[slotIdx]?.nativeElement;
    this.audio.playSfx('SPELL_CAST', card.faction);
    this.trackTimelineCleanup(animateMulliganCardOut(slotEl));
    this.dispatchAction({
      actionType: 'MULLIGAN_REPLACE_CARD',
      cardInstanceId: card.instanceId,
    });
    this.scheduleDelayedTimer(2500, () => {
      if (this.mulliganSwappingSlots().has(slotIdx)) {
        this.mulliganSwappingSlots.update((prev) => {
          const next = new Set(prev);
          next.delete(slotIdx);
          return next;
        });
        this.mulliganPendingPreviousIds.delete(slotIdx);
        const el = this.mulliganCardSlots()[slotIdx]?.nativeElement;
        if (el) {
          gsap.set(el, { clearProps: 'transform,opacity' });
        }
      }
    });
  }

  protected confirmMulligan(): void {
    this.mulliganSwappingSlots.set(new Set());
    this.mulliganPendingPreviousIds.clear();
    this.dispatchAction({
      actionType: 'MULLIGAN_SWAP',
      instanceIds: [],
    });
  }

  private syncMulliganCardAnimations(me: PlayerBoardViewDto | null, phase: GamePhase): void {
    if (phase !== 'MULLIGAN' || !me || me.mulliganCompleted) {
      this.mulliganPendingPreviousIds.clear();
      this.mulliganLastKnownIds.length = 0;
      if (this.mulliganSwappingSlots().size > 0) {
        this.mulliganSwappingSlots.set(new Set());
      }
      return;
    }

    const replacedSlots = new Set(me.mulliganReplacedSlots ?? []);
    const animatedSlots: { slotIdx: number; cardName: string }[] = [];

    for (let i = 0; i < me.hand.length; i++) {
      const card = me.hand[i];
      if (!card) continue;
      const pendingPrev = this.mulliganPendingPreviousIds.get(i);
      const prevKnown = this.mulliganLastKnownIds[i];
      const changed =
        (pendingPrev !== undefined && card.instanceId !== pendingPrev) ||
        (prevKnown !== undefined && card.instanceId !== prevKnown && replacedSlots.has(i));
      if (changed) {
        this.mulliganPendingPreviousIds.delete(i);
        if (this.mulliganSwappingSlots().has(i)) {
          this.mulliganSwappingSlots.update((prev) => {
            const next = new Set(prev);
            next.delete(i);
            return next;
          });
        }
        animatedSlots.push({ slotIdx: i, cardName: card.name ?? 'Carte' });
      }
      this.mulliganLastKnownIds[i] = card.instanceId;
    }
    this.mulliganLastKnownIds.length = me.hand.length;

    if (animatedSlots.length > 0 && typeof window !== 'undefined') {
      const rafId = window.requestAnimationFrame(() => {
        this.activeAnimationFrames.delete(rafId);
        if (this.destroyed) return;
        const refs = this.mulliganCardSlots();
        for (const item of animatedSlots) {
          const el = refs[item.slotIdx]?.nativeElement;
          this.trackTimelineCleanup(animateMulliganCardIn(el));
        }
      });
      this.activeAnimationFrames.add(rafId);
      const last = animatedSlots[animatedSlots.length - 1];
      this.announcer.announce(`Nouvelle carte piochée : ${last.cardName}.`, 'polite');
    }
  }

  protected getReactiveModalOptions(card: CardViewDto): AbilityOption[] {
    const reactive = card.abilitiesScript?.find((a) => a.trigger === 'REACTIVE');
    return reactive?.action === 'MODAL_CHOICE' ? (reactive.options ?? []) : [];
  }

  protected triggerReactionCard(
    cardInstanceId: string,
    choiceIndex: number | null,
    targetId: string | null,
  ): void {
    this.dispatchAction({
      actionType: 'TRIGGER_REACTION',
      cardInstanceId,
      choiceIndex,
      targetId,
    });
  }

  protected passReaction(): void {
    this.dispatchAction({ actionType: 'PASS_REACTION' });
  }

  protected selectModalOption(idx: number): void {
    this.modalChoiceIndex.set(idx);
    this.modalChoiceTargetId.set(null);
  }

  protected confirmModalChoice(): void {
    const idx = this.modalChoiceIndex();
    const targetId = this.modalChoiceTargetId();
    if (idx === null) return;
    this.modalChoiceIndex.set(null);
    this.modalChoiceTargetId.set(null);
    this.dispatchAction({
      actionType: 'RESOLVE_CHOICE',
      choiceIndex: idx,
      targetId,
    });
  }

  protected toggleDiscardExcessCard(instanceId: string, requiredCount: number): void {
    this.discardSelectedIds.update((ids) => {
      if (ids.includes(instanceId)) {
        return ids.filter((id) => id !== instanceId);
      }
      if (ids.length >= requiredCount) {
        return [...ids.slice(1), instanceId];
      }
      return [...ids, instanceId];
    });
  }

  protected confirmDiscardExcess(): void {
    const ids = this.discardSelectedIds();
    this.discardSelectedIds.set([]);
    this.dispatchAction({
      actionType: 'DISCARD_EXCESS',
      instanceIds: ids,
    });
  }

  protected sendSimpleAction(actionType: GameActionType): void {
    this.cancelInteraction();
    this.dispatchAction({ actionType });
  }

  protected openForfeitDialog(): void {
    this.forfeitDialogRef()?.nativeElement.showModal();
  }

  protected closeForfeitDialog(): void {
    this.forfeitDialogRef()?.nativeElement.close();
  }

  protected confirmForfeit(): void {
    this.closeForfeitDialog();
    this.sendSimpleAction('FORFEIT');
  }

  protected async returnToLobby(): Promise<void> {
    this.finishedDialogRef()?.nativeElement.close();
    this.stomp.clearLobbyNotification();
    this.stomp.leaveMatchSubscription();
    try {
      const navigated = await this.router.navigate(['/lobby']);
      if (!navigated && typeof window !== 'undefined') {
        window.location.assign('/lobby');
      }
    } catch {
      if (typeof window !== 'undefined') {
        window.location.assign('/lobby');
      }
    }
  }

  protected openDiscardViewer(board: PlayerBoardViewDto): void {
    this.viewedDiscardBoard.set(board);
    this.discardViewerDialogRef()?.nativeElement.showModal();
  }

  protected closeDiscardViewer(): void {
    this.discardViewerDialogRef()?.nativeElement.close();
  }

  protected onGameChatInput(event: Event): void {
    this.gameChatInput.set((event.target as HTMLInputElement).value);
  }

  protected onTeamChatToggle(event: Event): void {
    this.teamOnlyChat.set((event.target as HTMLInputElement).checked);
  }

  protected onSendGameChat(event: SubmitEvent): void {
    event.preventDefault();
    const text = this.gameChatInput().trim();
    if (!text || !this.matchId) return;
    if (this.stomp.sendGameChat(this.matchId, text, this.teamOnlyChat())) {
      this.gameChatInput.set('');
    }
  }

  private handleTargetCardClick(card: CardViewDto): void {
    const p = this.pendingAction();
    if (!p || p.kind !== 'PICK_TARGET' || !p.eligibleIds.has(card.instanceId)) {
      this.inspectBoardCard(card);
      return;
    }

    if (p.maxTargets === 2) {
      this.selectedTargetIds.update((list) =>
        list.includes(card.instanceId)
          ? list.filter((id) => id !== card.instanceId)
          : [...list.slice(-1), card.instanceId],
      );
      if (this.selectedTargetIds().length === 2) {
        this.confirmMultiTarget();
      }
      return;
    }

    this.cancelInteraction();
    this.dispatchAction({
      actionType: p.actionType,
      cardInstanceId: p.sourceInstanceId,
      targetSlot: p.targetSlot,
      posture: p.posture,
      targetId: card.instanceId,
    });
  }

  private dispatchAction(partial: {
    actionType: GameActionType;
    cardInstanceId?: string | null;
    targetSlot?: number | null;
    posture?: Posture | null;
    targetId?: string | null;
    secondaryTargetId?: string | null;
    choiceIndex?: number | null;
    instanceIds?: string[] | null;
  }): void {
    const view = this.gameView();
    const acting = this.activeControlledBoard();
    if (!view || !this.matchId) return;

    const actingAsPlayerId =
      acting && acting.userId !== view.viewerUserId ? acting.userId : null;

    this.stomp.sendGameAction({
      matchId: this.matchId,
      actingAsPlayerId,
      actionType: partial.actionType,
      cardInstanceId: partial.cardInstanceId ?? null,
      targetSlot: partial.targetSlot ?? null,
      posture: partial.posture ?? null,
      targetId: partial.targetId ?? null,
      secondaryTargetId: partial.secondaryTargetId ?? null,
      choiceIndex: partial.choiceIndex ?? null,
      instanceIds: partial.instanceIds ?? null,
    });
  }

  private elementCenter(el: HTMLElement): { x: number; y: number } {
    const r = el.getBoundingClientRect();
    return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
  }

  private syncDialog(dialog: HTMLDialogElement | undefined, shouldOpen: boolean): void {
    if (!dialog) return;
    if (shouldOpen && !dialog.open) {
      dialog.showModal();
    } else if (!shouldOpen && dialog.open) {
      dialog.close();
    }
  }

  protected slotBurstFor(playerId: string, zone: SlotZone, slotIndex: number): SlotVfxEvent | null {
    return this.slotBursts()[slotBurstKey(playerId, zone, slotIndex)] ?? null;
  }

  protected avatarBurstFor(playerId: string): AvatarImpactEvent | null {
    return this.avatarBursts()[playerId] ?? null;
  }

  protected waveEffectIconName(effect: CardEffectVisualKind): string {
    switch (effect) {
      case 'DAMAGE':
        return 'lucideFlame';
      case 'DESTROY':
      case 'CURSE':
        return 'lucideSkull';
      case 'SHIELD':
      case 'REACTIVE':
        return 'lucideShield';
      case 'ATK_BUFF':
        return 'lucideSwords';
      case 'SLOW':
        return 'lucideSnowflake';
      case 'ENERGY':
        return 'lucideZap';
      case 'STEALTH':
        return 'lucideEye';
      default:
        return 'lucideSparkles';
    }
  }

  private async loadCardCatalogForVfx(): Promise<void> {
    try {
      const cards = await this.lobbyApi.listCards();
      const map = new Map<string, CardDefinition>();
      for (const card of cards) {
        map.set(card.name.toLowerCase(), card);
      }
      this.catalogByName = map;
    } catch {
      // Fall back gracefully to live CardViewDto fields already embedded in gameState.
    }
  }

  private triggerArenaVfx(
    prevView: PlayerGameView | null,
    nextView: PlayerGameView,
    myTeam: TeamId,
  ): void {
    if (this.destroyed) {
      return;
    }
    const batch = extractArenaVfxEvents(prevView, nextView, this.catalogByName);
    const reduced = prefersReducedMotion();
    this.triggerBatchAudio(batch, reduced);

    if (
      (batch.projectiles.length > 0 ||
        batch.summonEvents.length > 0 ||
        batch.shatterEvents.length > 0 ||
        batch.popups.length > 0 ||
        batch.spellWaves.length > 0) &&
      typeof window !== 'undefined'
    ) {
      const rafId = window.requestAnimationFrame(() => {
        this.activeAnimationFrames.delete(rafId);
        if (this.destroyed) return;
        if (batch.projectiles.length > 0) {
          this.spawnProjectiles(batch.projectiles, myTeam, reduced);
        }
        if (batch.summonEvents.length > 0) {
          this.triggerSummonSlams(batch.summonEvents, reduced);
        }
        if (batch.shatterEvents.length > 0) {
          this.triggerShatterBursts(batch.shatterEvents, reduced);
        }
        if (batch.popups.length > 0) {
          this.triggerCombatPopups(batch.popups, reduced);
        }
        if (batch.spellWaves.length > 0) {
          this.triggerSpellCanvasBursts(batch.spellWaves, batch.slotEvents, reduced);
        }
      });
      this.activeAnimationFrames.add(rafId);
    }

    for (const evt of batch.slotEvents) {
      this.scheduleSlotBurst(evt, reduced);
    }
    for (const avEvt of batch.avatarImpacts) {
      this.scheduleAvatarImpact(avEvt, reduced);
    }
    for (const lpEvt of batch.lpEvents) {
      this.scheduleLpBurst(lpEvt, myTeam, reduced);
    }
    const showcase = batch.mythicShowcases.at(-1);
    const wave = batch.spellWaves.at(-1);
    if (showcase && nextView.phase !== 'MULLIGAN' && nextView.phase !== 'FINISHED') {
      this.scheduleMythicShowcase(showcase);
    } else if (wave) {
      this.scheduleSpellWave(wave);
    }
  }

  private triggerBatchAudio(batch: ArenaVfxBatch, reduced: boolean): void {
    const impactDelay = reduced ? 0 : PROJECTILE_IMPACT_DELAY_MS;
    for (const proj of batch.projectiles) {
      this.audio.playSfx('ATTACK_SWING', proj.faction);
      this.scheduleDelayedTimer(impactDelay, () => {
        if (
          proj.outcome === 'DEFENSE_BLOCKED' ||
          proj.outcome === 'ATTACKER_REPELLED' ||
          proj.outcome === 'DIRECT_BLOCKED'
        ) {
          this.audio.playSfx('DEFENSE_BLOCK', proj.faction);
        } else if (proj.outcome === 'DEFENSE_BROKEN') {
          this.audio.playSfx('DEFENSE_BROKEN', proj.faction);
        } else if (proj.outcome === 'DIRECT_HIT') {
          this.audio.playSfx('DIRECT_HIT', proj.faction);
        } else if (proj.hitStrength === 'CRITICAL') {
          this.audio.playSfx('HIT_CRITICAL', proj.faction);
        } else if (proj.hitStrength === 'HEAVY') {
          this.audio.playSfx('HIT_HEAVY', proj.faction);
        } else {
          this.audio.playSfx('HIT_LIGHT', proj.faction);
        }
      });
    }

    for (const sum of batch.summonEvents) {
      this.audio.playSfx(sum.tier === 'MYTHIC' ? 'SUMMON_MYTHIC' : 'SUMMON_STANDARD', sum.faction);
    }

    for (const shat of batch.shatterEvents) {
      const shatDelay = reduced ? 0 : shat.delayMs;
      if (shatDelay > 0) {
        this.scheduleDelayedTimer(shatDelay, () => this.audio.playSfx('CREATURE_DESTROY', shat.faction));
      } else {
        this.audio.playSfx('CREATURE_DESTROY', shat.faction);
      }
    }

    if (batch.projectiles.length === 0) {
      const slotEvt = batch.slotEvents.find(
        (evt) => evt.kind !== 'SUMMON' && evt.kind !== 'DESTROY',
      );
      if (slotEvt) {
        const sfx =
          slotEvt.kind === 'DEFENSE_BLOCK'
            ? 'DEFENSE_BLOCK'
            : slotEvt.kind === 'DEFENSE_BROKEN'
              ? 'DEFENSE_BROKEN'
              : slotEvt.kind === 'SHIELD' || slotEvt.kind === 'REACTIVE'
                ? 'SHIELD_BUFF'
                : slotEvt.kind === 'HEAL'
                  ? 'HEAL'
                  : 'SPELL_CAST';
        this.audio.playSfx(sfx, slotEvt.faction);
      } else if (batch.spellWaves.length > 0) {
        const wave = batch.spellWaves[batch.spellWaves.length - 1];
        this.audio.playSfx(wave.effect === 'SHIELD' ? 'SHIELD_BUFF' : 'SPELL_CAST', wave.faction);
      }
    }
  }

  private scheduleDelayedTimer(delayMs: number, callback: () => void): void {
    const timer = window.setTimeout(() => {
      this.activeVfxTimers.delete(timer);
      if (!this.destroyed) {
        callback();
      }
    }, delayMs);
    this.activeVfxTimers.add(timer);
  }

  private scheduleSlotBurst(evt: SlotVfxEvent, reduced: boolean): void {
    const startDelay = reduced ? 0 : (evt.delayMs ?? 0);
    const activate = () => {
      this.slotBursts.update((prev) => ({ ...prev, [evt.key]: evt }));
      this.scheduleDelayedTimer(evt.durationMs, () => {
        this.slotBursts.update((prev) => {
          if (prev[evt.key]?.id !== evt.id) {
            return prev;
          }
          const next = { ...prev };
          delete next[evt.key];
          return next;
        });
      });
    };

    if (startDelay > 0) {
      this.scheduleDelayedTimer(startDelay, activate);
    } else {
      activate();
    }
  }

  private scheduleAvatarImpact(evt: AvatarImpactEvent, reduced: boolean): void {
    const startDelay = reduced ? 0 : (evt.delayMs ?? 0);
    const activate = () => {
      this.avatarBursts.update((prev) => ({ ...prev, [evt.playerId]: evt }));
      this.scheduleDelayedTimer(evt.durationMs, () => {
        this.avatarBursts.update((prev) => {
          if (prev[evt.playerId]?.id !== evt.id) {
            return prev;
          }
          const next = { ...prev };
          delete next[evt.playerId];
          return next;
        });
      });
    };

    if (startDelay > 0) {
      this.scheduleDelayedTimer(startDelay, activate);
    } else {
      activate();
    }
  }

  private scheduleLpBurst(evt: LpVfxEvent, myTeam: TeamId, reduced: boolean): void {
    const startDelay = reduced ? 0 : (evt.delayMs ?? 0);
    const targetSignal = evt.teamId === myTeam ? this.allyLpBurst : this.enemyLpBurst;
    const activate = () => {
      targetSignal.set(evt);
      this.scheduleDelayedTimer(evt.durationMs, () => {
        if (targetSignal()?.id === evt.id) {
          targetSignal.set(null);
        }
      });
    };

    if (startDelay > 0) {
      this.scheduleDelayedTimer(startDelay, activate);
    } else {
      activate();
    }
  }

  private emitCanvasBurst(burst: CanvasImpactBurst, delayMs = 0): void {
    const activate = () => {
      this.canvasBursts.update((prev) => [...prev, burst]);
      this.scheduleDelayedTimer(920, () => {
        this.canvasBursts.update((prev) => prev.filter((item) => item.id !== burst.id));
      });
    };
    if (delayMs > 0) {
      this.scheduleDelayedTimer(delayMs, activate);
    } else {
      activate();
    }
  }

  private triggerBoardVignette(tint: Faction | 'CRITICAL_HIT'): void {
    this.boardImpactTint.set(null);
    if (typeof window !== 'undefined') {
      this.scheduleDelayedTimer(16, () => {
        this.boardImpactTint.set(tint);
        this.scheduleDelayedTimer(480, () => {
          if (this.boardImpactTint() === tint) {
            this.boardImpactTint.set(null);
          }
        });
      });
    }
  }

  private scheduleMythicShowcase(showcase: MythicShowcaseBanner): void {
    if (this.turnAnnouncement()) {
      this.turnAnnouncement.set(null);
    }
    this.mythicShowcase.set(showcase);
    this.scheduleDelayedTimer(showcase.durationMs, () => {
      if (this.mythicShowcase()?.id === showcase.id) {
        this.mythicShowcase.set(null);
      }
    });
  }

  private trackTimelineCleanup(cleanup: () => void): void {
    this.activeTimelineCleanups.add(cleanup);
    this.scheduleDelayedTimer(650, () => {
      this.activeTimelineCleanups.delete(cleanup);
    });
  }

  private collectBoardSlotMap(container: HTMLElement): Map<string, HTMLElement> {
    const slotMap = new Map<string, HTMLElement>();
    for (const el of Array.from(container.querySelectorAll<HTMLElement>('[data-board-slot]'))) {
      const key = el.getAttribute('data-board-slot');
      if (key) slotMap.set(key, el);
    }
    return slotMap;
  }

  private collectPlayerAvatarMap(container: HTMLElement): Map<string, HTMLElement> {
    const avatarMap = new Map<string, HTMLElement>();
    for (const el of Array.from(container.querySelectorAll<HTMLElement>('[data-player-avatar]'))) {
      const id = el.getAttribute('data-player-avatar');
      if (id) avatarMap.set(id, el);
    }
    return avatarMap;
  }

  private triggerCombatPopups(requests: readonly CombatPopupRequest[], reduced: boolean): void {
    const container = this.arenaPlayColRef()?.nativeElement;
    if (!container) return;

    const containerRect = container.getBoundingClientRect();
    const slotMap = this.collectBoardSlotMap(container);
    const avatarMap = this.collectPlayerAvatarMap(container);

    requests.forEach((req, idx) => {
      const targetEl =
        req.targetKind === 'SLOT' ? slotMap.get(req.key) : avatarMap.get(req.key);
      if (!targetEl) return;

      const center = this.elementCenter(targetEl);
      const jitterX = req.targetKind === 'SLOT' ? ((idx % 3) - 1) * 14 : 0;
      const popup: FloatingCombatPopup = {
        id: req.id,
        x: center.x - containerRect.left + jitterX,
        y: Math.max(28, center.y - containerRect.top - (req.targetKind === 'SLOT' ? 14 : 6)),
        headline: req.headline,
        sublabel: req.sublabel,
        tone: req.tone,
        faction: req.faction,
        tiltDeg: ((idx % 2) === 0 ? -1 : 1) * (req.tone === 'CRIT' || req.tone === 'DIRECT' ? 4 : 2),
      };
      const launchDelay = reduced ? 0 : req.delayMs;
      const pushPopup = () => {
        this.floatingPopups.update((prev) => [...prev, popup]);
        this.scheduleDelayedTimer(1020, () => {
          this.floatingPopups.update((prev) => prev.filter((item) => item.id !== popup.id));
        });
      };

      if (launchDelay > 0) {
        this.scheduleDelayedTimer(launchDelay, pushPopup);
      } else {
        pushPopup();
      }
    });
  }

  private triggerSpellCanvasBursts(
    waves: readonly SpellWaveBanner[],
    slotEvents: readonly SlotVfxEvent[],
    reduced: boolean,
  ): void {
    if (reduced) return;
    const container = this.arenaPlayColRef()?.nativeElement;
    if (!container) return;

    const containerRect = container.getBoundingClientRect();
    const slotMap = this.collectBoardSlotMap(container);
    const avatarMap = this.collectPlayerAvatarMap(container);
    const wave = waves.at(-1);
    if (!wave) return;

    const targetSlots = slotEvents.filter(
      (evt) =>
        (evt.delayMs ?? 0) === 0 &&
        (evt.kind === 'DAMAGE' || evt.kind === 'DESTROY' || evt.kind === 'ATK_BUFF' || evt.kind === 'SHIELD' || evt.kind === 'SLOW'),
    );

    if (targetSlots.length > 0) {
      for (const evt of targetSlots) {
        const el = slotMap.get(evt.key);
        if (!el) continue;
        const center = this.elementCenter(el);
        this.emitCanvasBurst({
          id: `${wave.id}-${evt.key}`,
          kind: 'SPELL_BURST',
          x: center.x - containerRect.left,
          y: center.y - containerRect.top,
          faction: wave.faction,
          power: evt.kind === 'DESTROY' ? 4 : 3,
        });
      }
      if (wave.effect === 'DAMAGE' || wave.effect === 'DESTROY') {
        this.trackTimelineCleanup(animateBoardShake(container, 'light'));
      }
      return;
    }

    const actorEl = wave.actorPlayerId ? avatarMap.get(wave.actorPlayerId) : undefined;
    const origin = actorEl
      ? this.elementCenter(actorEl)
      : { x: containerRect.left + containerRect.width * 0.5, y: containerRect.top + containerRect.height * 0.45 };
    this.emitCanvasBurst({
      id: `${wave.id}-cast`,
      kind: 'SPELL_BURST',
      x: origin.x - containerRect.left,
      y: origin.y - containerRect.top,
      faction: wave.faction,
      power: 3,
    });
  }

  private triggerSummonSlams(summons: readonly SummonImpactEvent[], reduced: boolean): void {
    const container = this.arenaPlayColRef()?.nativeElement;
    if (!container) return;
    const containerRect = container.getBoundingClientRect();
    const cWidth = Math.max(containerRect.width, 1);
    const cHeight = Math.max(containerRect.height, 1);
    const slotMap = this.collectBoardSlotMap(container);

    for (const sum of summons) {
      const slotEl = slotMap.get(sum.key);
      if (!slotEl) continue;
      const cardEl = (slotEl.querySelector(':scope > button') as HTMLElement | null) ?? slotEl;
      const center = this.elementCenter(slotEl);
      const x = center.x - containerRect.left;
      const y = center.y - containerRect.top;

      if (!reduced) {
        this.trackTimelineCleanup(animateCardSummonSlam(cardEl, sum.tier));
        const burstDelay = sum.tier === 'MYTHIC' ? 140 : 40;
        const kind =
          sum.tier === 'MYTHIC'
            ? 'SUMMON_MYTHIC'
            : sum.tier === 'HEAVY'
              ? 'SUMMON_HEAVY'
              : 'SUMMON_STANDARD';
        this.emitCanvasBurst({ id: sum.id, kind, x, y, faction: sum.faction, power: sum.energyCost }, burstDelay);

        if (sum.tier === 'MYTHIC') {
          this.scheduleDelayedTimer(140, () => {
            this.trackTimelineCleanup(animateBoardShake(container, 'heavy'));
            this.threeCombatPulse.set({
              id: sum.id,
              faction: sum.faction,
              kind: 'SUMMON_MYTHIC',
              nx: (x / cWidth - 0.5) * 2,
              ny: (0.5 - y / cHeight) * 2,
            });
            this.triggerBoardVignette(sum.faction);
          });
        }
      }
    }
  }

  private triggerShatterBursts(shatters: readonly CombatShatterEvent[], reduced: boolean): void {
    if (reduced) return;
    const container = this.arenaPlayColRef()?.nativeElement;
    if (!container) return;
    const containerRect = container.getBoundingClientRect();
    const slotMap = this.collectBoardSlotMap(container);
    for (const shat of shatters) {
      const slotEl = slotMap.get(shat.key);
      if (!slotEl) continue;
      const center = this.elementCenter(slotEl);
      this.emitCanvasBurst(
        {
          id: shat.id,
          kind: 'SHATTER',
          x: center.x - containerRect.left,
          y: center.y - containerRect.top,
          faction: shat.faction,
          power: 3,
        },
        shat.delayMs,
      );
    }
  }

  private spawnProjectiles(
    requests: readonly VisualProjectileRequest[],
    myTeam: TeamId,
    reduced: boolean,
  ): void {
    const container = this.arenaPlayColRef()?.nativeElement;
    if (!container) return;

    const containerRect = container.getBoundingClientRect();
    const cWidth = Math.max(containerRect.width, 1);
    const cHeight = Math.max(containerRect.height, 1);
    const slotMap = this.collectBoardSlotMap(container);
    const avatarMap = this.collectPlayerAvatarMap(container);

    const activeUserId = this.activeControlledBoard()?.userId ?? null;
    const launched: ActiveProjectile[] = [];
    for (const req of requests) {
      const sourceEl =
        (req.sourcePlayerId && req.sourceZone && req.sourceSlot >= 0
          ? slotMap.get(slotBurstKey(req.sourcePlayerId, req.sourceZone, req.sourceSlot))
          : undefined) ??
        (req.sourcePlayerId ? avatarMap.get(req.sourcePlayerId) : undefined);

      const fallbackLpEl =
        req.targetPlayerId &&
          this.gameView()?.players.find((p) => p.userId === req.targetPlayerId)?.teamId === myTeam
          ? this.allyLpBoxRef()?.nativeElement
          : this.enemyLpBoxRef()?.nativeElement;

      const targetEl =
        (!req.directAttack && req.targetPlayerId && req.targetZone && req.targetSlot >= 0
          ? slotMap.get(slotBurstKey(req.targetPlayerId, req.targetZone, req.targetSlot))
          : undefined) ??
        (req.targetPlayerId ? avatarMap.get(req.targetPlayerId) : undefined) ??
        fallbackLpEl;

      if (!sourceEl || !targetEl) {
        continue;
      }

      const start = this.elementCenter(sourceEl);
      const end = this.elementCenter(targetEl);
      const startX = start.x - containerRect.left;
      const startY = start.y - containerRect.top;
      const endX = end.x - containerRect.left;
      const endY = end.y - containerRect.top;
      const dx = end.x - start.x;
      const dy = end.y - start.y;
      const distance = Math.hypot(dx, dy);
      const angleDeg = Math.atan2(dy, dx) * (180 / Math.PI);

      const blocked =
        req.outcome === 'DEFENSE_BLOCKED' ||
        req.outcome === 'DIRECT_BLOCKED' ||
        req.outcome === 'ATTACKER_REPELLED';
      const gsHitStrength: 'light' | 'heavy' | 'critical' =
        req.hitStrength === 'CRITICAL' ? 'critical' : req.hitStrength === 'HEAVY' ? 'heavy' : 'light';

      const sourceCardEl = (sourceEl.querySelector(':scope > button') as HTMLElement | null) ?? sourceEl;
      const targetCardEl = !req.directAttack
        ? ((targetEl.querySelector(':scope > button') as HTMLElement | null) ?? targetEl)
        : targetEl;

      if (!reduced && req.outcome !== 'SPELL_STRIKE') {
        this.trackTimelineCleanup(
          animateCardAttackLunge(sourceCardEl, targetCardEl, dx, dy, blocked, gsHitStrength),
        );
        this.emitCanvasBurst(
          {
            id: `${req.uid}-trail`,
            kind: 'TRAIL_STREAK',
            x: startX + dx * 0.22,
            y: startY + dy * 0.22,
            dx,
            dy,
            faction: req.faction,
            power: 2,
          },
          35,
        );
      } else {
        pulseElementImpact(sourceEl, 'attack');
      }

      const impactDelay = reduced ? 0 : PROJECTILE_IMPACT_DELAY_MS;
      const impactKind = blocked ? 'defense' : 'damage';
      this.scheduleDelayedTimer(impactDelay, () => {
        pulseElementImpact(targetEl, impactKind);
        if (!reduced) {
          this.trackTimelineCleanup(animateBoardShake(container, gsHitStrength, dx, dy));
        }
        this.threeCombatPulse.set({
          id: `${req.uid}-3d`,
          faction: req.faction,
          kind: req.directAttack ? 'DIRECT' : 'CLASH',
          nx: (endX / cWidth - 0.5) * 2,
          ny: (0.5 - endY / cHeight) * 2,
        });
        if (req.directAttack && req.outcome !== 'DIRECT_BLOCKED') {
          this.triggerBoardVignette(
            req.targetPlayerId && req.targetPlayerId === activeUserId ? 'CRITICAL_HIT' : req.faction,
          );
        }
      });

      if (!reduced) {
        this.emitCanvasBurst(
          {
            id: `${req.uid}-canvas`,
            kind: blocked ? 'PARRY' : req.directAttack ? 'DIRECT_STRIKE' : 'CLASH',
            x: endX,
            y: endY,
            dx,
            dy,
            faction: req.faction,
            power: Math.max(2, req.damageDealt || (req.hitStrength === 'CRITICAL' ? 4 : 2)),
          },
          impactDelay,
        );
      }

      launched.push({
        uid: req.uid,
        faction: req.faction,
        startX,
        startY,
        dx,
        dy,
        angleDeg,
        distance,
        outcome: req.outcome,
        directAttack: req.directAttack,
        hitStrength: req.hitStrength,
      });
    }

    if (launched.length === 0) {
      return;
    }

    this.activeProjectiles.update((prev) => [...prev, ...launched]);
    const launchedIds = new Set(launched.map((item) => item.uid));
    this.scheduleDelayedTimer(740, () => {
      this.activeProjectiles.update((prev) => prev.filter((item) => !launchedIds.has(item.uid)));
    });
  }

  private scheduleSpellWave(wave: SpellWaveBanner): void {
    this.spellWave.set(wave);
    this.scheduleDelayedTimer(wave.durationMs, () => {
      if (this.spellWave()?.id === wave.id) {
        this.spellWave.set(null);
      }
    });
  }

  private clearVfxTimers(): void {
    if (this.turnBeaconTimer !== null && typeof window !== 'undefined') {
      window.clearTimeout(this.turnBeaconTimer);
      this.turnBeaconTimer = null;
    }
    for (const timer of this.activeVfxTimers) {
      window.clearTimeout(timer);
    }
    this.activeVfxTimers.clear();
    if (typeof window !== 'undefined') {
      for (const rafId of this.activeAnimationFrames) {
        window.cancelAnimationFrame(rafId);
      }
    }
    this.activeAnimationFrames.clear();
    for (const cleanup of this.activeTimelineCleanups) {
      cleanup();
    }
    this.activeTimelineCleanups.clear();
  }

  private animateLatestCombatPulse(): void {
    if (typeof window === 'undefined') return;
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
      return;
    }
    const grid = this.arenaGridRef()?.nativeElement;
    if (!grid) return;
    gsap.fromTo(
      grid,
      { opacity: 0.94, scale: 0.995 },
      { opacity: 1, scale: 1, duration: 0.24, ease: 'power2.out', clearProps: 'transform,opacity' },
    );
  }
}
