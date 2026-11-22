import {
  CardDefinition,
  CardViewDto,
  CombatLogEntry,
  CombatOutcome,
  CombatVisualEvent,
  Faction,
  PlayerBoardViewDto,
  PlayerGameView,
  TeamId,
  TeamViewDto,
} from '../../core/models/arcane.models';
import {
  buildCardIllustrationSpec,
  CardEffectVisualKind,
} from './card-artwork';
import type { CombatPopupTone } from '../components/arena-combat-popups/arena-combat-popups.component';

export type SlotZone = 'monster' | 'familiar' | 'spell';

export type SlotVfxKind =
  | 'DAMAGE'
  | 'DESTROY'
  | 'DEFENSE_BLOCK'
  | 'DEFENSE_BROKEN'
  | 'SHIELD'
  | 'ATK_BUFF'
  | 'HEAL'
  | 'SLOW'
  | 'STEALTH'
  | 'SUMMON'
  | 'CURSE'
  | 'REACTIVE';

export type AvatarImpactKind = 'DEFENSE_BLOCK' | 'DAMAGE';
export type SummonTier = 'STANDARD' | 'HEAVY' | 'MYTHIC';
export type ImpactHitStrength = 'LIGHT' | 'HEAVY' | 'CRITICAL';

export interface SummonImpactEvent {
  id: string;
  key: string;
  playerId: string;
  zone: SlotZone;
  slotIndex: number;
  faction: Faction;
  tier: SummonTier;
  cardName: string;
  cardId: string | null;
  cardType: CardViewDto['cardType'];
  energyCost: number;
  epic: boolean;
  baseAtk: number | null;
  baseDef: number | null;
}

export interface CombatShatterEvent {
  id: string;
  key: string;
  playerId: string;
  zone: SlotZone;
  slotIndex: number;
  faction: Faction;
  delayMs: number;
}

export interface MythicShowcaseBanner {
  id: string;
  title: string;
  subtitle: string;
  cardName: string;
  cardId: string | null;
  faction: Faction;
  isUltimate: boolean;
  atk: number | null;
  def: number | null;
  cost: number | null;
  durationMs: number;
}

export interface SlotVfxEvent {
  id: string;
  key: string;
  playerId: string;
  zone: SlotZone;
  slotIndex: number;
  faction: Faction;
  kind: SlotVfxKind;
  label: string;
  durationMs: number;
  delayMs?: number;
}

export interface AvatarImpactEvent {
  id: string;
  playerId: string;
  kind: AvatarImpactKind;
  faction: Faction;
  label: string;
  durationMs: number;
  delayMs?: number;
}

export interface VisualProjectileRequest {
  uid: string;
  faction: Faction;
  sourcePlayerId: string | null;
  sourceZone: SlotZone | null;
  sourceSlot: number;
  targetPlayerId: string | null;
  targetZone: SlotZone | null;
  targetSlot: number;
  directAttack: boolean;
  outcome: CombatOutcome;
  damageDealt: number;
  recoilDamage: number;
  hitStrength: ImpactHitStrength;
}

export interface LpVfxEvent {
  id: string;
  teamId: TeamId;
  delta: number;
  faction: Faction;
  kind: 'DAMAGE' | 'HEAL';
  label: string;
  durationMs: number;
  delayMs?: number;
}

export interface SpellWaveBanner {
  id: string;
  actorPlayerId: string | null;
  cardName: string;
  faction: Faction;
  effect: CardEffectVisualKind;
  effectTag: string;
  actorUsername: string;
  summary: string;
  durationMs: number;
}

export interface CombatPopupRequest {
  id: string;
  targetKind: 'SLOT' | 'AVATAR';
  key: string;
  faction: Faction;
  headline: string;
  sublabel: string | null;
  tone: CombatPopupTone;
  delayMs: number;
}

