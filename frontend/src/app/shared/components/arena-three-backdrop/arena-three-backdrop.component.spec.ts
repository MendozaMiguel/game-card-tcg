import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ArenaThreeBackdropComponent } from './arena-three-backdrop.component';

describe('ArenaThreeBackdropComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ArenaThreeBackdropComponent],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
  });

  it('mounts decorative aria-hidden canvas and degrades safely without WebGL context', async () => {
    const fixture = TestBed.createComponent(ArenaThreeBackdropComponent);
    fixture.detectChanges();
    await fixture.whenStable();

    const canvas = (fixture.nativeElement as HTMLElement).querySelector('canvas');
    expect(canvas).toBeTruthy();
    expect(canvas?.getAttribute('aria-hidden')).toBe('true');

    fixture.destroy();
  });

  it('handles faction changes and combat pulse updates without errors during teardown', async () => {
    const fixture = TestBed.createComponent(ArenaThreeBackdropComponent);
    fixture.componentRef.setInput('faction', 'FIRE');
    fixture.componentRef.setInput('pulseTrigger', 'wave-1');
    fixture.detectChanges();
    await fixture.whenStable();

    fixture.componentRef.setInput('faction', 'SHADOW');
    fixture.componentRef.setInput('pulseTrigger', 'wave-2');
    fixture.detectChanges();

    expect(() => fixture.destroy()).not.toThrow();
  });
});
