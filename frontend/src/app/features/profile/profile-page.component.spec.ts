import { signal } from '@angular/core';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import {
  MatchSource,
  PlayerMatchHistoryResponse,
  UserProfileDto,
} from '../../core/models/arcane.models';
import { AuthService } from '../../core/services/auth.service';
import { ProfilePageComponent } from './profile-page.component';

describe('ProfilePageComponent', () => {
  const sampleProfile: UserProfileDto = {
    userId: 'user-1',
    username: 'ArchimageTest',
    mmr1v1: 1124,
    mmr2v2: 1000,
    leagueTier: 'INITIE',
    goldenShards: 120,
    arcaneCrowns: 9,
    equippedCardBack: 'card_back_default',
    equippedBoardSkin: 'board_sanctuary',
    equippedPet: 'pet_none',
    equippedEnergySkin: 'energy_arcane',
    equippedAvatar: 'avatar_initiate',
    equippedLeagueFrame: 'frame_apprenti',
    equippedTitle: 'Apprenti Arcane',
    equippedFinisher: 'finisher_arcane_burst',
    factionWins: { SHADOW: 3, FIRE: 1 },
    unlockedCosmetics: [],
  };

  const fullHistory: PlayerMatchHistoryResponse = {
    stats: {
      totalMatches: 6,
      wins: 4,
      losses: 2,
      winRate: 66.7,
      rankedWins: 3,
      rankedLosses: 1,
      rankedWinRate: 75,
      tournamentWins: 1,
      tournamentLosses: 1,
      aiWins: 0,
      aiLosses: 0,
      customCasualWins: 0,
      customCasualLosses: 0,
      currentWinStreak: 2,
      averageTurnCount: 9.5,
      favoriteFaction: 'SHADOW',
      recentForm: ['W', 'W', 'L', 'W', 'W', 'L'],
      breakdown: [
        { matchSource: 'RANKED', mode: '1V1', matches: 4, wins: 3, losses: 1, winRate: 75 },
        { matchSource: 'TOURNAMENT', mode: '1V1', matches: 2, wins: 1, losses: 1, winRate: 50 },
      ],
    },
    matches: [
      {
        matchId: 'm-ranked-1',
        mode: '1V1',
        matchSource: 'RANKED',
        tournamentId: null,
        tournamentName: null,
        won: true,
        playerTeam: 'TEAM_A',
        winnerTeam: 'TEAM_A',
        endReason: 'LP_ZERO',
        endSummary: "L'Équipe B tombe à 0 PV ! Victoire de TEAM_A !",
        turnCount: 10,
        durationSeconds: 145,
        primaryFaction: 'SHADOW',
        startingLp: 25,
        myTeamFinalLp: 16,
        opponentTeamFinalLp: 0,
        mmrDelta: 24,
        allies: [],
        opponents: [
          {
            userId: 'opp-1',
            username: 'RivalGlace',
            bot: false,
            teamId: 'TEAM_B',
            seatIndex: 1,
            primaryFaction: 'ICE',
          },
        ],
        createdAt: '2026-11-15T01:30:00Z',
        finishedAt: '2026-11-15T01:32:25Z',
      },
      {
        matchId: 'm-tourney-1',
        mode: '1V1',
        matchSource: 'TOURNAMENT',
        tournamentId: 'tour-1',
        tournamentName: 'Coupe Conquest',
        won: false,
        playerTeam: 'TEAM_B',
        winnerTeam: 'TEAM_A',
        endReason: 'FORFEIT',
        endSummary: 'ArchimageTest a déclaré forfait.',
        turnCount: 6,
        durationSeconds: 95,
        primaryFaction: 'FIRE',
        startingLp: 25,
        myTeamFinalLp: 11,
        opponentTeamFinalLp: 19,
        mmrDelta: 0,
        allies: [],
        opponents: [
          {
            userId: 'opp-2',
            username: 'SeigneurLumiere',
            bot: false,
            teamId: 'TEAM_A',
            seatIndex: 0,
            primaryFaction: 'LIGHT',
          },
        ],
        createdAt: '2026-11-15T00:50:00Z',
        finishedAt: '2026-11-15T00:51:35Z',
      },
    ],
  };

  let authStub: {
    user: ReturnType<typeof signal<UserProfileDto | null>>;
    refreshProfile: ReturnType<typeof vi.fn>;
    getMatchHistory: ReturnType<typeof vi.fn>;
    equipCosmetics: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    authStub = {
      user: signal(sampleProfile),
      refreshProfile: vi.fn().mockResolvedValue(sampleProfile),
      getMatchHistory: vi.fn().mockImplementation((_limit: number, source?: MatchSource | null) => {
        if (source) {
          return Promise.resolve({
            stats: fullHistory.stats,
            matches: fullHistory.matches.filter((m) => m.matchSource === source),
          });
        }
        return Promise.resolve(fullHistory);
      }),
      equipCosmetics: vi.fn().mockResolvedValue(sampleProfile),
    };

    await TestBed.configureTestingModule({
      imports: [ProfilePageComponent],
      providers: [
        provideZonelessChangeDetection(),
        { provide: AuthService, useValue: authStub },
      ],
    }).compileComponents();
  });

  it('displays player winrate stats, recent form, and match history with expandable game overview', async () => {
    const fixture = TestBed.createComponent(ProfilePageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(authStub.getMatchHistory).toHaveBeenCalledWith(20, null);
    expect(text).toContain('66.7 %');
    expect(text).toContain('4 V · 2 D');
    expect(text).toContain('2 victoire(s) de suite');
    expect(text).toContain('Ombres Occultes');
    expect(text).toContain('Classé');
    expect(text).toContain('Tournoi');
    expect(text).toContain('Coupe Conquest');
    expect(text).toContain('+24 MMR');
    expect(text).toContain('Vous vs RivalGlace');
    expect(text).toContain('16 / 25 PV');
    expect(text).toContain('KO (0 PV)');
  });

  it('requests filtered match history when selecting a source filter button', async () => {
    const fixture = TestBed.createComponent(ProfilePageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const buttons = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    const tournamentBtn = buttons.find((b) => b.textContent?.trim() === 'Tournoi');
    expect(tournamentBtn).toBeDefined();

    tournamentBtn!.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(authStub.getMatchHistory).toHaveBeenCalledWith(20, 'TOURNAMENT');
    const items = fixture.nativeElement.querySelectorAll('details.match-disclosure');
    expect(items.length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Vous vs SeigneurLumiere');
    expect(fixture.nativeElement.textContent).not.toContain('Vous vs RivalGlace');
  });
});