export interface ArenaVfxBatch {
  slotEvents: SlotVfxEvent[];
  lpEvents: LpVfxEvent[];
  spellWaves: SpellWaveBanner[];
  projectiles: VisualProjectileRequest[];
  avatarImpacts: AvatarImpactEvent[];
  summonEvents: SummonImpactEvent[];
  shatterEvents: CombatShatterEvent[];
  mythicShowcases: MythicShowcaseBanner[];
  popups: CombatPopupRequest[];
}

export const PROJECTILE_IMPACT_DELAY_MS = 185;

export function slotBurstKey(playerId: string, zone: SlotZone, slotIndex: number): string {
  return `${playerId}:${zone}:${slotIndex}`;
}

export function extractArenaVfxEvents(
  prevView: PlayerGameView | null,
  nextView: PlayerGameView,
  catalogByName: ReadonlyMap<string, CardDefinition>,
): ArenaVfxBatch {
  if (!prevView || prevView.matchId !== nextView.matchId) {
    return {
      slotEvents: [],
      lpEvents: [],
      spellWaves: [],
      projectiles: [],
      avatarImpacts: [],
      summonEvents: [],
      shatterEvents: [],
      mythicShowcases: [],
      popups: [],
    };
  }

  const prevLastSeq = prevView.combatLog.at(-1)?.sequence ?? 0;
  const newLogs = nextView.combatLog.filter((entry) => entry.sequence > prevLastSeq);
  const { waves: spellWaves, showcases: spellShowcases } = extractSpellWaves(newLogs, nextView, catalogByName);

  const prevVisualSeq = prevView.visualEvents?.at(-1)?.sequence ?? 0;
  const newVisualEvents = (nextView.visualEvents ?? []).filter((ev) => ev.sequence > prevVisualSeq);
  const projectiles: VisualProjectileRequest[] = [];
  const combatSlotEvents = new Map<string, SlotVfxEvent>();
  const delayedHitSlotKeys = new Set<string>();
  const avatarImpacts: AvatarImpactEvent[] = [];

  for (const vis of newVisualEvents) {
    projectiles.push(toProjectileRequest(vis));
    collectCombatVisualResponses(vis, combatSlotEvents, delayedHitSlotKeys, avatarImpacts);
  }

  const waveFaction = newVisualEvents.at(-1)?.faction ?? spellWaves.at(-1)?.faction ?? null;
  const slotEvents: SlotVfxEvent[] = [...combatSlotEvents.values()];
  const summonEvents: SummonImpactEvent[] = [];
  const shatterEvents: CombatShatterEvent[] = [];
  const mythicShowcases: MythicShowcaseBanner[] = [...spellShowcases];
  const prevByPlayer = new Map(prevView.players.map((p) => [p.userId, p]));
  const uidBase = `${nextView.turnNumber}-${nextView.combatLog.at(-1)?.sequence ?? 0}`;

  for (const nextPlayer of nextView.players) {
    const prevPlayer = prevByPlayer.get(nextPlayer.userId);
    if (!prevPlayer) {
      continue;
    }
    diffZoneSlots(
      prevPlayer.monsters,
      nextPlayer.monsters,
      nextPlayer.userId,
      'monster',
      waveFaction,
      uidBase,
      slotEvents,
      summonEvents,
      shatterEvents,
      mythicShowcases,
    );
    diffZoneSlots(
      prevPlayer.familiars,
      nextPlayer.familiars,
      nextPlayer.userId,
      'familiar',
      waveFaction,
      uidBase,
      slotEvents,
      summonEvents,
      shatterEvents,
      mythicShowcases,
    );
    diffZoneSlots(
      prevPlayer.spellArtifacts,
      nextPlayer.spellArtifacts,
      nextPlayer.userId,
      'spell',
      waveFaction,
      uidBase,
      slotEvents,
      summonEvents,
      shatterEvents,
      mythicShowcases,
    );
  }

  for (const shatter of shatterEvents) {
    if (delayedHitSlotKeys.has(shatter.key)) {
      shatter.delayMs = PROJECTILE_IMPACT_DELAY_MS;
    }
  }

  // Filter duplicate board diff destruction/damage events when combat visual event already drives that slot cleanly,
  // and synchronize damage/destroy burst with projectile arrival when targeted by a projectile.
  const dedupedSlots = slotEvents.filter((evt, idx) => {
    const combatEvt = combatSlotEvents.get(evt.key);
    if (combatEvt && combatEvt !== evt && (evt.kind === 'DESTROY' || evt.kind === 'DAMAGE')) {
      return false;
    }
    if (!combatEvt && delayedHitSlotKeys.has(evt.key) && evt.delayMs == null) {
      evt.delayMs = PROJECTILE_IMPACT_DELAY_MS;
    }
    return idx === slotEvents.findIndex((other) => other === evt);
  });

  const lpEvents: LpVfxEvent[] = [];
  diffTeamLp(prevView.teamA, nextView.teamA, waveFaction, uidBase, lpEvents);
  diffTeamLp(prevView.teamB, nextView.teamB, waveFaction, uidBase, lpEvents);
  if (projectiles.length > 0) {
    for (const lpEvt of lpEvents) {
      if (lpEvt.kind === 'DAMAGE') {
        lpEvt.delayMs = PROJECTILE_IMPACT_DELAY_MS;
      }
    }
  }

  const popups = buildCombatPopupRequests(dedupedSlots, avatarImpacts);

  return {
    slotEvents: dedupedSlots,
    lpEvents,
    spellWaves,
    projectiles,
    avatarImpacts,
    summonEvents,
    shatterEvents,
    mythicShowcases: mythicShowcases.length > 1 ? [mythicShowcases[mythicShowcases.length - 1]] : mythicShowcases,
    popups,
  };
}

