import { CombatLogEntry, PlayerBoardViewDto } from '../../core/models/arcane.models';
import { presentCombatLogEntries } from './combat-log-presenter';

function makePlayer(overrides: Partial<PlayerBoardViewDto>): PlayerBoardViewDto {
  return {
    userId: 'p1',
    username: 'IgnisPlayer',
    bot: false,
    aiDifficulty: null,
    teamId: 'TEAM_A',
    seatIndex: 0,
    connected: true,
    controlledByUserId: 'p1',
    mulliganCompleted: true,
    energyTokens: 3,
    deckCount: 20,
    handCount: 4,
    hand: [],
    monsters: [],
    familiars: [],
    spellArtifacts: [],
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

function makeLog(overrides: Partial<CombatLogEntry>): CombatLogEntry {
  return {
    sequence: 1,
    roundNumber: 2,
    turnNumber: 3,
    actorPlayerId: 'p1',
    actorUsername: 'IgnisPlayer',
    eventType: 'PLAY_MONSTER',
    message: 'IgnisPlayer invoque Salamandre (ATTACK).',
    timestamp: '2026-11-15T19:00:00Z',
    ...overrides,
  };
}

describe('presentCombatLogEntries', () => {
  const players: PlayerBoardViewDto[] = [
    makePlayer({ userId: 'p1', username: 'IgnisPlayer', teamId: 'TEAM_A', primaryFaction: 'FIRE' }),
    makePlayer({
      userId: 'p2',
      username: 'Mage Automate',
      bot: true,
      teamId: 'TEAM_B',
      primaryFaction: 'ICE',
    }),
  ];

  it('personalizes player actions, French postures, team targets, and arrows for 1v1', () => {
    const entries: CombatLogEntry[] = [
      makeLog({
        sequence: 1,
        eventType: 'PLAY_MONSTER',
        actorPlayerId: 'p1',
        actorUsername: 'IgnisPlayer',
        message: 'IgnisPlayer invoque Phénix de Braise (ATTACK).',
      }),
      makeLog({
        sequence: 2,
        eventType: 'LP_DAMAGE',
        actorPlayerId: 'p1',
        actorUsername: 'IgnisPlayer',
        message: 'Phénix de Braise inflige 4 dégât(s) aux PV de TEAM_B -> 21 PV restants.',
      }),
      makeLog({
        sequence: 3,
        eventType: 'CHANGE_POSTURE',
        actorPlayerId: 'p2',
        actorUsername: 'Mage Automate',
        message: 'Golem de Glace passe en position DEFENSE.',
      }),
      makeLog({
        sequence: 4,
        eventType: 'MATCH_FINISHED',
        actorPlayerId: null,
        actorUsername: 'Système',
        message: "L'Équipe B tombe à 0 PV ! Victoire de TEAM_A !",
      }),
    ];

    const presented = presentCombatLogEntries(entries, 'p1', 'TEAM_A', players);

    expect(presented[0].perspective).toBe('ME');
    expect(presented[0].actorLabel).toBe('Vous');
    expect(presented[0].faction).toBe('FIRE');
    expect(presented[0].text).toBe('Vous invoquez Phénix de Braise (Attaque).');
    expect(presented[0].categoryLabel).toBe('Invocation');
    expect(presented[0].isHighlight).toBe(true);

    expect(presented[1].text).toBe('Phénix de Braise inflige 4 dégâts aux PV adverses → 21 PV restants.');
    expect(presented[1].category).toBe('DAMAGE');
    expect(presented[1].isHighlight).toBe(true);

    expect(presented[2].perspective).toBe('OPPONENT');
    expect(presented[2].faction).toBe('ICE');
    expect(presented[2].text).toBe('Golem de Glace passe en posture de Défense.');
    expect(presented[2].isHighlight).toBe(false);

    expect(presented[3].perspective).toBe('SYSTEM');
    expect(presented[3].text).toBe("L'adversaire tombe à 0 PV ! Victoire !");
    expect(presented[3].isHighlight).toBe(true);
  });

  it('classifies routine turn chatter as non-highlights while keeping impactful turn milestones', () => {
    const entries: CombatLogEntry[] = [
      makeLog({
        sequence: 10,
        eventType: 'DRAW',
        actorPlayerId: 'p2',
        actorUsername: 'Mage Automate',
        message: 'Mage Automate pioche 1 carte.',
      }),
      makeLog({
        sequence: 11,
        eventType: 'TURN_START',
        actorPlayerId: 'p1',
        actorUsername: 'IgnisPlayer',
        message: 'Tour 3 (Manche 2) — IgnisPlayer (+1 Énergie -> 4).',
      }),
      makeLog({
        sequence: 12,
        eventType: 'TRUCE_ENDED',
        actorPlayerId: null,
        actorUsername: 'Système',
        message: 'Fin de la Trêve du Tour 1 ! La Phase de Combat est désormais débloquée.',
      }),
    ];

    const presented = presentCombatLogEntries(entries, 'p1', 'TEAM_A', players);
    expect(presented[0].isHighlight).toBe(false);
    expect(presented[1].isHighlight).toBe(false);
    expect(presented[2].isHighlight).toBe(true);
    expect(presented[2].category).toBe('MATCH');
  });

  it('handles 2v2 team perspective and spectator neutral team labels', () => {
    const fourPlayers: PlayerBoardViewDto[] = [
      makePlayer({ userId: 'a1', username: 'AllyA', teamId: 'TEAM_A' }),
      makePlayer({ userId: 'a2', username: 'AllyB', teamId: 'TEAM_A' }),
      makePlayer({ userId: 'b1', username: 'OppA', teamId: 'TEAM_B' }),
      makePlayer({ userId: 'b2', username: 'OppB', teamId: 'TEAM_B' }),
    ];
    const log = makeLog({
      actorPlayerId: 'b1',
      actorUsername: 'OppA',
      eventType: 'LP_DAMAGE',
      message: 'Torrent inflige 1 dégât(s) aux PV de TEAM_A -> 39 PV restants.',
    });

    const playerView = presentCombatLogEntries([log], 'a1', 'TEAM_A', fourPlayers)[0];
    expect(playerView.text).toBe('Torrent inflige 1 dégât aux PV alliés → 39 PV restants.');

    const spectatorView = presentCombatLogEntries([log], 'spec', null, fourPlayers)[0];
    expect(spectatorView.text).toBe("Torrent inflige 1 dégât aux PV de l'Équipe A → 39 PV restants.");
  });
});
