import { AbilityDefinition, CardDefinition, CardType, CardViewDto, Faction } from '../../core/models/arcane.models';

/**
 * Registre d'illustrations peintes Dark-Fantasy (style Yu-Gi-Oh! / Magic: The Gathering)
 * stockées localement dans frontend/public/cards/ (100 % CSP img-src 'self').
 */
export const CARD_ARTWORK_REGISTRY: Readonly<Record<string, string>> = {
  // FIRE — 30/30 cartes illustrées
  FIRE_M_01: '/cards/fire-dragon.jpg',
  FIRE_M_02: '/cards/fire-beast.jpg',
  FIRE_M_03: '/cards/fire-dragon.jpg',
  FIRE_M_04: '/cards/fire-dragon.jpg',
  FIRE_M_05: '/cards/fire-beast.jpg',
  FIRE_M_06: '/cards/fire-phoenix.jpg',
  FIRE_M_07: '/cards/fire-golem.jpg',
  FIRE_M_08: '/cards/fire-phoenix.jpg',
  FIRE_M_09: '/cards/fire-dragon.jpg',
  FIRE_M_10: '/cards/fire-dragon.jpg',
  FIRE_F_01: '/cards/fire-fam-drake.jpg',
  FIRE_F_02: '/cards/fire-fam-cat.jpg',
  FIRE_F_03: '/cards/fire-fam-drake.jpg',
  FIRE_F_04: '/cards/fire-fam-drake.jpg',
  FIRE_F_05: '/cards/fire-fam-cat.jpg',
  FIRE_F_06: '/cards/fire-fam-cat.jpg',
  FIRE_F_07: '/cards/fire-fam-drake.jpg',
  FIRE_F_08: '/cards/fire-fam-cat.jpg',
  FIRE_F_09: '/cards/fire-fam-drake.jpg',
  FIRE_F_10: '/cards/fire-fam-drake.jpg',
  FIRE_S_01: '/cards/fire-spell.jpg',
  FIRE_S_02: '/cards/fire-spell.jpg',
  FIRE_S_03: '/cards/fire-spell.jpg',
  FIRE_S_04: '/cards/fire-spell.jpg',
  FIRE_S_05: '/cards/fire-spell.jpg',
  FIRE_S_06: '/cards/fire-spell.jpg',
  FIRE_S_07: '/cards/fire-spell.jpg',
  FIRE_S_08: '/cards/fire-spell.jpg',
  FIRE_S_09: '/cards/fire-spell.jpg',
  FIRE_S_10: '/cards/fire-spell.jpg',

  // ICE — 30/30 cartes illustrées
  ICE_M_01: '/cards/ice-dragon.jpg',
  ICE_M_02: '/cards/ice-wolf.jpg',
  ICE_M_03: '/cards/ice-dragon.jpg',
  ICE_M_04: '/cards/ice-yeti.jpg',
  ICE_M_05: '/cards/ice-wraith.jpg',
  ICE_M_06: '/cards/ice-banshee.jpg',
  ICE_M_07: '/cards/ice-golem.jpg',
  ICE_M_08: '/cards/ice-yeti.jpg',
  ICE_M_09: '/cards/ice-golem.jpg',
  ICE_M_10: '/cards/ice-dragon.jpg',
  ICE_F_01: '/cards/ice-familiar.jpg',
  ICE_F_02: '/cards/ice-familiar.jpg',
  ICE_F_03: '/cards/ice-familiar.jpg',
  ICE_F_04: '/cards/ice-familiar.jpg',
  ICE_F_05: '/cards/ice-familiar.jpg',
  ICE_F_06: '/cards/ice-familiar.jpg',
  ICE_F_07: '/cards/ice-familiar.jpg',
  ICE_F_08: '/cards/ice-familiar.jpg',
  ICE_F_09: '/cards/ice-familiar.jpg',
  ICE_F_10: '/cards/ice-wraith.jpg',
  ICE_S_01: '/cards/ice-spell.jpg',
  ICE_S_02: '/cards/ice-spell.jpg',
  ICE_S_03: '/cards/ice-spell.jpg',
  ICE_S_04: '/cards/ice-spell.jpg',
  ICE_S_05: '/cards/ice-spell.jpg',
  ICE_S_06: '/cards/ice-spell.jpg',
  ICE_S_07: '/cards/ice-spell.jpg',
  ICE_S_08: '/cards/ice-spell.jpg',
  ICE_S_09: '/cards/ice-spell.jpg',
  ICE_S_10: '/cards/ice-spell.jpg',

  // SHADOW — 30/30 cartes illustrées (Loups, Liches, Harpies, Hydres, Familiers & Éclipse)
  SHADOW_M_01: '/cards/shadow-wolf.jpg',
  SHADOW_M_02: '/cards/shadow-wolf.jpg',
  SHADOW_M_03: '/cards/shadow-harpy.jpg',
  SHADOW_M_04: '/cards/shadow-lich.jpg',
  SHADOW_M_05: '/cards/shadow-lich.jpg',
  SHADOW_M_06: '/cards/shadow-lich.jpg',
  SHADOW_M_07: '/cards/shadow-wolf.jpg',
  SHADOW_M_08: '/cards/shadow-abyss.jpg',
  SHADOW_M_09: '/cards/shadow-abyss.jpg',
  SHADOW_M_10: '/cards/shadow-abyss.jpg',
  SHADOW_F_01: '/cards/shadow-familiar.jpg',
  SHADOW_F_02: '/cards/shadow-familiar.jpg',
  SHADOW_F_03: '/cards/shadow-familiar.jpg',
  SHADOW_F_04: '/cards/shadow-harpy.jpg',
  SHADOW_F_05: '/cards/shadow-abyss.jpg',
  SHADOW_F_06: '/cards/shadow-familiar.jpg',
  SHADOW_F_07: '/cards/shadow-familiar.jpg',
  SHADOW_F_08: '/cards/shadow-familiar.jpg',
  SHADOW_F_09: '/cards/shadow-lich.jpg',
  SHADOW_F_10: '/cards/shadow-familiar.jpg',
  SHADOW_S_01: '/cards/shadow-eclipse.jpg',
  SHADOW_S_02: '/cards/shadow-eclipse.jpg',
  SHADOW_S_03: '/cards/shadow-eclipse.jpg',
  SHADOW_S_04: '/cards/shadow-eclipse.jpg',
  SHADOW_S_05: '/cards/shadow-eclipse.jpg',
  SHADOW_S_06: '/cards/shadow-eclipse.jpg',
  SHADOW_S_07: '/cards/shadow-eclipse.jpg',
  SHADOW_S_08: '/cards/shadow-eclipse.jpg',
  SHADOW_S_09: '/cards/shadow-eclipse.jpg',
  SHADOW_S_10: '/cards/shadow-eclipse.jpg',

  // LIGHT — 30/30 cartes illustrées (Licornes, Séraphins, Dragons Solaires, Griffons, Familiers & Sorts Divins)
  LIGHT_M_01: '/cards/light-unicorn.jpg',
  LIGHT_M_02: '/cards/light-seraph.jpg',
  LIGHT_M_03: '/cards/light-seraph.jpg',
  LIGHT_M_04: '/cards/light-dragon.jpg',
  LIGHT_M_05: '/cards/light-griffin.jpg',
  LIGHT_M_06: '/cards/light-seraph.jpg',
  LIGHT_M_07: '/cards/light-griffin.jpg',
  LIGHT_M_08: '/cards/light-seraph.jpg',
  LIGHT_M_09: '/cards/light-seraph.jpg',
  LIGHT_M_10: '/cards/light-dragon.jpg',
  LIGHT_F_01: '/cards/light-familiar.jpg',
  LIGHT_F_02: '/cards/light-familiar.jpg',
  LIGHT_F_03: '/cards/light-unicorn.jpg',
  LIGHT_F_04: '/cards/light-familiar.jpg',
  LIGHT_F_05: '/cards/light-dragon.jpg',
  LIGHT_F_06: '/cards/light-griffin.jpg',
  LIGHT_F_07: '/cards/light-familiar.jpg',
  LIGHT_F_08: '/cards/light-griffin.jpg',
  LIGHT_F_09: '/cards/light-familiar.jpg',
  LIGHT_F_10: '/cards/light-seraph.jpg',
  LIGHT_S_01: '/cards/light-spell.jpg',
  LIGHT_S_02: '/cards/light-spell.jpg',
  LIGHT_S_03: '/cards/light-spell.jpg',
  LIGHT_S_04: '/cards/light-seraph.jpg',
  LIGHT_S_05: '/cards/light-seraph.jpg',
  LIGHT_S_06: '/cards/light-spell.jpg',
  LIGHT_S_07: '/cards/light-spell.jpg',
  LIGHT_S_08: '/cards/light-spell.jpg',
  LIGHT_S_09: '/cards/light-spell.jpg',
  LIGHT_S_10: '/cards/light-spell.jpg',
};