function buildCombatPopupRequests(
  slotEvents: readonly SlotVfxEvent[],
  avatarImpacts: readonly AvatarImpactEvent[],
): CombatPopupRequest[] {
  const popups: CombatPopupRequest[] = [];

  for (const evt of slotEvents) {
    if (evt.kind === 'SUMMON' || evt.kind === 'REACTIVE') {
      continue;
    }
    const baseDelay = evt.delayMs ?? 0;
    let headline = evt.label;
    let sublabel: string | null = null;
    let tone: CombatPopupTone = 'DAMAGE';

    switch (evt.kind) {
      case 'DESTROY':
        headline = 'K.O.!';
        sublabel = 'DÉTRUIT';
        tone = 'DESTROY';
        break;
      case 'DEFENSE_BROKEN':
        headline = 'BRISÉ!';
        sublabel = 'GARDE CÉDÉE';
        tone = 'CRIT';
        break;
      case 'DEFENSE_BLOCK': {
        const recoilMatch = evt.label.match(/-(\d+)/);
        headline = recoilMatch ? `-${recoilMatch[1]} RENVOI` : 'PARÉ!';
        sublabel = recoilMatch ? 'CONTRE-CHOC' : 'DÉFENSE';
        tone = 'BLOCK';
        break;
      }
      case 'DAMAGE': {
        const dmgMatch = evt.label.match(/-(\d+)/);
        const dmg = dmgMatch ? Number(dmgMatch[1]) : 1;
        headline = `-${dmg}`;
        sublabel = dmg >= 4 ? 'CRITIQUE!' : 'DÉF';
        tone = dmg >= 4 ? 'CRIT' : 'DAMAGE';
        break;
      }
      case 'SHIELD':
        headline = evt.label;
        tone = 'BLOCK';
        break;
      case 'ATK_BUFF':
        headline = evt.label;
        sublabel = 'BOOST';
        tone = 'BUFF';
        break;
      case 'SLOW':
        headline = 'RALENTI';
        tone = 'DEBUFF';
        break;
      case 'STEALTH':
        headline = 'FURTIF';
        tone = 'DEBUFF';
        break;
      case 'CURSE':
      case 'HEAL':
        headline = evt.label;
        tone = evt.kind === 'HEAL' ? 'HEAL' : 'DEBUFF';
        break;
    }

    popups.push({
      id: `pop-${evt.id}`,
      targetKind: 'SLOT',
      key: evt.key,
      faction: evt.faction,
      headline,
      sublabel,
      tone,
      delayMs: baseDelay,
    });
  }

  for (const av of avatarImpacts) {
    const baseDelay = av.delayMs ?? 0;
    if (av.kind === 'DEFENSE_BLOCK') {
      popups.push({
        id: `pop-${av.id}`,
        targetKind: 'AVATAR',
        key: av.playerId,
        faction: av.faction,
        headline: 'BLOQUÉ!',
        sublabel: 'BOUCLIER ACTIF',
        tone: 'BLOCK',
        delayMs: baseDelay,
      });
    } else {
      const dmgMatch = av.label.match(/-(\d+)/);
      const dmg = dmgMatch ? Number(dmgMatch[1]) : 1;
      popups.push({
        id: `pop-${av.id}`,
        targetKind: 'AVATAR',
        key: av.playerId,
        faction: av.faction,
        headline: av.label,
        sublabel: dmg >= 3 ? 'FRAPPE DIRECTE!' : 'COUP DIRECT',
        tone: dmg >= 3 ? 'DIRECT' : 'DAMAGE',
        delayMs: baseDelay,
      });
    }
  }

  return popups;
}

