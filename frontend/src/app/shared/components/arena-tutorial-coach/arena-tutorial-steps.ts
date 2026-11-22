import { PlayerBoardViewDto, PlayerGameView } from '../../../core/models/arcane.models';

export type TutorialStepId =
  | 'MULLIGAN'
  | 'BOARD_INTRO'
  | 'SUMMON_FIRST'
  | 'END_FIRST_TURN'
  | 'COMBAT_PHASE'
  | 'SUMMARY_ANTIMETA';

export interface TutorialStepDefinition {
  readonly index: number;
  readonly id: TutorialStepId;
  readonly title: string;
  readonly badge: string;
  readonly body: string;
  readonly actionHint: string;
  readonly anchorSelector: string | null;
}

export const TUTORIAL_TOTAL_STEPS = 6;

export const TUTORIAL_STEPS: readonly TutorialStepDefinition[] = [
  {
    index: 0,
    id: 'MULLIGAN',
    title: 'Main de départ & Mulligan',
    badge: 'Étape 1 / 6',
    body: 'Vous commencez avec 5 cartes. Renvoyez les cartes de coût 4-5 Énergie trop lourdes grâce à « Remplacer » pour viser des invocations rapides (1-2 Énergie), puis validez.',
    actionHint: 'Remplacez au besoin puis cliquez sur « Valider et commencer ».',
    anchorSelector: null,
  },
  {
    index: 1,
    id: 'BOARD_INTRO',
    title: 'Votre Plateau & Trêve du Tour 1',
    badge: 'Étape 2 / 6',
    body: 'Réduisez les 25 PV adverses à 0. Vos 4 emplacements Monstre protègent vos PV, vos 3 Familiers soutiennent le terrain et 2 emplacements accueillent Sorts ou Artéfacts. Au Tour 1, la Trêve empêche toute attaque.',
    actionHint: 'Observez votre main ci-dessous puis cliquez sur « Suivant » (ou jouez une carte).',
    anchorSelector: '#arena-player-hand',
  },
  {
    index: 2,
    id: 'SUMMON_FIRST',
    title: 'Énergie, Postures ATQ / DÉF & Invocation',
    badge: 'Étape 3 / 6',
    body: 'Chaque tour accorde +1 Énergie (max 10). En Attaque (ATQ), votre créature frappe fort ; en Défense (DÉF 90°), sa DÉF encaisse les coups et inflige un contrecoup aux attaquants trop faibles.',
    actionHint: 'Cliquez sur une carte « Jouable » (1 Énergie) dans votre main, puis « + Poser ici ».',
    anchorSelector: '#arena-hero-dock',
  },
  {
    index: 3,
    id: 'END_FIRST_TURN',
    title: 'Fin du Tour & Réponse Adverse',
    badge: 'Étape 4 / 6',
    body: 'Une fois votre Énergie dépensée, passez la main. À la fin du tour, vous ne pouvez conserver que 6 cartes en main maximum : tout surplus est défaussé.',
    actionHint: 'Cliquez sur « Fin du Tour » en bas à droite et observez le tour de l’IA.',
    anchorSelector: '#arena-end-turn-btn',
  },
  {
    index: 4,
    id: 'COMBAT_PHASE',
    title: 'Combat, Rempart & Mot-clé SURVOL',
    badge: 'Étape 5 / 6',
    body: 'Après le Mal d’Invocation d’un tour, vos créatures affichent « PRÊT ». Tout Monstre ennemi visible bloque l’accès aux PV ; SURVOL contourne uniquement les Familiers si aucun Monstre ne garde la ligne.',
    actionHint: 'Cliquez sur « Phase Combat », sélectionnez une créature PRÊTE et attaquez !',
    anchorSelector: '#arena-combat-btn',
  },
  {
    index: 5,
    id: 'SUMMARY_ANTIMETA',
    title: 'Sorts, Effets & 4 Lois Anti-Méta',
    badge: 'Étape 6 / 6',
    body: 'Certaines créatures ont un Effet (0 Énergie) par tour et vos Sorts Réactifs se déclenchent automatiquement face aux assauts ennemis. Vos decks respectent les 4 Lois : 30 cartes uniques (10 Monstres / 10 Familiers / 10 Sorts), max 2 factions et max 3 Épiques.',
    actionHint: 'Cliquez sur « Terminer le didacticiel » pour valider votre initiation !',
    anchorSelector: null,
  },
];

export interface TutorialProgressState {
  readonly currentIndex: number;
  readonly waitMessage: string | null;
}

/**
 * Advances the guided tutorial index monotonically based on authoritative game state,
 * while always preserving explicit user navigation via Previous/Next controls.
 */
export function computeAutoTutorialIndex(
  currentIndex: number,
  view: PlayerGameView | null,
  myBoard: PlayerBoardViewDto | null,
  isMyTurn: boolean,
): TutorialProgressState {
  if (!view || !myBoard) {
    return { currentIndex, waitMessage: null };
  }

  if (view.phase === 'MULLIGAN' && currentIndex === 0) {
    return { currentIndex: 0, waitMessage: null };
  }

  let next = currentIndex;
  if (view.phase !== 'MULLIGAN' && next === 0) {
    next = 1;
  }

  const myBoardCardCount = countBoardCards(myBoard);
  const myAttacksCount = view.combatLog.filter(
    (entry) =>
      entry.actorPlayerId === myBoard.userId &&
      (entry.eventType === 'COMBAT' || entry.eventType === 'DAMAGE'),
  ).length;

  // Player already summoned or played a card during BOARD_INTRO or SUMMON_FIRST.
  if (myBoardCardCount > 0 && next < 3) {
    next = 3;
  }

  // Player passed turn 1 and now controls Turn 3+ with combat unlocked.
  if (isMyTurn && !view.turn1TruceActive && view.turnNumber >= 3 && next < 4) {
    next = 4;
  }

  // Player actively entered Combat Phase on a combat-enabled turn.
  if (isMyTurn && view.phase === 'COMBAT_PHASE' && next === 3) {
    next = 4;
  }

  // Player resolved at least one attack during or after Step 5.
  if (myAttacksCount > 0 && next === 4) {
    next = 5;
  }

  let waitMessage: string | null = null;
  if (next === 3 && !isMyTurn && view.phase !== 'FINISHED') {
    waitMessage = 'Tour du Mage Automate en cours : votre prochain tour débloquera le combat…';
  } else if (next === 4 && isMyTurn && view.phase === 'MAIN_PHASE' && !view.turn1TruceActive) {
    waitMessage = 'Prêt pour l’assaut : cliquez maintenant sur « Phase Combat » !';
  }

  return { currentIndex: next, waitMessage };
}

function countBoardCards(board: PlayerBoardViewDto): number {
  const monsters = board.monsters.filter((c) => c !== null).length;
  const familiars = board.familiars.filter((c) => c !== null).length;
  const spells = board.spellArtifacts.filter((c) => c !== null).length;
  return monsters + familiars + spells;
}
