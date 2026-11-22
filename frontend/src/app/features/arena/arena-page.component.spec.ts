import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { CardViewDto, ChatMessageDto, LobbyNotificationDto, PlayerBoardViewDto, PlayerGameView } from '../../core/models/arcane.models';
import { AnnouncerService } from '../../core/services/announcer.service';
import { LobbyApiService } from '../../core/services/lobby-api.service';
import { StompService } from '../../core/services/stomp.service';
import { ArenaPageComponent } from './arena-page.component';

function makeCard(id: string, name: string): CardViewDto {
  return {
    instanceId: id,
    ownerPlayerId: 'p1',
    slot: -1,
    hidden: false,
    faceDown: false,
    cardBackSkin: 'DEFAULT',
    foil: false,
    cardId: 'FIRE_M_01',
    name,
    faction: 'FIRE',
    cardType: 'MONSTER',
    epic: false,
    ramp: false,
    energyCost: 2,
    baseAtk: 3,
    baseDef: 2,
    effectiveAtk: 3,
    effectiveDef: 2,
    keywords: [],
    description: 'Frappe ardente.',
    abilitiesScript: [],
    posture: 'ATTACK',
    exhausted: false,
    slowed: false,
    camouflaged: false,
    activatedThisTurn: false,
    changedPostureThisTurn: false,
    canAttackNow: false,
    curseRemainingTurns: null,
  };
}

function makeBoard(hand: CardViewDto[], mulliganReplacedSlots: number[] = []): PlayerBoardViewDto {
  return {
    userId: 'p1',
    username: 'MageTest',
    bot: false,
    aiDifficulty: null,
    teamId: 'TEAM_A',
    seatIndex: 0,
    connected: true,
    controlledByUserId: 'p1',
    mulliganCompleted: false,
    mulliganReplacedSlots,
    energyTokens: 0,
    deckCount: 25,
    handCount: hand.length,
    hand,
    monsters: [null, null, null, null],
    familiars: [null, null, null],
    spellArtifacts: [null, null],
    discard: [],
    peekedOpponentTopCard: null,
    spellsLockedThisTurn: false,
    directAttacksBlockedThisTurn: false,
    cardBackSkin: 'DEFAULT',
    boardSkin: 'DEFAULT',
    pet: 'NONE',
    energySkin: 'DEFAULT',
    avatar: 'avatar_initiate',
    leagueFrame: 'BRONZE',
    title: 'Apprenti',
    finisher: 'DEFAULT',
    primaryFaction: 'FIRE',
  };
}

function makeView(hand: CardViewDto[], mulliganReplacedSlots: number[] = []): PlayerGameView {
  return {
    matchId: 'match-mulligan-1',
    mode: 'DUEL_1V1',
    ranked: false,
    aiMatch: true,
    customRoom: false,
    spectator: false,
    viewerUserId: 'p1',
    activeTurnPlayerId: 'p1',
    controlledRelays: [],
    phase: 'MULLIGAN',
    phaseDeadline: new Date(Date.now() + 20_000).toISOString(),
    turnNumber: 1,
    roundNumber: 1,
    turn1TruceActive: true,
    teamA: { teamId: 'TEAM_A', lp: 25, maxLp: 25, playerIds: ['p1'] },
    teamB: { teamId: 'TEAM_B', lp: 25, maxLp: 25, playerIds: ['bot1'] },
    players: [
      makeBoard(hand, mulliganReplacedSlots),
      {
        ...makeBoard([], []),
        userId: 'bot1',
        username: 'Automate',
        bot: true,
        teamId: 'TEAM_B',
        seatIndex: 1,
        mulliganCompleted: true,
        handCount: 5,
      },
    ],
    pendingReaction: null,
    pendingChoice: null,
    winnerTeam: null,
    endReason: null,
    combatLog: [],
    visualEvents: [],
  };
}