function classifyHitStrength(ev: CombatVisualEvent): ImpactHitStrength {
  if (
    ev.outcome === 'DEFENSE_BROKEN' ||
    ev.outcome === 'TRADE' ||
    ev.damageDealt >= 4 ||
    (ev.directAttack && ev.outcome === 'DIRECT_HIT' && ev.damageDealt >= 3)
  ) {
    return 'CRITICAL';
  }
  if (
    ev.directAttack ||
    ev.outcome === 'ATTACKER_VICTORY' ||
    ev.outcome === 'DEFENSE_BLOCKED' ||
    ev.outcome === 'ATTACKER_REPELLED' ||
    ev.damageDealt >= 2
  ) {
    return 'HEAVY';
  }
  return 'LIGHT';
}

function toProjectileRequest(ev: CombatVisualEvent): VisualProjectileRequest {
  return {
    uid: `proj-${ev.sequence}`,
    faction: ev.faction,
    sourcePlayerId: ev.sourcePlayerId,
    sourceZone: ev.sourceZone,
    sourceSlot: ev.sourceSlot,
    targetPlayerId: ev.targetPlayerId,
    targetZone: ev.targetZone,
    targetSlot: ev.targetSlot,
    directAttack: ev.directAttack,
    outcome: ev.outcome,
    damageDealt: ev.damageDealt,
    recoilDamage: ev.recoilDamage,
    hitStrength: classifyHitStrength(ev),
  };
}

