import { CardDefinition, CardViewDto, LeagueTier, PlayerGameView } from '../../core/models/arcane.models';
import {
  evaluateAntiMetaLocally,
  findEligibleTargetsForRequirement,
  formatDurationCompact,
  LEAGUE_LABELS,
  resolveMatchSource,
} from './faction-theme';

function makeCard(
  idx: number,
  overrides: Partial<CardDefinition> = {},
): CardDefinition {
  return {
    cardId: `FIRE_M_${String(idx).padStart(2, '0')}`,
    name: `Carte ${idx}`,
    faction: 'FIRE',
    cardType: 'MONSTER',
    isEpic: false,
    isRamp: false,
    energyCost: 1,
    baseAtk: 2,
    baseDef: 2,
    keywords: [],
    description: 'Description',
    abilitiesScript: [],
    ...overrides,
  };
}

function makeValid30Deck(): CardDefinition[] {
  const deck: CardDefinition[] = [];
  // 10 Monstres (8 cost 1-2, 2 cost 5+ incl 1 Epic)
  for (let i = 1; i <= 8; i++) {
    deck.push(makeCard(i, { cardType: 'MONSTER', energyCost: 2, cardId: `FIRE_M_${i}` }));
  }
  deck.push(makeCard(9, { cardType: 'MONSTER', energyCost: 5, isEpic: true, cardId: 'FIRE_M_09' }));
  deck.push(makeCard(10, { cardType: 'MONSTER', energyCost: 5, cardId: 'FIRE_M_10' }));

  // 10 Familiers (6 cost 1-2, 4 cost 3-4)
  for (let i = 11; i <= 16; i++) {
    deck.push(makeCard(i, { cardType: 'FAMILIAR', energyCost: 1, cardId: `FIRE_F_${i}` }));
  }
  for (let i = 17; i <= 20; i++) {
    deck.push(makeCard(i, { cardType: 'FAMILIAR', energyCost: 3, cardId: `FIRE_F_${i}` }));
  }

  // 10 Sorts (8 cost 3-4, 2 cost 5+) -> total curve: 14 (1-2), 12 (3-4), 4 (5+)
  for (let i = 21; i <= 28; i++) {
    deck.push(makeCard(i, { cardType: 'SPELL_NORMAL', energyCost: 3, cardId: `FIRE_S_${i}` }));
  }
  deck.push(makeCard(29, { cardType: 'SPELL_ULTIMATE', energyCost: 6, cardId: 'FIRE_S_29' }));
  deck.push(makeCard(30, { cardType: 'ARTIFACT', energyCost: 5, cardId: 'FIRE_S_30' }));

  return deck;
}

describe('Anti-Meta Client Validator & Targeting Helper', () => {
  it('validates a compliant 30-card 14/12/4 mono-faction deck', () => {
    const res = evaluateAntiMetaLocally(makeValid30Deck());
    expect(res.valid).toBe(true);
    expect(res.violations).toEqual([]);
    expect(res.stats.primaryFaction).toBe('FIRE');
  });

  it('rejects Ramp count above 3 and duplicate Singleton cards', () => {
    const deck = makeValid30Deck();
    deck[0] = { ...deck[0], isRamp: true };
    deck[1] = { ...deck[1], isRamp: true };
    deck[2] = { ...deck[2], isRamp: true };
    deck[3] = { ...deck[3], isRamp: true };
    deck[4] = { ...deck[0] }; // duplicate

    const res = evaluateAntiMetaLocally(deck);
    expect(res.valid).toBe(false);
    expect(res.violations.some((v) => v.includes('Ramp'))).toBe(true);
    expect(res.violations.some((v) => v.includes('Singleton'))).toBe(true);
  });

  it('excludes Camouflaged opponent creatures when selecting targets', () => {
    const baseCreature = (id: string, camouflaged: boolean): CardViewDto => ({
      instanceId: id,
      ownerPlayerId: 'p2',
      slot: 0,
      hidden: false,
      faceDown: false,
      cardBackSkin: 'default',
      foil: false,
      cardId: 'FIRE_M_01',
      name: id,
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
      camouflaged,
      activatedThisTurn: false,
      changedPostureThisTurn: false,
      canAttackNow: true,
      curseRemainingTurns: null,
    });

    const view = {
      players: [
        { userId: 'p1', teamId: 'TEAM_A', monsters: [], familiars: [], spellArtifacts: [] },
        {
          userId: 'p2',
          teamId: 'TEAM_B',
          monsters: [baseCreature('visible_m', false), baseCreature('hidden_m', true)],
          familiars: [],
          spellArtifacts: [],
        },
      ],
    } as unknown as PlayerGameView;

    const actorBoard = view.players[0];
    const targets = findEligibleTargetsForRequirement(view, actorBoard, 'src', 'OPPONENT_MONSTER');
    expect(targets.map((c) => c.instanceId)).toEqual(['visible_m']);
  });

  it('resolves match source and omits empty zero/null durations cleanly', () => {
    expect(resolveMatchSource({ ranked: true })).toBe('RANKED');
    expect(resolveMatchSource({ customRoom: true, aiMatch: true })).toBe('CUSTOM');
    expect(resolveMatchSource({ tournamentId: 't-1', ranked: false })).toBe('TOURNAMENT');
    expect(formatDurationCompact(0)).toBe('');
    expect(formatDurationCompact(null)).toBe('');
    expect(formatDurationCompact(145)).toMatch(/2.*25/);
  });

  it('maps every backend LeagueTier value to a non-empty French label', () => {
    const tiers: LeagueTier[] = [
      'APPRENTI',
      'INITIE',
      'ADEPTE',
      'MAGE',
      'ARCHIMAGE',
      'GRAND_MAITRE',
    ];
    for (const tier of tiers) {
      expect(LEAGUE_LABELS[tier]).toBeTruthy();
    }
    expect(LEAGUE_LABELS.GRAND_MAITRE).toBe('Grand Maître');
  });
});
