export type Faction = 'FIRE' | 'ICE' | 'SHADOW' | 'LIGHT';

export type CardType =
  | 'MONSTER'
  | 'FAMILIAR'
  | 'SPELL_NORMAL'
  | 'SPELL_REACTIVE'
  | 'SPELL_ULTIMATE'
  | 'CURSE'
  | 'ARTIFACT';

export type LeagueTier =
  | 'APPRENTI'
  | 'INITIE'
  | 'ADEPT'
  | 'ARCHIMAGE'
  | 'GRAND_ARCHIMAGE'
  | 'MAITRE_ARCANES';

export type CosmeticType =
  | 'CARD_FOIL'
  | 'CARD_BACK'
  | 'BOARD_SKIN'
  | 'COMPANION_PET'
  | 'ENERGY_SKIN'
  | 'MAGE_PROFILE'
  | 'EMOTE_FINISHER';

export type GameMode = 'DUEL_1V1' | 'TEAM_2V2';
export type AiDifficulty = 'EASY' | 'MEDIUM' | 'HARD';
export type TeamId = 'TEAM_A' | 'TEAM_B';
export type Posture = 'ATTACK' | 'DEFENSE';
export type MatchSource = 'RANKED' | 'CASUAL' | 'CUSTOM' | 'AI' | 'TOURNAMENT';

export type GamePhase =
  | 'MULLIGAN'
  | 'MAIN_PHASE'
  | 'COMBAT_PHASE'
  | 'WAITING_FOR_REACTION'
  | 'WAITING_FOR_MODAL_CHOICE'
  | 'WAITING_FOR_DISCARD'
  | 'FINISHED';

export type GameActionType =
  | 'MULLIGAN_SWAP'
  | 'PLAY_CARD'
  | 'SET_REACTIVE_SPELL'
  | 'CHANGE_POSTURE'
  | 'ACTIVATE_EFFECT'
  | 'ENTER_COMBAT'
  | 'DECLARE_ATTACK'
  | 'TRIGGER_REACTION'
  | 'PASS_REACTION'
  | 'RESOLVE_CHOICE'
  | 'DISCARD_EXCESS'
  | 'END_TURN'
  | 'FORFEIT';

export type EndReason =
  | 'LP_ZERO'
  | 'DECK_OUT'
  | 'FORFEIT'
  | 'DISCONNECT_TIMEOUT';

export interface HealthResponse {
  status: string;
  application: string;
  virtualThreadsEnabled: boolean;
  javaVersion: string;
}

export interface CosmeticItemDto {
  cosmeticType: CosmeticType;
  itemId: string;
  unlockedAt: string;
}

export interface UserProfileDto {
  userId: string;
  username: string;
  mmr1v1: number;
  mmr2v2: number;
  leagueTier: LeagueTier;
  goldenShards: number;
  arcaneCrowns: number;
  equippedCardBack: string;
  equippedBoardSkin: string;
  equippedPet: string;
  equippedEnergySkin: string;
  equippedAvatar: string;
  equippedLeagueFrame: string;
  equippedTitle: string;
  equippedFinisher: string;
  factionWins: Record<string, number>;
  unlockedCosmetics: CosmeticItemDto[];
}

export interface UpdateCosmeticsRequest {
  equippedCardBack?: string | null;
  equippedBoardSkin?: string | null;
  equippedPet?: string | null;
  equippedEnergySkin?: string | null;
  equippedAvatar?: string | null;
  equippedLeagueFrame?: string | null;
  equippedTitle?: string | null;
  equippedFinisher?: string | null;
}

export interface AbilityOption {
  label: string;
  action: string;
  target: string | null;
  value: number | null;
  duration: string | null;
}

export interface AbilityDefinition {
  trigger: string;
  condition: string | null;
  target: string | null;
  action: string;
  value: number | null;
  secondaryValue: number | null;
  duration: string | null;
  options: AbilityOption[] | null;
}

export interface CardDefinition {
  cardId: string;
  name: string;
  faction: Faction;
  cardType: CardType;
  isEpic: boolean;
  isRamp: boolean;
  energyCost: number;
  baseAtk: number | null;
  baseDef: number | null;
  keywords: string[];
  description: string;
  abilitiesScript: AbilityDefinition[];
}

export interface DeckStats {
  totalCards: number;
  monsterCount: number;
  standardMonsterCount: number;
  epicMonsterCount: number;
  familiarCount: number;
  spellCategoryCount: number;
  cost1To2Count: number;
  cost3To4Count: number;
  cost5PlusCount: number;
  rampCount: number;
  factionCounts: Partial<Record<Faction, number>>;
  primaryFaction: Faction | null;
  secondaryFaction: Faction | null;
}

export interface DeckValidationResult {
  valid: boolean;
  violations: string[];
  stats: DeckStats;
}