export function resolveCardArtworkUrl(
  cardId: string | null | undefined,
  explicitUrl?: string | null,
): string | null {
  if (explicitUrl && explicitUrl.trim().length > 0) {
    return explicitUrl;
  }
  if (!cardId) {
    return null;
  }
  return CARD_ARTWORK_REGISTRY[cardId] ?? null;
}

export interface ProceduralArtTheme {
  backdropGradient: string;
  sigilColor: string;
  ringColor: string;
  accentHex: string;
  emblemLabel: string;
}

export type CardSubjectKind =
  | 'dragon'
  | 'phoenix'
  | 'griffin'
  | 'golem'
  | 'guardian'
  | 'beast'
  | 'owl'
  | 'serpent'
  | 'wisp'
  | 'reaper'
  | 'seraph'
  | 'campfire'
  | 'fireball'
  | 'vortex'
  | 'chains'
  | 'shield'
  | 'blade'
  | 'eclipse'
  | 'siphon'
  | 'rune';

export type CardEffectVisualKind =
  | 'DAMAGE'
  | 'DESTROY'
  | 'SHIELD'
  | 'ATK_BUFF'
  | 'HEAL'
  | 'SLOW'
  | 'STEALTH'
  | 'ENERGY'
  | 'SURVOL'
  | 'PEEK'
  | 'REACTIVE'
  | 'CURSE';

