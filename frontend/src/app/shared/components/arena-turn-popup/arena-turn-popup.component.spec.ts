import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ArenaTurnPopupComponent, TurnAnnouncement } from './arena-turn-popup.component';

describe('ArenaTurnPopupComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ArenaTurnPopupComponent],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
  });

  it('stays unrendered when no turn announcement is active', () => {
    const fixture = TestBed.createComponent(ArenaTurnPopupComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.arcane-turn-card')).toBeNull();
  });

  it('renders the large non-blocking turn phrase with opponent context and faction attribute', () => {
    const fixture = TestBed.createComponent(ArenaTurnPopupComponent);
    const announcement: TurnAnnouncement = {
      key: 'match-1:3:player-1',
      turnNumber: 3,
      phrase: 'C’est à vous de jouer !',
      opponentName: 'Archimage Lyra',
      faction: 'FIRE',
    };
    fixture.componentRef.setInput('announcement', announcement);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const scrim = el.querySelector('.arcane-turn-scrim');
    const phrase = el.querySelector('.arcane-turn-phrase');
    const eyebrow = el.querySelector('.arcane-turn-eyebrow');

    expect(scrim?.getAttribute('data-faction')).toBe('FIRE');
    expect(phrase?.textContent?.trim()).toBe('C’est à vous de jouer !');
    expect(eyebrow?.textContent).toContain('Tour 3');
    expect(eyebrow?.textContent).toContain('Contre Archimage Lyra');
  });
});
