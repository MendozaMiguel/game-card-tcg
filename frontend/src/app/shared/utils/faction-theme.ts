import {
  AbilityDefinition,
  AiDifficulty,
  CardDefinition,
  CardType,
  CardViewDto,
  DeckStats,
  DeckValidationResult,
  Faction,
  LeagueTier,
  MatchSource,
  PlayerBoardViewDto,
  PlayerGameView,
} from '../../core/models/arcane.models';

export interface FactionMeta {
  code: Faction;
  label: string;
  shortLabel: string;
  accentHex: string;
  badgeClass: string;
  borderClass: string;
  glowClass: string;
  surfaceClass: string;
}

export const FACTION_META: Record<Faction, FactionMeta> = {
  FIRE: {
    code: 'FIRE',
    label: 'Feu Ardent',
    shortLabel: 'Feu',
    accentHex: '#f97316',
    badgeClass: 'bg-orange-950/85 text-orange-200 border-orange-500/55',
    borderClass: 'border-orange-500/60',
    glowClass: 'shadow-orange-500/20',
    surfaceClass: 'from-orange-950/55 via-arcane-surface to-amber-950/30',
  },
  ICE: {
    code: 'ICE',
    label: 'Glace Éternelle',
    shortLabel: 'Glace',
    accentHex: '#38bdf8',
    badgeClass: 'bg-sky-950/85 text-sky-200 border-sky-400/55',
    borderClass: 'border-sky-400/60',
    glowClass: 'shadow-sky-400/20',
    surfaceClass: 'from-sky-950/55 via-arcane-surface to-cyan-950/35',
  },
  SHADOW: {
    code: 'SHADOW',
    label: 'Ombres Occultes',
    shortLabel: 'Ombres',
    accentHex: '#a855f7',
    badgeClass: 'bg-purple-950/85 text-purple-200 border-purple-400/55',
    borderClass: 'border-purple-400/60',
    glowClass: 'shadow-purple-500/20',
    surfaceClass: 'from-purple-950/55 via-arcane-surface to-fuchsia-950/30',
  },
  LIGHT: {
    code: 'LIGHT',
    label: 'Lumière Astrale',
    shortLabel: 'Lumière',
    accentHex: '#fef08a',
    badgeClass: 'bg-yellow-950/80 text-yellow-100 border-yellow-200/55',
    borderClass: 'border-yellow-200/60',
    glowClass: 'shadow-yellow-200/20',
    surfaceClass: 'from-yellow-950/40 via-arcane-surface to-amber-950/25',
  },
};

export const CARD_TYPE_LABELS: Record<CardType, string> = {
  MONSTER: 'Monstre',
  FAMILIAR: 'Familier',
  SPELL_NORMAL: 'Sort Normal',
  SPELL_REACTIVE: 'Sort Réactif',
  SPELL_ULTIMATE: 'Sort Ultime',
  CURSE: 'Malédiction',
  ARTIFACT: 'Artefact',
};

export const LEAGUE_LABELS: Record<LeagueTier, string> = {
  APPRENTI: 'Apprenti',
  INITIE: 'Initié',
  ADEPT: 'Adepte',
  ARCHIMAGE: 'Archimage',
  GRAND_ARCHIMAGE: 'Grand Archimage',
  MAITRE_ARCANES: 'Maître des Arcanes',
};

export interface MatchSourceMeta {
  code: MatchSource;
  label: string;
  shortLabel: string;
  badgeClass: string;
}

export const MATCH_SOURCE_META: Record<MatchSource, MatchSourceMeta> = {
  RANKED: {
    code: 'RANKED',
    label: 'Match Classé',
    shortLabel: 'Classé',
    badgeClass: 'border-purple-400/50 bg-purple-950/70 text-purple-200',
  },
  TOURNAMENT: {
    code: 'TOURNAMENT',
    label: 'Tournoi Conquest',
    shortLabel: 'Tournoi',
    badgeClass: 'border-amber-400/60 bg-amber-950/70 text-amber-200',
  },
  CUSTOM: {
    code: 'CUSTOM',
    label: 'Salon Personnalisé',
    shortLabel: 'Salon',
    badgeClass: 'border-sky-400/50 bg-sky-950/70 text-sky-200',
  },
  CASUAL: {
    code: 'CASUAL',
    label: 'Duel Amical',
    shortLabel: 'Amical',
    badgeClass: 'border-emerald-400/45 bg-emerald-950/60 text-emerald-200',
  },
  AI: {
    code: 'AI',
    label: 'Contre IA Automate',
    shortLabel: 'IA',
    badgeClass: 'border-cyan-400/45 bg-cyan-950/60 text-cyan-200',
  },
};

