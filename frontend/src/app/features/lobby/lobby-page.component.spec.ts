import { signal } from '@angular/core';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AuthService } from '../../core/services/auth.service';
import { LobbyApiService } from '../../core/services/lobby-api.service';
import { StompService } from '../../core/services/stomp.service';
import { LobbyPageComponent } from './lobby-page.component';

describe('LobbyPageComponent', () => {
  const lobbyApiStub = {
    listDecks: vi.fn().mockResolvedValue([]),
    getActiveMatch: vi.fn().mockResolvedValue({}),
    getQueueStatus: vi.fn().mockResolvedValue({ queued: false }),
    listCustomRooms: vi.fn().mockResolvedValue([]),
    listFriends: vi.fn().mockResolvedValue([]),
    getMatchHistory: vi.fn().mockResolvedValue([]),
    startAiMatch: vi.fn().mockResolvedValue({ matchId: 'match-ai-1', mode: 'DUEL_1V1', difficulty: 'HARD' }),
  };

  const authStub = {
    user: signal({
      id: 'user-1',
      username: 'MageTest',
      leagueTier: 'BRONZE',
      mmr1v1: 1000,
      mmr2v2: 1000,
      goldenShards: 0,
      arcaneCrowns: 0,
    }),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LobbyPageComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: LobbyApiService, useValue: lobbyApiStub },
        { provide: AuthService, useValue: authStub },
      ],
    }).compileComponents();
  });

  it('consumes MATCH_FOUND / CUSTOM_ROOM_STARTED lobbyNotification once without bouncing back on revisit', async () => {
    const router = TestBed.inject(Router);
    const navSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const stomp = TestBed.inject(StompService);
    stomp.connect = vi.fn();

    stomp.lobbyNotification.set({
      eventType: 'CUSTOM_ROOM_STARTED',
      message: 'La salle démarre !',
      matchId: 'match-bb0992b0',
      mode: 'DUEL_1V1',
      ranked: false,
      timestamp: new Date().toISOString(),
    });

    const firstVisit = TestBed.createComponent(LobbyPageComponent);
    firstVisit.detectChanges();
    await firstVisit.whenStable();

    expect(navSpy).toHaveBeenCalledTimes(1);
    expect(navSpy).toHaveBeenCalledWith(['/arena', 'match-bb0992b0']);
    expect(stomp.lobbyNotification()).toBeNull();
    firstVisit.destroy();

    navSpy.mockClear();

    const revisit = TestBed.createComponent(LobbyPageComponent);
    revisit.detectChanges();
    await revisit.whenStable();

    expect(navSpy).not.toHaveBeenCalled();
    revisit.destroy();
  });

  it('defaults AI difficulty to MEDIUM and sends EASY/HARD selection when starting an AI match', async () => {
    const router = TestBed.inject(Router);
    const navSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const stomp = TestBed.inject(StompService);
    stomp.connect = vi.fn();
    lobbyApiStub.startAiMatch.mockClear();
    lobbyApiStub.listDecks.mockResolvedValueOnce([
      {
        deckId: 'deck-fire-1',
        name: 'Ordre du Feu',
        primaryFaction: 'FIRE',
        secondaryFaction: null,
        favorite: true,
        validAntimeta: true,
        starter: true,
        updatedAt: new Date().toISOString(),
        cards: [],
        validation: {
          valid: true,
          violations: [],
          stats: {
            totalCards: 30,
            monsterCount: 10,
            standardMonsterCount: 10,
            epicMonsterCount: 0,
            familiarCount: 10,
            spellCategoryCount: 10,
            cost1To2Count: 14,
            cost3To4Count: 12,
            cost5PlusCount: 4,
            rampCount: 2,
            factionCounts: { FIRE: 30 },
            primaryFaction: 'FIRE',
            secondaryFaction: null,
          },
        },
      },
    ]);

    const fixture = TestBed.createComponent(LobbyPageComponent);
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const medRadio = host.querySelector('#lobby-ai-diff-MEDIUM') as HTMLInputElement;
    const hardRadio = host.querySelector('#lobby-ai-diff-HARD') as HTMLInputElement;
    const easyRadio = host.querySelector('#lobby-ai-diff-EASY') as HTMLInputElement;

    expect(medRadio.checked).toBe(true);
    expect(easyRadio).not.toBeNull();

    // Selecting EASY then HARD updates hint without auto-submitting
    easyRadio.checked = true;
    easyRadio.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(host.textContent).toContain('IA Faible :');
    expect(lobbyApiStub.startAiMatch).not.toHaveBeenCalled();

    hardRadio.checked = true;
    hardRadio.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(host.textContent).toContain('IA Fort :');
    expect(lobbyApiStub.startAiMatch).not.toHaveBeenCalled();

    const launchBtn = Array.from(host.querySelectorAll('button')).find((b) =>
      b.textContent?.includes('Affronter Mage Automate (IA)'),
    ) as HTMLButtonElement;
    expect(launchBtn.disabled).toBe(false);
    expect(launchBtn.textContent).toContain('Fort');

    launchBtn.click();
    await new Promise((resolve) => setTimeout(resolve, 0));
    await fixture.whenStable();

    expect(lobbyApiStub.startAiMatch).toHaveBeenCalledWith('DUEL_1V1', 'deck-fire-1', null, 'HARD');
    expect(navSpy).toHaveBeenCalledWith(['/arena', 'match-ai-1']);
    fixture.destroy();
  });

  it('sends Sanctuary chat messages when connected, preserves draft when offline, and renders incoming messages', async () => {
    const stomp = TestBed.inject(StompService);
    const connectSpy = vi.spyOn(stomp, 'connect').mockImplementation(() => {});
    const sendSpy = vi.spyOn(stomp, 'sendGlobalChat').mockReturnValueOnce(false).mockReturnValueOnce(true);

    const fixture = TestBed.createComponent(LobbyPageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const input = host.querySelector('#global-chat-input') as HTMLTextAreaElement;
    const form = input.closest('form') as HTMLFormElement;

    // 1. Offline attempt triggers reconnect without wiping the draft
    input.value = 'Salut les mages !';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(sendSpy).toHaveBeenCalledWith('Salut les mages !');
    expect(connectSpy).toHaveBeenCalled();
    expect(input.value).toBe('Salut les mages !');

    // 2. Connected attempt clears draft and renders incoming STOMP chat item
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(input.value).toBe('');

    stomp.globalMessages.set([
      {
        messageId: 'msg-1',
        matchId: null,
        senderUserId: 'user-2',
        senderUsername: 'Chronomancien',
        teamOnly: false,
        content: 'Bienvenue dans le Sanctuaire !',
        timestamp: new Date().toISOString(),
      },
    ]);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(host.textContent).toContain('Chronomancien :');
    expect(host.textContent).toContain('Bienvenue dans le Sanctuaire !');

    stomp.globalMessages.set([]);
    fixture.destroy();
  });
});