function collectCombatVisualResponses(
  ev: CombatVisualEvent,
  combatSlots: Map<string, SlotVfxEvent>,
  delayedKeys: Set<string>,
  avatarImpacts: AvatarImpactEvent[],
): void {
  if (ev.targetPlayerId && ev.targetZone && ev.targetSlot >= 0) {
    const targetKey = slotBurstKey(ev.targetPlayerId, ev.targetZone, ev.targetSlot);
    delayedKeys.add(targetKey);

    if (ev.outcome === 'DEFENSE_BLOCKED') {
      const label = ev.recoilDamage > 0 ? `RENVOI -${ev.recoilDamage} PV` : 'ENCAISSÉ · DÉF';
      combatSlots.set(targetKey, {
        id: `vfx-def-${ev.sequence}`,
        key: targetKey,
        playerId: ev.targetPlayerId,
        zone: ev.targetZone,
        slotIndex: ev.targetSlot,
        faction: ev.faction,
        kind: 'DEFENSE_BLOCK',
        label,
        durationMs: 1300,
        delayMs: PROJECTILE_IMPACT_DELAY_MS,
      });
    } else if (ev.outcome === 'DEFENSE_BROKEN') {
      combatSlots.set(targetKey, {
        id: `vfx-brk-${ev.sequence}`,
        key: targetKey,
        playerId: ev.targetPlayerId,
        zone: ev.targetZone,
        slotIndex: ev.targetSlot,
        faction: ev.faction,
        kind: 'DEFENSE_BROKEN',
        label: 'BRISÉ · DÉF',
        durationMs: 1250,
        delayMs: PROJECTILE_IMPACT_DELAY_MS,
      });
    } else if (ev.outcome === 'ATTACKER_REPELLED') {
      const label = ev.recoilDamage > 0 ? `PARÉ -${ev.recoilDamage} PV` : 'CONTRE · ATQ';
      combatSlots.set(targetKey, {
        id: `vfx-parry-${ev.sequence}`,
        key: targetKey,
        playerId: ev.targetPlayerId,
        zone: ev.targetZone,
        slotIndex: ev.targetSlot,
        faction: ev.faction,
        kind: 'DEFENSE_BLOCK',
        label,
        durationMs: 1250,
        delayMs: PROJECTILE_IMPACT_DELAY_MS,
      });
    }
  }

  if (ev.directAttack && ev.targetPlayerId) {
    if (ev.outcome === 'DIRECT_BLOCKED') {
      avatarImpacts.push({
        id: `av-blk-${ev.sequence}`,
        playerId: ev.targetPlayerId,
        kind: 'DEFENSE_BLOCK',
        faction: ev.faction,
        label: 'BLOQUÉ',
        durationMs: 1300,
        delayMs: PROJECTILE_IMPACT_DELAY_MS,
      });
    } else {
      avatarImpacts.push({
        id: `av-hit-${ev.sequence}`,
        playerId: ev.targetPlayerId,
        kind: 'DAMAGE',
        faction: ev.faction,
        label: ev.damageDealt > 0 ? `-${ev.damageDealt} PV` : 'IMPACT',
        durationMs: 1250,
        delayMs: PROJECTILE_IMPACT_DELAY_MS,
      });
    }
  }

  if (ev.recoilDamage > 0 && ev.sourcePlayerId) {
    avatarImpacts.push({
      id: `av-rec-${ev.sequence}`,
      playerId: ev.sourcePlayerId,
      kind: 'DAMAGE',
      faction: ev.faction,
      label: `-${ev.recoilDamage} PV`,
      durationMs: 1250,
      delayMs: PROJECTILE_IMPACT_DELAY_MS + 100,
    });
  }
}

function classifySummonTier(card: CardViewDto): SummonTier {
  const cost = card.energyCost ?? 0;
  if (card.epic || card.cardType === 'SPELL_ULTIMATE' || cost >= 5) {
    return 'MYTHIC';
  }
  if (cost >= 3) {
    return 'HEAVY';
  }
  return 'STANDARD';
}