export interface CardIllustrationSpec {
  faction: Faction;
  subject: CardSubjectKind;
  effect: CardEffectVisualKind;
  secondaryEffect: CardEffectVisualKind | null;
  backdropGradient: string;
  primaryColor: string;
  secondaryColor: string;
  glowColor: string;
  effectTag: string;
}

interface FactionPaletteSpec {
  bg: string;
  sigil: string;
  ring: string;
  accent: string;
  secondary: string;
  glow: string;
}

const FACTION_ART_COLORS: Record<Faction, FactionPaletteSpec> = {
  FIRE: {
    bg: 'radial-gradient(circle at 50% 38%, rgb(249 115 22 / 0.44), rgb(154 52 18 / 0.34) 44%, rgb(11 14 23 / 0.96) 86%)',
    sigil: '#fb923c',
    ring: 'rgb(249 115 22 / 0.55)',
    accent: '#fb923c',
    secondary: '#fde047',
    glow: 'rgb(249 115 22 / 0.32)',
  },
  ICE: {
    bg: 'radial-gradient(circle at 50% 38%, rgb(56 189 248 / 0.40), rgb(12 74 110 / 0.32) 44%, rgb(11 14 23 / 0.96) 86%)',
    sigil: '#7dd3fc',
    ring: 'rgb(56 189 248 / 0.55)',
    accent: '#7dd3fc',
    secondary: '#e0f2fe',
    glow: 'rgb(56 189 248 / 0.30)',
  },
  SHADOW: {
    bg: 'radial-gradient(circle at 50% 38%, rgb(168 85 247 / 0.42), rgb(88 28 135 / 0.32) 44%, rgb(11 14 23 / 0.96) 86%)',
    sigil: '#d8b4fe',
    ring: 'rgb(168 85 247 / 0.55)',
    accent: '#c084fc',
    secondary: '#fb7185',
    glow: 'rgb(168 85 247 / 0.32)',
  },
  LIGHT: {
    bg: 'radial-gradient(circle at 50% 38%, rgb(250 204 21 / 0.38), rgb(133 77 14 / 0.28) 44%, rgb(11 14 23 / 0.96) 86%)',
    sigil: '#fef08a',
    ring: 'rgb(250 204 21 / 0.55)',
    accent: '#fde047',
    secondary: '#bae6fd',
    glow: 'rgb(250 204 21 / 0.30)',
  },
};