export interface DeckCardDto {
  card: CardDefinition;
  foil: boolean;
  position: number;
}

export interface DeckCardInput {
  cardId: string;
  foil: boolean;
}

export interface DeckDto {
  deckId: string;
  name: string;
  primaryFaction: Faction | null;
  secondaryFaction: Faction | null;
  favorite: boolean;
  validAntimeta: boolean;
  starter: boolean;
  updatedAt: string;
  cards: DeckCardDto[];
  validation: DeckValidationResult;
}

export interface SaveDeckRequest {
  name: string;
  cards: DeckCardInput[];
  favorite: boolean;
}

export interface CardViewDto {
  instanceId: string;
  ownerPlayerId: string;
  slot: number;
  hidden: boolean;
  faceDown: boolean;
  cardBackSkin: string;
  foil: boolean;
  cardId: string | null;
  name: string | null;
  faction: Faction | null;
  cardType: CardType | null;
  epic: boolean | null;
  ramp: boolean | null;
  energyCost: number | null;
  baseAtk: number | null;
  baseDef: number | null;
  effectiveAtk: number | null;
  effectiveDef: number | null;
  keywords: string[] | null;
  description: string | null;
  abilitiesScript: AbilityDefinition[] | null;
  posture: Posture | null;
  exhausted: boolean;
  slowed: boolean;
  camouflaged: boolean;
  activatedThisTurn: boolean;
  changedPostureThisTurn: boolean;
  canAttackNow: boolean;
  curseRemainingTurns: number | null;
}

export interface PlayerBoardViewDto {
  userId: string;
  username: string;
  bot: boolean;
  aiDifficulty?: AiDifficulty | null;
  teamId: TeamId;
  seatIndex: number;
  connected: boolean;
  controlledByUserId: string;
  mulliganCompleted: boolean;
  energyTokens: number;
  deckCount: number;
  handCount: number;
  hand: CardViewDto[];
  monsters: (CardViewDto | null)[];
  familiars: (CardViewDto | null)[];
  spellArtifacts: (CardViewDto | null)[];
  discard: CardViewDto[];
  peekedOpponentTopCard: CardViewDto | null;
  spellsLockedThisTurn: boolean;
  directAttacksBlockedThisTurn: boolean;
  cardBackSkin: string;
  boardSkin: string;
  pet: string;
  energySkin: string;
  avatar: string;
  leagueFrame: string;
  title: string;
  finisher: string;
  primaryFaction?: Faction | null;
}

export interface TeamViewDto {
  teamId: TeamId;
  lp: number;
  maxLp: number;
  playerIds: string[];
}

export interface PendingReactionViewDto {
  windowId: string;
  triggerType: string;
  currentResponderPlayerId: string;
  expiresAt: string;
  attackerPlayerId: string | null;
  attackerInstanceId: string | null;
  defenderTargetId: string | null;
  directAttack: boolean;
  sourceActorPlayerId: string | null;
  sourceCardInstanceId: string | null;
  eligibleReactiveInstanceIds: string[];
}

export interface PendingChoiceViewDto {
  ownerPlayerId: string;
  sourceInstanceId: string;
  sourceCardName: string;
  options: AbilityOption[];
  preselectedTargetId: string | null;
}

export type CombatOutcome =
  | 'DIRECT_HIT'
  | 'DIRECT_BLOCKED'
  | 'ATTACKER_VICTORY'
  | 'TRADE'
  | 'ATTACKER_REPELLED'
  | 'DEFENSE_BLOCKED'
  | 'DEFENSE_BROKEN'
  | 'SPELL_STRIKE';

export interface CombatVisualEvent {
  sequence: number;
  faction: Faction;
  sourcePlayerId: string | null;
  sourceInstanceId: string | null;
  sourceZone: 'monster' | 'familiar' | 'spell' | null;
  sourceSlot: number;
  targetPlayerId: string | null;
  targetInstanceId: string | null;
  targetZone: 'monster' | 'familiar' | 'spell' | null;
  targetSlot: number;
  directAttack: boolean;
  defenderPosture: Posture;
  outcome: CombatOutcome;
  damageDealt: number;
  recoilDamage: number;
}

export interface CombatLogEntry {
  sequence: number;
  roundNumber: number;
  turnNumber: number;
  actorPlayerId: string | null;
  actorUsername: string;
  eventType: string;
  message: string;
  timestamp: string;
}

export interface PlayerGameView {
  matchId: string;
  mode: GameMode;
  ranked: boolean;
  aiMatch: boolean;
  customRoom: boolean;
  spectator: boolean;
  viewerUserId: string;
  activeTurnPlayerId: string;
  controlledRelays: string[];
  phase: GamePhase;
  phaseDeadline: string | null;
  turnNumber: number;
  roundNumber: number;
  turn1TruceActive: boolean;
  teamA: TeamViewDto;
  teamB: TeamViewDto;
  players: PlayerBoardViewDto[];
  pendingReaction: PendingReactionViewDto | null;
  pendingChoice: PendingChoiceViewDto | null;
  winnerTeam: TeamId | null;
  endReason: EndReason | null;
  combatLog: CombatLogEntry[];
  visualEvents?: CombatVisualEvent[];
}

