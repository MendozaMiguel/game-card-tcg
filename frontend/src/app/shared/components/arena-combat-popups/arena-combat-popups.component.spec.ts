import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import {
  ArenaCombatPopupsComponent,
  FloatingCombatPopup,
} from './arena-combat-popups.component';

describe('ArenaCombatPopupsComponent', () => {
  it('renders Cross Blitz arcade damage and guard callouts cleanly without blocking focus', () => {
    const fixture = TestBed.createComponent(ArenaCombatPopupsComponent);
    const popups: FloatingCombatPopup[] = [
      {
        id: 'pop-1',
        x: 180,
        y: 95,
        headline: '-5 PV',
        sublabel: 'FRAPPE DIRECTE!',
        tone: 'DIRECT',
        faction: 'FIRE',
        tiltDeg: -4,
      },
      {
        id: 'pop-2',
        x: 260,
        y: 150,
        headline: 'PARÉ!',
        sublabel: 'DÉFENSE',
        tone: 'BLOCK',
        faction: 'ICE',
        tiltDeg: 2,
      },
    ];

    fixture.componentRef.setInput('popups', popups);
    fixture.detectChanges();

    const layer = fixture.nativeElement.querySelector('.arcane-popups-layer');
    expect(layer?.getAttribute('aria-hidden')).toBe('true');

    const items = Array.from(
      fixture.nativeElement.querySelectorAll('.arcane-popup-item') as NodeListOf<HTMLElement>,
    );
    expect(items.length).toBe(2);
    expect(items[0].getAttribute('data-tone')).toBe('DIRECT');
    expect(items[0].textContent).toContain('-5 PV');
    expect(items[0].textContent).toContain('FRAPPE DIRECTE!');
    expect(items[1].getAttribute('data-tone')).toBe('BLOCK');
    expect(items[1].textContent).toContain('PARÉ!');

    fixture.destroy();
  });
});
