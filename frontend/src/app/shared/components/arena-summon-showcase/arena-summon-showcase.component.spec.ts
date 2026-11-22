import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { MythicShowcaseBanner } from '../../utils/arena-vfx';
import { ArenaSummonShowcaseComponent } from './arena-summon-showcase.component';

describe('ArenaSummonShowcaseComponent', () => {
  it('renders non-blocking mythic card showcase with resolved artwork when active', () => {
    const fixture = TestBed.createComponent(ArenaSummonShowcaseComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.arcane-showcase-card')).toBeNull();

    const banner: MythicShowcaseBanner = {
      id: 'mythic-1',
      title: 'INVOCATION ÉPIQUE ★',
      subtitle: 'Présence Légendaire sur le Plateau',
      cardName: "Dragon de Braise d'Aurëa",
      cardId: 'FIRE_M_10',
      faction: 'FIRE',
      isUltimate: false,
      atk: 5,
      def: 4,
      cost: 5,
      durationMs: 1450,
    };
    fixture.componentRef.setInput('showcase', banner);
    fixture.detectChanges();

    const stage = fixture.nativeElement.querySelector('.arcane-showcase-stage');
    const img = fixture.nativeElement.querySelector('img');
    expect(stage?.getAttribute('aria-hidden')).toBe('true');
    expect(fixture.nativeElement.textContent).toContain("Dragon de Braise d'Aurëa");
    expect(fixture.nativeElement.textContent).toContain('5/4');
    expect(img?.getAttribute('src')).toBe('/cards/fire-dragon.jpg');
  });
});