function diffZoneSlots(
  prevSlots: readonly (CardViewDto | null)[],
  nextSlots: readonly (CardViewDto | null)[],
  playerId: string,
  zone: SlotZone,
  waveFaction: Faction | null,
  uidBase: string,
  out: SlotVfxEvent[],
  summonOut: SummonImpactEvent[],
  shatterOut: CombatShatterEvent[],
  showcaseOut: MythicShowcaseBanner[],
): void {
  const maxLen = Math.max(prevSlots.length, nextSlots.length);
  for (let i = 0; i < maxLen; i += 1) {
    const prevCard = prevSlots[i] ?? null;
    const nextCard = nextSlots[i] ?? null;
    const event = resolveSlotTransition(prevCard, nextCard, playerId, zone, i, waveFaction, uidBase);
    if (event) {
      out.push(event);
    }

    const key = slotBurstKey(playerId, zone, i);
    if ((!prevCard && nextCard) || (prevCard && nextCard && prevCard.instanceId !== nextCard.instanceId)) {
      const isFaceDown = nextCard.faceDown || nextCard.hidden;
      const faction: Faction = nextCard.faction ?? (isFaceDown ? 'SHADOW' : 'LIGHT');
      const tier: SummonTier = isFaceDown ? 'STANDARD' : classifySummonTier(nextCard);
      const energyCost = nextCard.energyCost ?? 0;
      summonOut.push({
        id: `${uidBase}:sum:${key}`,
        key,
        playerId,
        zone,
        slotIndex: i,
        faction,
        tier,
        cardName: nextCard.name ?? 'Arcane',
        cardId: nextCard.cardId,
        cardType: nextCard.cardType,
        energyCost,
        epic: !!nextCard.epic,
        baseAtk: nextCard.effectiveAtk ?? nextCard.baseAtk,
        baseDef: nextCard.effectiveDef ?? nextCard.baseDef,
      });

      if (!isFaceDown && tier === 'MYTHIC') {
        showcaseOut.push({
          id: `${uidBase}:mythic:${nextCard.instanceId}`,
          title: nextCard.epic ? 'INVOCATION ÉPIQUE ★' : nextCard.cardType === 'SPELL_ULTIMATE' ? 'SORT ULTIME ⚡' : 'COLOSSE INVOCATOIRE',
          subtitle: nextCard.epic ? 'Présence Légendaire sur le Plateau' : `Coût ${energyCost} Énergie · Entrée Fracassante`,
          cardName: nextCard.name ?? 'Carte Mythique',
          cardId: nextCard.cardId,
          faction,
          isUltimate: nextCard.cardType === 'SPELL_ULTIMATE',
          atk: nextCard.effectiveAtk ?? nextCard.baseAtk,
          def: nextCard.effectiveDef ?? nextCard.baseDef,
          cost: nextCard.energyCost,
          durationMs: 1450,
        });
      }
    } else if (prevCard && !nextCard) {
      shatterOut.push({
        id: `${uidBase}:shat:${key}`,
        key,
        playerId,
        zone,
        slotIndex: i,
        faction: waveFaction ?? prevCard.faction ?? 'FIRE',
        delayMs: 0,
      });
    }
  }
}

