import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { AnnouncerService } from './core/services/announcer.service';

describe('App Root Shell', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
  });

  it('should create the app shell and expose centralized live regions', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(fixture.componentInstance).toBeTruthy();
    expect(el.querySelectorAll('[aria-live]').length).toBe(2);
  });

  it('should display toast notifications pushed to AnnouncerService', async () => {
    const fixture = TestBed.createComponent(App);
    const announcer = TestBed.inject(AnnouncerService);
    announcer.pushToast('Deck validé avec succès !', 'success');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Deck validé avec succès !');
  });
});
