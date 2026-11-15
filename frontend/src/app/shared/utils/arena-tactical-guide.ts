import {
  CardType,
  CardViewDto,
  PlayerBoardViewDto,
  PlayerGameView,
} from '../../core/models/arcane.models';

export interface HandCardPlayability {
  playable: boolean;
  shortBadge: string | null;
  reason: string | null;
  category: 'READY' | 'NO_ENERGY' | 'LOCKED' | 'SLOT_FULL' | 'WAIT';
}

export interface TacticalGuidance {
  headline: string;
  subline: string;
  iconName: string;
  tone: 'MY_ACTION' | 'COMBAT' | 'REACTION' | 'WAIT' | 'DONE';
  playableCardCount: number;
  readyAttackerCount: number;
}

const MAX_MONSTER_SLOTS = 4;
const MAX_FAMILIAR_SLOTS = 3;
const MAX_SPELL_ARTIFACT_SLOTS = 2;

export function isSpellCategoryType(cardType: CardType | null | undefined): boolean {
  return (
    cardType === 'SPELL_NORMAL' ||
    cardType === 'SPELL_REACTIVE' ||
    cardType === 'SPELL_ULTIMATE' ||
    cardType === 'CURSE' ||
    cardType === 'ARTIFACT'
  );
}

function countOccupiedSlots(slots: readonly (CardViewDto | null)[]): number {
  let count = 0;
  for (const item of slots) {
    if (item !== null) {
      count += 1;
    }
  }
  return count;
}

export function evaluateHandCardPlayability(
  view: PlayerGameView | null,
  me: PlayerBoardViewDto | null,
  card: CardViewDto,
): HandCardPlayability {
  if (
    !view ||
    !me ||
    view.spectator ||
    view.phase !== 'MAIN_PHASE' ||
    view.activeTurnPlayerId !== me.userId
  ) {
    return { playable: false, shortBadge: null, reason: null, category: 'WAIT' };
  }

  const cost = card.energyCost ?? 0;
  if (cost > me.energyTokens) {
    const missing = cost - me.energyTokens;
    return {
      playable: false,
      shortBadge: `-${missing} ⚡`,
      reason: `Énergie insuffisante : il manque ${missing} ⚡.`,
      category: 'NO_ENERGY',
    };
  }

  if (isSpellCategoryType(card.cardType) && me.spellsLockedThisTurn) {
    return {
      playable: false,
      shortBadge: 'Verrouillé',
      reason: 'Vos Sorts sont verrouillés pour ce tour.',
      category: 'LOCKED',
    };
  }

  if (card.cardType === 'MONSTER') {
    if (countOccupiedSlots(me.monsters) >= MAX_MONSTER_SLOTS) {
      return {
        playable: false,
        shortBadge: '4/4 Plein',
        reason: 'Vos 4 emplacements de Monstres sont occupés.',
        category: 'SLOT_FULL',
      };
    }
    if (card.epic && me.monsters.some((m) => m?.epic)) {
      return {
        playable: false,
        shortBadge: '1 Épique',
        reason: 'Maximum 1 Monstre Épique ★ sur votre terrain.',
        category: 'SLOT_FULL',
      };
    }
  } else if (card.cardType === 'FAMILIAR') {
    if (countOccupiedSlots(me.familiars) >= MAX_FAMILIAR_SLOTS) {
      return {
        playable: false,
        shortBadge: '3/3 Plein',
        reason: 'Vos 3 emplacements de Familiers sont occupés.',
        category: 'SLOT_FULL',
      };
    }
  } else if (
    card.cardType === 'SPELL_REACTIVE' ||
    card.cardType === 'CURSE' ||
    card.cardType === 'ARTIFACT'
  ) {
    if (countOccupiedSlots(me.spellArtifacts) >= MAX_SPELL_ARTIFACT_SLOTS) {
      return {
        playable: false,
        shortBadge: '2/2 Plein',
        reason: 'Vos 2 emplacements de Soutien/Artefact sont occupés.',
        category: 'SLOT_FULL',
      };
    }
  }

  return {
    playable: true,
    shortBadge: 'Jouable',
    reason: null,
    category: 'READY',
  };
}

