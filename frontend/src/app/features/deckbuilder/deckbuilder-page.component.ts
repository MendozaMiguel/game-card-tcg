import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  computed,
  effect,
  inject,
  signal,
  viewChild,
  viewChildren,
} from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideAlertTriangle,
  lucideCheck,
  lucideChevronLeft,
  lucideChevronRight,
  lucideEye,
  lucideFlame,
  lucideMoon,
  lucidePlus,
  lucideSnowflake,
  lucideSparkles,
  lucideStar,
  lucideSun,
  lucideTrash2,
  lucideX,
} from '@ng-icons/lucide';
import { CardDefinition, CardType, DeckDto, Faction } from '../../core/models/arcane.models';
import { AnnouncerService } from '../../core/services/announcer.service';
import { extractApiErrorMessage } from '../../core/services/auth.service';
import { LobbyApiService } from '../../core/services/lobby-api.service';
import { ArcaneCardComponent } from '../../shared/components/arcane-card/arcane-card.component';
import { FormAriaSyncDirective } from '../../shared/directives/form-aria-sync.directive';
import {
  CARD_TYPE_LABELS,
  FACTION_META,
  KEYWORD_DESCRIPTIONS,
  evaluateAntiMetaLocally,
} from '../../shared/utils/faction-theme';
import { animateCardGridEntrance } from '../../shared/utils/motion-presets';

interface DraftDeckEntry {
  card: CardDefinition;
  foil: boolean;
}

type DeckbuilderViewMode = 'LIST' | 'EDITOR';

