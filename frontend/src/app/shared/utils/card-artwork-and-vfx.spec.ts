import {
  AbilityDefinition,
  CardDefinition,
  CardViewDto,
  Faction,
  PlayerBoardViewDto,
  PlayerGameView,
} from '../../core/models/arcane.models';
import { extractArenaVfxEvents, slotBurstKey } from './arena-vfx';
import {
  CARD_ARTWORK_REGISTRY,
  buildCardIllustrationSpec,
  resolveCardArtworkUrl,
} from './card-artwork';
import { COSMETIC_CATALOG } from './faction-theme';

function makeAbility(partial: Partial<AbilityDefinition> = {}): AbilityDefinition {
  return {
    trigger: 'ON_PLAY',
    condition: null,
    target: null,
    action: 'DAMAGE_LP',
    value: null,
    secondaryValue: null,
    duration: null,
    options: null,
    ...partial,
  };
}

function makeCardDef(partial: Partial<CardDefinition> & Pick<CardDefinition, 'name' | 'faction' | 'cardType'>): CardDefinition {
  return {
    cardId: 'c-test',
    isEpic: false,
    isRamp: false,
    energyCost: 2,
    baseAtk: partial.cardType === 'MONSTER' || partial.cardType === 'FAMILIAR' ? 3 : null,
    baseDef: partial.cardType === 'MONSTER' || partial.cardType === 'FAMILIAR' ? 4 : null,
    keywords: [],
    description: '',
    abilitiesScript: [],
    ...partial,
  };
}

function makeSlotCard(partial: Partial<CardViewDto> = {}): CardViewDto {
  return {
    instanceId: 'inst-1',
    ownerPlayerId: 'p1',
    slot: 0,
    hidden: false,
    faceDown: false,
    cardBackSkin: 'DEFAULT',
    foil: false,
    cardId: 'c-1',
    name: 'Dragon de Feu',
    faction: 'FIRE',
    cardType: 'MONSTER',
    epic: false,
    ramp: false,
    energyCost: 4,
    baseAtk: 4,
    baseDef: 4,
    effectiveAtk: 4,
    effectiveDef: 4,
    keywords: [],
    description: '',
    abilitiesScript: [],
    posture: 'ATTACK',
    exhausted: false,
    slowed: false,
    camouflaged: false,
    activatedThisTurn: false,
    changedPostureThisTurn: false,
    canAttackNow: true,
    curseRemainingTurns: null,
    ...partial,
  };
}

function makePlayerBoard(userId: string, username: string, teamId: 'TEAM_A' | 'TEAM_B'): PlayerBoardViewDto {
  return {
    userId,
    username,
    bot: false,
    teamId,
    seatIndex: teamId === 'TEAM_A' ? 0 : 1,
    connected: true,
    controlledByUserId: userId,
    mulliganCompleted: true,
    energyTokens: 4,
    deckCount: 20,
    handCount: 4,
    hand: [],
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
    avatar: 'MAGE',
    leagueFrame: 'BRONZE',
    title: 'Apprenti',
    finisher: 'DEFAULT',
  };
}

function makeGameView(partial: Partial<PlayerGameView> = {}): PlayerGameView {
  return {
    matchId: 'match-1',
    mode: 'DUEL_1V1',
    ranked: false,
    aiMatch: true,
    customRoom: false,
    spectator: false,
    viewerUserId: 'p1',
    activeTurnPlayerId: 'p1',
    controlledRelays: ['p1'],
    phase: 'MAIN_PHASE',
    phaseDeadline: null,
    turnNumber: 2,
    roundNumber: 1,
    turn1TruceActive: false,
    teamA: { teamId: 'TEAM_A', lp: 25, maxLp: 25, playerIds: ['p1'] },
    teamB: { teamId: 'TEAM_B', lp: 25, maxLp: 25, playerIds: ['p2'] },
    players: [makePlayerBoard('p1', 'Mage', 'TEAM_A'), makePlayerBoard('p2', 'Automate', 'TEAM_B')],
    pendingReaction: null,
    pendingChoice: null,
    winnerTeam: null,
    endReason: null,
    combatLog: [],
    ...partial,
  };
}

