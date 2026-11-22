import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { CardDefinition, DeckDto } from '../../core/models/arcane.models';
import { LobbyApiService } from '../../core/services/lobby-api.service';
import { DeckbuilderPageComponent } from './deckbuilder-page.component';

function buildSampleCatalog(): CardDefinition[] {
  const cards: CardDefinition[] = [];
  for (let i = 1; i <= 8; i++) {
    cards.push({
      cardId: `FIRE_M_0${i}`,
      name: `Pyrolame ${i}`,
      faction: 'FIRE',
      cardType: 'MONSTER',
      isEpic: false,
      isRamp: false,
      energyCost: 2,
      baseAtk: 3,
      baseDef: 2,
      keywords: i === 1 ? ['CHARGE'] : [],
      description: `Effet feu ardent ${i} : inflige des dégâts directs.`,
      abilitiesScript: [],
    });
  }
  cards.push({
    cardId: 'FIRE_M_09',
    name: 'Dragon d’Obsidienne',
    faction: 'FIRE',
    cardType: 'MONSTER',
    isEpic: true,
    isRamp: false,
    energyCost: 5,
    baseAtk: 6,
    baseDef: 5,
    keywords: ['SURVOL'],
    description: 'Dragon épique survolant les défenses terrestres.',
    abilitiesScript: [],
  });
  cards.push({
    cardId: 'FIRE_M_10',
    name: 'Titan Magma',
    faction: 'FIRE',
    cardType: 'MONSTER',
    isEpic: false,
    isRamp: false,
    energyCost: 5,
    baseAtk: 5,
    baseDef: 5,
    keywords: ['GARDIEN'],
    description: 'Protège les alliés avec Gardien.',
    abilitiesScript: [],
  });

  for (let i = 1; i <= 6; i++) {
    cards.push({
      cardId: `FIRE_F_0${i}`,
      name: `Esprit Braise ${i}`,
      faction: 'FIRE',
      cardType: 'FAMILIAR',
      isEpic: false,
      isRamp: false,
      energyCost: 1,
      baseAtk: 1,
      baseDef: 2,
      keywords: [],
      description: `Familier agile ${i}.`,
      abilitiesScript: [],
    });
  }
  for (let i = 7; i <= 10; i++) {
    cards.push({
      cardId: `FIRE_F_${String(i).padStart(2, '0')}`,
      name: `Golem Cendre ${i}`,
      faction: 'FIRE',
      cardType: 'FAMILIAR',
      isEpic: false,
      isRamp: false,
      energyCost: 3,
      baseAtk: 2,
      baseDef: 3,
      keywords: [],
      description: `Familier de soutien ${i}.`,
      abilitiesScript: [],
    });
  }

  for (let i = 1; i <= 8; i++) {
    cards.push({
      cardId: `FIRE_S_0${i}`,
      name: `Salve Solaire ${i}`,
      faction: 'FIRE',
      cardType: 'SPELL_NORMAL',
      isEpic: false,
      isRamp: false,
      energyCost: 3,
      baseAtk: null,
      baseDef: null,
      keywords: [],
      description: `Sort offensif complet numéro ${i}.`,
      abilitiesScript: [],
    });
  }
  cards.push({
    cardId: 'FIRE_S_09',
    name: 'Cataclysme Ultime',
    faction: 'FIRE',
    cardType: 'SPELL_ULTIMATE',
    isEpic: false,
    isRamp: false,
    energyCost: 6,
    baseAtk: null,
    baseDef: null,
    keywords: [],
    description: 'Sort ultime dévastateur.',
    abilitiesScript: [],
  });
  cards.push({
    cardId: 'FIRE_S_10',
    name: 'Brasier Persistant',
    faction: 'FIRE',
    cardType: 'ARTIFACT',
    isEpic: false,
    isRamp: false,
    energyCost: 5,
    baseAtk: null,
    baseDef: null,
    keywords: [],
    description: 'Artefact de forge permanent.',
    abilitiesScript: [],
  });
  cards.push({
    cardId: 'ICE_M_01',
    name: 'Sentinelle Boréale',
    faction: 'ICE',
    cardType: 'MONSTER',
    isEpic: false,
    isRamp: false,
    energyCost: 2,
    baseAtk: 2,
    baseDef: 4,
    keywords: ['GARDIEN'],
    description: 'Glace éternelle : bloque les attaques.',
    abilitiesScript: [],
  });
  return cards;
}

function buildStarterDeck(cards: CardDefinition[]): DeckDto {
  const fire30 = cards.filter((c) => c.faction === 'FIRE');
  return {
    deckId: 'deck-fire-1',
    name: 'Ordre du Feu',
    primaryFaction: 'FIRE',
    secondaryFaction: null,
    favorite: true,
    validAntimeta: true,
    starter: true,
    updatedAt: '2026-11-21T03:00:00Z',
    cards: fire30.map((card, position) => ({ card, foil: false, position })),
    validation: {
      valid: true,
      violations: [],
      stats: {
        totalCards: 30,
        monsterCount: 10,
        standardMonsterCount: 9,
        epicMonsterCount: 1,
        familiarCount: 10,
        spellCategoryCount: 10,
        cost1To2Count: 14,
        cost3To4Count: 12,
        cost5PlusCount: 4,
        rampCount: 0,
        factionCounts: { FIRE: 30 },
        primaryFaction: 'FIRE',
        secondaryFaction: null,
      },
    },
  };
}

