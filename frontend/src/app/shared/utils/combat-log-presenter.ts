import {
  CombatLogEntry,
  Faction,
  PlayerBoardViewDto,
  TeamId,
} from '../../core/models/arcane.models';

export type CombatLogCategory =
  | 'COMBAT'
  | 'DAMAGE'
  | 'DEFENSE'
  | 'HEAL'
  | 'SPELL'
  | 'SUMMON'
  | 'EFFECT'
  | 'TURN'
  | 'MATCH';

export type LogPerspective = 'ME' | 'ALLY' | 'OPPONENT' | 'SYSTEM';

export interface PresentedCombatLogEntry {
  sequence: number;
  roundNumber: number;
  turnNumber: number;
  eventType: string;
  actorLabel: string;
  perspective: LogPerspective;
  faction: Faction | null;
  category: CombatLogCategory;
  categoryLabel: string;
  iconName: string;
  text: string;
  isHighlight: boolean;
  pillClass: string;
  rowClass: string;
}

interface CategoryVisual {
  category: CombatLogCategory;
  label: string;
  iconName: string;
  pillClass: string;
  rowClass: string;
}

const ROUTINE_TURN_EVENTS = new Set<string>([
  'DRAW',
  'COMBAT_PHASE_START',
  'REACTION_WINDOW',
  'TURN_START',
  'MULLIGAN',
]);

function classifyEntry(eventType: string): { visual: CategoryVisual; isHighlight: boolean } {
  switch (eventType) {
    case 'COMBAT':
    case 'COMBAT_FIZZLE':
      return {
        isHighlight: true,
        visual: {
          category: 'COMBAT',
          label: 'Duel',
          iconName: 'lucideSwords',
          pillClass: 'border-amber-400/45 bg-amber-950/65 text-amber-200',
          rowClass: 'border-amber-500/35 bg-amber-950/15',
        },
      };

    case 'DESTROY':
    case 'CREATURE_DESTROYED_0_DEF':
      return {
        isHighlight: true,
        visual: {
          category: 'DAMAGE',
          label: 'Destruction',
          iconName: 'lucideSkull',
          pillClass: 'border-rose-400/50 bg-rose-950/70 text-rose-200',
          rowClass: 'border-rose-500/40 bg-rose-950/20',
        },
      };

    case 'LP_DAMAGE':
    case 'BLOOD_PACT':
      return {
        isHighlight: true,
        visual: {
          category: 'DAMAGE',
          label: 'Dégâts PV',
          iconName: 'lucideFlame',
          pillClass: 'border-rose-400/50 bg-rose-950/70 text-rose-200',
          rowClass: 'border-rose-500/40 bg-rose-950/20',
        },
      };

    case 'DIRECT_BLOCKED':
    case 'DESTROY_PREVENTED':
    case 'BLOCK_DIRECT':
    case 'CANCEL_ATTACK':
    case 'CANCEL_FAMILIAR':
    case 'REDIRECT':
    case 'SHIELD':
    case 'SPELL_IMMUNE':
      return {
        isHighlight: true,
        visual: {
          category: 'DEFENSE',
          label: 'Défense',
          iconName: 'lucideShield',
          pillClass: 'border-sky-400/50 bg-sky-950/70 text-sky-200',
          rowClass: 'border-sky-500/35 bg-sky-950/15',
        },
      };

    case 'HEAL':
    case 'CLEANSE':
    case 'BUFF':
    case 'STAT_MOD':
    case 'GAIN_ENERGY':
      return {
        isHighlight: true,
        visual: {
          category: 'HEAL',
          label: eventType === 'GAIN_ENERGY' ? 'Énergie' : 'Soutien',
          iconName: eventType === 'GAIN_ENERGY' ? 'lucideZap' : 'lucideSparkles',
          pillClass: 'border-emerald-400/45 bg-emerald-950/65 text-emerald-200',
          rowClass: 'border-emerald-500/35 bg-emerald-950/15',
        },
      };

    case 'CAST_SPELL':
    case 'CAST_SPELL_PENDING':
    case 'PLAY_PERSISTENT_SPELL':
    case 'SET_REACTIVE':
    case 'REACTION_TRIGGERED':
    case 'SPELL_LOCK':
    case 'MODAL_CHOICE':
      return {
        isHighlight: true,
        visual: {
          category: 'SPELL',
          label: eventType === 'REACTION_TRIGGERED' ? 'Réaction' : 'Sort',
          iconName: 'lucideWand2',
          pillClass: 'border-purple-400/50 bg-purple-950/70 text-purple-200',
          rowClass: 'border-purple-500/35 bg-purple-950/15',
        },
      };

    case 'PLAY_MONSTER':
    case 'PLAY_FAMILIAR':
    case 'ACTIVATE_EFFECT':
    case 'CHANGE_POSTURE':
      return {
        isHighlight: eventType !== 'CHANGE_POSTURE',
        visual: {
          category: 'SUMMON',
          label:
            eventType === 'CHANGE_POSTURE'
              ? 'Posture'
              : eventType === 'ACTIVATE_EFFECT'
                ? 'Effet'
                : 'Invocation',
          iconName: eventType === 'CHANGE_POSTURE' ? 'lucideRotateCw' : 'lucideZap',
          pillClass: 'border-arcane-gold/45 bg-arcane-gold/15 text-arcane-gold-bright',
          rowClass: 'border-arcane-gold/25 bg-arcane-recessed',
        },
      };

    case 'SLOW':
    case 'EXHAUST':
    case 'CAMOUFLAGE':
    case 'FORCE_DEFENSE':
    case 'SACRIFICE':
    case 'CURSE_EXPIRED':
      return {
        isHighlight: true,
        visual: {
          category: 'EFFECT',
          label: 'Altération',
          iconName: 'lucideSnowflake',
          pillClass: 'border-indigo-400/45 bg-indigo-950/65 text-indigo-200',
          rowClass: 'border-indigo-500/30 bg-indigo-950/15',
        },
      };

    case 'SCOUT':
    case 'SCOUT_EMPTY':
      return {
        isHighlight: true,
        visual: {
          category: 'EFFECT',
          label: 'Éclaireur',
          iconName: 'lucideEye',
          pillClass: 'border-cyan-400/45 bg-cyan-950/65 text-cyan-200',
          rowClass: 'border-cyan-500/30 bg-cyan-950/15',
        },
      };

    case 'MATCH_START':
    case 'MATCH_FINISHED':
    case 'TRUCE_ENDED':
    case 'ARCANUM_RELAY':
    case 'DISCONNECTED':
    case 'RECONNECTED':
      return {
        isHighlight: true,
        visual: {
          category: 'MATCH',
          label: eventType === 'TRUCE_ENDED' ? 'Combat ouvert' : 'Match',
          iconName: 'lucideTrophy',
          pillClass: 'border-arcane-gold/55 bg-arcane-gold/20 text-arcane-gold-bright',
          rowClass: 'border-arcane-gold/40 bg-arcane-gold/10',
        },
      };

    default:
      return {
        isHighlight: !ROUTINE_TURN_EVENTS.has(eventType),
        visual: {
          category: 'TURN',
          label: 'Phase',
          iconName: 'lucideClock',
          pillClass: 'border-slate-600/60 bg-slate-900/80 text-slate-300',
          rowClass: 'border-slate-800/80 bg-arcane-recessed',
        },
      };
  }
}

