import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnInit,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideAlertTriangle,
  lucideArrowRight,
  lucideBookOpen,
  lucideBot,
  lucideCheck,
  lucideClock,
  lucideCrown,
  lucideEye,
  lucideFeather,
  lucideHistory,
  lucideMessageSquare,
  lucidePlus,
  lucideRefreshCw,
  lucideSend,
  lucideShield,
  lucideSparkles,
  lucideSwords,
  lucideUserPlus,
  lucideUsers,
  lucideX,
} from '@ng-icons/lucide';
import {
  AiDifficulty,
  CustomRoomDto,
  DeckDto,
  Faction,
  FriendDto,
  GameMode,
  MatchSummaryDto,
  QueueStatusResponse,
  TeamId,
} from '../../core/models/arcane.models';
import { AnnouncerService } from '../../core/services/announcer.service';
import { AuthService, extractApiErrorMessage } from '../../core/services/auth.service';
import { LobbyApiService } from '../../core/services/lobby-api.service';
import { StompService } from '../../core/services/stomp.service';
import { ArenaAudioControlComponent } from '../../shared/components/arena-audio-control/arena-audio-control.component';
import { FormAriaSyncDirective } from '../../shared/directives/form-aria-sync.directive';
import { ImeEnterSubmitDirective } from '../../shared/directives/ime-enter-submit.directive';
import { ArcaneAudioService } from '../../shared/services/arcane-audio.service';
import {
  AI_DIFFICULTY_META,
  AI_DIFFICULTY_ORDER,
  END_REASON_LABELS,
  FACTION_META,
  MATCH_SOURCE_META,
  resolveMatchSource,
} from '../../shared/utils/faction-theme';