const TYPE_MOTIF_LABELS: Record<CardType, string> = {
  MONSTER: 'Créature de Front',
  FAMILIAR: 'Esprit Familier',
  SPELL_NORMAL: 'Incantation',
  SPELL_REACTIVE: 'Sceau Réactif',
  SPELL_ULTIMATE: 'Arcane Ultime',
  CURSE: 'Malédiction',
  ARTIFACT: 'Relique Runique',
};

export function getProceduralArtTheme(
  faction: Faction | null,
  cardType: CardType | null,
): ProceduralArtTheme {
  const fac = faction ? FACTION_ART_COLORS[faction] : FACTION_ART_COLORS.LIGHT;
  return {
    backdropGradient: fac.bg,
    sigilColor: fac.sigil,
    ringColor: fac.ring,
    accentHex: fac.accent,
    emblemLabel: cardType ? TYPE_MOTIF_LABELS[cardType] : 'Arcane',
  };
}

function normalizeForMatch(value: string | null | undefined): string {
  return (value ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();
}

function resolveCardSubject(
  name: string | null | undefined,
  cardType: CardType | null | undefined,
  description: string | null | undefined,
): CardSubjectKind {
  const n = normalizeForMatch(name);
  const d = normalizeForMatch(description);

  // Creatures & Familiars
  if (/dragon|hydre/.test(n)) return 'dragon';
  if (/phenix|phoenix/.test(n)) return 'phoenix';
  if (/griffon|harpie/.test(n)) return 'griffin';
  if (/chouette|hibou/.test(n)) return 'owl';
  if (/serpent/.test(n)) return 'serpent';
  if (/golem|troll|yeti|grizzli/.test(n)) return 'golem';
  if (/garde|chevalier|paladin|belier|lion/.test(n)) return 'guardian';
  if (/licorne|archange/.test(n)) return 'seraph';
  if (/loup|chien|chat|herisson|loutre|pingouin/.test(n)) return 'beast';
  if (/lutin|fee|esprit/.test(n)) return 'wisp';
  if (/spectre|liche|vampire|demon|goule|banshee|ombre agile|ombre rampante/.test(n)) return 'reaper';

  // Spells, Curses & Artifacts
  if (/feu de camp/.test(n)) return 'campfire';
  if (/chaine|frimas|gel|givre/.test(n) && !/protecteur|mur/.test(n)) return 'chains';
  if (/mur|bouclier|voile|aura|protecteur/.test(n)) return 'shield';
  if (/blizzard|tempete|souffle|brume|eruption|vent/.test(n)) return 'vortex';
  if (/boule de feu|explosion|brasier|flamme/.test(n)) return 'fireball';
  if (/lame|rayon|eclat/.test(n)) return 'blade';
  if (/eclipse|nuit|malediction/.test(n) && !/purificatrice/.test(n)) return 'eclipse';
  if (/siphon|main de|cri|ombres/.test(n)) return 'siphon';
  if (/soin|benediction|chaleur|purificatrice/.test(n)) return 'campfire';

  if (cardType === 'CURSE') return 'eclipse';
  if (cardType === 'SPELL_REACTIVE' || /empeche|annule/.test(d)) return 'shield';
  if (cardType === 'ARTIFACT') return 'shield';
  if (cardType === 'MONSTER') return 'guardian';
  if (cardType === 'FAMILIAR') return 'wisp';
  if (cardType === 'SPELL_ULTIMATE') return 'fireball';
  return 'rune';
}

interface ActionSummary {
  actions: Set<string>;
  atkDelta: number | null;
  defDelta: number | null;
  lpHeal: number | null;
  lpDamage: number | null;
  energyGain: number | null;
  aoe: boolean;
}

function collectActionSummary(abilities: readonly AbilityDefinition[] | null | undefined): ActionSummary {
  const actions = new Set<string>();
  let atkDelta: number | null = null;
  let defDelta: number | null = null;
  let lpHeal: number | null = null;
  let lpDamage: number | null = null;
  let energyGain: number | null = null;
  let aoe = false;

  const inspect = (action: string, target: string | null, value: number | null) => {
    actions.add(action);
    if (target && /ALL_|UP_TO_2/.test(target)) {
      aoe = true;
    }
    if ((action === 'MODIFY_ATK' || action === 'MODIFY_ATK_AND_DEF') && value !== null) {
      atkDelta = value;
    }
    if ((action === 'MODIFY_DEF' || action === 'MODIFY_ATK_AND_DEF' || action === 'MODIFY_DEF_OR_LP_IF_EMPTY') && value !== null) {
      defDelta = value;
    }
    if ((action === 'HEAL_LP' || action === 'HEAL_OR_GAIN_ENERGY_IF_LP_GE_20') && value !== null) {
      lpHeal = value;
    }
    if (action === 'DAMAGE_LP' && value !== null) {
      lpDamage = value;
    }
    if (action === 'GAIN_ENERGY' && value !== null) {
      energyGain = value;
    }
  };

  for (const ab of abilities ?? []) {
    inspect(ab.action, ab.target, ab.value);
    for (const opt of ab.options ?? []) {
      inspect(opt.action, opt.target, opt.value);
    }
  }

  return { actions, atkDelta, defDelta, lpHeal, lpDamage, energyGain, aoe };
}

function resolveEffectAndTag(
  cardType: CardType | null | undefined,
  keywords: readonly string[] | null | undefined,
  description: string | null | undefined,
  abilities: readonly AbilityDefinition[] | null | undefined,
): { effect: CardEffectVisualKind; secondaryEffect: CardEffectVisualKind | null; effectTag: string } {
  const kw = new Set((keywords ?? []).map((k) => k.toUpperCase()));
  const d = normalizeForMatch(description);
  const sum = collectActionSummary(abilities);
  const acts = sum.actions;
  const secondaryEnergy: CardEffectVisualKind | null = sum.energyGain ? 'ENERGY' : null;

  if (acts.has('CURSE') || cardType === 'CURSE' || acts.has('LOCK_OPPONENT_SPELLS')) {
    return {
      effect: 'CURSE',
      secondaryEffect: acts.has('APPLY_SLOW') ? 'SLOW' : null,
      effectTag: acts.has('LOCK_OPPONENT_SPELLS') ? 'VERROU SORTS' : 'MALÉDICTION',
    };
  }

  if (acts.has('SPELL_DESTRUCTION_IMMUNITY')) {
    return {
      effect: 'SHIELD',
      secondaryEffect: null,
      effectTag: 'IMMUNISÉ SORTS',
    };
  }

  if (acts.has('PREVENT_DESTRUCTION') || acts.has('CANCEL_ATTACK') || acts.has('CANCEL_AND_EXHAUST') || acts.has('BLOCK_DIRECT_ATTACKS') || acts.has('REDIRECT_ATTACK_TO_SELF') || cardType === 'SPELL_REACTIVE' || kw.has('REACTIVE')) {
    const shieldLike = acts.has('PREVENT_DESTRUCTION') || acts.has('BLOCK_DIRECT_ATTACKS') || acts.has('REDIRECT_ATTACK_TO_SELF') || acts.has('MODIFY_DEF');
    return {
      effect: 'REACTIVE',
      secondaryEffect: shieldLike ? 'SHIELD' : secondaryEnergy,
      effectTag: acts.has('BLOCK_DIRECT_ATTACKS')
        ? 'MUR PROTECTEUR'
        : acts.has('PREVENT_DESTRUCTION')
          ? 'BOUCLIER RÉACTIF'
          : 'CONTRE RÉACTIF',
    };
  }

  if (acts.has('APPLY_SLOW') || kw.has('RALENTIT')) {
    return {
      effect: 'SLOW',
      secondaryEffect: sum.lpDamage ? 'DAMAGE' : null,
      effectTag: sum.aoe ? 'GEL · ZONE' : 'RALENTIT',
    };
  }

  if (acts.has('GRANT_CAMOUFLAGE') || kw.has('CAMOUFLAGE')) {
    return {
      effect: 'STEALTH',
      secondaryEffect: secondaryEnergy,
      effectTag: 'CAMOUFLAGE',
    };
  }

  if (acts.has('PEEK_OPPONENT_TOP_CARD') || acts.has('PEEK_OPPONENT_TOP_CARD_BUFF_IF_SPELL')) {
    return {
      effect: 'PEEK',
      secondaryEffect: (sum.defDelta ?? 0) > 0 ? 'SHIELD' : null,
      effectTag: 'VISION DECK',
    };
  }

  if (kw.has('GARDIEN') || acts.has('KEYWORD_GARDIEN')) {
    return {
      effect: 'SHIELD',
      secondaryEffect: null,
      effectTag: 'GARDIEN',
    };
  }

  if (acts.has('CLEANSE_DEBUFFS')) {
    return {
      effect: 'SHIELD',
      secondaryEffect: secondaryEnergy ?? 'HEAL',
      effectTag: sum.energyGain ? `PURIFIE · +${sum.energyGain}⚡` : 'PURIFICATION',
    };
  }

  if (acts.has('REDUCE_LP_DAMAGE_TAKEN') || ((sum.defDelta ?? 0) > 0 && (sum.atkDelta ?? 0) <= 0)) {
    const defText = sum.defDelta ? `+${sum.defDelta} DÉF` : 'BOUCLIER';
    return {
      effect: 'SHIELD',
      secondaryEffect: secondaryEnergy,
      effectTag: sum.energyGain ? `${defText} · +${sum.energyGain}⚡` : sum.aoe ? `${defText} ZONE` : defText,
    };
  }

  if (acts.has('HEAL_LP') || acts.has('HEAL_OR_GAIN_ENERGY_IF_LP_GE_20')) {
    const dualSiphon = (sum.defDelta ?? 0) < 0 || (sum.lpDamage ?? 0) > 0;
    const tag = dualSiphon
      ? `DRAIN ±${sum.lpHeal ?? 2}`
      : acts.has('DRAW_CARD')
        ? `SOIN +${sum.lpHeal ?? 2} · PIOCHE`
        : `SOIN +${sum.lpHeal ?? 1} PV`;
    return {
      effect: 'HEAL',
      secondaryEffect: dualSiphon ? 'DAMAGE' : secondaryEnergy,
      effectTag: tag,
    };
  }

  if (acts.has('DESTROY_TARGET') || acts.has('DESTROY_ALL')) {
    return {
      effect: 'DESTROY',
      secondaryEffect: sum.lpDamage ? 'DAMAGE' : null,
      effectTag: sum.aoe ? 'DESTRUCTION ZONE' : 'DESTRUCTION',
    };
  }

  if ((sum.defDelta ?? 0) < 0 || (sum.atkDelta ?? 0) < 0 || (sum.lpDamage ?? 0) > 0) {
    const val = sum.defDelta ?? sum.lpDamage ?? sum.atkDelta;
    const tag = sum.lpDamage
      ? `${sum.lpDamage} DÉGÂT${sum.lpDamage > 1 ? 'S' : ''} PV`
      : sum.aoe
        ? `ZONE ${val} DÉF`
        : `FRAPPE ${val} DÉF`;
    return {
      effect: 'DAMAGE',
      secondaryEffect: acts.has('FORCE_DEFENSE_POSTURE') ? 'SLOW' : secondaryEnergy,
      effectTag: tag,
    };
  }

  if (acts.has('SACRIFICE_SELF') || (acts.has('GAIN_ENERGY') && !kw.has('SURVOL'))) {
    return {
      effect: 'ENERGY',
      secondaryEffect: null,
      effectTag: `ÉNERGIE +${sum.energyGain ?? 1}⚡`,
    };
  }

  if (kw.has('CHARGE') || acts.has('KEYWORD_CHARGE') || acts.has('ALLOW_EXTRA_ATTACK') || (sum.atkDelta ?? 0) > 0) {
    return {
      effect: 'ATK_BUFF',
      secondaryEffect: (sum.defDelta ?? 0) > 0 ? 'SHIELD' : secondaryEnergy,
      effectTag: kw.has('CHARGE')
        ? 'CHARGE'
        : acts.has('ALLOW_EXTRA_ATTACK')
          ? 'DOUBLE ATTAQUE'
          : (sum.defDelta ?? 0) > 0
            ? `+${sum.atkDelta} ATQ · +${sum.defDelta} DÉF`
            : `+${sum.atkDelta ?? 1} ATQ`,
    };
  }

  if (kw.has('SURVOL') || acts.has('KEYWORD_SURVOL')) {
    return {
      effect: 'SURVOL',
      secondaryEffect: secondaryEnergy,
      effectTag: sum.energyGain ? `SURVOL · +${sum.energyGain}⚡` : 'SURVOL',
    };
  }

  // Fallback text inference for cards built without abilitiesScript
  if (/bouclier|armure|mur|\+.*def/.test(d)) {
    return { effect: 'SHIELD', secondaryEffect: null, effectTag: 'BOUCLIER' };
  }
  if (/ralentit|givre|gel/.test(d)) {
    return { effect: 'SLOW', secondaryEffect: null, effectTag: 'RALENTIT' };
  }
  if (/regenere|recupere.*pv|soin/.test(d)) {
    return { effect: 'HEAL', secondaryEffect: null, effectTag: 'SOIN PV' };
  }
  if (/detruit|degat|-.*def/.test(d)) {
    return { effect: 'DAMAGE', secondaryEffect: null, effectTag: 'DÉGÂTS' };
  }
  return { effect: 'ATK_BUFF', secondaryEffect: null, effectTag: TYPE_MOTIF_LABELS[cardType ?? 'MONSTER'] };
}

export function buildCardIllustrationSpec(
  card: Pick<CardDefinition, 'faction' | 'cardType' | 'name' | 'description' | 'keywords' | 'abilitiesScript'> |
        Pick<CardViewDto, 'faction' | 'cardType' | 'name' | 'description' | 'keywords' | 'abilitiesScript'> |
        null | undefined,
): CardIllustrationSpec {
  const faction: Faction = card?.faction ?? 'LIGHT';
  const palette = FACTION_ART_COLORS[faction];
  const subject = resolveCardSubject(card?.name, card?.cardType, card?.description);
  const { effect, secondaryEffect, effectTag } = resolveEffectAndTag(
    card?.cardType,
    card?.keywords,
    card?.description,
    card?.abilitiesScript,
  );

  return {
    faction,
    subject,
    effect,
    secondaryEffect,
    backdropGradient: palette.bg,
    primaryColor: palette.accent,
    secondaryColor: palette.secondary,
    glowColor: palette.glow,
    effectTag,
  };
}
