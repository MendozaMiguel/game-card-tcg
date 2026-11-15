import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  AiDifficulty,
  CardDefinition,
  CardType,
  CustomRoomDto,
  DeckDto,
  DeckValidationResult,
  Faction,
  FriendDto,
  GameMode,
  MatchSummaryDto,
  PlayerGameView,
  QueueStatusResponse,
  SaveDeckRequest,
  TeamId,
  TournamentDto,
} from '../models/arcane.models';

@Injectable({ providedIn: 'root' })
export class LobbyApiService {
  private readonly http = inject(HttpClient);

  listCards(filters?: {
    faction?: Faction;
    type?: CardType;
    cost?: number;
    keyword?: string;
  }): Promise<CardDefinition[]> {
    let params = new HttpParams();
    if (filters?.faction) params = params.set('faction', filters.faction);
    if (filters?.type) params = params.set('type', filters.type);
    if (filters?.cost !== undefined) params = params.set('cost', String(filters.cost));
    if (filters?.keyword) params = params.set('keyword', filters.keyword);
    return firstValueFrom(this.http.get<CardDefinition[]>('/api/cards', { params }));
  }

  listDecks(): Promise<DeckDto[]> {
    return firstValueFrom(this.http.get<DeckDto[]>('/api/decks'));
  }

  validateDeck(cardIds: string[]): Promise<DeckValidationResult> {
    return firstValueFrom(
      this.http.post<DeckValidationResult>('/api/decks/validate', { cardIds }),
    );
  }

  createDeck(request: SaveDeckRequest): Promise<DeckDto> {
    return firstValueFrom(this.http.post<DeckDto>('/api/decks', request));
  }

  updateDeck(deckId: string, request: SaveDeckRequest): Promise<DeckDto> {
    return firstValueFrom(this.http.put<DeckDto>(`/api/decks/${deckId}`, request));
  }

  toggleFavoriteDeck(deckId: string): Promise<DeckDto> {
    return firstValueFrom(this.http.patch<DeckDto>(`/api/decks/${deckId}/favorite`, {}));
  }

  deleteDeck(deckId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/decks/${deckId}`));
  }

  startAiMatch(
    mode: GameMode,
    deckId: string,
    botFaction: Faction | null,
    difficulty: AiDifficulty = 'MEDIUM',
  ): Promise<{ matchId: string; mode: GameMode; difficulty?: AiDifficulty }> {
    return firstValueFrom(
      this.http.post<{ matchId: string; mode: GameMode; difficulty?: AiDifficulty }>('/api/lobby/ai-match', {
        mode,
        deckId,
        botFaction,
        difficulty,
      }),
    );
  }

  joinQueue(mode: GameMode, ranked: boolean, deckId: string): Promise<QueueStatusResponse> {
    return firstValueFrom(
      this.http.post<QueueStatusResponse>('/api/lobby/queue/join', { mode, ranked, deckId }),
    );
  }

  leaveQueue(): Promise<{ queued: boolean }> {
    return firstValueFrom(this.http.delete<{ queued: boolean }>('/api/lobby/queue'));
  }

  getQueueStatus(): Promise<QueueStatusResponse> {
    return firstValueFrom(this.http.get<QueueStatusResponse>('/api/lobby/queue/status'));
  }

  listCustomRooms(): Promise<CustomRoomDto[]> {
    return firstValueFrom(this.http.get<CustomRoomDto[]>('/api/lobby/custom-rooms'));
  }

  createCustomRoom(payload: {
    name: string;
    mode: GameMode;
    deckId: string;
    allowSpectators: boolean;
  }): Promise<CustomRoomDto> {
    return firstValueFrom(this.http.post<CustomRoomDto>('/api/lobby/custom-rooms', payload));
  }

  joinCustomRoom(
    roomCode: string,
    payload: { deckId: string | null; preferredTeam: TeamId; asSpectator: boolean },
  ): Promise<CustomRoomDto> {
    return firstValueFrom(
      this.http.post<CustomRoomDto>(`/api/lobby/custom-rooms/${roomCode}/join`, payload),
    );
  }

  startCustomRoom(roomCode: string, fillWithBots: boolean): Promise<CustomRoomDto> {
    return firstValueFrom(
      this.http.post<CustomRoomDto>(`/api/lobby/custom-rooms/${roomCode}/start`, null, {
        params: { fillWithBots: String(fillWithBots) },
      }),
    );
  }

  getMatchHistory(): Promise<MatchSummaryDto[]> {
    return firstValueFrom(this.http.get<MatchSummaryDto[]>('/api/lobby/matches/history'));
  }

  getActiveMatch(): Promise<{ activeMatchId?: string }> {
    return firstValueFrom(this.http.get<{ activeMatchId?: string }>('/api/game/active'));
  }

  getGameState(matchId: string): Promise<PlayerGameView> {
    return firstValueFrom(this.http.get<PlayerGameView>(`/api/game/${matchId}/state`));
  }

  listFriends(): Promise<FriendDto[]> {
    return firstValueFrom(this.http.get<FriendDto[]>('/api/friends'));
  }

  sendFriendRequest(username: string): Promise<FriendDto> {
    return firstValueFrom(this.http.post<FriendDto>('/api/friends/request', { username }));
  }

  acceptFriendRequest(friendshipId: string): Promise<FriendDto> {
    return firstValueFrom(this.http.post<FriendDto>(`/api/friends/${friendshipId}/accept`, {}));
  }

  removeFriend(friendshipId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/friends/${friendshipId}`));
  }

  listTournaments(): Promise<TournamentDto[]> {
    return firstValueFrom(this.http.get<TournamentDto[]>('/api/tournaments'));
  }

  createTournament(name: string, maxPlayers: number): Promise<TournamentDto> {
    return firstValueFrom(
      this.http.post<TournamentDto>('/api/tournaments', { name, maxPlayers }),
    );
  }

  joinTournament(tournamentId: string, deck1Id: string, deck2Id: string): Promise<TournamentDto> {
    return firstValueFrom(
      this.http.post<TournamentDto>(`/api/tournaments/${tournamentId}/join`, {
        deck1Id,
        deck2Id,
      }),
    );
  }
}