@Component({
  selector: 'app-lobby-page',
  standalone: true,
  imports: [RouterLink, FormAriaSyncDirective, ImeEnterSubmitDirective, NgIcon, ArenaAudioControlComponent],
  viewProviders: [
    provideIcons({
      lucideAlertTriangle,
      lucideArrowRight,
      lucideBookOpen,
      lucideBot,
      lucideCheck,
      lucideClock,
      lucideCrown,
      lucideEye,
      lucideFeather,
      lucideHistory,
      lucideMessageSquare,
      lucidePlus,
      lucideRefreshCw,
      lucideSend,
      lucideShield,
      lucideSparkles,
      lucideSwords,
      lucideUserPlus,
      lucideUsers,
      lucideX,
    }),
  ],
  templateUrl: './lobby-page.component.html',
  styles: `
    .lobby-tuto-banner {
      border: 1px solid rgba(217, 164, 65, 0.5);
    }
    .lobby-tuto-icon {
      border: 1px solid rgba(217, 164, 65, 0.55);
      background: rgba(217, 164, 65, 0.2);
    }
    .lobby-tuto-tag {
      border: 1px solid rgba(52, 211, 153, 0.5);
      background: rgba(6, 78, 59, 0.7);
    }
    .lobby-tuto-grid {
      display: grid;
      gap: 0.625rem;
    }
    @media (min-width: 640px) {
      .lobby-tuto-grid {
        grid-template-columns: repeat(3, minmax(0, 1fr));
      }
    }
    .lobby-tuto-replay {
      border: 1px solid rgba(52, 211, 153, 0.45);
      background: rgba(6, 78, 59, 0.55);
    }
    .lobby-tuto-replay:hover {
      border-color: rgba(52, 211, 153, 0.9);
      background: rgba(6, 95, 70, 0.7);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LobbyPageComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  protected readonly stomp = inject(StompService);
  private readonly lobbyApi = inject(LobbyApiService);
  private readonly announcer = inject(AnnouncerService);
  private readonly audio = inject(ArcaneAudioService);
  private readonly router = inject(Router);

  private readonly createRoomDialogRef = viewChild<ElementRef<HTMLDialogElement>>('createRoomDialog');

  protected readonly decks = signal<readonly DeckDto[]>([]);
  protected readonly validDecks = computed(() => this.decks().filter((d) => d.validAntimeta));
  protected readonly factionMeta = FACTION_META;
  protected readonly aiDifficultyMeta = AI_DIFFICULTY_META;
  protected readonly aiDifficultyOrder = AI_DIFFICULTY_ORDER;

  protected readonly activeMatchId = signal<string | null>(null);
  protected readonly selectedDeckId = signal('');
  protected readonly selectedDeck = computed(
    () => this.validDecks().find((d) => d.deckId === this.selectedDeckId()) ?? null,
  );
  protected readonly selectedMode = signal<GameMode>('DUEL_1V1');
  protected readonly selectedBotFaction = signal<Faction | ''>('');
  protected readonly selectedBotDifficulty = signal<AiDifficulty>('MEDIUM');
  protected readonly queueStatus = signal<QueueStatusResponse>({ queued: false });
  protected readonly busyPlay = signal(false);
  protected readonly tutorialDismissed = signal(false);
  protected readonly busyTutorial = signal(false);
  protected readonly showTutorialBanner = computed(
    () => !this.tutorialDismissed() && this.auth.user()?.tutorialCompleted === false,
  );

  protected readonly customRooms = signal<readonly CustomRoomDto[]>([]);
  protected readonly friends = signal<readonly FriendDto[]>([]);
  protected readonly matchHistory = signal<readonly MatchSummaryDto[]>([]);

  protected readonly friendUsername = signal('');
  protected readonly chatInput = signal('');

  protected readonly newRoomName = signal('Arène Privée');
  protected readonly newRoomMode = signal<GameMode>('DUEL_1V1');
  protected readonly newRoomSpectators = signal(true);

  private readonly globalChatListRef = viewChild<ElementRef<HTMLUListElement>>('globalChatList');

  constructor() {
    effect(() => {
      const notif = this.stomp.lobbyNotification();
      if (notif?.matchId && (notif.eventType === 'MATCH_FOUND' || notif.eventType === 'CUSTOM_ROOM_STARTED')) {
        // Consume notification once so returning to Lobby later never bounces back to the finished match
        this.stomp.clearLobbyNotification();
        void this.router.navigate(['/arena', notif.matchId]);
      }
    });

    effect(() => {
      this.stomp.globalMessages().length;
      const list = this.globalChatListRef()?.nativeElement;
      if (!list) return;
      requestAnimationFrame(() => {
        list.scrollTop = list.scrollHeight;
      });
    });
  }

  async ngOnInit(): Promise<void> {
    this.stomp.connect();
    this.audio.startMusic('CALM');
    await this.loadInitialData();
  }

  private async loadInitialData(): Promise<void> {
    try {
      const [userDecks, active, qStatus, rooms, friendList, history] = await Promise.all([
        this.lobbyApi.listDecks(),
        this.lobbyApi.getActiveMatch().catch(() => ({})),
        this.lobbyApi.getQueueStatus().catch(() => ({ queued: false })),
        this.lobbyApi.listCustomRooms().catch(() => []),
        this.lobbyApi.listFriends().catch(() => []),
        this.lobbyApi.getMatchHistory().catch(() => []),
      ]);

      this.decks.set(userDecks);
      const valid = userDecks.filter((d) => d.validAntimeta);
      if (valid.length > 0 && !this.selectedDeckId()) {
        const fav = valid.find((d) => d.favorite) ?? valid[0];
        this.selectedDeckId.set(fav.deckId);
      }

      if ('activeMatchId' in active && active.activeMatchId) {
        this.activeMatchId.set(active.activeMatchId);
      }
      this.queueStatus.set(qStatus);
      this.customRooms.set(rooms);
      this.friends.set(friendList);
      this.matchHistory.set(history);
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    }
  }

  protected onDeckChange(event: Event): void {
    this.selectedDeckId.set((event.target as HTMLSelectElement).value);
  }

  protected onModeChange(event: Event): void {
    this.selectedMode.set((event.target as HTMLSelectElement).value as GameMode);
  }

  protected onBotFactionChange(event: Event): void {
    this.selectedBotFaction.set((event.target as HTMLSelectElement).value as Faction | '');
  }

  protected onBotDifficultyChange(difficulty: AiDifficulty): void {
    this.selectedBotDifficulty.set(difficulty);
  }

  protected async onStartAiMatch(): Promise<void> {
    const deckId = this.selectedDeckId();
    if (!deckId) return;
    this.busyPlay.set(true);
    try {
      const botFaction = this.selectedBotFaction() || null;
      const res = await this.lobbyApi.startAiMatch(
        this.selectedMode(),
        deckId,
        botFaction,
        this.selectedBotDifficulty(),
      );
      await this.router.navigate(['/arena', res.matchId]);
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    } finally {
      this.busyPlay.set(false);
    }
  }

  protected async onStartTutorialDuel(): Promise<void> {
    const deckId = this.selectedDeckId() || this.validDecks()[0]?.deckId;
    if (!deckId) {
      this.announcer.pushToast('Sélectionnez un deck valide pour lancer l’initiation.', 'info');
      return;
    }
    this.busyTutorial.set(true);
    try {
      const res = await this.lobbyApi.startAiMatch('DUEL_1V1', deckId, null, 'EASY');
      await this.router.navigate(['/arena', res.matchId], { queryParams: { tutorial: '1' } });
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    } finally {
      this.busyTutorial.set(false);
    }
  }

  protected async onDismissTutorial(): Promise<void> {
    this.tutorialDismissed.set(true);
    try {
      await this.auth.completeTutorial();
      this.announcer.pushToast(
        'Didacticiel ignoré : vous pouvez le relancer à tout moment depuis le Sanctuaire.',
        'info',
      );
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    }
  }

  protected async onJoinQueue(ranked: boolean): Promise<void> {
    const deckId = this.selectedDeckId();
    if (!deckId) return;
    this.busyPlay.set(true);
    try {
      const res = await this.lobbyApi.joinQueue(this.selectedMode(), ranked, deckId);
      if (res.status === 'MATCH_FOUND' && res.matchId) {
        await this.router.navigate(['/arena', res.matchId]);
        return;
      }
      this.queueStatus.set({ queued: true, mode: this.selectedMode(), ranked });
      this.announcer.pushToast('Vous avez rejoint la file de matchmaking.', 'info');
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    } finally {
      this.busyPlay.set(false);
    }
  }

  protected async onLeaveQueue(): Promise<void> {
    try {
      await this.lobbyApi.leaveQueue();
      this.queueStatus.set({ queued: false });
      this.announcer.pushToast('File de matchmaking quittée.', 'info');
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    }
  }

  protected async refreshRooms(): Promise<void> {
    try {
      this.customRooms.set(await this.lobbyApi.listCustomRooms());
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    }
  }

  protected openCreateRoomDialog(): void {
    this.createRoomDialogRef()?.nativeElement.showModal();
  }

  protected closeCreateRoomDialog(): void {
    this.createRoomDialogRef()?.nativeElement.close();
  }

  protected onRoomNameInput(event: Event): void {
    this.newRoomName.set((event.target as HTMLInputElement).value);
  }

  protected onRoomModeChange(event: Event): void {
    this.newRoomMode.set((event.target as HTMLSelectElement).value as GameMode);
  }

  protected onRoomSpecChange(event: Event): void {
    this.newRoomSpectators.set((event.target as HTMLInputElement).checked);
  }

  protected async onCreateRoomSubmit(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    const form = event.target as HTMLFormElement;
    if (!form.reportValidity()) return;
    const deckId = this.selectedDeckId();
    if (!deckId) return;

    try {
      const created = await this.lobbyApi.createCustomRoom({
        name: this.newRoomName().trim(),
        mode: this.newRoomMode(),
        deckId,
        allowSpectators: this.newRoomSpectators(),
      });
      this.customRooms.update((rooms) => [created, ...rooms]);
      this.closeCreateRoomDialog();
      this.announcer.pushToast(`Salle « ${created.name} » (${created.roomCode}) créée !`, 'success');
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    }
  }

  protected async onJoinRoom(roomCode: string, team: TeamId, asSpectator: boolean): Promise<void> {
    try {
      const updated = await this.lobbyApi.joinCustomRoom(roomCode, {
        deckId: asSpectator ? null : this.selectedDeckId(),
        preferredTeam: team,
        asSpectator,
      });
      this.customRooms.update((list) =>
        list.map((r) => (r.roomCode === roomCode ? updated : r)),
      );
      this.announcer.pushToast(`Vous avez rejoint la salle ${updated.name}.`, 'success');
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    }
  }

  protected async onSpectateRoom(room: CustomRoomDto): Promise<void> {
    try {
      const updated = await this.lobbyApi.joinCustomRoom(room.roomCode, {
        deckId: null,
        preferredTeam: 'TEAM_A',
        asSpectator: true,
      });
      if (updated.started && updated.matchId) {
        await this.router.navigate(['/arena', updated.matchId], { queryParams: { spectate: '1' } });
      } else {
        this.customRooms.update((list) =>
          list.map((r) => (r.roomCode === room.roomCode ? updated : r)),
        );
        this.announcer.pushToast(
          'Vous observez la salle. La vue s’ouvrira au lancement du match.',
          'info',
        );
      }
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    }
  }

  protected async onStartRoom(roomCode: string, fillWithBots: boolean): Promise<void> {
    try {
      const started = await this.lobbyApi.startCustomRoom(roomCode, fillWithBots);
      if (started.matchId) {
        await this.router.navigate(['/arena', started.matchId]);
      }
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    }
  }

  protected onFriendInput(event: Event): void {
    this.friendUsername.set((event.target as HTMLInputElement).value);
  }

  protected async onSendFriendRequest(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    const form = event.target as HTMLFormElement;
    if (!form.reportValidity()) return;
    try {
      const f = await this.lobbyApi.sendFriendRequest(this.friendUsername().trim());
      this.friends.update((list) => [f, ...list]);
      this.friendUsername.set('');
      this.announcer.pushToast(`Demande d'ami envoyée à ${f.friendUsername}.`, 'success');
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    }
  }

  protected async onAcceptFriend(friendshipId: string): Promise<void> {
    try {
      const accepted = await this.lobbyApi.acceptFriendRequest(friendshipId);
      this.friends.update((list) =>
        list.map((f) => (f.friendshipId === friendshipId ? accepted : f)),
      );
      this.announcer.pushToast(`${accepted.friendUsername} ajouté à vos amis.`, 'success');
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    }
  }

  protected async onRemoveFriend(friendshipId: string): Promise<void> {
    try {
      await this.lobbyApi.removeFriend(friendshipId);
      this.friends.update((list) => list.filter((f) => f.friendshipId !== friendshipId));
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    }
  }

  protected readonly matchSourceMeta = MATCH_SOURCE_META;
  protected readonly endReasonLabels = END_REASON_LABELS;

  protected matchSourceForSummary(summary: MatchSummaryDto) {
    return this.matchSourceMeta[resolveMatchSource(summary)];
  }

  protected matchWinnerLabel(summary: MatchSummaryDto): string {
    if (!summary.winnerTeam) return '-';
    if (summary.winnerTeam === 'TEAM_A' && summary.teamANames?.length) {
      return `Éq. A (${summary.teamANames.join(', ')})`;
    }
    if (summary.winnerTeam === 'TEAM_B' && summary.teamBNames?.length) {
      return `Éq. B (${summary.teamBNames.join(', ')})`;
    }
    return summary.winnerTeam === 'TEAM_A' ? 'Équipe A' : 'Équipe B';
  }

  protected onChatInput(event: Event): void {
    this.chatInput.set((event.target as HTMLTextAreaElement).value);
  }

  protected onSendGlobalChat(event: SubmitEvent): void {
    event.preventDefault();
    const text = this.chatInput().trim();
    if (!text) return;
    if (!this.stomp.connected()) {
      this.stomp.connect();
    }
    if (this.stomp.sendGlobalChat(text)) {
      this.chatInput.set('');
    }
  }
}