export interface AiDifficultyMeta {
  code: AiDifficulty;
  label: string;
  shortDesc: string;
  badgeClass: string;
}

export const AI_DIFFICULTY_ORDER: readonly AiDifficulty[] = ['EASY', 'MEDIUM', 'HARD'];

export const AI_DIFFICULTY_META: Record<AiDifficulty, AiDifficultyMeta> = {
  EASY: {
    code: 'EASY',
    label: 'Faible',
    shortDesc: 'Jeu prudent, sans combos complexes ni échanges risqués.',
    badgeClass: 'border-emerald-400/45 bg-emerald-950/60 text-emerald-200',
  },
  MEDIUM: {
    code: 'MEDIUM',
    label: 'Moyen',
    shortDesc: 'Rythme équilibré, gestion des menaces et réactions standards.',
    badgeClass: 'border-sky-400/45 bg-sky-950/60 text-sky-200',
  },
  HARD: {
    code: 'HARD',
    label: 'Fort',
    shortDesc: 'Priorité au létal, ciblage optimal et gestion serrée du terrain.',
    badgeClass: 'border-amber-400/55 bg-amber-950/65 text-amber-200',
  },
};

export const END_REASON_LABELS: Record<string, string> = {
  LP_ZERO: 'KO (0 PV)',
  DECK_OUT: 'Deck épuisé',
  FORFEIT: 'Abandon',
  DISCONNECT_TIMEOUT: 'Déconnexion',
};

export function resolveMatchSource(summary: {
  matchSource?: MatchSource;
  tournamentId?: string | null;
  customRoom?: boolean;
  aiMatch?: boolean;
  ranked?: boolean;
}): MatchSource {
  if (summary.matchSource) return summary.matchSource;
  if (summary.tournamentId) return 'TOURNAMENT';
  if (summary.customRoom) return 'CUSTOM';
  if (summary.aiMatch) return 'AI';
  return summary.ranked ? 'RANKED' : 'CASUAL';
}

export function formatDurationCompact(seconds: number | null | undefined): string {
  if (seconds == null || !Number.isFinite(seconds) || seconds <= 0) {
    return '';
  }
  const rounded = Math.round(seconds);
  if (rounded <= 0) {
    return '';
  }
  const hours = Math.floor(rounded / 3600);
  const minutes = Math.floor((rounded % 3600) / 60);
  const secs = rounded % 60;
  const intlAny = Intl as unknown as {
    DurationFormat?: new (
      locale: string,
      opts: { style: 'narrow' | 'short' | 'long' },
    ) => { format: (d: Record<string, number>) => string };
  };
  if (typeof intlAny.DurationFormat === 'function') {
    const duration: Record<string, number> = {};
    if (hours > 0) duration['hours'] = hours;
    if (minutes > 0) duration['minutes'] = minutes;
    if (secs > 0) duration['seconds'] = secs;
    const formatted = new intlAny.DurationFormat('fr-FR', { style: 'narrow' }).format(duration);
    if (formatted.trim().length > 0) {
      return formatted;
    }
  }
  if (hours > 0) return `${hours}h ${minutes}m`;
  if (minutes > 0) return `${minutes}m ${secs}s`;
  return `${secs}s`;
}

export const KEYWORD_DESCRIPTIONS: Record<string, string> = {
  SURVOL: "Peut attaquer directement les PV si l'adversaire n'a aucun Monstre.",
  VOL: "Peut attaquer directement les PV si l'adversaire n'a aucun Monstre.",
  CAMOUFLAGE: 'Ne peut être ciblé ni compter pour le Rempart jusqu’au prochain tour.',
  ESQUIVE: 'Ne peut être ciblé ni compter pour le Rempart jusqu’au prochain tour.',
  CHARGE: 'Bloque les effets réactifs adverses lors de son attaque.',
  RALENTIT: 'La cible ne peut pas déclarer d’attaque à son prochain tour.',
  GARDIEN: 'Doit être ciblé en priorité par les attaques ennemies.',
};