describe('buildCardIllustrationSpec', () => {
  it('maps distinct silhouettes and effect visuals from description and abilitiesScript', () => {
    const golem = buildCardIllustrationSpec(
      makeCardDef({
        name: 'Golem de Glace',
        faction: 'ICE',
        cardType: 'MONSTER',
        keywords: ['INDESTRUCTIBLE_DEFENSE'],
        description: 'Mur Impénétrable : En Position de Défense, ne peut pas être détruit par les Sorts adverses.',
        abilitiesScript: [makeAbility({ trigger: 'PASSIVE', action: 'SPELL_DESTRUCTION_IMMUNITY' })],
      }),
    );
    expect(golem.subject).toBe('golem');
    expect(golem.effect).toBe('SHIELD');

    const chains = buildCardIllustrationSpec(
      makeCardDef({
        name: 'Chaînes de Glace',
        faction: 'ICE',
        cardType: 'SPELL_NORMAL',
        keywords: ['RALENTIT'],
        description: 'Ralentit un Monstre adverse ciblé (ne peut pas attaquer pendant 1 tour).',
        abilitiesScript: [makeAbility({ action: 'APPLY_SLOW', value: 1 })],
      }),
    );
    expect(chains.subject).toBe('chains');
    expect(chains.effect).toBe('SLOW');

    const campfire = buildCardIllustrationSpec(
      makeCardDef({
        name: 'Feu de Camp',
        faction: 'FIRE',
        cardType: 'ARTIFACT',
        description: 'Une fois par tour : Votre équipe regagne +2 PV.',
        abilitiesScript: [makeAbility({ trigger: 'ACTIVATED_MAIN', action: 'HEAL_LP', value: 2 })],
      }),
    );
    expect(campfire.subject).toBe('campfire');
    expect(campfire.effect).toBe('HEAL');

    const fireball = buildCardIllustrationSpec(
      makeCardDef({
        name: 'Boule de Feu Arcanique',
        faction: 'FIRE',
        cardType: 'SPELL_NORMAL',
        description: "Inflige 3 points de dégâts directement aux PV de l'équipe adverse.",
        abilitiesScript: [makeAbility({ action: 'DAMAGE_LP', value: 3 })],
      }),
    );
    expect(fireball.subject).toBe('fireball');
    expect(fireball.effect).toBe('DAMAGE');

    const sacredShield = buildCardIllustrationSpec(
      makeCardDef({
        name: 'Bouclier Sacré',
        faction: 'LIGHT',
        cardType: 'SPELL_REACTIVE',
        description: "Annule une attaque directe et bloque les attaques directes jusqu'à la fin du tour.",
        abilitiesScript: [makeAbility({ trigger: 'ON_DIRECT_ATTACK', action: 'BLOCK_DIRECT_ATTACKS' })],
      }),
    );
    expect(sacredShield.subject).toBe('shield');
    expect(sacredShield.effect).toBe('REACTIVE');
    expect(sacredShield.secondaryEffect).toBe('SHIELD');
    expect(sacredShield.effectTag).toBe('MUR PROTECTEUR');
  });

  it('produces valid faction palette defaults even for partial views', () => {
    for (const faction of ['FIRE', 'ICE', 'SHADOW', 'LIGHT'] as Faction[]) {
      const spec = buildCardIllustrationSpec(
        makeCardDef({ name: 'Carte Mystère', faction, cardType: 'MONSTER' }),
      );
      expect(spec.faction).toBe(faction);
      expect(spec.primaryColor.length).toBeGreaterThan(0);
      expect(spec.effectTag.length).toBeGreaterThan(0);
    }
  });

  it('resolves registered painted TCG artwork paths cleanly across all factions', () => {
    expect(resolveCardArtworkUrl('FIRE_M_10')).toBe('/cards/fire-dragon.jpg');
    expect(resolveCardArtworkUrl('FIRE_F_06')).toBe('/cards/fire-fam-cat.jpg');
    expect(resolveCardArtworkUrl('ICE_M_08')).toBe('/cards/ice-yeti.jpg');
    expect(resolveCardArtworkUrl('ICE_S_09')).toBe('/cards/ice-spell.jpg');
    expect(resolveCardArtworkUrl('SHADOW_S_09')).toBe('/cards/shadow-eclipse.jpg');
    expect(resolveCardArtworkUrl('LIGHT_M_08')).toBe('/cards/light-seraph.jpg');
    expect(resolveCardArtworkUrl('FIRE_M_10', '/cards/custom.jpg')).toBe('/cards/custom.jpg');
    expect(Object.keys(CARD_ARTWORK_REGISTRY).length).toBeGreaterThanOrEqual(70);
  });
});

