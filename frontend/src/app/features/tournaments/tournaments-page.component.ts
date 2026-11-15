import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideAlertTriangle,
  lucideCheck,
  lucideRefreshCw,
  lucideSparkles,
  lucideTrophy,
} from '@ng-icons/lucide';
import { DeckDto, TournamentDto } from '../../core/models/arcane.models';
import { AnnouncerService } from '../../core/services/announcer.service';
import { AuthService, extractApiErrorMessage } from '../../core/services/auth.service';
import { LobbyApiService } from '../../core/services/lobby-api.service';
import { FormAriaSyncDirective } from '../../shared/directives/form-aria-sync.directive';
import { FACTION_META } from '../../shared/utils/faction-theme';

@Component({
  selector: 'app-tournaments-page',
  standalone: true,
  imports: [FormAriaSyncDirective, NgIcon],
  viewProviders: [
    provideIcons({
      lucideAlertTriangle,
      lucideCheck,
      lucideRefreshCw,
      lucideSparkles,
      lucideTrophy,
    }),
  ],
  templateUrl: './tournaments-page.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TournamentsPageComponent implements OnInit {
  private readonly lobbyApi = inject(LobbyApiService);
  private readonly auth = inject(AuthService);
  private readonly announcer = inject(AnnouncerService);

  protected readonly factionMeta = FACTION_META;

  protected readonly tournaments = signal<readonly TournamentDto[]>([]);
  protected readonly decks = signal<readonly DeckDto[]>([]);
  protected readonly creating = signal(false);
  protected readonly joiningId = signal<string | null>(null);

  protected readonly newTournamentName = signal('Coupe de l’Ordre');
  protected readonly newMaxPlayers = signal(8);
  protected readonly selectedDeck1 = signal('');
  protected readonly selectedDeck2 = signal('');

  protected readonly validDecks = computed(() => this.decks().filter((d) => d.validAntimeta));

  protected readonly conquestFactionConflict = computed(() => {
    const id1 = this.selectedDeck1();
    const id2 = this.selectedDeck2();
    if (!id1 || !id2) return false;
    if (id1 === id2) return true;
    const d1 = this.validDecks().find((d) => d.deckId === id1);
    const d2 = this.validDecks().find((d) => d.deckId === id2);
    return !!d1?.primaryFaction && d1.primaryFaction === d2?.primaryFaction;
  });

  async ngOnInit(): Promise<void> {
    await this.reloadAll();
  }

  protected async reloadAll(): Promise<void> {
    try {
      const [tourneys, myDecks] = await Promise.all([
        this.lobbyApi.listTournaments(),
        this.lobbyApi.listDecks(),
      ]);
      this.tournaments.set(tourneys);
      this.decks.set(myDecks);

      const valid = myDecks.filter((d) => d.validAntimeta);
      if (valid.length >= 2 && !this.selectedDeck1()) {
        this.selectedDeck1.set(valid[0].deckId);
        const other = valid.find((d) => d.primaryFaction !== valid[0].primaryFaction);
        if (other) this.selectedDeck2.set(other.deckId);
      }
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    }
  }

  protected isRegistered(tournament: TournamentDto): boolean {
    const uid = this.auth.user()?.userId;
    return !!uid && tournament.participants.some((p) => p.userId === uid);
  }

  protected onNameInput(event: Event): void {
    this.newTournamentName.set((event.target as HTMLInputElement).value);
  }

  protected onMaxPlayersChange(event: Event): void {
    this.newMaxPlayers.set(Number((event.target as HTMLSelectElement).value));
  }

  protected onDeck1Change(event: Event): void {
    this.selectedDeck1.set((event.target as HTMLSelectElement).value);
  }

  protected onDeck2Change(event: Event): void {
    this.selectedDeck2.set((event.target as HTMLSelectElement).value);
  }

  protected async onCreateTournament(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    const form = event.target as HTMLFormElement;
    if (!form.reportValidity()) return;

    this.creating.set(true);
    try {
      const created = await this.lobbyApi.createTournament(
        this.newTournamentName().trim(),
        this.newMaxPlayers(),
      );
      this.tournaments.update((list) => [created, ...list]);
      this.announcer.pushToast(`Tournoi « ${created.name} » ouvert !`, 'success');
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    } finally {
      this.creating.set(false);
    }
  }

  protected async onJoinTournament(tournamentId: string): Promise<void> {
    if (!this.selectedDeck1() || !this.selectedDeck2() || this.conquestFactionConflict()) {
      return;
    }
    this.joiningId.set(tournamentId);
    try {
      const updated = await this.lobbyApi.joinTournament(
        tournamentId,
        this.selectedDeck1(),
        this.selectedDeck2(),
      );
      this.tournaments.update((list) =>
        list.map((t) => (t.tournamentId === tournamentId ? updated : t)),
      );
      this.announcer.pushToast(`Inscription validée dans « ${updated.name} » !`, 'success');
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    } finally {
      this.joiningId.set(null);
    }
  }
}
