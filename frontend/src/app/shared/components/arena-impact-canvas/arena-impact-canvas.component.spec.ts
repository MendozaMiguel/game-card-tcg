import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { ArenaImpactCanvasComponent, CanvasImpactBurst } from './arena-impact-canvas.component';

describe('ArenaImpactCanvasComponent', () => {
  it('mounts safely and accepts bursts under JSDOM/reduced-motion without throwing', () => {
    const fixture = TestBed.createComponent(ArenaImpactCanvasComponent);
    const bursts: CanvasImpactBurst[] = [
      {
        id: 'burst-1',
        kind: 'CLASH',
        x: 120,
        y: 80,
        dx: 40,
        dy: -110,
        faction: 'FIRE',
        power: 5,
      },
      {
        id: 'burst-2',
        kind: 'SUMMON_MYTHIC',
        x: 200,
        y: 160,
        faction: 'LIGHT',
        power: 5,
      },
      {
        id: 'burst-3',
        kind: 'TRAIL_STREAK',
        x: 90,
        y: 140,
        dx: 60,
        dy: -90,
        faction: 'FIRE',
        power: 2,
      },
      {
        id: 'burst-4',
        kind: 'SPELL_BURST',
        x: 220,
        y: 100,
        faction: 'SHADOW',
        power: 4,
      },
    ];
    fixture.componentRef.setInput('bursts', bursts);
    fixture.detectChanges();

    const canvas = fixture.nativeElement.querySelector('canvas');
    expect(canvas).toBeTruthy();
    expect(canvas?.getAttribute('aria-hidden')).toBe('true');

    fixture.destroy();
  });
});