function resolveSlotTransition(
  prevCard: CardViewDto | null,
  nextCard: CardViewDto | null,
  playerId: string,
  zone: SlotZone,
  slotIndex: number,
  waveFaction: Faction | null,
  uidBase: string,
): SlotVfxEvent | null {
  const key = slotBurstKey(playerId, zone, slotIndex);
  const id = `${uidBase}:${key}`;

  if ((!prevCard && nextCard) || (prevCard && nextCard && prevCard.instanceId !== nextCard.instanceId)) {
    const isFaceDown = nextCard.faceDown || nextCard.hidden;
    const faction: Faction = nextCard.faction ?? (isFaceDown ? 'SHADOW' : 'LIGHT');
    return {
      id,
      key,
      playerId,
      zone,
      slotIndex,
      faction,
      kind: isFaceDown ? 'REACTIVE' : 'SUMMON',
      label: isFaceDown ? 'SCEAU POSÉ' : zone === 'spell' ? 'SOUTIEN' : 'INVOCATION',
      durationMs: 1050,
    };
  }

  if (prevCard && !nextCard) {
    const faction: Faction = waveFaction ?? prevCard.faction ?? 'FIRE';
    return {
      id,
      key,
      playerId,
      zone,
      slotIndex,
      faction,
      kind: 'DESTROY',
      label: 'DÉTRUIT',
      durationMs: 1100,
    };
  }

  if (!prevCard || !nextCard) {
    return null;
  }

  const prevDef = prevCard.effectiveDef ?? prevCard.baseDef ?? 0;
  const nextDef = nextCard.effectiveDef ?? nextCard.baseDef ?? 0;
  const prevAtk = prevCard.effectiveAtk ?? prevCard.baseAtk ?? 0;
  const nextAtk = nextCard.effectiveAtk ?? nextCard.baseAtk ?? 0;

  if (nextDef < prevDef) {
    return {
      id,
      key,
      playerId,
      zone,
      slotIndex,
      faction: waveFaction ?? nextCard.faction ?? 'FIRE',
      kind: 'DAMAGE',
      label: `-${prevDef - nextDef} DÉF`,
      durationMs: 1200,
    };
  }

  if (nextDef > prevDef) {
    return {
      id,
      key,
      playerId,
      zone,
      slotIndex,
      faction: nextCard.faction ?? 'LIGHT',
      kind: 'SHIELD',
      label: `+${nextDef - prevDef} DÉF`,
      durationMs: 1200,
    };
  }

  if (nextAtk > prevAtk) {
    return {
      id,
      key,
      playerId,
      zone,
      slotIndex,
      faction: nextCard.faction ?? 'FIRE',
      kind: 'ATK_BUFF',
      label: `+${nextAtk - prevAtk} ATQ`,
      durationMs: 1150,
    };
  }

  if (nextAtk < prevAtk) {
    return {
      id,
      key,
      playerId,
      zone,
      slotIndex,
      faction: waveFaction ?? 'SHADOW',
      kind: 'CURSE',
      label: `-${prevAtk - nextAtk} ATQ`,
      durationMs: 1150,
    };
  }

  if (!prevCard.slowed && nextCard.slowed) {
    return {
      id,
      key,
      playerId,
      zone,
      slotIndex,
      faction: 'ICE',
      kind: 'SLOW',
      label: 'RALENTIT',
      durationMs: 1200,
    };
  }

  if (!prevCard.camouflaged && nextCard.camouflaged) {
    return {
      id,
      key,
      playerId,
      zone,
      slotIndex,
      faction: 'SHADOW',
      kind: 'STEALTH',
      label: 'CAMOUFLAGE',
      durationMs: 1150,
    };
  }

  const prevCurse = prevCard.curseRemainingTurns ?? 0;
  const nextCurse = nextCard.curseRemainingTurns ?? 0;
  if (prevCurse === 0 && nextCurse > 0) {
    return {
      id,
      key,
      playerId,
      zone,
      slotIndex,
      faction: 'SHADOW',
      kind: 'CURSE',
      label: 'MALÉDICTION',
      durationMs: 1200,
    };
  }

  if (prevCard.faceDown && !nextCard.faceDown) {
    return {
      id,
      key,
      playerId,
      zone,
      slotIndex,
      faction: nextCard.faction ?? 'LIGHT',
      kind: 'REACTIVE',
      label: 'RÉVÉLATION',
      durationMs: 1150,
    };
  }

  return null;
}

function diffTeamLp(
  prevTeam: TeamViewDto,
  nextTeam: TeamViewDto,
  waveFaction: Faction | null,
  uidBase: string,
  out: LpVfxEvent[],
): void {
  const delta = nextTeam.lp - prevTeam.lp;
  if (delta === 0) {
    return;
  }
  const isDamage = delta < 0;
  out.push({
    id: `${uidBase}:lp:${nextTeam.teamId}`,
    teamId: nextTeam.teamId,
    delta,
    faction: waveFaction ?? (isDamage ? 'FIRE' : 'LIGHT'),
    kind: isDamage ? 'DAMAGE' : 'HEAL',
    label: isDamage ? `${delta} PV` : `+${delta} PV`,
    durationMs: 1350,
  });
}