export interface CosmeticDisplayInfo {
  itemId: string;
  label: string;
  subtitle: string;
  swatchClass: string;
}

export const COSMETIC_CATALOG: Record<string, CosmeticDisplayInfo> = {
  card_back_default: {
    itemId: 'card_back_default',
    label: 'Grimoire de l’Ordre',
    subtitle: 'Dos standard doré & obsidienne',
    swatchClass: 'from-slate-900 via-amber-950/40 to-slate-950',
  },
  board_sanctuary: {
    itemId: 'board_sanctuary',
    label: 'Sanctuaire Astral',
    subtitle: 'Arène de pierre runique sombre',
    swatchClass: 'from-slate-950 via-indigo-950/35 to-slate-950',
  },
  pet_none: {
    itemId: 'pet_none',
    label: 'Aucun familier d’accompagnement',
    subtitle: 'Emplacement sobre',
    swatchClass: 'from-slate-900 to-slate-800',
  },
  energy_arcane: {
    itemId: 'energy_arcane',
    label: 'Cristal d’Éther Doré',
    subtitle: 'Jetons d’énergie classiques',
    swatchClass: 'from-amber-500/40 to-yellow-600/30',
  },
  avatar_initiate: {
    itemId: 'avatar_initiate',
    label: 'Mage de la Citadelle',
    subtitle: 'Portrait initial',
    swatchClass: 'from-amber-700/50 to-slate-900',
  },
  avatar_pyromancer: {
    itemId: 'avatar_pyromancer',
    label: 'Pyromancien d’Obsidienne',
    subtitle: 'Avatar Élément Feu Ardent',
    swatchClass: 'from-orange-700/55 to-rose-950',
  },
  avatar_cryomancer: {
    itemId: 'avatar_cryomancer',
    label: 'Cryomancien Boréal',
    subtitle: 'Avatar Élément Glace Éternelle',
    swatchClass: 'from-sky-700/55 to-slate-950',
  },
  avatar_shadowmage: {
    itemId: 'avatar_shadowmage',
    label: 'Ombrelame du Néant',
    subtitle: 'Avatar Élément Ombre Occulte',
    swatchClass: 'from-purple-700/55 to-indigo-950',
  },
  avatar_luminarch: {
    itemId: 'avatar_luminarch',
    label: 'Luminarque Solaire',
    subtitle: 'Avatar Élément Lumière Sacrée',
    swatchClass: 'from-amber-500/50 to-yellow-950',
  },
  avatar_automaton: {
    itemId: 'avatar_automaton',
    label: 'Sentinelle Automate',
    subtitle: 'Avatar du Mage Automate IA',
    swatchClass: 'from-teal-700/55 to-slate-950',
  },
  frame_apprenti: {
    itemId: 'frame_apprenti',
    label: 'Cadre de Bronze Runique',
    subtitle: 'Bordure de profil initiale',
    swatchClass: 'from-amber-600/40 to-stone-900',
  },
  'Apprenti Arcane': {
    itemId: 'Apprenti Arcane',
    label: 'Apprenti Arcane',
    subtitle: 'Titre officiel de l’Ordre',
    swatchClass: 'from-indigo-700/40 to-slate-900',
  },
  finisher_arcane_burst: {
    itemId: 'finisher_arcane_burst',
    label: 'Déflagration Arcanique',
    subtitle: 'Animation de victoire par défaut',
    swatchClass: 'from-amber-500/50 to-rose-700/40',
  },
};