@Component({
  selector: 'app-deckbuilder-page',
  standalone: true,
  imports: [ArcaneCardComponent, FormAriaSyncDirective, NgIcon],
  viewProviders: [
    provideIcons({
      lucideAlertTriangle,
      lucideCheck,
      lucideChevronLeft,
      lucideChevronRight,
      lucideEye,
      lucideFlame,
      lucideMoon,
      lucidePlus,
      lucideSnowflake,
      lucideSparkles,
      lucideStar,
      lucideSun,
      lucideTrash2,
      lucideX,
    }),
  ],
  templateUrl: './deckbuilder-page.component.html',
  styleUrl: './deckbuilder-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeckbuilderPageComponent implements OnInit {
  private readonly lobbyApi = inject(LobbyApiService);
  private readonly announcer = inject(AnnouncerService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly deleteDialogRef = viewChild<ElementRef<HTMLDialogElement>>('deleteDialog');
  private readonly previewDialogRef = viewChild<ElementRef<HTMLDialogElement>>('cardPreviewDialog');
  private readonly catalogTiles = viewChildren<ElementRef<HTMLElement>>('catalogTile');

  private stopGridAnim: () => void = () => {};
  private lastAnimatedFilterKey = '';

  protected readonly factionMeta = FACTION_META;
  protected readonly typeLabels = CARD_TYPE_LABELS;
  protected readonly keywordDescriptions = KEYWORD_DESCRIPTIONS;

  protected readonly allCards = signal<readonly CardDefinition[]>([]);
  protected readonly decks = signal<readonly DeckDto[]>([]);
  protected readonly loadingCatalog = signal(true);
  protected readonly saving = signal(false);

  protected readonly viewMode = signal<DeckbuilderViewMode>('LIST');
  protected readonly editingDeckId = signal<string | null>(null);
  protected readonly draftName = signal('Nouveau Deck');
  protected readonly draftFavorite = signal(false);
  protected readonly draftEntries = signal<readonly DraftDeckEntry[]>([]);

  protected readonly searchQuery = signal('');
  protected readonly factionFilter = signal<Faction | 'ALL'>('ALL');
  protected readonly typeFilter = signal<CardType | 'ALL'>('ALL');
  protected readonly costFilter = signal<'ALL' | '1-2' | '3-4' | '5+'>('ALL');
  protected readonly keywordFilter = signal<string>('ALL');

  protected readonly previewedCard = signal<CardDefinition | null>(null);
  protected readonly previewFoilActive = signal(false);
  protected readonly deleteCandidateId = signal<string | null>(null);
  protected readonly deleteCandidateName = signal('');

  protected readonly draftCardIdSet = computed(
    () => new Set(this.draftEntries().map((e) => e.card.cardId)),
  );

  protected readonly hasActiveFilters = computed(
    () =>
      this.searchQuery().trim().length > 0 ||
      this.factionFilter() !== 'ALL' ||
      this.typeFilter() !== 'ALL' ||
      this.costFilter() !== 'ALL' ||
      this.keywordFilter() !== 'ALL',
  );

  protected readonly placeholderSlots = computed(() => {
    const count = Math.max(0, 6 - this.draftEntries().length);
    return Array.from({ length: count }, (_, idx) => idx);
  });

  constructor() {
    this.destroyRef.onDestroy(() => this.stopGridAnim());

    effect(() => {
      if (this.viewMode() !== 'EDITOR' || this.loadingCatalog()) return;
      const tiles = this.catalogTiles();
      const key = `${this.viewMode()}|${this.factionFilter()}|${this.typeFilter()}|${this.costFilter()}|${this.keywordFilter()}|${this.searchQuery()}|${tiles.length}`;
      if (tiles.length > 0 && key !== this.lastAnimatedFilterKey) {
        this.lastAnimatedFilterKey = key;
        this.stopGridAnim();
        this.stopGridAnim = animateCardGridEntrance(tiles.map((t) => t.nativeElement));
      }
    });
  }

  protected readonly liveValidation = computed(() =>
    evaluateAntiMetaLocally(this.draftEntries().map((e) => e.card)),
  );

  protected readonly filteredCatalog = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const faction = this.factionFilter();
    const cardType = this.typeFilter();
    const cost = this.costFilter();
    const kw = this.keywordFilter();

    return this.allCards().filter((c) => {
      if (faction !== 'ALL' && c.faction !== faction) return false;
      if (cardType !== 'ALL' && c.cardType !== cardType) return false;
      if (cost === '1-2' && c.energyCost > 2) return false;
      if (cost === '3-4' && (c.energyCost < 3 || c.energyCost > 4)) return false;
      if (cost === '5+' && c.energyCost < 5) return false;
      if (kw === 'RAMP' && !c.isRamp) return false;
      if (kw !== 'ALL' && kw !== 'RAMP') {
        const hasKw = c.keywords.some((k) =>
          kw === 'SURVOL'
            ? k === 'SURVOL' || k === 'VOL'
            : kw === 'CAMOUFLAGE'
              ? k === 'CAMOUFLAGE' || k === 'ESQUIVE'
              : k === kw,
        );
        if (!hasKw) return false;
      }
      if (query) {
        const hay = `${c.name} ${c.description} ${c.cardId}`.toLowerCase();
        if (!hay.includes(query)) return false;
      }
      return true;
    });
  });

  async ngOnInit(): Promise<void> {
    try {
      const [cards, userDecks] = await Promise.all([
        this.lobbyApi.listCards(),
        this.lobbyApi.listDecks(),
      ]);
      this.allCards.set(cards);
      this.decks.set(userDecks);
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    } finally {
      this.loadingCatalog.set(false);
    }
  }

  protected factionEntries(counts: Partial<Record<Faction, number>>): [Faction, number][] {
    return Object.entries(counts) as [Faction, number][];
  }

  protected factionIconName(faction: Faction | null): string {
    switch (faction) {
      case 'FIRE':
        return 'lucideFlame';
      case 'ICE':
        return 'lucideSnowflake';
      case 'SHADOW':
        return 'lucideMoon';
      case 'LIGHT':
        return 'lucideSun';
      default:
        return 'lucideSparkles';
    }
  }

  protected selectDeck(deck: DeckDto): void {
    this.editingDeckId.set(deck.deckId);
    this.draftName.set(deck.name);
    this.draftFavorite.set(deck.favorite);
    this.draftEntries.set(deck.cards.map((dc) => ({ card: dc.card, foil: dc.foil })));
  }

  protected openDeckInEditor(deck: DeckDto): void {
    this.selectDeck(deck);
    this.lastAnimatedFilterKey = '';
    this.viewMode.set('EDITOR');
  }

  protected startNewDeck(): void {
    this.editingDeckId.set(null);
    this.draftName.set('Nouveau Deck');
    this.draftFavorite.set(false);
    this.draftEntries.set([]);
    this.resetFilters();
    this.lastAnimatedFilterKey = '';
    this.viewMode.set('EDITOR');
  }

  protected backToDeckList(): void {
    this.viewMode.set('LIST');
  }

  protected resetFilters(): void {
    this.searchQuery.set('');
    this.factionFilter.set('ALL');
    this.typeFilter.set('ALL');
    this.costFilter.set('ALL');
    this.keywordFilter.set('ALL');
  }

  protected toggleCardInDraft(card: CardDefinition): void {
    if (this.draftCardIdSet().has(card.cardId)) {
      this.removeCardFromDraft(card.cardId);
      return;
    }
    if (this.draftEntries().length >= 30) {
      this.announcer.pushToast('Le deck contient déjà 30 cartes maximum.', 'error');
      return;
    }
    this.draftEntries.update((list) => [...list, { card, foil: false }]);
  }

  protected removeCardFromDraft(cardId: string): void {
    this.draftEntries.update((list) => list.filter((e) => e.card.cardId !== cardId));
  }

  protected toggleFoil(cardId: string): void {
    this.draftEntries.update((list) =>
      list.map((e) => (e.card.cardId === cardId ? { ...e, foil: !e.foil } : e)),
    );
  }

  protected onSearchInput(event: Event): void {
    this.searchQuery.set((event.target as HTMLInputElement).value);
  }

  protected onFactionChange(event: Event): void {
    this.factionFilter.set((event.target as HTMLSelectElement).value as Faction | 'ALL');
  }

  protected onTypeChange(event: Event): void {
    this.typeFilter.set((event.target as HTMLSelectElement).value as CardType | 'ALL');
  }

  protected onCostChange(event: Event): void {
    this.costFilter.set((event.target as HTMLSelectElement).value as 'ALL' | '1-2' | '3-4' | '5+');
  }

  protected onKeywordChange(event: Event): void {
    this.keywordFilter.set((event.target as HTMLSelectElement).value);
  }

  protected onDeckNameInput(event: Event): void {
    this.draftName.set((event.target as HTMLInputElement).value);
  }

  protected async verifyWithServer(): Promise<void> {
    try {
      const res = await this.lobbyApi.validateDeck(this.draftEntries().map((e) => e.card.cardId));
      if (res.valid) {
        this.announcer.pushToast('Serveur : Deck 100 % conforme aux 4 Lois Anti-Méta !', 'success');
      } else {
        this.announcer.pushToast(res.violations[0] ?? 'Deck non conforme.', 'error');
      }
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    }
  }

  protected async onSaveDeck(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    const form = event.target as HTMLFormElement;
    if (!form.reportValidity()) {
      return;
    }
    const entries = this.draftEntries();
    if (!this.liveValidation().valid) {
      this.announcer.pushToast('Le deck doit valider les 4 Lois Anti-Méta avant sauvegarde.', 'error');
      return;
    }

    const payload = {
      name: this.draftName().trim(),
      favorite: this.draftFavorite(),
      cards: entries.map((e) => ({ cardId: e.card.cardId, foil: e.foil })),
    };

    this.saving.set(true);
    try {
      const id = this.editingDeckId();
      const saved = id
        ? await this.lobbyApi.updateDeck(id, payload)
        : await this.lobbyApi.createDeck(payload);
      const updatedList = await this.lobbyApi.listDecks();
      this.decks.set(updatedList);
      this.selectDeck(saved);
      this.viewMode.set('LIST');
      this.announcer.pushToast(`Deck « ${saved.name} » validé et enregistré.`, 'success');
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    } finally {
      this.saving.set(false);
    }
  }

  protected async onToggleFavorite(deckId: string): Promise<void> {
    try {
      const updated = await this.lobbyApi.toggleFavoriteDeck(deckId);
      const list = await this.lobbyApi.listDecks();
      this.decks.set(list);
      if (this.editingDeckId() === updated.deckId) {
        this.draftFavorite.set(updated.favorite);
      }
      this.announcer.pushToast(
        updated.favorite ? `Deck favori : ${updated.name}` : `Retiré des favoris : ${updated.name}`,
        'success',
      );
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    }
  }

  protected openDeleteDialog(deck?: DeckDto): void {
    const id = deck?.deckId ?? this.editingDeckId();
    if (!id) return;
    this.deleteCandidateId.set(id);
    this.deleteCandidateName.set(deck?.name ?? this.draftName());
    this.deleteDialogRef()?.nativeElement.showModal();
  }

  protected closeDeleteDialog(): void {
    this.deleteDialogRef()?.nativeElement.close();
  }

  protected openCardPreview(card: CardDefinition): void {
    this.previewedCard.set(card);
    const inDraft = this.draftEntries().find((e) => e.card.cardId === card.cardId);
    this.previewFoilActive.set(inDraft?.foil ?? false);
    const dialog = this.previewDialogRef()?.nativeElement;
    if (dialog && !dialog.open) {
      dialog.showModal();
    }
  }

  protected closeCardPreview(): void {
    const dialog = this.previewDialogRef()?.nativeElement;
    if (dialog?.open) {
      dialog.close();
    }
  }

  protected stepCardPreview(delta: -1 | 1): void {
    const current = this.previewedCard();
    const list = this.filteredCatalog();
    if (!current || list.length === 0) return;
    const idx = list.findIndex((c) => c.cardId === current.cardId);
    const nextIdx = idx < 0 ? 0 : (idx + delta + list.length) % list.length;
    const next = list[nextIdx];
    this.previewedCard.set(next);
    const inDraft = this.draftEntries().find((e) => e.card.cardId === next.cardId);
    this.previewFoilActive.set(inDraft?.foil ?? false);
  }

  protected togglePreviewFoil(): void {
    this.previewFoilActive.update((v) => !v);
  }

  protected async confirmDeleteDeck(): Promise<void> {
    const id = this.deleteCandidateId();
    this.closeDeleteDialog();
    this.deleteCandidateId.set(null);
    if (!id) return;
    try {
      await this.lobbyApi.deleteDeck(id);
      const remaining = await this.lobbyApi.listDecks();
      this.decks.set(remaining);
      if (this.editingDeckId() === id) {
        this.editingDeckId.set(null);
        this.draftEntries.set([]);
        this.viewMode.set('LIST');
      }
      this.announcer.pushToast('Deck supprimé.', 'info');
    } catch (err) {
      this.announcer.pushToast(extractApiErrorMessage(err), 'error');
    }
  }
}