describe('ArenaPageComponent — Mulligan instantané animé', () => {
  const initialHand = [
    makeCard('h0', 'Éruption'),
    makeCard('h1', 'Salamandre'),
    makeCard('h2', 'Chouette'),
    makeCard('h3', 'Lutin'),
    makeCard('h4', 'Boule de Feu'),
  ];

  const stompStub = {
    connected: signal(true),
    gameState: signal<PlayerGameView | null>(makeView(initialHand)),
    lastError: signal<{ code: string; message: string } | null>(null),
    lobbyNotification: signal<LobbyNotificationDto | null>(null),
    globalMessages: signal<ChatMessageDto[]>([]),
    gameMessages: signal<ChatMessageDto[]>([]),
    connect: vi.fn(),
    subscribeMatch: vi.fn(),
    leaveMatchSubscription: vi.fn(),
    clearLobbyNotification: vi.fn(),
    sendGameAction: vi.fn(),
    sendGameChat: vi.fn().mockReturnValue(true),
  };

  const lobbyApiStub = {
    getGameState: vi.fn().mockResolvedValue(makeView(initialHand)),
    listCards: vi.fn().mockResolvedValue([]),
  };

  beforeEach(async () => {
    stompStub.gameState.set(makeView(initialHand));
    stompStub.sendGameAction.mockClear();

    if (typeof HTMLDialogElement !== 'undefined') {
      if (!HTMLDialogElement.prototype.showModal) {
        HTMLDialogElement.prototype.showModal = function () {
          this.setAttribute('open', '');
        };
      }
      if (!HTMLDialogElement.prototype.close) {
        HTMLDialogElement.prototype.close = function () {
          this.removeAttribute('open');
        };
      }
    }

    await TestBed.configureTestingModule({
      imports: [ArenaPageComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ matchId: 'match-mulligan-1' }),
              queryParamMap: convertToParamMap({}),
            },
          },
        },
        { provide: LobbyApiService, useValue: lobbyApiStub },
        { provide: StompService, useValue: stompStub },
      ],
    }).compileComponents();
  });

  it('dispatches MULLIGAN_REPLACE_CARD immediately per slot, displays new card and confirms with MULLIGAN_SWAP', async () => {
    const announcer = TestBed.inject(AnnouncerService);
    const announceSpy = vi.spyOn(announcer, 'announce');

    const fixture = TestBed.createComponent(ArenaPageComponent);
    fixture.detectChanges();
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    const replaceBtn = el.querySelector(
      'dialog button[aria-label*="Remplacer Éruption"]',
    ) as HTMLButtonElement | null;
    expect(replaceBtn).toBeTruthy();

    replaceBtn!.click();
    fixture.detectChanges();

    expect(stompStub.sendGameAction).toHaveBeenCalledTimes(1);
    expect(stompStub.sendGameAction).toHaveBeenCalledWith(
      expect.objectContaining({
        matchId: 'match-mulligan-1',
        actionType: 'MULLIGAN_REPLACE_CARD',
        cardInstanceId: 'h0',
      }),
    );

    // Prevent duplicate swap while awaiting server update
    const pendingBtn = Array.from(el.querySelectorAll('dialog button')).find((b) =>
      b.textContent?.includes('Échange…'),
    ) as HTMLButtonElement | undefined;
    expect(pendingBtn?.disabled).toBe(true);

    // Server returns replacement card in slot 0 while keeping Mulligan open
    const updatedHand = [makeCard('h0-new', 'Phénix Solaire'), ...initialHand.slice(1)];
    stompStub.gameState.set(makeView(updatedHand, [0]));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(el.querySelector('dialog')?.textContent).toContain('Phénix Solaire');
    expect(el.querySelector('dialog')?.textContent).toContain('1 / 5 remplacée(s)');
    expect(el.querySelector('dialog')?.textContent).toContain('Nouvelle');
    expect(announceSpy).toHaveBeenCalledWith('Nouvelle carte piochée : Phénix Solaire.', 'polite');

    const lockedBtn = el.querySelector(
      'dialog button[aria-label*="Phénix Solaire déjà remplacée"]',
    ) as HTMLButtonElement | null;
    expect(lockedBtn?.disabled).toBe(true);

    const confirmBtn = Array.from(el.querySelectorAll('dialog button')).find((b) =>
      b.textContent?.includes('Valider et commencer'),
    ) as HTMLButtonElement | undefined;
    expect(confirmBtn?.disabled).toBe(false);
    confirmBtn!.click();

    expect(stompStub.sendGameAction).toHaveBeenCalledWith(
      expect.objectContaining({
        matchId: 'match-mulligan-1',
        actionType: 'MULLIGAN_SWAP',
        instanceIds: [],
      }),
    );

    fixture.destroy();
  });
});