export function evaluateAntiMetaLocally(cards: readonly CardDefinition[]): DeckValidationResult {
  const violations: string[] = [];
  const seenIds = new Set<string>();
  const seenNames = new Set<string>();
  let duplicateDetected = false;

  let monsterCount = 0;
  let standardMonsterCount = 0;
  let epicMonsterCount = 0;
  let familiarCount = 0;
  let spellCategoryCount = 0;
  let cost1To2Count = 0;
  let cost3To4Count = 0;
  let cost5PlusCount = 0;
  let rampCount = 0;
  const factionCounts: Partial<Record<Faction, number>> = {};

  for (const card of cards) {
    if (seenIds.has(card.cardId) || seenNames.has(card.name)) {
      duplicateDetected = true;
    }
    seenIds.add(card.cardId);
    seenNames.add(card.name);

    factionCounts[card.faction] = (factionCounts[card.faction] ?? 0) + 1;

    if (card.cardType === 'MONSTER') {
      monsterCount++;
      if (card.isEpic) epicMonsterCount++;
      else standardMonsterCount++;
    } else if (card.cardType === 'FAMILIAR') {
      familiarCount++;
    } else {
      spellCategoryCount++;
    }

    if (card.energyCost <= 2) cost1To2Count++;
    else if (card.energyCost <= 4) cost3To4Count++;
    else cost5PlusCount++;

    if (card.isRamp) rampCount++;
  }

  if (duplicateDetected) {
    violations.push("Loi d'Unicité (Singleton) violée : maximum 1 exemplaire par carte.");
  }
  if (cards.length !== 30) {
    violations.push(`Structure : exactement 30 cartes requises (${cards.length}/30).`);
  }
  if (monsterCount !== 10) {
    violations.push(`Structure : exactement 10 Monstres requis (${monsterCount}/10).`);
  }
  if (epicMonsterCount > 3) {
    violations.push(`Structure : maximum 3 Monstres Épiques★ autorisés (${epicMonsterCount}/3).`);
  }
  if (monsterCount === 10 && standardMonsterCount < 7) {
    violations.push(`Structure : minimum 7 Monstres Standards requis (${standardMonsterCount}/7).`);
  }
  if (familiarCount !== 10) {
    violations.push(`Structure : exactement 10 Familiers requis (${familiarCount}/10).`);
  }
  if (spellCategoryCount !== 10) {
    violations.push(
      `Structure : exactement 10 Sorts/Malédictions/Artefacts requis (${spellCategoryCount}/10).`,
    );
  }
  if (cost1To2Count !== 14 || cost3To4Count !== 12 || cost5PlusCount !== 4) {
    violations.push(
      `Courbe d'Énergie 14/12/4 requise (actuelle : ${cost1To2Count}/${cost3To4Count}/${cost5PlusCount}).`,
    );
  }

  const activeFactions = Object.entries(factionCounts) as [Faction, number][];
  if (activeFactions.length > 2) {
    violations.push(`Loi d'Allégeance : maximum 2 Factions autorisées (${activeFactions.length}/2).`);
  }
  for (const card of cards) {
    if (card.isEpic || card.cardType === 'SPELL_ULTIMATE') {
      const countInFaction = factionCounts[card.faction] ?? 0;
      if (countInFaction < 15) {
        violations.push(
          `Allégeance : '${card.name}' exige 15 cartes ${FACTION_META[card.faction].shortLabel} (${countInFaction}/15).`,
        );
      }
    }
  }
  if (rampCount > 3) {
    violations.push(`Plafond Ramp : maximum 3 générateurs d'Énergie (${rampCount}/3).`);
  }

  const sorted = [...activeFactions].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]));
  const stats: DeckStats = {
    totalCards: cards.length,
    monsterCount,
    standardMonsterCount,
    epicMonsterCount,
    familiarCount,
    spellCategoryCount,
    cost1To2Count,
    cost3To4Count,
    cost5PlusCount,
    rampCount,
    factionCounts,
    primaryFaction: sorted[0]?.[0] ?? null,
    secondaryFaction: sorted[1]?.[0] ?? null,
  };

  return {
    valid: violations.length === 0,
    violations,
    stats,
  };
}

export interface TargetRequirement {
  targetType: string;
  maxTargets: 1 | 2;
  optional: boolean;
  label: string;
}

