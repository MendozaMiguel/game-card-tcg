import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { Faction } from '../../core/models/arcane.models';

export type MusicIntensity = 'CALM' | 'COMBAT';

export type SfxKind =
  | 'ATTACK_SWING'
  | 'HIT_LIGHT'
  | 'HIT_HEAVY'
  | 'HIT_CRITICAL'
  | 'DIRECT_HIT'
  | 'DEFENSE_BLOCK'
  | 'DEFENSE_BROKEN'
  | 'POSTURE_DEFENSE'
  | 'POSTURE_ATTACK'
  | 'SHIELD_BUFF'
  | 'REACTIVE'
  | 'SUMMON_STANDARD'
  | 'SUMMON_MYTHIC'
  | 'SPELL_CAST'
  | 'HEAL'
  | 'CREATURE_DESTROY'
  | 'YOUR_TURN'
  | 'VICTORY'
  | 'DEFEAT';

interface AudioPreferences {
  musicEnabled: boolean;
  sfxEnabled: boolean;
  volume: number;
}

const PREFS_STORAGE_KEY = 'arcane_audio_prefs_v1';
const STEP_SECONDS = 60 / 116 / 2; // Eighth-note pulse at ~116 BPM (bardic 8-beat groove)
const TOTAL_PATTERN_STEPS = 64; // 8-bar looping Slavic D-Dorian phrase

// Frequencies in D Dorian: D, E, F, G, A, B, C, plus C# cadence tension
const N = {
  D2: 73.42,
  A2: 110.0,
  Bb2: 116.54,
  C3: 130.81,
  D3: 146.83,
  E3: 164.81,
  F3: 174.61,
  G3: 196.0,
  A3: 220.0,
  Bb3: 233.08,
  C4: 261.63,
  Cs4: 277.18,
  D4: 293.66,
  E4: 329.63,
  F4: 349.23,
  G4: 392.0,
  A4: 440.0,
  Bb4: 466.16,
  B4: 493.88,
  C5: 523.25,
  Cs5: 554.37,
  D5: 587.33,
  E5: 659.25,
  F5: 698.46,
} as const;

// 8-bar D-Dorian chord roots (1 chord every 8 steps)
const BAR_HARMONY: readonly { bass: number; fifth: number; third: number; oct: number }[] = [
  { bass: N.D2, fifth: N.A2, third: N.F3, oct: N.D3 },   // Bar 1: Dm
  { bass: N.C3, fifth: N.G3, third: N.E3, oct: N.C4 },   // Bar 2: C
  { bass: N.Bb2, fifth: N.F3, third: N.D3, oct: N.Bb3 }, // Bar 3: Bb
  { bass: N.A2, fifth: N.E3, third: N.Cs4, oct: N.A3 },  // Bar 4: A (Witcher tension)
  { bass: N.D2, fifth: N.A2, third: N.F3, oct: N.D3 },   // Bar 5: Dm
  { bass: N.F3, fifth: N.C4, third: N.A3, oct: N.F4 },   // Bar 6: F
  { bass: N.C3, fifth: N.G3, third: N.E3, oct: N.C4 },   // Bar 7: C
  { bass: N.A2, fifth: N.E3, third: N.Cs4, oct: N.A3 },  // Bar 8: A turnaround
];

// Slavic bowed fiddle / kemenche lead motif (sparse haunting phrases on 64-step grid)
const FIDDLE_MELODY: Readonly<Record<number, { freq: number; steps: number; amp: number }>> = {
  0: { freq: N.D4, steps: 3, amp: 0.75 },
  3: { freq: N.F4, steps: 2, amp: 0.7 },
  5: { freq: N.E4, steps: 3, amp: 0.65 },
  8: { freq: N.G4, steps: 4, amp: 0.8 },
  12: { freq: N.E4, steps: 2, amp: 0.65 },
  14: { freq: N.C4, steps: 2, amp: 0.6 },
  16: { freq: N.D4, steps: 3, amp: 0.8 },
  19: { freq: N.F4, steps: 2, amp: 0.75 },
  21: { freq: N.A4, steps: 3, amp: 0.88 },
  24: { freq: N.A4, steps: 2, amp: 0.9 },
  26: { freq: N.G4, steps: 2, amp: 0.75 },
  28: { freq: N.E4, steps: 4, amp: 0.82 },
  32: { freq: N.A4, steps: 3, amp: 0.92 },
  35: { freq: N.Bb4, steps: 2, amp: 0.88 },
  37: { freq: N.A4, steps: 3, amp: 0.84 },
  40: { freq: N.C5, steps: 3, amp: 0.96 },
  43: { freq: N.A4, steps: 2, amp: 0.86 },
  45: { freq: N.F4, steps: 3, amp: 0.82 },
  48: { freq: N.G4, steps: 3, amp: 0.85 },
  51: { freq: N.E4, steps: 2, amp: 0.78 },
  53: { freq: N.F4, steps: 3, amp: 0.8 },
  56: { freq: N.E4, steps: 3, amp: 0.84 },
  59: { freq: N.Cs4, steps: 3, amp: 0.86 },
  62: { freq: N.E4, steps: 2, amp: 0.76 },
};

