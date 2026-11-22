import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { CardViewDto, PlayerBoardViewDto, PlayerGameView } from '../../../core/models/arcane.models';
import { ArenaTutorialCoachComponent } from './arena-tutorial-coach.component';
import { computeAutoTutorialIndex } from './arena-tutorial-steps';

function makeMonster(id: string): CardViewDto {
  return {
    instanceId: id,
    ownerPlayerId: 'p1',
    slot: 0,
    hidden: false,
    faceDown: false,
    cardBackSkin: 'DEFAULT',
    foil: false,
    cardId: 'FIRE_M_01',
    name: 'Diablotin de Braise',
    faction: 'FIRE',
    cardType: 'MONSTER',
    epic: false,
    ramp: false,
    energyCost: 1,
    baseAtk: 2,
    baseDef: 1,
    effectiveAtk: 2,
    effectiveDef: 1,
    keywords: [],
    description: 'Attaque rapide.',
    abilitiesScript: [],
    posture: 'ATTACK',
    exhausted: false,
    slowed: false,
    camouflaged: false,
    activatedThisTurn: false,
    changedPostureThisTurn: false,
    canAttackNow: true,
    curseRemainingTurns: null,
  };
}

function makeBoard(hasMonster = false): PlayerBoardViewDto {
  return {
    userId: 'p1',
    username: 'ApprentiMage',
    bot: false,
    aiDifficulty: null,
    teamId: 'TEAM_A',
    seatIndex: 0,
    connected: true,
    controlledByUserId: 'p1',
    mulliganCompleted: true,
    mulliganReplacedSlots: [],
    energyTokens: 1,
    deckCount: 25,
    handCount: 4,
    hand: [],
    monsters: hasMonster ? [makeMonster('m1'), null, null, null] : [null, null, null, null],
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

function makeView(overrides: Partial<PlayerGameView> = {}): PlayerGameView {
  return {
    matchId: 'match-tuto-1',
    mode: 'DUEL_1V1',
    ranked: false,
    aiMatch: true,
    customRoom: false,
    spectator: false,
    viewerUserId: 'p1',
    activeTurnPlayerId: 'p1',
    controlledRelays: [],
    turnNumber: 1,
    roundNumber: 1,
    turn1TruceActive: true,
    phase: 'MAIN_PHASE',
    phaseDeadline: new Date(Date.now() + 60_000).toISOString(),
    winnerTeam: null,
    endReason: null,
    teamA: { teamId: 'TEAM_A', lp: 25, maxLp: 25, playerIds: ['p1'] },
    teamB: { teamId: 'TEAM_B', lp: 25, maxLp: 25, playerIds: ['bot1'] },
    players: [],
    pendingReaction: null,
    pendingChoice: null,
    combatLog: [],
    ...overrides,
  };
}

describe('ArenaTutorialCoachComponent & computeAutoTutorialIndex', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ArenaTutorialCoachComponent],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
  });

  it('progresses monotonically from Mulligan -> Board -> Summon -> Turn Pass -> Combat -> Summary', () => {
    const mulliganView = makeView({ phase: 'MULLIGAN' });
    expect(computeAutoTutorialIndex(0, mulliganView, makeBoard(false), true).currentIndex).toBe(0);

    const turn1View = makeView({ phase: 'MAIN_PHASE', turnNumber: 1, turn1TruceActive: true });
    expect(computeAutoTutorialIndex(0, turn1View, makeBoard(false), true).currentIndex).toBe(1);

    const summonedProgress = computeAutoTutorialIndex(1, turn1View, makeBoard(true), true);
    expect(summonedProgress.currentIndex).toBe(3);

    const aiTurnProgress = computeAutoTutorialIndex(3, turn1View, makeBoard(true), false);
    expect(aiTurnProgress.waitMessage).toContain('Mage Automate');

    const turn3View = makeView({ phase: 'MAIN_PHASE', turnNumber: 3, turn1TruceActive: false });
    const combatUnlock = computeAutoTutorialIndex(3, turn3View, makeBoard(true), true);
    expect(combatUnlock.currentIndex).toBe(4);
    expect(combatUnlock.waitMessage).toContain('Phase Combat');

    const afterAttackView = makeView({
      phase: 'COMBAT_PHASE',
      turnNumber: 3,
      turn1TruceActive: false,
      combatLog: [
        {
          sequence: 1,
          roundNumber: 2,
          turnNumber: 3,
          actorPlayerId: 'p1',
          actorUsername: 'ApprentiMage',
          eventType: 'COMBAT',
          message: 'Diablotin de Braise attaque.',
          timestamp: new Date().toISOString(),
        },
      ],
    });
    expect(computeAutoTutorialIndex(4, afterAttackView, makeBoard(true), true).currentIndex).toBe(5);
  });

  it('renders step controls, supports minimizing, and emits completed on final step CTA', async () => {
    const fixture = TestBed.createComponent(ArenaTutorialCoachComponent);
    fixture.componentRef.setInput('active', true);
    fixture.componentRef.setInput('gameView', makeView({ phase: 'MAIN_PHASE', turnNumber: 1 }));
    fixture.componentRef.setInput('myBoard', makeBoard(false));
    fixture.componentRef.setInput('isMyTurn', true);
    fixture.detectChanges();

    let completedCount = 0;
    let dismissedCount = 0;
    fixture.componentInstance.completed.subscribe(() => completedCount++);
    fixture.componentInstance.dismissed.subscribe(() => dismissedCount++);

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('#tutorial-coach-title')?.textContent).toContain('Plateau');

    // Step forward cleanly until the final step
    for (let i = 0; i < 4; i++) {
      const nextBtn = Array.from(host.querySelectorAll('button')).find((b) =>
        b.textContent?.includes('Suivant'),
      );
      expect(nextBtn).toBeTruthy();
      nextBtn?.click();
      fixture.detectChanges();
    }

    expect(host.querySelector('#tutorial-coach-title')?.textContent).toContain('4 Lois Anti-Méta');
    const finishBtn = Array.from(host.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Terminer le didacticiel'),
    );
    expect(finishBtn).toBeTruthy();
    finishBtn?.click();
    expect(completedCount).toBe(1);

    // Dismissing calls dismissed output
    const dismissBtn = host.querySelector('button[aria-label="Passer le didacticiel"]') as HTMLButtonElement;
    dismissBtn.click();
    expect(dismissedCount).toBe(1);
    fixture.destroy();
  });
});