const TARGET_LABELS: Record<string, string> = {
  ALLY_MONSTER: 'Choisissez un Monstre allié',
  ALLY_OTHER_MONSTER: 'Choisissez un autre Monstre allié',
  TARGETED_ALLY_MONSTER: 'Choisissez un Monstre allié',
  OPPONENT_CREATURE: 'Choisissez une créature adverse',
  OPPONENT_MONSTER: 'Choisissez un Monstre adverse',
  OPPONENT_FAMILIAR: 'Choisissez un Familier adverse',
  OPPONENT_CREATURE_COST_LE_2: 'Choisissez une créature adverse (Coût <= 2)',
  OPPONENT_FAMILIAR_DEF_1: 'Choisissez un Familier adverse (DEF <= 1)',
  OPPONENT_MONSTER_ATK_LE_2: 'Choisissez un Monstre adverse (ATQ <= 2)',
  OPPONENT_MONSTER_DEF_LE_3: 'Choisissez un Monstre adverse (DEF <= 3)',
  UP_TO_2_OPPONENT_CREATURES: "Choisissez jusqu'à 2 créatures adverses",
};

export function getTargetRequirementForTrigger(
  abilities: readonly AbilityDefinition[] | null | undefined,
  trigger: 'ON_PLAY' | 'ACTIVATED',
): TargetRequirement | null {
  if (!abilities) return null;
  for (const ab of abilities) {
    if (ab.trigger !== trigger || ab.action === 'MODAL_CHOICE' || !ab.target) {
      continue;
    }
    if (ab.target in TARGET_LABELS) {
      return {
        targetType: ab.target,
        maxTargets: ab.target === 'UP_TO_2_OPPONENT_CREATURES' ? 2 : 1,
        optional: false,
        label: TARGET_LABELS[ab.target],
      };
    }
  }
  return null;
}

export function getTargetRequirementForTargetString(target: string | null | undefined): TargetRequirement | null {
  if (!target || !(target in TARGET_LABELS)) {
    return null;
  }
  return {
    targetType: target,
    maxTargets: target === 'UP_TO_2_OPPONENT_CREATURES' ? 2 : 1,
    optional: false,
    label: TARGET_LABELS[target],
  };
}

export function findEligibleTargetsForRequirement(
  view: PlayerGameView,
  actorPlayer: PlayerBoardViewDto,
  sourceInstanceId: string | null,
  targetType: string,
): CardViewDto[] {
  const allyMonsters: CardViewDto[] = [];
  const oppMonsters: CardViewDto[] = [];
  const oppFamiliars: CardViewDto[] = [];
  const oppCreatures: CardViewDto[] = [];

  for (const p of view.players) {
    const isAlly = p.teamId === actorPlayer.teamId;
    const monsters = p.monsters.filter((c): c is CardViewDto => c !== null && !c.hidden);
    const familiars = p.familiars.filter((c): c is CardViewDto => c !== null && !c.hidden);
    if (isAlly) {
      allyMonsters.push(...monsters);
    } else {
      for (const m of monsters) {
        if (!m.camouflaged) {
          oppMonsters.push(m);
          oppCreatures.push(m);
        }
      }
      for (const f of familiars) {
        if (!f.camouflaged) {
          oppFamiliars.push(f);
          oppCreatures.push(f);
        }
      }
    }
  }

  switch (targetType) {
    case 'ALLY_MONSTER':
    case 'TARGETED_ALLY_MONSTER':
      return allyMonsters;
    case 'ALLY_OTHER_MONSTER':
      return allyMonsters.filter((c) => c.instanceId !== sourceInstanceId);
    case 'OPPONENT_CREATURE':
    case 'UP_TO_2_OPPONENT_CREATURES':
      return oppCreatures;
    case 'OPPONENT_MONSTER':
      return oppMonsters;
    case 'OPPONENT_FAMILIAR':
      return oppFamiliars;
    case 'OPPONENT_CREATURE_COST_LE_2':
      return oppCreatures.filter((c) => (c.energyCost ?? 99) <= 2);
    case 'OPPONENT_FAMILIAR_DEF_1':
      return oppFamiliars.filter((c) => (c.effectiveDef ?? c.baseDef ?? 99) <= 1);
    case 'OPPONENT_MONSTER_ATK_LE_2':
      return oppMonsters.filter((c) => (c.effectiveAtk ?? c.baseAtk ?? 99) <= 2);
    case 'OPPONENT_MONSTER_DEF_LE_3':
      return oppMonsters.filter((c) => (c.effectiveDef ?? c.baseDef ?? 99) <= 3);
    default:
      return [];
  }
}