@Injectable({
  providedIn: 'root',
})
export class ArcaneAudioService {
  private readonly destroyRef = inject(DestroyRef);

  readonly musicEnabled = signal(true);
  readonly sfxEnabled = signal(true);
  readonly masterVolume = signal(0.7);
  readonly intensity = signal<MusicIntensity>('CALM');
  readonly activeContextRunning = signal(false);

  private ctx: AudioContext | null = null;
  private masterGain: GainNode | null = null;
  private musicBus: GainNode | null = null;
  private sfxBus: GainNode | null = null;
  private hallReverb: ConvolverNode | null = null;
  private noiseBuffer: AudioBuffer | null = null;

  private musicRequested = false;
  private schedulerTimer: number | null = null;
  private nextNoteTime = 0;
  private currentStep = 0;
  private gestureAttached = false;
  private visibilityAttached = false;

  constructor() {
    this.loadPreferences();
    this.attachBrowserLifecycleListeners();
    this.destroyRef.onDestroy(() => this.dispose());
  }

  /** Enables background Witcher-inspired theme at the chosen intensity ('CALM' or 'COMBAT'). */
  startMusic(mode: MusicIntensity = 'CALM'): void {
    this.intensity.set(mode);
    this.musicRequested = true;
    if (!this.musicEnabled() || typeof window === 'undefined') {
      return;
    }
    const ctx = this.ensureContext();
    if (!ctx) return;
    if (ctx.state === 'running') {
      this.activeContextRunning.set(true);
      this.beginSequencer(ctx);
    } else {
      this.attachGestureUnlock();
    }
  }

  stopMusic(): void {
    this.musicRequested = false;
    this.stopSequencer();
  }

  setMusicEnabled(enabled: boolean): void {
    this.musicEnabled.set(enabled);
    this.savePreferences();
    this.applyGainLevels();
    if (enabled && this.musicRequested) {
      this.unlockAndResume();
    } else if (!enabled) {
      this.stopSequencer();
    }
  }

  setSfxEnabled(enabled: boolean): void {
    this.sfxEnabled.set(enabled);
    this.savePreferences();
    this.applyGainLevels();
    if (enabled) {
      this.unlockAndResume();
    }
  }

  setMasterVolume(volume: number): void {
    const clamped = Math.min(1, Math.max(0, volume));
    this.masterVolume.set(clamped);
    this.savePreferences();
    this.applyGainLevels();
  }

  /** Plays a synthesized dark-fantasy TCG sound effect tailored to combat outcome & faction. */
  playSfx(kind: SfxKind, faction: Faction | null = null): void {
    if (!this.sfxEnabled() || typeof window === 'undefined') {
      return;
    }
    const ctx = this.ensureContext();
    if (!ctx) return;

    if (ctx.state === 'suspended') {
      void ctx.resume().then(() => {
        if (ctx.state === 'running') {
          this.activeContextRunning.set(true);
          this.renderSfx(ctx, kind, faction);
        }
      }).catch(() => {});
      return;
    }

    if (ctx.state === 'running') {
      this.renderSfx(ctx, kind, faction);
    }
  }

  unlockAndResume(): void {
    if (typeof window === 'undefined') return;
    const ctx = this.ensureContext();
    if (!ctx) return;
    if (ctx.state === 'suspended') {
      void ctx.resume().then(() => {
        this.activeContextRunning.set(ctx.state === 'running');
        if (ctx.state === 'running' && this.musicRequested && this.musicEnabled()) {
          this.beginSequencer(ctx);
        }
      }).catch(() => {});
    } else if (ctx.state === 'running') {
      this.activeContextRunning.set(true);
      if (this.musicRequested && this.musicEnabled()) {
        this.beginSequencer(ctx);
      }
    }
  }