function pluralizeFrenchCount(text: string): string {
  return text
    .replace(/(\d+)\s+dégât\(s\)/gi, (_, n: string) => (Number(n) === 1 ? '1 dégât' : `${n} dégâts`))
    .replace(/(\d+)\s+jeton\(s\)/gi, (_, n: string) => (Number(n) === 1 ? '1 jeton' : `${n} jetons`))
    .replace(/(\d+)\s+carte\(s\)/gi, (_, n: string) => (Number(n) === 1 ? '1 carte' : `${n} cartes`));
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function personalizeSelfActorPrefix(message: string, actorUsername: string): string {
  if (!actorUsername) return message;
  const escaped = escapeRegExp(actorUsername);
  const replacements: Array<[RegExp, string]> = [
    [new RegExp(`^${escaped}\\s+invoque\\s+`), 'Vous invoquez '],
    [new RegExp(`^${escaped}\\s+active\\s+`), 'Vous activez '],
    [new RegExp(`^${escaped}\\s+lance\\s+`), 'Vous lancez '],
    [new RegExp(`^${escaped}\\s+pose\\s+`), 'Vous posez '],
    [new RegExp(`^${escaped}\\s+pioche\\s+`), 'Vous piochez '],
    [new RegExp(`^${escaped}\\s+remplace\\s+`), 'Vous remplacez '],
    [new RegExp(`^${escaped}\\s+choisit\\s+`), 'Vous choisissez '],
    [new RegExp(`^${escaped}\\s+entre en Phase de Combat\\.`), 'Passage en Phase de Combat.'],
    [new RegExp(`^${escaped}\\s+consulte\\s+`), 'Vous consultez '],
    [new RegExp(`^${escaped}\\s+défausse\\s+`), 'Vous défaussez '],
    [new RegExp(`^${escaped}\\s+a\\s+`), 'Vous avez '],
  ];

  for (const [pattern, replacement] of replacements) {
    if (pattern.test(message)) {
      return message.replace(pattern, replacement);
    }
  }
  return message;
}

function replaceTeamIdentifiers(
  message: string,
  myTeamId: TeamId | null,
  is1v1: boolean,
): string {
  const friendlyLpPhrase = is1v1 ? 'à vos PV' : 'aux PV alliés';
  const friendlySubject = is1v1 ? 'Vous' : 'Votre équipe';
  const enemySubject = is1v1 ? "L'adversaire" : "L'équipe adverse";
  const friendlyVictory = is1v1 ? 'Victoire !' : 'Victoire de votre équipe !';
  const enemyVictory = is1v1 ? "Victoire de l'adversaire !" : "Victoire de l'équipe adverse !";

  let out = message
    .replace(/\baux PV de TEAM_A\b/g, myTeamId === null ? "aux PV de l'Équipe A" : myTeamId === 'TEAM_A' ? friendlyLpPhrase : 'aux PV adverses')
    .replace(/\baux PV de TEAM_B\b/g, myTeamId === null ? "aux PV de l'Équipe B" : myTeamId === 'TEAM_B' ? friendlyLpPhrase : 'aux PV adverses')
    .replace(/\bVictoire de TEAM_A\s*!/g, myTeamId === null ? "Victoire de l'Équipe A !" : myTeamId === 'TEAM_A' ? friendlyVictory : enemyVictory)
    .replace(/\bVictoire de TEAM_B\s*!/g, myTeamId === null ? "Victoire de l'Équipe B !" : myTeamId === 'TEAM_B' ? friendlyVictory : enemyVictory)
    .replace(/\bL['’]Équipe A tombe à 0 PV/g, myTeamId === null ? "L'Équipe A tombe à 0 PV" : myTeamId === 'TEAM_A' ? `${friendlySubject} tombez à 0 PV` : `${enemySubject} tombe à 0 PV`)
    .replace(/\bL['’]Équipe B tombe à 0 PV/g, myTeamId === null ? "L'Équipe B tombe à 0 PV" : myTeamId === 'TEAM_B' ? `${friendlySubject} tombez à 0 PV` : `${enemySubject} tombe à 0 PV`)
    .replace(/\bTEAM_A\b/g, myTeamId === null ? 'Équipe A' : myTeamId === 'TEAM_A' ? friendlySubject : enemySubject)
    .replace(/\bTEAM_B\b/g, myTeamId === null ? 'Équipe B' : myTeamId === 'TEAM_B' ? friendlySubject : enemySubject)
    .replace(/\(ATTACK\)/g, '(Attaque)')
    .replace(/\(DEFENSE\)/g, '(Défense)')
    .replace(/\bposition ATTACK\b/g, 'posture d’Attaque')
    .replace(/\bposition DEFENSE\b/g, 'posture de Défense')
    .replace(/\s*->\s*/g, ' → ');

  return pluralizeFrenchCount(out);
}

export function presentCombatLogEntries(
  entries: CombatLogEntry[],
  viewerUserId: string,
  myTeamId: TeamId | null,
  players: PlayerBoardViewDto[],
): PresentedCombatLogEntry[] {
  const playerMap = new Map<string, PlayerBoardViewDto>();
  for (const p of players) {
    playerMap.set(p.userId, p);
  }
  const is1v1 = players.length <= 2;

  return entries.map((entry) => {
    const actor = entry.actorPlayerId ? playerMap.get(entry.actorPlayerId) ?? null : null;
    let perspective: LogPerspective = 'SYSTEM';
    let actorLabel = entry.actorUsername || 'Système';

    if (entry.actorPlayerId && entry.actorUsername !== 'Système') {
      if (entry.actorPlayerId === viewerUserId) {
        perspective = 'ME';
        actorLabel = 'Vous';
      } else if (actor && myTeamId && actor.teamId === myTeamId) {
        perspective = 'ALLY';
        actorLabel = `${actor.username} (Allié)`;
      } else {
        perspective = 'OPPONENT';
        actorLabel = actor?.username ?? entry.actorUsername;
      }
    }

    const { visual, isHighlight } = classifyEntry(entry.eventType);
    let text = replaceTeamIdentifiers(entry.message, myTeamId, is1v1);
    if (perspective === 'ME') {
      text = personalizeSelfActorPrefix(text, entry.actorUsername);
    }

    return {
      sequence: entry.sequence,
      roundNumber: entry.roundNumber,
      turnNumber: entry.turnNumber,
      eventType: entry.eventType,
      actorLabel,
      perspective,
      faction: actor?.primaryFaction ?? null,
      category: visual.category,
      categoryLabel: visual.label,
      iconName: visual.iconName,
      text,
      isHighlight,
      pillClass: visual.pillClass,
      rowClass: visual.rowClass,
    };
  });
}