describe('DeckbuilderPageComponent', () => {
  let catalog: CardDefinition[];
  let starterDeck: DeckDto;
  let lobbyApiStub: {
    listCards: ReturnType<typeof vi.fn>;
    listDecks: ReturnType<typeof vi.fn>;
    createDeck: ReturnType<typeof vi.fn>;
    updateDeck: ReturnType<typeof vi.fn>;
    toggleFavoriteDeck: ReturnType<typeof vi.fn>;
    deleteDeck: ReturnType<typeof vi.fn>;
    validateDeck: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    catalog = buildSampleCatalog();
    starterDeck = buildStarterDeck(catalog);

    lobbyApiStub = {
      listCards: vi.fn().mockResolvedValue(catalog),
      listDecks: vi.fn().mockResolvedValue([starterDeck]),
      createDeck: vi.fn().mockResolvedValue({
        ...starterDeck,
        deckId: 'deck-custom-1',
        name: 'Mon Grimoire Test',
        starter: false,
        favorite: false,
      }),
      updateDeck: vi.fn().mockResolvedValue(starterDeck),
      toggleFavoriteDeck: vi.fn().mockResolvedValue({ ...starterDeck, favorite: false }),
      deleteDeck: vi.fn().mockResolvedValue(undefined),
      validateDeck: vi.fn().mockResolvedValue(starterDeck.validation),
    };

    await TestBed.configureTestingModule({
      imports: [DeckbuilderPageComponent],
      providers: [
        provideZonelessChangeDetection(),
        { provide: LobbyApiService, useValue: lobbyApiStub },
      ],
    }).compileComponents();
  });

  async function flushComponent(fixture: {
    detectChanges: () => void;
    whenStable: () => Promise<unknown>;
  }): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise<void>((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();
  }

  it('starts in LIST view displaying existing decks alongside the Nouveau creation slot', async () => {
    const fixture = TestBed.createComponent(DeckbuilderPageComponent);
    await flushComponent(fixture);

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Ordre du Feu');
    expect(el.querySelector('[data-testid="create-new-deck-slot"]')).not.toBeNull();
    expect(el.querySelector('#filter-search')).toBeNull();
  });

  it('opens EDITOR on clicking Nouveau, filters cards, builds 30-card deck, validates and returns to LIST', async () => {
    const createdDeck: DeckDto = {
      ...starterDeck,
      deckId: 'deck-custom-1',
      name: 'Mon Grimoire Test',
      starter: false,
      favorite: false,
    };
    lobbyApiStub.listDecks
      .mockResolvedValueOnce([starterDeck])
      .mockResolvedValueOnce([starterDeck, createdDeck]);

    const fixture = TestBed.createComponent(DeckbuilderPageComponent);
    await flushComponent(fixture);

    const el = fixture.nativeElement as HTMLElement;
    const newBtn = el.querySelector('[data-testid="create-new-deck-slot"]') as HTMLButtonElement;
    newBtn.click();
    fixture.detectChanges();

    // EDITOR view shows empty slots (6), search/filter bar, card descriptions, disabled Valider button
    expect(el.querySelector('#filter-search')).not.toBeNull();
    expect(el.querySelectorAll('.arcane-empty-forge-card').length).toBe(6);
    const validateBtn = el.querySelector('[data-testid="validate-deck-btn"]') as HTMLButtonElement;
    expect(validateBtn.disabled).toBe(true);
    expect(el.textContent).toContain('Effet feu ardent 1 : inflige des dégâts directs.');

    // Filter by ICE faction -> only Sentinelle Boréale visible
    const factionSelect = el.querySelector('#filter-faction') as HTMLSelectElement;
    factionSelect.value = 'ICE';
    factionSelect.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(el.textContent).toContain('Sentinelle Boréale');
    expect(el.textContent).not.toContain('Pyrolame 1');

    // Reset back to FIRE and add all 30 FIRE cards to build a valid deck
    factionSelect.value = 'FIRE';
    factionSelect.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const nameInput = el.querySelector('#deck-name') as HTMLInputElement;
    nameInput.value = 'Mon Grimoire Test';
    nameInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const addButtons = Array.from(el.querySelectorAll('article button')).filter(
      (b) => b.textContent?.trim() === 'Ajouter',
    ) as HTMLButtonElement[];
    expect(addButtons.length).toBe(30);
    for (const btn of addButtons) {
      btn.click();
    }
    fixture.detectChanges();

    expect(el.querySelectorAll('.arcane-empty-forge-card').length).toBe(0);
    expect(validateBtn.disabled).toBe(false);

    // Click Valider -> createDeck called and returns to LIST with new deck visible
    const form = el.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    await flushComponent(fixture);

    expect(lobbyApiStub.createDeck).toHaveBeenCalledTimes(1);
    expect(el.querySelector('[data-testid="create-new-deck-slot"]')).not.toBeNull();
    expect(el.textContent).toContain('Mon Grimoire Test');
  });

  it('opens an existing deck in EDITOR when clicking it in LIST and toggles favorite cleanly from LIST', async () => {
    const fixture = TestBed.createComponent(DeckbuilderPageComponent);
    await flushComponent(fixture);

    const el = fixture.nativeElement as HTMLElement;
    const favBtn = Array.from(el.querySelectorAll('article button')).find((b) =>
      b.textContent?.includes('Favori actif'),
    ) as HTMLButtonElement;
    expect(favBtn).toBeDefined();
    favBtn.click();
    await flushComponent(fixture);
    expect(lobbyApiStub.toggleFavoriteDeck).toHaveBeenCalledWith('deck-fire-1');
    expect(el.querySelector('#filter-search')).toBeNull();

    const deckCardBtn = Array.from(el.querySelectorAll('article button')).find((b) =>
      b.textContent?.includes('Ordre du Feu'),
    ) as HTMLButtonElement;
    expect(deckCardBtn).toBeDefined();

    deckCardBtn.click();
    fixture.detectChanges();

    expect(el.querySelector('#filter-search')).not.toBeNull();
    expect(el.textContent).toContain('Modifier le Deck');
    expect(el.textContent).toContain('30/30');
  });
});