  private ensureContext(): AudioContext | null {
    if (typeof window === 'undefined') return null;
    if (this.ctx) return this.ctx;

    const Ctor =
      window.AudioContext ??
      (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (typeof Ctor !== 'function') {
      return null;
    }

    try {
      const ctx = new Ctor();
      const compressor = ctx.createDynamicsCompressor();
      compressor.threshold.setValueAtTime(-14, ctx.currentTime);
      compressor.knee.setValueAtTime(16, ctx.currentTime);
      compressor.ratio.setValueAtTime(6, ctx.currentTime);
      compressor.attack.setValueAtTime(0.004, ctx.currentTime);
      compressor.release.setValueAtTime(0.18, ctx.currentTime);

      const master = ctx.createGain();
      const music = ctx.createGain();
      const sfx = ctx.createGain();
      const reverb = this.createHallReverb(ctx);
      const reverbSend = ctx.createGain();
      reverbSend.gain.setValueAtTime(0.32, ctx.currentTime);

      music.connect(master);
      music.connect(reverbSend);
      reverbSend.connect(reverb);
      reverb.connect(master);

      sfx.connect(master);
      master.connect(compressor);
      compressor.connect(ctx.destination);

      this.ctx = ctx;
      this.masterGain = master;
      this.musicBus = music;
      this.sfxBus = sfx;
      this.hallReverb = reverb;
      this.noiseBuffer = this.createNoiseBuffer(ctx);
      this.applyGainLevels();
      this.attachGestureUnlock();
      return ctx;
    } catch {
      return null;
    }
  }

  private applyGainLevels(): void {
    if (!this.ctx || !this.masterGain || !this.musicBus || !this.sfxBus) return;
    const now = this.ctx.currentTime;
    const master = this.masterVolume();
    this.masterGain.gain.setTargetAtTime(master, now, 0.03);
    this.musicBus.gain.setTargetAtTime(this.musicEnabled() ? 0.34 : 0, now, 0.05);
    this.sfxBus.gain.setTargetAtTime(this.sfxEnabled() ? 0.85 : 0, now, 0.02);
  }

  private beginSequencer(ctx: AudioContext): void {
    if (this.schedulerTimer !== null || typeof window === 'undefined') {
      return;
    }
    this.nextNoteTime = ctx.currentTime + 0.06;
    this.schedulerTimer = window.setInterval(() => this.tickSequencer(), 80);
  }

  private stopSequencer(): void {
    if (this.schedulerTimer !== null && typeof window !== 'undefined') {
      window.clearInterval(this.schedulerTimer);
      this.schedulerTimer = null;
    }
  }

  private tickSequencer(): void {
    const ctx = this.ctx;
    if (!ctx || !this.musicEnabled() || !this.musicRequested || ctx.state !== 'running') {
      return;
    }
    if (typeof document !== 'undefined' && document.hidden) {
      return;
    }

    const horizon = ctx.currentTime + 0.22;
    while (this.nextNoteTime < horizon) {
      this.scheduleMusicStep(ctx, this.currentStep, this.nextNoteTime);
      this.nextNoteTime += STEP_SECONDS;
      this.currentStep = (this.currentStep + 1) % TOTAL_PATTERN_STEPS;
    }
  }

  private scheduleMusicStep(ctx: AudioContext, step: number, when: number): void {
    if (!this.musicBus) return;
    const barIdx = Math.floor(step / 8);
    const stepInBar = step % 8;
    const chord = BAR_HARMONY[barIdx];
    const isCombat = this.intensity() === 'COMBAT';

    // 1. Deep hurdy-gurdy drone on bar starts
    if (stepInBar === 0) {
      this.triggerDrone(ctx, chord.bass, STEP_SECONDS * 7.8, when, isCombat ? 0.22 : 0.16);
      this.triggerDrone(ctx, chord.fifth * 2, STEP_SECONDS * 7.8, when, isCombat ? 0.12 : 0.08);
    }

    // 2. Driving Slavic plucked lute/bouzouki 3+3+2 ostinato pattern (steps 0, 2, 3, 5, 6)
    const luteNotes: Readonly<Record<number, { freq: number; gain: number }>> = {
      0: { freq: chord.oct * 2, gain: 0.2 },
      2: { freq: chord.third * 2, gain: 0.14 },
      3: { freq: chord.fifth * 2, gain: 0.17 },
      5: { freq: chord.oct * 2, gain: 0.14 },
      6: { freq: chord.third * 2, gain: 0.17 },
      7: { freq: chord.fifth * 2, gain: isCombat ? 0.13 : 0.09 },
    };
    const luteHit = luteNotes[stepInBar];
    if (luteHit) {
      this.triggerPluckedLute(ctx, luteHit.freq, when, luteHit.gain);
    }

    // 3. Bodhran / frame drum pulse (Tavern pulse in CALM, war groove in COMBAT)
    if (stepInBar === 0 || (isCombat && (stepInBar === 3 || stepInBar === 6))) {
      const drumAccent = stepInBar === 0 ? 1.0 : 0.72;
      this.triggerFrameDrum(ctx, when, (isCombat ? 0.48 : 0.26) * drumAccent);
    } else if (isCombat && (stepInBar === 2 || stepInBar === 5 || stepInBar === 7)) {
      this.triggerRimTap(ctx, when, 0.11);
    }

    // 4. Bowed Slavic fiddle / kemenche melody (full melody in COMBAT, sparse phrase in CALM)
    const fiddle = FIDDLE_MELODY[step];
    if (fiddle && (isCombat || step % 16 < 8)) {
      this.triggerBowedFiddle(
        ctx,
        fiddle.freq,
        fiddle.steps * STEP_SECONDS,
        when,
        (isCombat ? 0.16 : 0.11) * fiddle.amp,
      );
    }
  }

  private triggerDrone(
    ctx: AudioContext,
    freq: number,
    duration: number,
    when: number,
    peakGain: number,
  ): void {
    if (!this.musicBus) return;
    const osc = ctx.createOscillator();
    const osc2 = ctx.createOscillator();
    const filter = ctx.createBiquadFilter();
    const gain = ctx.createGain();

    osc.type = 'sawtooth';
    osc.frequency.setValueAtTime(freq, when);
    osc2.type = 'triangle';
    osc2.frequency.setValueAtTime(freq * 1.003, when);

    filter.type = 'lowpass';
    filter.frequency.setValueAtTime(240, when);

    gain.gain.setValueAtTime(0.0001, when);
    gain.gain.exponentialRampToValueAtTime(peakGain, when + 0.18);
    gain.gain.exponentialRampToValueAtTime(peakGain * 0.65, when + duration * 0.7);
    gain.gain.exponentialRampToValueAtTime(0.0001, when + duration);

    osc.connect(filter);
    osc2.connect(filter);
    filter.connect(gain);
    gain.connect(this.musicBus);

    osc.start(when);
    osc2.start(when);
    osc.stop(when + duration + 0.04);
    osc2.stop(when + duration + 0.04);
  }

  private triggerPluckedLute(ctx: AudioContext, freq: number, when: number, peakGain: number): void {
    if (!this.musicBus) return;
    const osc = ctx.createOscillator();
    const body = ctx.createOscillator();
    const filter = ctx.createBiquadFilter();
    const gain = ctx.createGain();

    osc.type = 'sawtooth';
    osc.frequency.setValueAtTime(freq, when);
    body.type = 'triangle';
    body.frequency.setValueAtTime(freq * 2, when);

    filter.type = 'lowpass';
    filter.Q.setValueAtTime(2.8, when);
    filter.frequency.setValueAtTime(Math.min(4800, freq * 7), when);
    filter.frequency.exponentialRampToValueAtTime(Math.max(320, freq * 1.4), when + 0.28);

    gain.gain.setValueAtTime(0.0001, when);
    gain.gain.exponentialRampToValueAtTime(peakGain, when + 0.008);
    gain.gain.exponentialRampToValueAtTime(0.0001, when + 0.42);

    const bodyGain = ctx.createGain();
    bodyGain.gain.setValueAtTime(0.25, when);

    osc.connect(filter);
    body.connect(bodyGain);
    bodyGain.connect(filter);
    filter.connect(gain);
    gain.connect(this.musicBus);

    osc.start(when);
    body.start(when);
    osc.stop(when + 0.45);
    body.stop(when + 0.45);
  }

  private triggerBowedFiddle(
    ctx: AudioContext,
    freq: number,
    duration: number,
    when: number,
    peakGain: number,
  ): void {
    if (!this.musicBus) return;
    const osc = ctx.createOscillator();
    const sub = ctx.createOscillator();
    const lfo = ctx.createOscillator();
    const lfoGain = ctx.createGain();
    const formant = ctx.createBiquadFilter();
    const warm = ctx.createBiquadFilter();
    const gain = ctx.createGain();

    osc.type = 'sawtooth';
    osc.frequency.setValueAtTime(freq * 0.992, when);
    osc.frequency.exponentialRampToValueAtTime(freq, when + 0.06);

    sub.type = 'triangle';
    sub.frequency.setValueAtTime(freq, when);

    lfo.frequency.setValueAtTime(5.6, when);
    lfoGain.gain.setValueAtTime(3.2, when);
    lfo.connect(lfoGain);
    lfoGain.connect(osc.frequency);

    formant.type = 'bandpass';
    formant.frequency.setValueAtTime(1050, when);
    formant.Q.setValueAtTime(1.1, when);

    warm.type = 'lowpass';
    warm.frequency.setValueAtTime(2400, when);

    const fade = Math.min(0.11, duration * 0.35);
    gain.gain.setValueAtTime(0.0001, when);
    gain.gain.exponentialRampToValueAtTime(peakGain, when + fade);
    gain.gain.setValueAtTime(peakGain * 0.85, when + Math.max(fade, duration - 0.08));
    gain.gain.exponentialRampToValueAtTime(0.0001, when + duration);

    osc.connect(formant);
    sub.connect(formant);
    formant.connect(warm);
    warm.connect(gain);
    gain.connect(this.musicBus);

    osc.start(when);
    sub.start(when);
    lfo.start(when);
    osc.stop(when + duration + 0.03);
    sub.stop(when + duration + 0.03);
    lfo.stop(when + duration + 0.03);
  }

  private triggerFrameDrum(ctx: AudioContext, when: number, peakGain: number): void {
    if (!this.musicBus) return;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    osc.type = 'sine';
    osc.frequency.setValueAtTime(102, when);
    osc.frequency.exponentialRampToValueAtTime(42, when + 0.13);

    gain.gain.setValueAtTime(0.0001, when);
    gain.gain.exponentialRampToValueAtTime(peakGain, when + 0.008);
    gain.gain.exponentialRampToValueAtTime(0.0001, when + 0.24);

    osc.connect(gain);
    gain.connect(this.musicBus);

    osc.start(when);
    osc.stop(when + 0.26);
  }

  private triggerRimTap(ctx: AudioContext, when: number, peakGain: number): void {
    if (!this.musicBus || !this.noiseBuffer) return;
    const src = ctx.createBufferSource();
    src.buffer = this.noiseBuffer;

    const bp = ctx.createBiquadFilter();
    bp.type = 'bandpass';
    bp.frequency.setValueAtTime(2100, when);
    bp.Q.setValueAtTime(3.2, when);

    const gain = ctx.createGain();
    gain.gain.setValueAtTime(0.0001, when);
    gain.gain.exponentialRampToValueAtTime(peakGain, when + 0.004);
    gain.gain.exponentialRampToValueAtTime(0.0001, when + 0.065);

    src.connect(bp);
    bp.connect(gain);
    gain.connect(this.musicBus);
    src.start(when);
    src.stop(when + 0.08);
  }

  private renderSfx(ctx: AudioContext, kind: SfxKind, faction: Faction | null): void {
    const bus = this.sfxBus;
    if (!bus) return;
    const now = ctx.currentTime;

    switch (kind) {
      case 'ATTACK_SWING':
        this.sfxNoiseSweep(ctx, bus, now, 420, 2600, 0.16, 0.34, 1.4);
        this.sfxTone(ctx, bus, 'triangle', this.factionPitch(faction) * 1.4, this.factionPitch(faction) * 0.7, now, 0.14, 0.16);
        break;
      case 'HIT_LIGHT':
        this.sfxTone(ctx, bus, 'sine', 135, 42, now, 0.16, 0.45);
        this.sfxNoiseSweep(ctx, bus, now, 1800, 320, 0.11, 0.36, 0.9);
        break;
      case 'HIT_HEAVY':
        this.sfxTone(ctx, bus, 'sine', 160, 36, now, 0.24, 0.62);
        this.sfxNoiseSweep(ctx, bus, now, 2400, 240, 0.16, 0.48, 0.8);
        this.sfxTone(ctx, bus, 'sawtooth', 420, 140, now, 0.12, 0.18);
        break;
      case 'HIT_CRITICAL':
        this.sfxTone(ctx, bus, 'sine', 190, 32, now, 0.28, 0.74);
        this.sfxNoiseSweep(ctx, bus, now, 3200, 220, 0.2, 0.56, 0.7);
        this.sfxMetallicClang(ctx, bus, now, [540, 890, 1480], 0.22, 0.24);
        break;
      case 'DIRECT_HIT':
        this.sfxTone(ctx, bus, 'sine', 145, 28, now, 0.34, 0.78);
        this.sfxNoiseSweep(ctx, bus, now, 1600, 180, 0.22, 0.5, 0.8);
        this.sfxTone(ctx, bus, 'sawtooth', N.D3, N.D2, now + 0.02, 0.32, 0.22);
        break;
      case 'DEFENSE_BLOCK':
        // Oak-and-iron shield block + resonant parry ring
        this.sfxTone(ctx, bus, 'sine', 180, 78, now, 0.14, 0.46);
        this.sfxMetallicClang(ctx, bus, now, [395, 650, 1180], 0.28, 0.34);
        this.sfxNoiseSweep(ctx, bus, now, 2600, 900, 0.08, 0.22, 2.2);
        break;
      case 'DEFENSE_BROKEN':
        this.sfxMetallicClang(ctx, bus, now, [430, 720, 1340], 0.22, 0.32);
        this.sfxTone(ctx, bus, 'sawtooth', 520, 115, now + 0.03, 0.24, 0.28);
        this.sfxNoiseSweep(ctx, bus, now + 0.02, 2800, 280, 0.24, 0.42, 1.0);
        break;
      case 'POSTURE_DEFENSE':
        this.sfxTone(ctx, bus, 'sine', 240, 360, now, 0.1, 0.28);
        this.sfxMetallicClang(ctx, bus, now + 0.02, [510, 820], 0.15, 0.22);
        break;
      case 'POSTURE_ATTACK':
        this.sfxNoiseSweep(ctx, bus, now, 900, 3400, 0.14, 0.24, 3.0);
        this.sfxTone(ctx, bus, 'triangle', 480, 760, now + 0.02, 0.12, 0.18);
        break;
      case 'SHIELD_BUFF':
      case 'REACTIVE':
        this.sfxTone(ctx, bus, 'triangle', N.D4, N.A4, now, 0.18, 0.24);
        this.sfxTone(ctx, bus, 'sine', N.A4 * 2, N.D5 * 2, now + 0.04, 0.22, 0.18);
        break;
      case 'SUMMON_STANDARD':
        this.sfxTone(ctx, bus, 'sine', 118, 46, now, 0.18, 0.5);
        this.sfxNoiseSweep(ctx, bus, now, 900, 220, 0.1, 0.22, 1.2);
        break;
      case 'SUMMON_MYTHIC':
        this.sfxTone(ctx, bus, 'sine', 135, 34, now, 0.32, 0.68);
        this.sfxTone(ctx, bus, 'sawtooth', N.D3, N.D3, now + 0.02, 0.55, 0.18);
        this.sfxTone(ctx, bus, 'sawtooth', N.A3, N.A3, now + 0.05, 0.52, 0.15);
        this.sfxTone(ctx, bus, 'triangle', N.D4, N.D4, now + 0.08, 0.5, 0.16);
        break;
      case 'SPELL_CAST': {
        const root = this.factionPitch(faction);
        this.sfxNoiseSweep(ctx, bus, now, 400, 2800, 0.24, 0.26, 2.0);
        this.sfxTone(ctx, bus, 'triangle', root, root * 1.5, now, 0.28, 0.22);
        this.sfxTone(ctx, bus, 'sine', root * 1.5, root * 2, now + 0.07, 0.26, 0.18);
        break;
      }
      case 'HEAL':
        this.sfxTone(ctx, bus, 'sine', N.D4, N.D4, now, 0.24, 0.2);
        this.sfxTone(ctx, bus, 'sine', N.F4, N.F4, now + 0.07, 0.24, 0.2);
        this.sfxTone(ctx, bus, 'sine', N.A4, N.A4, now + 0.14, 0.28, 0.22);
        break;
      case 'CREATURE_DESTROY':
        this.sfxTone(ctx, bus, 'sine', 120, 30, now, 0.26, 0.52);
        this.sfxNoiseSweep(ctx, bus, now, 1800, 140, 0.28, 0.38, 1.2);
        break;
      case 'YOUR_TURN':
        this.sfxTone(ctx, bus, 'sine', 96, 48, now, 0.18, 0.32);
        this.sfxTone(ctx, bus, 'triangle', N.D4, N.A4, now + 0.05, 0.24, 0.18);
        break;
      case 'VICTORY':
        this.sfxTone(ctx, bus, 'triangle', N.D4, N.D4, now, 0.22, 0.25);
        this.sfxTone(ctx, bus, 'triangle', N.A4, N.A4, now + 0.14, 0.22, 0.25);
        this.sfxTone(ctx, bus, 'triangle', N.D5, N.D5, now + 0.28, 0.55, 0.3);
        break;
      case 'DEFEAT':
        this.sfxTone(ctx, bus, 'sawtooth', N.A3, N.A3, now, 0.35, 0.2);
        this.sfxTone(ctx, bus, 'sawtooth', N.F3, N.F3, now + 0.25, 0.4, 0.2);
        this.sfxTone(ctx, bus, 'sawtooth', N.D3, N.D3 * 0.96, now + 0.52, 0.65, 0.24);
        break;
    }
  }

  private factionPitch(faction: Faction | null): number {
    switch (faction) {
      case 'FIRE':
        return N.E4;
      case 'ICE':
        return N.A4;
      case 'SHADOW':
        return N.C4;
      case 'LIGHT':
        return N.G4;
      default:
        return N.D4;
    }
  }

  private sfxTone(
    ctx: AudioContext,
    dest: AudioNode,
    type: OscillatorType,
    startFreq: number,
    endFreq: number,
    when: number,
    duration: number,
    peakGain: number,
  ): void {
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = type;
    osc.frequency.setValueAtTime(Math.max(20, startFreq), when);
    if (Math.abs(endFreq - startFreq) > 1) {
      osc.frequency.exponentialRampToValueAtTime(Math.max(20, endFreq), when + duration);
    }

    gain.gain.setValueAtTime(0.0001, when);
    gain.gain.exponentialRampToValueAtTime(peakGain, when + 0.008);
    gain.gain.exponentialRampToValueAtTime(0.0001, when + duration);

    osc.connect(gain);
    gain.connect(dest);
    osc.start(when);
    osc.stop(when + duration + 0.02);
  }

  private sfxMetallicClang(
    ctx: AudioContext,
    dest: AudioNode,
    when: number,
    freqs: readonly number[],
    duration: number,
    peakGain: number,
  ): void {
    const perOscGain = peakGain / Math.max(1, freqs.length);
    for (const f of freqs) {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'triangle';
      osc.frequency.setValueAtTime(f, when);
      osc.frequency.exponentialRampToValueAtTime(f * 0.96, when + duration);

      gain.gain.setValueAtTime(0.0001, when);
      gain.gain.exponentialRampToValueAtTime(perOscGain, when + 0.004);
      gain.gain.exponentialRampToValueAtTime(0.0001, when + duration);

      osc.connect(gain);
      gain.connect(dest);
      osc.start(when);
      osc.stop(when + duration + 0.02);
    }
  }

  private sfxNoiseSweep(
    ctx: AudioContext,
    dest: AudioNode,
    when: number,
    startFreq: number,
    endFreq: number,
    duration: number,
    peakGain: number,
    q: number,
  ): void {
    if (!this.noiseBuffer) return;
    const src = ctx.createBufferSource();
    src.buffer = this.noiseBuffer;

    const filter = ctx.createBiquadFilter();
    filter.type = 'bandpass';
    filter.Q.setValueAtTime(q, when);
    filter.frequency.setValueAtTime(Math.max(40, startFreq), when);
    filter.frequency.exponentialRampToValueAtTime(Math.max(40, endFreq), when + duration);

    const gain = ctx.createGain();
    gain.gain.setValueAtTime(0.0001, when);
    gain.gain.exponentialRampToValueAtTime(peakGain, when + 0.01);
    gain.gain.exponentialRampToValueAtTime(0.0001, when + duration);

    src.connect(filter);
    filter.connect(gain);
    gain.connect(dest);
    src.start(when);
    src.stop(when + duration + 0.02);
  }

  private createNoiseBuffer(ctx: AudioContext): AudioBuffer {
    const length = Math.floor(ctx.sampleRate * 0.6);
    const buf = ctx.createBuffer(1, length, ctx.sampleRate);
    const data = buf.getChannelData(0);
    for (let i = 0; i < length; i++) {
      data[i] = Math.random() * 2 - 1;
    }
    return buf;
  }

  private createHallReverb(ctx: AudioContext): ConvolverNode {
    const duration = 1.35;
    const length = Math.floor(ctx.sampleRate * duration);
    const impulse = ctx.createBuffer(2, length, ctx.sampleRate);
    for (let ch = 0; ch < 2; ch++) {
      const channel = impulse.getChannelData(ch);
      for (let i = 0; i < length; i++) {
        const decay = Math.pow(1 - i / length, 2.6);
        channel[i] = (Math.random() * 2 - 1) * decay;
      }
    }
    const convolver = ctx.createConvolver();
    convolver.buffer = impulse;
    return convolver;
  }

  private attachGestureUnlock(): void {
    if (this.gestureAttached || typeof window === 'undefined') return;
    this.gestureAttached = true;
    const unlock = (): void => {
      this.unlockAndResume();
      if (this.ctx?.state === 'running') {
        window.removeEventListener('pointerdown', unlock);
        window.removeEventListener('keydown', unlock);
        this.gestureAttached = false;
      }
    };
    window.addEventListener('pointerdown', unlock, { passive: true });
    window.addEventListener('keydown', unlock, { passive: true });
  }

  private attachBrowserLifecycleListeners(): void {
    if (this.visibilityAttached || typeof document === 'undefined') return;
    this.visibilityAttached = true;
    document.addEventListener('visibilitychange', this.onVisibilityChange);
  }

  private readonly onVisibilityChange = (): void => {
    const ctx = this.ctx;
    if (!ctx) return;
    if (document.hidden) {
      if (ctx.state === 'running') {
        void ctx.suspend().catch(() => {});
      }
    } else if ((this.musicRequested && this.musicEnabled()) || this.sfxEnabled()) {
      void ctx.resume().then(() => {
        this.activeContextRunning.set(ctx.state === 'running');
      }).catch(() => {});
    }
  };

  private loadPreferences(): void {
    if (typeof window === 'undefined' || typeof localStorage === 'undefined') return;
    try {
      const raw = localStorage.getItem(PREFS_STORAGE_KEY);
      if (!raw) return;
      const parsed = JSON.parse(raw) as Partial<AudioPreferences>;
      if (typeof parsed.musicEnabled === 'boolean') {
        this.musicEnabled.set(parsed.musicEnabled);
      }
      if (typeof parsed.sfxEnabled === 'boolean') {
        this.sfxEnabled.set(parsed.sfxEnabled);
      }
      if (typeof parsed.volume === 'number' && Number.isFinite(parsed.volume)) {
        this.masterVolume.set(Math.min(1, Math.max(0, parsed.volume)));
      }
    } catch {
      // Ignore malformed stored preferences
    }
  }

  private savePreferences(): void {
    if (typeof window === 'undefined' || typeof localStorage === 'undefined') return;
    try {
      const prefs: AudioPreferences = {
        musicEnabled: this.musicEnabled(),
        sfxEnabled: this.sfxEnabled(),
        volume: this.masterVolume(),
      };
      localStorage.setItem(PREFS_STORAGE_KEY, JSON.stringify(prefs));
    } catch {
      // Ignore quota / privacy mode storage restrictions
    }
  }

  private dispose(): void {
    this.stopSequencer();
    if (typeof document !== 'undefined' && this.visibilityAttached) {
      document.removeEventListener('visibilitychange', this.onVisibilityChange);
    }
    if (this.ctx) {
      void this.ctx.close().catch(() => {});
      this.ctx = null;
    }
  }
}