describe('extractArenaVfxEvents', () => {
  it('returns empty batch on initial connect or matchId change', () => {
    const next = makeGameView();
    const res = extractArenaVfxEvents(null, next, new Map());
    expect(res.slotEvents).toEqual([]);
    expect(res.lpEvents).toEqual([]);
    expect(res.spellWaves).toEqual([]);
  });

  it('emits spell wave, creature damage (-DEF) and LP fire damage deltas cleanly', () => {
    const fireballDef = makeCardDef({
      name: 'Boule de Feu Arcanique',
      faction: 'FIRE',
      cardType: 'SPELL_NORMAL',
      description: 'Inflige 3 dégâts aux PV adverses.',
      abilitiesScript: [makeAbility({ action: 'DAMAGE_LP', value: 3 })],
    });
    const catalog = new Map([[fireballDef.name.toLowerCase(), fireballDef]]);

    const prev = makeGameView();
    prev.players[1].monsters[0] = makeSlotCard({
      instanceId: 'opp-m0',
      ownerPlayerId: 'p2',
      faction: 'ICE',
      baseDef: 5,
      effectiveDef: 5,
    });

    const next = makeGameView({
      teamB: { teamId: 'TEAM_B', lp: 22, maxLp: 25, playerIds: ['p2'] },
      combatLog: [
        {
          sequence: 12,
          roundNumber: 1,
          turnNumber: 2,
          actorPlayerId: 'p1',
          actorUsername: 'Mage',
          eventType: 'CAST_SPELL',
          message: 'Mage lance Boule de Feu Arcanique.',
          timestamp: '2026-11-15T20:00:00Z',
        },
      ],
    });
    next.players[1].monsters[0] = makeSlotCard({
      instanceId: 'opp-m0',
      ownerPlayerId: 'p2',
      faction: 'ICE',
      baseDef: 5,
      effectiveDef: 3,
    });

    const res = extractArenaVfxEvents(prev, next, catalog);

    expect(res.spellWaves.length).toBe(1);
    expect(res.spellWaves[0].faction).toBe('FIRE');
    expect(res.spellWaves[0].effect).toBe('DAMAGE');
    expect(res.spellWaves[0].cardName).toBe('Boule de Feu Arcanique');

    expect(res.lpEvents.length).toBe(1);
    expect(res.lpEvents[0].teamId).toBe('TEAM_B');
    expect(res.lpEvents[0].kind).toBe('DAMAGE');
    expect(res.lpEvents[0].faction).toBe('FIRE');
    expect(res.lpEvents[0].label).toBe('-3 PV');

    expect(res.slotEvents.length).toBe(1);
    expect(res.slotEvents[0].key).toBe(slotBurstKey('p2', 'monster', 0));
    expect(res.slotEvents[0].kind).toBe('DAMAGE');
    expect(res.slotEvents[0].label).toBe('-2 DÉF');
  });

  it('detects shield (+DEF), slow, summon, destruction shatter, and LP heal', () => {
    const prev = makeGameView();
    prev.players[0].monsters[0] = makeSlotCard({
      instanceId: 'ally-m0',
      ownerPlayerId: 'p1',
      faction: 'LIGHT',
      effectiveDef: 4,
    });
    prev.players[1].monsters[1] = makeSlotCard({
      instanceId: 'opp-m1',
      ownerPlayerId: 'p2',
      faction: 'FIRE',
      effectiveDef: 2,
    });
    prev.players[1].familiars[0] = makeSlotCard({
      instanceId: 'opp-f0',
      ownerPlayerId: 'p2',
      cardType: 'FAMILIAR',
      faction: 'ICE',
      slowed: false,
    });

    const next = makeGameView({
      teamA: { teamId: 'TEAM_A', lp: 27, maxLp: 25, playerIds: ['p1'] },
    });
    next.players[0].monsters[0] = makeSlotCard({
      instanceId: 'ally-m0',
      ownerPlayerId: 'p1',
      faction: 'LIGHT',
      effectiveDef: 6,
    });
    next.players[0].spellArtifacts[0] = makeSlotCard({
      instanceId: 'ally-s0',
      ownerPlayerId: 'p1',
      cardType: 'SPELL_REACTIVE',
      faceDown: true,
      hidden: false,
    });
    // opp-m1 destroyed
    next.players[1].monsters[1] = null;
    // opp-f0 slowed
    next.players[1].familiars[0] = makeSlotCard({
      instanceId: 'opp-f0',
      ownerPlayerId: 'p2',
      cardType: 'FAMILIAR',
      faction: 'ICE',
      slowed: true,
    });

    const res = extractArenaVfxEvents(prev, next, new Map());

    expect(res.lpEvents.find((e) => e.teamId === 'TEAM_A')?.label).toBe('+2 PV');
    expect(res.lpEvents.find((e) => e.teamId === 'TEAM_A')?.kind).toBe('HEAL');

    const byKey = new Map(res.slotEvents.map((e) => [e.key, e]));
    expect(byKey.get(slotBurstKey('p1', 'monster', 0))?.kind).toBe('SHIELD');
    expect(byKey.get(slotBurstKey('p1', 'monster', 0))?.label).toBe('+2 DÉF');

    expect(byKey.get(slotBurstKey('p1', 'spell', 0))?.kind).toBe('REACTIVE');
    expect(byKey.get(slotBurstKey('p1', 'spell', 0))?.label).toBe('SCEAU POSÉ');

    expect(byKey.get(slotBurstKey('p2', 'monster', 1))?.kind).toBe('DESTROY');
    expect(byKey.get(slotBurstKey('p2', 'monster', 1))?.label).toBe('DÉTRUIT');

    expect(byKey.get(slotBurstKey('p2', 'familiar', 0))?.kind).toBe('SLOW');
    expect(byKey.get(slotBurstKey('p2', 'familiar', 0))?.label).toBe('RALENTIT');
  });

  it('extracts deck-elemental projectiles, defense tanking impacts, and direct avatar responses', () => {
    const prev = makeGameView({
      visualEvents: [],
    });
    const next = makeGameView({
      teamA: { teamId: 'TEAM_A', lp: 23, maxLp: 25, playerIds: ['p1'] },
      visualEvents: [
        {
          sequence: 1,
          faction: 'SHADOW',
          sourcePlayerId: 'p1',
          sourceInstanceId: 'm-atk',
          sourceZone: 'monster',
          sourceSlot: 0,
          targetPlayerId: 'p2',
          targetInstanceId: 'm-def',
          targetZone: 'monster',
          targetSlot: 2,
          directAttack: false,
          defenderPosture: 'DEFENSE',
          outcome: 'DEFENSE_BLOCKED',
          damageDealt: 0,
          recoilDamage: 2,
        },
        {
          sequence: 2,
          faction: 'ICE',
          sourcePlayerId: 'p2',
          sourceInstanceId: 'm-ice',
          sourceZone: 'monster',
          sourceSlot: 1,
          targetPlayerId: 'p1',
          targetInstanceId: null,
          targetZone: null,
          targetSlot: -1,
          directAttack: true,
          defenderPosture: 'DEFENSE',
          outcome: 'DIRECT_BLOCKED',
          damageDealt: 0,
          recoilDamage: 0,
        },
      ],
    });

    const batch = extractArenaVfxEvents(prev, next, new Map());

    expect(batch.projectiles.length).toBe(2);
    expect(batch.projectiles[0].faction).toBe('SHADOW');
    expect(batch.projectiles[0].sourceSlot).toBe(0);
    expect(batch.projectiles[0].targetSlot).toBe(2);
    expect(batch.projectiles[1].faction).toBe('ICE');
    expect(batch.projectiles[1].directAttack).toBe(true);

    const defBurst = batch.slotEvents.find((e) => e.key === slotBurstKey('p2', 'monster', 2));
    expect(defBurst?.kind).toBe('DEFENSE_BLOCK');
    expect(defBurst?.label).toBe('RENVOI -2 PV');
    expect(defBurst?.delayMs).toBeGreaterThan(0);

    const p1Impacts = batch.avatarImpacts.filter((a) => a.playerId === 'p1');
    expect(p1Impacts.some((a) => a.kind === 'DAMAGE' && a.label === '-2 PV')).toBe(true);
    expect(p1Impacts.some((a) => a.kind === 'DEFENSE_BLOCK' && a.label === 'BLOQUÉ')).toBe(true);

    expect(COSMETIC_CATALOG['avatar_pyromancer']?.label).toContain('Pyromancien');
    expect(COSMETIC_CATALOG['avatar_automaton']?.label).toContain('Automate');
  });

  it('classifies summon tiers (MYTHIC/HEAVY/STANDARD), mythic showcases, hitStrength, and synced shatter events', () => {
    const prev = makeGameView();
    prev.players[1].monsters[0] = makeSlotCard({
      instanceId: 'opp-m0',
      ownerPlayerId: 'p2',
      faction: 'SHADOW',
      effectiveDef: 4,
    });

    const next = makeGameView({
      visualEvents: [
        {
          sequence: 5,
          faction: 'FIRE',
          sourcePlayerId: 'p1',
          sourceInstanceId: 'ally-m0',
          sourceZone: 'monster',
          sourceSlot: 0,
          targetPlayerId: 'p2',
          targetInstanceId: 'opp-m0',
          targetZone: 'monster',
          targetSlot: 0,
          directAttack: false,
          defenderPosture: 'ATTACK',
          outcome: 'DEFENSE_BROKEN',
          damageDealt: 5,
          recoilDamage: 0,
        },
        {
          sequence: 6,
          faction: 'FIRE',
          sourcePlayerId: 'p1',
          sourceInstanceId: 'ally-m1',
          sourceZone: 'monster',
          sourceSlot: 1,
          targetPlayerId: 'p2',
          targetInstanceId: null,
          targetZone: null,
          targetSlot: -1,
          directAttack: true,
          defenderPosture: 'ATTACK',
          outcome: 'DIRECT_HIT',
          damageDealt: 5,
          recoilDamage: 0,
        },
      ],
    });
    next.players[0].monsters[0] = makeSlotCard({
      instanceId: 'ally-m0',
      ownerPlayerId: 'p1',
      cardId: 'FIRE_M_10',
      name: "Dragon de Braise d'Aurëa",
      faction: 'FIRE',
      epic: true,
      energyCost: 5,
      effectiveAtk: 5,
      effectiveDef: 4,
    });
    next.players[0].familiars[0] = makeSlotCard({
      instanceId: 'ally-f0',
      ownerPlayerId: 'p1',
      cardType: 'FAMILIAR',
      faction: 'FIRE',
      epic: false,
      energyCost: 3,
    });
    next.players[1].monsters[0] = null;

    const batch = extractArenaVfxEvents(prev, next, new Map());

    expect(batch.summonEvents.length).toBe(2);
    expect(batch.summonEvents[0].tier).toBe('MYTHIC');
    expect(batch.summonEvents[1].tier).toBe('HEAVY');

    expect(batch.mythicShowcases.length).toBe(1);
    expect(batch.mythicShowcases[0].cardName).toBe("Dragon de Braise d'Aurëa");
    expect(batch.mythicShowcases[0].title).toContain('ÉPIQUE');

    expect(batch.shatterEvents.length).toBe(1);
    expect(batch.shatterEvents[0].delayMs).toBeGreaterThan(0);

    expect(batch.projectiles[0].hitStrength).toBe('CRITICAL');
    expect(batch.projectiles[1].hitStrength).toBe('CRITICAL');
    expect(batch.projectiles[1].directAttack).toBe(true);

    const brokenPopup = batch.popups.find((p) => p.key === slotBurstKey('p2', 'monster', 0));
    expect(brokenPopup?.headline).toBe('BRISÉ!');
    expect(brokenPopup?.sublabel).toBe('GARDE CÉDÉE');
    expect(brokenPopup?.tone).toBe('CRIT');
    expect(brokenPopup?.delayMs).toBe(185);

    const directPopup = batch.popups.find((p) => p.targetKind === 'AVATAR' && p.key === 'p2');
    expect(directPopup?.headline).toBe('-5 PV');
    expect(directPopup?.sublabel).toBe('FRAPPE DIRECTE!');
    expect(directPopup?.tone).toBe('DIRECT');
    expect(directPopup?.delayMs).toBe(185);
  });

  it('provides painted artwork URLs for all 120 cards across FIRE, ICE, SHADOW, and LIGHT', () => {
    const factions: Faction[] = ['FIRE', 'ICE', 'SHADOW', 'LIGHT'];
    const buckets = ['M', 'F', 'S'] as const;
    for (const faction of factions) {
      for (const bucket of buckets) {
        for (let idx = 1; idx <= 10; idx++) {
          const id = `${faction}_${bucket}_${String(idx).padStart(2, '0')}`;
          const url = resolveCardArtworkUrl(id);
          expect(url, `Missing artwork mapping for ${id}`).toMatch(/^\/cards\/[a-z0-9-]+\.jpg$/);
        }
      }
    }
    expect(Object.keys(CARD_ARTWORK_REGISTRY).length).toBe(120);
  });
});
