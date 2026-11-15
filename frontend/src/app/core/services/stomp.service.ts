import { Injectable, OnDestroy, inject, signal } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import {
  ChatMessageDto,
  GameActionCommand,
  GameErrorEvent,
  LobbyNotificationDto,
  PlayerGameView,
} from '../models/arcane.models';
import { AnnouncerService } from './announcer.service';

@Injectable({ providedIn: 'root' })
export class StompService implements OnDestroy {
  private readonly announcer = inject(AnnouncerService);

  private client: Client | null = null;
  private baseSubs: StompSubscription[] = [];
  private matchSubs: StompSubscription[] = [];
  private subscribedMatchId: string | null = null;
  private subscribedSpectate = false;

  readonly connected = signal(false);
  readonly gameState = signal<PlayerGameView | null>(null);
  readonly lastError = signal<GameErrorEvent | null>(null);
  readonly lobbyNotification = signal<LobbyNotificationDto | null>(null);
  readonly globalMessages = signal<readonly ChatMessageDto[]>([]);
  readonly gameMessages = signal<readonly ChatMessageDto[]>([]);

  connect(): void {
    if (this.client?.active) {
      return;
    }
    if (typeof window === 'undefined' || typeof WebSocket === 'undefined') {
      return;
    }

    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const brokerURL = `${wsProtocol}//${window.location.host}/ws`;

    const client = new Client({
      brokerURL,
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
    });

    client.onConnect = () => {
      this.connected.set(true);
      this.subscribeBaseChannels(client);
      if (this.subscribedMatchId) {
        this.subscribeMatchChannelsInternal(client, this.subscribedMatchId, this.subscribedSpectate);
      }
    };

    client.onWebSocketClose = () => {
      this.connected.set(false);
    };

    client.onStompError = (frame) => {
      this.connected.set(false);
      const detail = frame.headers['message'] ?? 'Erreur de connexion WebSocket STOMP.';
      this.announcer.pushToast(detail, 'error');
    };

    this.client = client;
    client.activate();
  }

  disconnect(): void {
    this.clearMatchSubscriptions();
    for (const sub of this.baseSubs) {
      sub.unsubscribe();
    }
    this.baseSubs = [];
    this.subscribedMatchId = null;
    this.subscribedSpectate = false;
    this.connected.set(false);
    if (this.client) {
      void this.client.deactivate();
      this.client = null;
    }
    this.gameState.set(null);
    this.lobbyNotification.set(null);
    this.gameMessages.set([]);
  }

  subscribeMatch(matchId: string, spectator: boolean): void {
    if (this.subscribedMatchId === matchId && this.subscribedSpectate === spectator && this.matchSubs.length > 0) {
      return;
    }
    this.subscribedMatchId = matchId;
    this.subscribedSpectate = spectator;
    this.gameMessages.set([]);
    if (this.client?.connected) {
      this.subscribeMatchChannelsInternal(this.client, matchId, spectator);
    }
  }

  clearLobbyNotification(): void {
    this.lobbyNotification.set(null);
  }

  leaveMatchSubscription(): void {
    this.subscribedMatchId = null;
    this.subscribedSpectate = false;
    this.clearMatchSubscriptions();
    this.gameState.set(null);
    this.lobbyNotification.set(null);
    this.gameMessages.set([]);
  }

  sendGameAction(command: GameActionCommand): void {
    if (!this.client?.connected) {
      this.announcer.pushToast('Connexion temps réel indisponible, reconnexion en cours…', 'error');
      return;
    }
    this.client.publish({
      destination: '/app/game.action',
      body: JSON.stringify(command),
    });
  }

  sendGlobalChat(content: string): boolean {
    if (!this.client?.connected) {
      this.announcer.pushToast('Chat hors ligne (WebSocket déconnecté).', 'error');
      return false;
    }
    this.client.publish({
      destination: '/app/chat.global',
      body: JSON.stringify({ matchId: null, teamOnly: false, content }),
    });
    return true;
  }

  sendGameChat(matchId: string, content: string, teamOnly: boolean): boolean {
    if (!this.client?.connected) {
      this.announcer.pushToast('Chat de partie hors ligne.', 'error');
      return false;
    }
    this.client.publish({
      destination: '/app/chat.game',
      body: JSON.stringify({ matchId, teamOnly, content }),
    });
    return true;
  }

  ngOnDestroy(): void {
    this.disconnect();
  }

  private subscribeBaseChannels(client: Client): void {
    for (const sub of this.baseSubs) {
      sub.unsubscribe();
    }
    this.baseSubs = [
      client.subscribe('/user/queue/game-state', (msg: IMessage) => {
        const view = JSON.parse(msg.body) as PlayerGameView;
        this.gameState.set(view);
      }),
      client.subscribe('/user/queue/errors', (msg: IMessage) => {
        const err = JSON.parse(msg.body) as GameErrorEvent;
        this.lastError.set(err);
        this.announcer.pushToast(err.message, 'error');
      }),
      client.subscribe('/user/queue/lobby', (msg: IMessage) => {
        const notif = JSON.parse(msg.body) as LobbyNotificationDto;
        this.announcer.pushToast(notif.message, 'success');
        if (
          this.subscribedMatchId &&
          notif.matchId === this.subscribedMatchId &&
          (notif.eventType === 'MATCH_FOUND' || notif.eventType === 'CUSTOM_ROOM_STARTED')
        ) {
          return;
        }
        this.lobbyNotification.set(notif);
      }),
      client.subscribe('/topic/chat.global', (msg: IMessage) => {
        const chat = JSON.parse(msg.body) as ChatMessageDto;
        this.globalMessages.update((list) => [...list.slice(-79), chat]);
      }),
    ];
  }

  private subscribeMatchChannelsInternal(client: Client, matchId: string, spectator: boolean): void {
    this.clearMatchSubscriptions();
    this.matchSubs.push(
      client.subscribe(`/topic/chat.game.${matchId}`, (msg: IMessage) => {
        const chat = JSON.parse(msg.body) as ChatMessageDto;
        this.gameMessages.update((list) => [...list.slice(-79), chat]);
      }),
    );
    if (!spectator) {
      this.matchSubs.push(
        client.subscribe('/user/queue/chat.team', (msg: IMessage) => {
          const chat = JSON.parse(msg.body) as ChatMessageDto;
          this.gameMessages.update((list) => [...list.slice(-79), chat]);
        }),
      );
    } else {
      this.matchSubs.push(
        client.subscribe(`/topic/game.${matchId}.spectate`, (msg: IMessage) => {
          const view = JSON.parse(msg.body) as PlayerGameView;
          this.gameState.set(view);
        }),
      );
    }
  }

  private clearMatchSubscriptions(): void {
    for (const sub of this.matchSubs) {
      sub.unsubscribe();
    }
    this.matchSubs = [];
  }
}
