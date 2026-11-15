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
import { extractApiErrorMessage } from '../../core/services/auth.service';
import { LobbyApiService } from '../../core/services/lobby-api.service';
import { StompService } from '../../core/services/stomp.service';
import { ArcaneAvatarComponent } from '../../shared/components/arcane-avatar/arcane-avatar.component';
import { ArcaneCardComponent } from '../../shared/components/arcane-card/arcane-card.component';
import {
  ActiveProjectile,
  ArenaProjectileOverlayComponent,
} from '../../shared/components/arena-projectile-overlay/arena-projectile-overlay.component';
import { ArenaSlotVfxComponent } from '../../shared/components/arena-slot-vfx/arena-slot-vfx.component';
import { ImeEnterSubmitDirective } from '../../shared/directives/ime-enter-submit.directive';
import {
  HandCardPlayability,
  TacticalGuidance,
  buildTacticalGuidance,
  evaluateHandCardPlayability,
} from '../../shared/utils/arena-tactical-guide';
import {
  AvatarImpactEvent,
  LpVfxEvent,
  SlotVfxEvent,
  SlotZone,
  SpellWaveBanner,
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
    ArenaProjectileOverlayComponent,
    ArenaSlotVfxComponent,
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
  private readonly destroyRef = inject(DestroyRef);
  protected readonly stomp = inject(StompService);

  private readonly arenaGridRef = viewChild<ElementRef<HTMLElement>>('arenaGrid');
  private readonly arenaPlayColRef = viewChild<ElementRef<HTMLElement>>('arenaPlayCol');
  private readonly enemyLpBoxRef = viewChild<ElementRef<HTMLElement>>('enemyLpBox');
  private readonly allyLpBoxRef = viewChild<ElementRef<HTMLElement>>('allyLpBox');
  private readonly mulliganDialogRef = viewChild<ElementRef<HTMLDialogElement>>('mulliganDialog');
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
  private celebratedMatchFinish = false;
  private destroyed = false;
  private stopVictoryConfetti: (() => void) | null = null;

  protected readonly nowMs = signal(Date.now());
  protected readonly gameView = this.stomp.gameState;
  protected readonly slotBursts = signal<Readonly<Record<string, SlotVfxEvent>>>({});
  protected readonly avatarBursts = signal<Readonly<Record<string, AvatarImpactEvent>>>({});
  protected readonly activeProjectiles = signal<readonly ActiveProjectile[]>([]);
  protected readonly enemyLpBurst = signal<LpVfxEvent | null>(null);
  protected readonly allyLpBurst = signal<LpVfxEvent | null>(null);
  protected readonly spellWave = signal<SpellWaveBanner | null>(null);
  protected readonly aiDifficultyMeta = AI_DIFFICULTY_META;
  protected readonly factionMeta = FACTION_META;
  protected readonly logFilterMode = signal<'HIGHLIGHTS' | 'ALL'>('HIGHLIGHTS');
  protected readonly turnAnnouncement = signal<{
    turnNumber: number;
    label: string;
    faction: Faction | null;
  } | null>(null);
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

  protected readonly mulliganSelectedIds = signal<string[]>([]);
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
    const hasSurvol = !!attacker?.keywords?.includes('SURVOL');

    let hasGuardian = false;
    let hasMonster = false;
    let hasFamiliar = false;
    for (const opp of this.opponentBoards()) {
      for (const m of opp.monsters) {
        if (m && !m.hidden && !m.camouflaged) {
          hasMonster = true;
          if (m.keywords?.includes('GARDIEN')) hasGuardian = true;
        }
      }
      for (const f of opp.familiars) {
        if (f && !f.hidden && !f.camouflaged) {
          hasFamiliar = true;
          if (f.keywords?.includes('GARDIEN')) hasGuardian = true;
        }
      }
    }
    if (hasGuardian) {
      return { allowed: false, reason: 'Un Gardien adverse doit être ciblé en priorité' };
    }
    if (hasMonster) {
      return { allowed: false, reason: 'Bloqué par la Règle du Rempart (Monstre adverse présent)' };
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
      if (!view || view.spectator || view.phase === 'MULLIGAN' || view.phase === 'FINISHED') {
        return;
      }
      const turnKey = `${view.turnNumber}:${view.activeTurnPlayerId}`;
      if (turnKey === this.lastTurnBeaconKey) {
        return;
      }
      this.lastTurnBeaconKey = turnKey;
      if (this.turnBeaconTimer !== null && typeof window !== 'undefined') {
        window.clearTimeout(this.turnBeaconTimer);
        this.turnBeaconTimer = null;
      }
      if (this.isMyTurn() && view.phase === 'MAIN_PHASE') {
        const faction = this.activeControlledBoard()?.primaryFaction ?? null;
        this.turnAnnouncement.set({
          turnNumber: view.turnNumber,
          label: 'À vous de jouer',
          faction,
        });
        untracked(() => this.announcer.announce(`Tour ${view.turnNumber} : à vous de jouer.`, 'polite'));
        if (typeof window !== 'undefined') {
          this.turnBeaconTimer = window.setTimeout(() => {
            this.turnAnnouncement.set(null);
            this.turnBeaconTimer = null;
          }, 2100);
        }
      } else {
        this.turnAnnouncement.set(null);
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

    const interval = setInterval(() => this.nowMs.set(Date.now()), 250);
    this.destroyRef.onDestroy(() => {
      this.destroyed = true;
      clearInterval(interval);
      this.clearVfxTimers();
      this.stopVictoryConfetti?.();
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

  protected onChangePosture(card: CardViewDto): void {
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

  protected toggleMulliganCard(instanceId: string): void {
    this.mulliganSelectedIds.update((ids) =>
      ids.includes(instanceId) ? ids.filter((id) => id !== instanceId) : [...ids, instanceId],
    );
  }

  protected confirmMulligan(): void {
    const ids = this.mulliganSelectedIds();
    this.mulliganSelectedIds.set([]);
    this.dispatchAction({
      actionType: 'MULLIGAN_SWAP',
      instanceIds: ids,
    });
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

    if (batch.projectiles.length > 0 && typeof window !== 'undefined') {
      const rafId = window.requestAnimationFrame(() => {
        this.activeAnimationFrames.delete(rafId);
        if (!this.destroyed) {
          this.spawnProjectiles(batch.projectiles, myTeam, reduced);
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
    const wave = batch.spellWaves.at(-1);
    if (wave) {
      this.scheduleSpellWave(wave);
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

  private spawnProjectiles(
    requests: readonly VisualProjectileRequest[],
    myTeam: TeamId,
    reduced: boolean,
  ): void {
    const container = this.arenaPlayColRef()?.nativeElement;
    if (!container) return;

    const containerRect = container.getBoundingClientRect();
    const slotMap = new Map<string, HTMLElement>();
    for (const el of Array.from(container.querySelectorAll<HTMLElement>('[data-board-slot]'))) {
      const key = el.getAttribute('data-board-slot');
      if (key) slotMap.set(key, el);
    }
    const avatarMap = new Map<string, HTMLElement>();
    for (const el of Array.from(container.querySelectorAll<HTMLElement>('[data-player-avatar]'))) {
      const id = el.getAttribute('data-player-avatar');
      if (id) avatarMap.set(id, el);
    }

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
      const dx = end.x - start.x;
      const dy = end.y - start.y;
      const distance = Math.hypot(dx, dy);
      const angleDeg = Math.atan2(dy, dx) * (180 / Math.PI);

      pulseElementImpact(sourceEl, 'attack');
      const impactKind =
        req.outcome === 'DEFENSE_BLOCKED' ||
        req.outcome === 'DIRECT_BLOCKED' ||
        req.outcome === 'ATTACKER_REPELLED'
          ? 'defense'
          : 'damage';
      this.scheduleDelayedTimer(reduced ? 0 : 260, () => {
        pulseElementImpact(targetEl, impactKind);
      });

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
