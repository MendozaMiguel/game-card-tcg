import { CardViewDto, PlayerBoardViewDto, PlayerGameView } from '../../core/models/arcane.models';
import { buildTacticalGuidance, evaluateHandCardPlayability } from './arena-tactical-guide';

function card(overrides: Partial<CardViewDto>): CardViewDto {
  return {
    instanceId: 'c1',
    ownerPlayerId: 'me',
    slot: -1,
    hidden: false,
    faceDown: false,
    cardBackSkin: 'card_back_default',
    foil: false,
    cardId: 'FIRE_M_01',
    name: 'Salamandre',
    faction: 'FIRE',
    cardType: 'MONSTER',
    epic: false,
    ramp: false,
    energyCost: 2,
    baseAtk: 2,
    baseDef: 2,
    effectiveAtk: 2,
    effectiveDef: 2,
    keywords: [],
    description: '',
    abilitiesScript: [],
    posture: 'ATTACK',
    exhausted: false,
    slowed: false,
    camouflaged: false,
    activatedThisTurn: false,
    changedPostureThisTurn: false,
    canAttackNow: false,
    curseRemainingTurns: null,
    ...overrides,
  };
}

function board(overrides: Partial<PlayerBoardViewDto>): PlayerBoardViewDto {
  return {
    userId: 'me',
    username: 'MageMoi',
    bot: false,
    aiDifficulty: null,
    teamId: 'TEAM_A',
    seatIndex: 0,
    connected: true,
    controlledByUserId: 'me',
    mulliganCompleted: true,
    energyTokens: 3,
    deckCount: 20,
    handCount: 1,
    hand: [],
    monsters: [null, null, null, null],
    familiars: [null, null, null],
    spellArtifacts: [null, null],
    discard: [],
    peekedOpponentTopCard: null,
    spellsLockedThisTurn: false,
    directAttacksBlockedThisTurn: false,
    cardBackSkin: 'card_back_default',
    boardSkin: 'board_sanctuary',
    pet: 'pet_none',
    energySkin: 'energy_arcane',
    avatar: 'avatar_pyromancer',
    leagueFrame: 'frame_bronze',
    title: 'Initié',
    finisher: 'finisher_default',
    primaryFaction: 'FIRE',
    ...overrides,
  };
}

function view(overrides: Partial<PlayerGameView>): PlayerGameView {
  return {
    matchId: 'm1',
    mode: 'DUEL_1V1',
    ranked: false,
    aiMatch: true,
    customRoom: false,
    spectator: false,
    viewerUserId: 'me',
    activeTurnPlayerId: 'me',
    controlledRelays: [],
    phase: 'MAIN_PHASE',
    phaseDeadline: null,
    turnNumber: 3,
    roundNumber: 2,
    turn1TruceActive: false,
    teamA: { teamId: 'TEAM_A', lp: 25, maxLp: 25, playerIds: ['me'] },
    teamB: { teamId: 'TEAM_B', lp: 25, maxLp: 25, playerIds: ['opp'] },
    players: [],
    pendingReaction: null,
    pendingChoice: null,
    winnerTeam: null,
    endReason: null,
    combatLog: [],
    visualEvents: [],
    ...overrides,
  };
}

describe('arena-tactical-guide', () => {
  it('flags energy deficit, locked spells, full zones, epic limit, and playable cards accurately', () => {
    const cPlayable = card({ instanceId: 'c1', cardType: 'MONSTER', energyCost: 2 });
    const cExpensive = card({ instanceId: 'c2', cardType: 'MONSTER', energyCost: 5 });
    const cSpell = card({ instanceId: 'c3', cardType: 'SPELL_NORMAL', energyCost: 1 });
    const cEpic = card({ instanceId: 'c4', cardType: 'MONSTER', epic: true, energyCost: 3 });

    const meBoard = board({
      energyTokens: 3,
      spellsLockedThisTurn: true,
      monsters: [card({ instanceId: 'm0', epic: true }), null, null, null],
      familiars: [card({ instanceId: 'f0' }), card({ instanceId: 'f1' }), card({ instanceId: 'f2' })],
      spellArtifacts: [card({ instanceId: 's0', cardType: 'ARTIFACT' }), card({ instanceId: 's1', cardType: 'CURSE' })],
    });
    const gView = view({ phase: 'MAIN_PHASE', activeTurnPlayerId: 'me' });

    expect(evaluateHandCardPlayability(gView, meBoard, cPlayable)).toEqual({
      playable: true,
      shortBadge: 'Jouable',
      reason: null,
      category: 'READY',
    });

    const lowEnergy = evaluateHandCardPlayability(gView, meBoard, cExpensive);
    expect(lowEnergy.playable).toBe(false);
    expect(lowEnergy.category).toBe('NO_ENERGY');
    expect(lowEnergy.shortBadge).toBe('-2 ⚡');

    const lockedSpell = evaluateHandCardPlayability(gView, meBoard, cSpell);
    expect(lockedSpell.playable).toBe(false);
    expect(lockedSpell.category).toBe('LOCKED');

    const epicDup = evaluateHandCardPlayability(gView, meBoard, cEpic);
    expect(epicDup.playable).toBe(false);
    expect(epicDup.category).toBe('SLOT_FULL');

    const famFull = evaluateHandCardPlayability(
      gView,
      meBoard,
      card({ instanceId: 'c5', cardType: 'FAMILIAR', energyCost: 1 }),
    );
    expect(famFull.playable).toBe(false);
    expect(famFull.shortBadge).toBe('3/3 Plein');

    const unlockedFullBoard = board({
      energyTokens: 3,
      spellsLockedThisTurn: false,
      spellArtifacts: [
        card({ instanceId: 's0', cardType: 'ARTIFACT' }),
        card({ instanceId: 's1', cardType: 'CURSE' }),
      ],
    });
    const spellFull = evaluateHandCardPlayability(
      gView,
      unlockedFullBoard,
      card({ instanceId: 'c6', cardType: 'ARTIFACT', energyCost: 1 }),
    );
    expect(spellFull.playable).toBe(false);
    expect(spellFull.shortBadge).toBe('2/2 Plein');
  });

  it('produces clear opponent wait vs active player main/combat guidance', () => {
    const handCard = card({ instanceId: 'h1', energyCost: 2 });
    const attacker = card({ instanceId: 'a1', canAttackNow: true });
    const meBoard = board({ energyTokens: 3, hand: [handCard], monsters: [attacker, null, null, null] });

    const waitHint = buildTacticalGuidance(
      view({ activeTurnPlayerId: 'opp', phase: 'MAIN_PHASE' }),
      meBoard,
      false,
      'Mage Automate',
    );
    expect(waitHint.tone).toBe('WAIT');
    expect(waitHint.headline).toContain('Mage Automate');

    const mainHint = buildTacticalGuidance(
      view({ activeTurnPlayerId: 'me', phase: 'MAIN_PHASE' }),
      meBoard,
      true,
      'MageMoi',
    );
    expect(mainHint.tone).toBe('MY_ACTION');
    expect(mainHint.playableCardCount).toBe(1);
    expect(mainHint.readyAttackerCount).toBe(1);
    expect(mainHint.headline).toContain('À vous de jouer');

    const combatHint = buildTacticalGuidance(
      view({ activeTurnPlayerId: 'me', phase: 'COMBAT_PHASE' }),
      meBoard,
      true,
      'MageMoi',
    );
    expect(combatHint.tone).toBe('COMBAT');
    expect(combatHint.readyAttackerCount).toBe(1);
  });
});
