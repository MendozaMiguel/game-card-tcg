import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { ArcaneAudioService } from './arcane-audio.service';

describe('ArcaneAudioService', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection()],
    });
  });

  it('persists music, sfx, and master volume preferences cleanly in localStorage', () => {
    const service = TestBed.inject(ArcaneAudioService);

    expect(service.musicEnabled()).toBe(true);
    expect(service.sfxEnabled()).toBe(true);
    expect(service.masterVolume()).toBeCloseTo(0.7);

    service.setMusicEnabled(false);
    service.setSfxEnabled(false);
    service.setMasterVolume(0.45);

    const saved = JSON.parse(localStorage.getItem('arcane_audio_prefs_v1') ?? '{}') as Record<
      string,
      unknown
    >;
    expect(saved['musicEnabled']).toBe(false);
    expect(saved['sfxEnabled']).toBe(false);
    expect(saved['volume']).toBeCloseTo(0.45);

    service.setMasterVolume(1.5);
    expect(service.masterVolume()).toBe(1);
    service.setMasterVolume(-0.2);
    expect(service.masterVolume()).toBe(0);
  });

  it('triggers attack, defense block, turn, and Witcher-style D-Dorian sequencer via Web Audio API without errors', () => {
    const oscMock = {
      type: 'sine',
      frequency: { setValueAtTime: vi.fn(), exponentialRampToValueAtTime: vi.fn() },
      connect: vi.fn(),
      start: vi.fn(),
      stop: vi.fn(),
    };
    const gainMock = {
      gain: {
        setValueAtTime: vi.fn(),
        setTargetAtTime: vi.fn(),
        exponentialRampToValueAtTime: vi.fn(),
      },
      connect: vi.fn(),
    };
    const filterMock = {
      type: 'lowpass',
      frequency: { setValueAtTime: vi.fn(), exponentialRampToValueAtTime: vi.fn() },
      Q: { setValueAtTime: vi.fn() },
      connect: vi.fn(),
    };
    const ctxMock = {
      state: 'running',
      currentTime: 0,
      sampleRate: 44100,
      destination: {},
      resume: vi.fn().mockResolvedValue(undefined),
      close: vi.fn().mockResolvedValue(undefined),
      createDynamicsCompressor: vi.fn(() => ({
        threshold: { setValueAtTime: vi.fn() },
        knee: { setValueAtTime: vi.fn() },
        ratio: { setValueAtTime: vi.fn() },
        attack: { setValueAtTime: vi.fn() },
        release: { setValueAtTime: vi.fn() },
        connect: vi.fn(),
      })),
      createGain: vi.fn(() => gainMock),
      createOscillator: vi.fn(() => oscMock),
      createBiquadFilter: vi.fn(() => filterMock),
      createBuffer: vi.fn((_c: number, len: number) => ({
        getChannelData: () => new Float32Array(len),
      })),
      createBufferSource: vi.fn(() => ({
        buffer: null,
        connect: vi.fn(),
        start: vi.fn(),
        stop: vi.fn(),
      })),
      createConvolver: vi.fn(() => ({ buffer: null, connect: vi.fn() })),
    };

    (window as unknown as Record<string, unknown>)['AudioContext'] = function () {
      return ctxMock;
    };

    const service = TestBed.inject(ArcaneAudioService);
    service.startMusic('COMBAT');
    expect(service.intensity()).toBe('COMBAT');
    expect(service.activeContextRunning()).toBe(true);

    service.playSfx('ATTACK_SWING', 'FIRE');
    service.playSfx('HIT_CRITICAL', 'ICE');
    service.playSfx('DEFENSE_BLOCK', 'LIGHT');
    service.playSfx('DEFENSE_BROKEN', 'SHADOW');
    service.playSfx('SUMMON_MYTHIC', 'FIRE');
    service.playSfx('VICTORY');

    expect(ctxMock.createOscillator).toHaveBeenCalled();
    expect(ctxMock.createBufferSource).toHaveBeenCalled();

    service.stopMusic();
    delete (window as unknown as Record<string, unknown>)['AudioContext'];
  });
});