function extractSpellWaves(
  newLogs: readonly CombatLogEntry[],
  nextView: PlayerGameView,
  catalogByName: ReadonlyMap<string, CardDefinition>,
): { waves: SpellWaveBanner[]; showcases: MythicShowcaseBanner[] } {
  const waves: SpellWaveBanner[] = [];
  const showcases: MythicShowcaseBanner[] = [];
  for (const entry of newLogs) {
    if (
      entry.eventType !== 'CAST_SPELL'
      && entry.eventType !== 'REACTION_TRIGGERED'
      && entry.eventType !== 'ACTIVATE_EFFECT'
      && entry.eventType !== 'PLAY_PERSISTENT_SPELL'
    ) {
      continue;
    }

    const cardName = extractCardNameFromLog(entry);
    const matchedCard = resolveCardForLog(cardName, entry.actorPlayerId, nextView.players, catalogByName);
    if (!matchedCard) {
      continue;
    }

    const spec = buildCardIllustrationSpec(matchedCard);
    const resolvedName = matchedCard.name ?? cardName ?? 'Sort arcanique';
    waves.push({
      id: `wave-${entry.sequence}`,
      actorPlayerId: entry.actorPlayerId,
      cardName: resolvedName,
      faction: spec.faction,
      effect: spec.effect,
      effectTag: spec.effectTag,
      actorUsername: entry.actorUsername,
      summary: entry.message,
      durationMs: 1500,
    });

    const isUltimate = matchedCard.cardType === 'SPELL_ULTIMATE';
    const cost = matchedCard.energyCost ?? 0;
    const isEpicCard = 'isEpic' in matchedCard ? matchedCard.isEpic : !!matchedCard.epic;
    if (isUltimate || isEpicCard || cost >= 5) {
      showcases.push({
        id: `wave-mythic-${entry.sequence}`,
        title: isUltimate ? 'SORT ULTIME DÉCHAÎNÉ ⚡' : 'INCANTATION MAJEURE ★',
        subtitle: `${entry.actorUsername} · ${spec.effectTag}`,
        cardName: resolvedName,
        cardId: matchedCard.cardId ?? null,
        faction: spec.faction,
        isUltimate,
        atk: null,
        def: null,
        cost: matchedCard.energyCost,
        durationMs: 1450,
      });
    }
  }

  // Keep only the latest wave if multiple triggered synchronously in one log tick to stay visually focused.
  return {
    waves: waves.length > 1 ? [waves[waves.length - 1]] : waves,
    showcases: showcases.length > 1 ? [showcases[showcases.length - 1]] : showcases,
  };
}

function extractCardNameFromLog(entry: CombatLogEntry): string | null {
  const msg = entry.message.trim();
  const patterns = [
    /\sactive en réaction\s*:\s*(.+?)(?:\s*!)?$/,
    /\sactive l'effet de\s+(.+?)\s*\(0 Énergie\)\.?$/,
    /\sactive\s+(.+?)\s+sur sa Ligne de Soutien\.?$/,
    /\slance\s+(.+?)(?:\s*!|\.)?$/,
  ];
  for (const regex of patterns) {
    const match = msg.match(regex);
    if (match?.[1]) {
      return match[1].trim();
    }
  }
  return null;
}

function resolveCardForLog(
  cardName: string | null,
  actorPlayerId: string | null,
  players: readonly PlayerBoardViewDto[],
  catalogByName: ReadonlyMap<string, CardDefinition>,
): CardDefinition | CardViewDto | null {
  if (cardName) {
    const fromCatalog = catalogByName.get(cardName.toLowerCase());
    if (fromCatalog) {
      return fromCatalog;
    }
    const targetName = cardName.toLowerCase();
    const preferredPlayers = actorPlayerId
      ? [...players].sort((a, b) => (a.userId === actorPlayerId ? -1 : b.userId === actorPlayerId ? 1 : 0))
      : players;

    for (const player of preferredPlayers) {
      const candidates = [
        ...player.discard,
        ...player.monsters,
        ...player.familiars,
        ...player.spellArtifacts,
      ];
      for (const candidate of candidates) {
        if (candidate?.name && candidate.name.toLowerCase() === targetName) {
          return candidate;
        }
      }
    }
  }
  return null;
}
