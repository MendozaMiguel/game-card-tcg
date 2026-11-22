import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { CardViewDto } from '../../../core/models/arcane.models';
import { ArcaneCardComponent } from './arcane-card.component';

function makeMonsterCard(partial: Partial<CardViewDto> = {}): CardViewDto {
  return {
    instanceId: 'inst-1',
    ownerPlayerId: 'p1',
    slot: 0,
    hidden: false,
    faceDown: false,
    cardBackSkin: 'DEFAULT',
    foil: false,
    cardId: 'FIRE_M_10',
    name: "Dragon de Braise d'Aurëa",
    faction: 'FIRE',
    cardType: 'MONSTER',
    epic: true,
    ramp: false,
    energyCost: 5,
    baseAtk: 5,
    baseDef: 4,
    effectiveAtk: 5,
    effectiveDef: 4,
    keywords: [],
    description: "Flamme Dévorante : Inflige 2 dégâts aux PV adverses.",
    abilitiesScript: [],
    posture: 'ATTACK',
    exhausted: false,
    slowed: false,
    camouflaged: false,
    activatedThisTurn: false,
    changedPostureThisTurn: false,
    canAttackNow: true,
    curseRemainingTurns: null,
    ...partial,
  };
}

describe('ArcaneCardComponent', () => {
  it('renders painted artwork without long rules text in tabletop mode while keeping epic aura', () => {
    const fixture = TestBed.createComponent(ArcaneCardComponent);
    fixture.componentRef.setInput('card', makeMonsterCard());
    fixture.componentRef.setInput('compact', true);
    fixture.componentRef.setInput('tabletop', true);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const img = el.querySelector('.arcane-art-frame img');
    expect(img?.getAttribute('src')).toBe('/cards/fire-dragon.jpg');
    expect(el.textContent).not.toContain('Flamme Dévorante');
    expect(el.querySelector('.arcane-card-mythic')).toBeTruthy();
    expect(el.querySelector('.arcane-card-stats')?.textContent).toContain('5');
    expect(el.querySelector('.arcane-card-stats')?.textContent).toContain('4');
  });

  it('falls back to multi-tone SVG artwork in tabletop mode for cards without raster assets', () => {
    const fixture = TestBed.createComponent(ArcaneCardComponent);
    fixture.componentRef.setInput(
      'card',
      makeMonsterCard({ cardId: 'UNMAPPED_CUSTOM_01', faction: 'SHADOW', epic: false, energyCost: 2 }),
    );
    fixture.componentRef.setInput('compact', true);
    fixture.componentRef.setInput('tabletop', true);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.arcane-art-frame img')).toBeNull();
    expect(el.querySelector('app-arcane-card-art svg')).toBeTruthy();
  });
});