export function countReadyAttackers(
  view: PlayerGameView | null,
  me: PlayerBoardViewDto | null,
): number {
  if (!view || !me || view.turn1TruceActive) {
    return 0;
  }
  let ready = 0;
  for (const m of me.monsters) {
    if (m?.canAttackNow) ready += 1;
  }
  for (const f of me.familiars) {
    if (f?.canAttackNow) ready += 1;
  }
  return ready;
}

export function buildTacticalGuidance(
  view: PlayerGameView | null,
  me: PlayerBoardViewDto | null,
  isMyTurn: boolean,
  activeTurnPlayerName: string,
): TacticalGuidance {
  if (!view) {
    return {
      headline: 'Connexion au duel…',
      subline: 'Chargement de l’état du plateau.',
      iconName: 'lucideClock',
      tone: 'WAIT',
      playableCardCount: 0,
      readyAttackerCount: 0,
    };
  }

  const playableCardCount =
    me && isMyTurn && view.phase === 'MAIN_PHASE'
      ? me.hand.reduce(
          (acc, c) => acc + (evaluateHandCardPlayability(view, me, c).playable ? 1 : 0),
          0,
        )
      : 0;
  const readyAttackerCount = countReadyAttackers(view, me);

  if (view.phase === 'FINISHED') {
    return {
      headline: 'Match terminé',
      subline: 'Consultez la chronique du duel ou retournez au Sanctuaire.',
      iconName: 'lucideTrophy',
      tone: 'DONE',
      playableCardCount: 0,
      readyAttackerCount: 0,
    };
  }

  if (view.spectator) {
    return {
      headline: 'Mode Spectateur',
      subline: `Manche ${view.roundNumber} · Tour de ${activeTurnPlayerName}.`,
      iconName: 'lucideEye',
      tone: 'WAIT',
      playableCardCount: 0,
      readyAttackerCount: 0,
    };
  }

  if (view.phase === 'MULLIGAN') {
    const waiting = me?.mulliganCompleted ?? false;
    return waiting
      ? {
          headline: 'Main validée',
          subline: 'En attente de la confirmation adverse pour lancer le Tour 1.',
          iconName: 'lucideClock',
          tone: 'WAIT',
          playableCardCount: 0,
          readyAttackerCount: 0,
        }
      : {
          headline: 'Mulligan de départ',
          subline: 'Sélectionnez les cartes à remplacer puis validez votre main.',
          iconName: 'lucideLayers',
          tone: 'MY_ACTION',
          playableCardCount: 0,
          readyAttackerCount: 0,
        };
  }

  if (view.phase === 'WAITING_FOR_REACTION') {
    const respondingToMe = view.pendingReaction?.currentResponderPlayerId === view.viewerUserId;
    return respondingToMe
      ? {
          headline: 'À vous de réagir !',
          subline: 'Activez une carte Réactive admissible ou cliquez « Passer ».',
          iconName: 'lucideShield',
          tone: 'REACTION',
          playableCardCount: 0,
          readyAttackerCount: 0,
        }
      : {
          headline: 'Fenêtre de Réaction adverse',
          subline: 'L’adversaire peut contrer votre action avant résolution.',
          iconName: 'lucideClock',
          tone: 'WAIT',
          playableCardCount: 0,
          readyAttackerCount: 0,
        };
  }

  if (view.phase === 'WAITING_FOR_MODAL_CHOICE') {
    const choosing = view.pendingChoice?.ownerPlayerId === view.viewerUserId;
    return choosing
      ? {
          headline: 'Choix d’effet requis',
          subline: 'Sélectionnez une option pour résoudre votre carte.',
          iconName: 'lucideSparkles',
          tone: 'REACTION',
          playableCardCount: 0,
          readyAttackerCount: 0,
        }
      : {
          headline: `Décision de ${activeTurnPlayerName}`,
          subline: 'Résolution d’un effet à choix multiple en cours.',
          iconName: 'lucideClock',
          tone: 'WAIT',
          playableCardCount: 0,
          readyAttackerCount: 0,
        };
  }

  if (view.phase === 'WAITING_FOR_DISCARD') {
    return isMyTurn
      ? {
          headline: 'Limite de main atteinte (6 cartes)',
          subline: 'Défaussez votre surplus pour conclure ce tour.',
          iconName: 'lucideLayers',
          tone: 'REACTION',
          playableCardCount: 0,
          readyAttackerCount: 0,
        }
      : {
          headline: `Défausse de ${activeTurnPlayerName}`,
          subline: 'Le joueur actif ajuste sa main à 6 cartes.',
          iconName: 'lucideClock',
          tone: 'WAIT',
          playableCardCount: 0,
          readyAttackerCount: 0,
        };
  }

  if (!isMyTurn) {
    return {
      headline: `Tour de ${activeTurnPlayerName}`,
      subline: 'Observez ses invocations et préparez votre riposte.',
      iconName: 'lucideClock',
      tone: 'WAIT',
      playableCardCount: 0,
      readyAttackerCount: 0,
    };
  }

  if (view.phase === 'COMBAT_PHASE') {
    if (readyAttackerCount > 0) {
      return {
        headline: 'À vous · Phase de Combat',
        subline: `${readyAttackerCount} créature(s) prête(s) : cliquez sur une carte PRÊT puis sur la cible ou Attaque Directe.`,
        iconName: 'lucideSwords',
        tone: 'COMBAT',
        playableCardCount: 0,
        readyAttackerCount,
      };
    }
    return {
      headline: 'Combat terminé',
      subline: 'Aucune attaque restante — cliquez « Fin du tour ».',
      iconName: 'lucideCheckCircle',
      tone: 'MY_ACTION',
      playableCardCount: 0,
      readyAttackerCount: 0,
    };
  }

  // MAIN_PHASE and isMyTurn
  const energy = me?.energyTokens ?? 0;
  if (playableCardCount > 0 && readyAttackerCount > 0 && !view.turn1TruceActive) {
    return {
      headline: `À vous de jouer · ${energy} ⚡`,
      subline: `${playableCardCount} carte(s) jouable(s) en main — cliquez une carte illuminée ou passez en Combat (${readyAttackerCount} prête(s)).`,
      iconName: 'lucideZap',
      tone: 'MY_ACTION',
      playableCardCount,
      readyAttackerCount,
    };
  }

  if (playableCardCount > 0) {
    return {
      headline: `À vous de jouer · ${energy} ⚡`,
      subline: `${playableCardCount} carte(s) jouable(s) — cliquez une carte illuminée puis posez-la sur votre terrain.`,
      iconName: 'lucideZap',
      tone: 'MY_ACTION',
      playableCardCount,
      readyAttackerCount,
    };
  }

  if (readyAttackerCount > 0 && !view.turn1TruceActive) {
    return {
      headline: `Phase Principale · ${energy} ⚡`,
      subline: `Aucune carte jouable · passez en Combat pour attaquer (${readyAttackerCount} prête(s)).`,
      iconName: 'lucideSwords',
      tone: 'COMBAT',
      playableCardCount: 0,
      readyAttackerCount,
    };
  }

  if (view.turn1TruceActive) {
    return {
      headline: `À vous · Trêve du Tour 1 (${energy} ⚡)`,
      subline: 'Les attaques sont bloquées au 1er tour — cliquez « Fin du tour » quand vous êtes prêt.',
      iconName: 'lucideShield',
      tone: 'MY_ACTION',
      playableCardCount: 0,
      readyAttackerCount: 0,
    };
  }

  return {
    headline: `Tour prêt à conclure · ${energy} ⚡`,
    subline: 'Aucune carte jouable — cliquez « Fin du tour » pour laisser jouer votre adversaire.',
    iconName: 'lucideCheckCircle',
    tone: 'MY_ACTION',
    playableCardCount: 0,
    readyAttackerCount: 0,
  };
}