export interface GameActionCommand {
  matchId: string;
  actingAsPlayerId?: string | null;
  actionType: GameActionType;
  cardInstanceId?: string | null;
  targetSlot?: number | null;
  posture?: Posture | null;
  targetId?: string | null;
  secondaryTargetId?: string | null;
  choiceIndex?: number | null;
  instanceIds?: string[] | null;
}

export interface GameErrorEvent {
  matchId: string | null;
  code: string;
  message: string;
  timestamp: string;
}

export interface CustomRoomSlotDto {
  userId: string;
  username: string;
  teamId: TeamId;
  host: boolean;
}

export interface CustomRoomDto {
  roomCode: string;
  name: string;
  mode: GameMode;
  hostUserId: string;
  allowSpectators: boolean;
  started: boolean;
  matchId: string | null;
  slots: CustomRoomSlotDto[];
  spectatorCount: number;
  createdAt: string;
}

export interface LobbyNotificationDto {
  eventType: string;
  matchId: string;
  mode: GameMode;
  ranked: boolean;
  message: string;
  timestamp: string;
}

export interface MatchSummaryDto {
  matchId: string;
  mode: string;
  ranked: boolean;
  aiMatch: boolean;
  customRoom?: boolean;
  matchSource?: MatchSource;
  tournamentId?: string | null;
  tournamentName?: string | null;
  teamAIds: string[];
  teamBIds: string[];
  teamANames?: string[];
  teamBNames?: string[];
  teamALp?: number | null;
  teamBLp?: number | null;
  winnerTeam: string | null;
  endReason: string | null;
  turnCount: number;
  createdAt: string;
  finishedAt: string | null;
}

export interface ModeStatsDto {
  matchSource: MatchSource;
  mode: string;
  matches: number;
  wins: number;
  losses: number;
  winRate: number;
}

export interface PlayerStatsDto {
  totalMatches: number;
  wins: number;
  losses: number;
  winRate: number;
  rankedWins: number;
  rankedLosses: number;
  rankedWinRate: number;
  tournamentWins: number;
  tournamentLosses: number;
  aiWins: number;
  aiLosses: number;
  customCasualWins: number;
  customCasualLosses: number;
  currentWinStreak: number;
  averageTurnCount: number;
  favoriteFaction: Faction | null;
  recentForm: ('W' | 'L')[];
  breakdown: ModeStatsDto[];
}

export interface MatchParticipantSummaryDto {
  userId: string | null;
  username: string;
  bot: boolean;
  teamId: TeamId;
  seatIndex: number;
  primaryFaction: Faction | null;
}

export interface PlayerMatchHistoryItemDto {
  matchId: string;
  mode: string;
  matchSource: MatchSource;
  tournamentId: string | null;
  tournamentName: string | null;
  won: boolean;
  playerTeam: TeamId;
  winnerTeam: string | null;
  endReason: string | null;
  endSummary: string | null;
  turnCount: number;
  durationSeconds: number | null;
  primaryFaction: Faction | null;
  startingLp: number;
  myTeamFinalLp: number | null;
  opponentTeamFinalLp: number | null;
  mmrDelta: number;
  allies: MatchParticipantSummaryDto[];
  opponents: MatchParticipantSummaryDto[];
  createdAt: string;
  finishedAt: string | null;
}

export interface PlayerMatchHistoryResponse {
  stats: PlayerStatsDto;
  matches: PlayerMatchHistoryItemDto[];
}

export interface ChatMessageDto {
  messageId: string;
  matchId: string | null;
  senderUserId: string;
  senderUsername: string;
  teamOnly: boolean;
  content: string;
  timestamp: string;
}

export interface FriendDto {
  friendshipId: string;
  friendUserId: string;
  friendUsername: string;
  status: string;
  incoming: boolean;
  online: boolean;
  createdAt: string;
}

export interface TournamentParticipantDto {
  userId: string;
  username: string;
  deck1Id: string;
  deck2Id: string;
  seed: number;
  eliminated: boolean;
}

export interface TournamentDto {
  tournamentId: string;
  name: string;
  maxPlayers: number;
  status: string;
  bracketData: Record<string, unknown>;
  participants: TournamentParticipantDto[];
  createdAt: string;
  finishedAt: string | null;
}

export interface QueueStatusResponse {
  queued: boolean;
  mode?: GameMode;
  ranked?: boolean;
  joinedAt?: string;
  status?: string;
  matchId?: string;
}
