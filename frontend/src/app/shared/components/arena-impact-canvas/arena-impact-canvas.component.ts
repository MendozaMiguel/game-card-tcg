import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  effect,
  inject,
  input,
  viewChild,
} from '@angular/core';
import { Faction } from '../../../core/models/arcane.models';
import { prefersReducedMotion } from '../../utils/motion-presets';

export type ImpactBurstKind =
  | 'CLASH'
  | 'DIRECT_STRIKE'
  | 'SHATTER'
  | 'PARRY'
  | 'TRAIL_STREAK'
  | 'SPELL_BURST'
  | 'SUMMON_STANDARD'
  | 'SUMMON_HEAVY'
  | 'SUMMON_MYTHIC';

export interface CanvasImpactBurst {
  id: string;
  kind: ImpactBurstKind;
  x: number;
  y: number;
  dx?: number;
  dy?: number;
  faction: Faction;
  power?: number;
}

interface CanvasParticle {
  x: number;
  y: number;
  px: number;
  py: number;
  vx: number;
  vy: number;
  gravity: number;
  drag: number;
  size: number;
  alpha: number;
  age: number;
  life: number;
  color: string;
  shard: boolean;
  rot: number;
  vRot: number;
}

interface ShockRing {
  x: number;
  y: number;
  r0: number;
  rMax: number;
  age: number;
  life: number;
  lineWidth: number;
  color: string;
  ellipseY: number;
  hexSeal: boolean;
  rot: number;
}

interface SlashArc {
  x: number;
  y: number;
  angle: number;
  length: number;
  age: number;
  life: number;
  color: string;
}

interface StarBurst {
  x: number;
  y: number;
  rMax: number;
  points: number;
  rot: number;
  age: number;
  life: number;
  color: string;
}

interface JaggedArc {
  x: number;
  y: number;
  segments: readonly { x: number; y: number }[];
  age: number;
  life: number;
  color: string;
}

const FACTION_HEX: Readonly<Record<Faction, { core: string; glow: string; spark: string }>> = {
  FIRE: { core: '#fff7ed', glow: '#f97316', spark: '#fde047' },
  ICE: { core: '#f0f9ff', glow: '#38bdf8', spark: '#bae6fd' },
  SHADOW: { core: '#faf5ff', glow: '#a855f7', spark: '#e879f9' },
  LIGHT: { core: '#fefce8', glow: '#eab308', spark: '#fde68a' },
};

const MAX_PARTICLES = 220;

/**
 * Calque Canvas 2D haute fréquence (blending additif) synchronisé avec les chocs
 * Monstre vs Monstre, frappes directes sur joueur, explosions de destruction et invocations épiques.
 * Met automatiquement `requestAnimationFrame` en veille (0 % CPU) dès qu'aucun effet n'est actif.
 */
@Component({
  selector: 'app-arena-impact-canvas',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [
    `
      :host {
        position: absolute;
        inset: 0;
        z-index: 43;
        display: block;
        pointer-events: none;
        overflow: hidden;
        border-radius: inherit;
        contain: strict;
        content-visibility: auto;
        contain-intrinsic-size: auto 480px;
      }

      .arena-impact-canvas {
        display: block;
        inline-size: 100%;
        block-size: 100%;
        pointer-events: none;
      }

      .arena-impact-vignette {
        position: absolute;
        inset: 0;
        pointer-events: none;
        border-radius: inherit;
        opacity: 0;
        --v-c: 249 115 22;
        box-shadow: inset 0 0 72px 14px rgb(var(--v-c) / 0.42);
      }

      .arena-impact-vignette[data-tint='CRITICAL_HIT'] {
        --v-c: 244 63 94;
      }
      .arena-impact-vignette[data-tint='ICE'] {
        --v-c: 56 189 248;
      }
      .arena-impact-vignette[data-tint='SHADOW'] {
        --v-c: 168 85 247;
      }
      .arena-impact-vignette[data-tint='LIGHT'] {
        --v-c: 250 204 21;
      }

      @media (prefers-reduced-motion: no-preference) {
        .arena-impact-vignette {
          animation: arenaVignettePulse 0.48s cubic-bezier(0.16, 1, 0.3, 1) forwards;
        }
        @keyframes arenaVignettePulse {
          0% {
            opacity: 0;
          }
          25% {
            opacity: 1;
          }
          100% {
            opacity: 0;
          }
        }
      }

      @media (forced-colors: active) {
        :host {
          display: none;
        }
      }
    `,
  ],
  template: `
    @if (vignetteTint(); as tint) {
      <div class="arena-impact-vignette" [attr.data-tint]="tint" aria-hidden="true"></div>
    }
    <canvas #fxCanvas class="arena-impact-canvas" aria-hidden="true"></canvas>
  `,
})
export class ArenaImpactCanvasComponent {
  readonly bursts = input<readonly CanvasImpactBurst[]>([]);
  readonly vignetteTint = input<Faction | 'CRITICAL_HIT' | null>(null);

  private readonly hostRef = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly canvasRef = viewChild<ElementRef<HTMLCanvasElement>>('fxCanvas');

  private ctx: CanvasRenderingContext2D | null = null;
  private width = 0;
  private height = 0;
  private dpr = 1;
  private rafId: number | null = null;
  private lastTickMs = 0;
  private disposed = false;

  private readonly particles: CanvasParticle[] = [];
  private readonly rings: ShockRing[] = [];
  private readonly slashes: SlashArc[] = [];
  private readonly stars: StarBurst[] = [];
  private readonly arcs: JaggedArc[] = [];
  private readonly seenIds = new Set<string>();
  private resizeObserver: ResizeObserver | null = null;
  private isPaused = false;
  private removeLifecycleListeners: (() => void) | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.disposed = true;
      this.stopLoop();
      this.resizeObserver?.disconnect();
      this.resizeObserver = null;
      this.removeLifecycleListeners?.();
      this.removeLifecycleListeners = null;
      this.particles.length = 0;
      this.rings.length = 0;
      this.slashes.length = 0;
      this.stars.length = 0;
      this.arcs.length = 0;
      this.ctx = null;
    });

    afterNextRender(() => {
      this.initCanvas();
    });

    effect(() => {
      const list = this.bursts();
      if (this.disposed || prefersReducedMotion()) {
        return;
      }
      for (const burst of list) {
        if (!this.seenIds.has(burst.id)) {
          this.seenIds.add(burst.id);
          if (this.seenIds.size > 96) {
            const first = this.seenIds.values().next().value;
            if (first !== undefined) this.seenIds.delete(first);
          }
          this.spawnBurst(burst);
        }
      }
    });
  }

  private initCanvas(): void {
    const canvas = this.canvasRef()?.nativeElement;
    const host = this.hostRef.nativeElement;
    if (!canvas || !host || typeof canvas.getContext !== 'function') {
      return;
    }
    try {
      this.ctx = canvas.getContext('2d');
    } catch {
      this.ctx = null;
      return;
    }
    if (!this.ctx) {
      return;
    }

    const syncSize = (w: number, h: number): void => {
      this.width = Math.max(1, Math.round(w));
      this.height = Math.max(1, Math.round(h));
      this.dpr = typeof window !== 'undefined' ? Math.min(window.devicePixelRatio || 1, 2) : 1;
      canvas.width = Math.round(this.width * this.dpr);
      canvas.height = Math.round(this.height * this.dpr);
    };

    syncSize(host.clientWidth || 640, host.clientHeight || 480);

    if (typeof ResizeObserver !== 'undefined') {
      this.resizeObserver = new ResizeObserver((entries) => {
        const rect = entries[0]?.contentRect;
        if (rect && rect.width > 4 && rect.height > 4) {
          syncSize(rect.width, rect.height);
        }
      });
      this.resizeObserver.observe(host);
    }

    const syncPausedState = (hidden: boolean): void => {
      this.isPaused = hidden;
      if (hidden) {
        this.stopLoop();
      } else if (this.hasActiveFx()) {
        this.ensureLoop();
      }
    };

    const onVisibility = (): void => {
      syncPausedState(typeof document !== 'undefined' && document.visibilityState === 'hidden');
    };
    const onContentVisibility = (evt: Event): void => {
      const skipped = (evt as Event & { skipped?: boolean }).skipped;
      syncPausedState(Boolean(skipped) || (typeof document !== 'undefined' && document.visibilityState === 'hidden'));
    };
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', onVisibility);
    }
    host.addEventListener('contentvisibilityautostatechange', onContentVisibility);
    this.removeLifecycleListeners = () => {
      if (typeof document !== 'undefined') {
        document.removeEventListener('visibilitychange', onVisibility);
      }
      host.removeEventListener('contentvisibilityautostatechange', onContentVisibility);
    };

    if (this.hasActiveFx()) {
      this.ensureLoop();
    }
  }

  private hasActiveFx(): boolean {
    return (
      this.particles.length > 0 ||
      this.rings.length > 0 ||
      this.slashes.length > 0 ||
      this.stars.length > 0 ||
      this.arcs.length > 0
    );
  }

  private spawnBurst(burst: CanvasImpactBurst): void {
    const pal = FACTION_HEX[burst.faction] ?? FACTION_HEX.FIRE;
    const power = Math.max(1, burst.power ?? 2);
    const angle = Math.atan2(burst.dy ?? -1, burst.dx ?? 0);

    switch (burst.kind) {
      case 'TRAIL_STREAK': {
        this.emitConeSparks(burst.x, burst.y, 12, angle, 0.34, 240, 520, pal);
        break;
      }

      case 'CLASH': {
        this.rings.push(
          {
            x: burst.x,
            y: burst.y,
            r0: 10,
            rMax: 72 + power * 9,
            age: 0,
            life: 0.38,
            lineWidth: 4.8,
            color: pal.glow,
            ellipseY: 1,
            hexSeal: false,
            rot: 0,
          },
          {
            x: burst.x,
            y: burst.y,
            r0: 4,
            rMax: 46 + power * 5,
            age: 0,
            life: 0.24,
            lineWidth: 3.2,
            color: pal.core,
            ellipseY: 1,
            hexSeal: false,
            rot: 0,
          },
        );
        this.stars.push({
          x: burst.x,
          y: burst.y,
          rMax: 44 + power * 7,
          points: 8,
          rot: angle + 0.3,
          age: 0,
          life: 0.2,
          color: pal.spark,
        });
        this.slashes.push(
          {
            x: burst.x,
            y: burst.y,
            angle: angle + 0.48,
            length: 84 + power * 9,
            age: 0,
            life: 0.26,
            color: pal.glow,
          },
          {
            x: burst.x,
            y: burst.y,
            angle: angle - 0.54,
            length: 76 + power * 8,
            age: 0,
            life: 0.24,
            color: pal.spark,
          },
        );
        if (power >= 4) {
          this.emitJaggedArcs(burst.x, burst.y, 3, 52 + power * 5, pal.spark);
        }
        this.emitRadialSparks(burst.x, burst.y, Math.min(40, 20 + power * 4), 130, 390, pal, false);
        break;
      }

      case 'PARRY': {
        this.rings.push({
          x: burst.x,
          y: burst.y,
          r0: 14,
          rMax: 68,
          age: 0,
          life: 0.4,
          lineWidth: 3.8,
          color: '#60a5fa',
          ellipseY: 1,
          hexSeal: true,
          rot: 0,
        });
        this.stars.push({
          x: burst.x,
          y: burst.y,
          rMax: 34,
          points: 6,
          rot: 0.2,
          age: 0,
          life: 0.16,
          color: '#bae6fd',
        });
        const backAngle = angle + Math.PI;
        this.emitConeSparks(burst.x, burst.y, 22, backAngle, 1.45, 140, 330, {
          core: '#e0f2fe',
          glow: '#60a5fa',
          spark: '#fde047',
        });
        break;
      }

      case 'DIRECT_STRIKE': {
        this.rings.push(
          {
            x: burst.x,
            y: burst.y,
            r0: 14,
            rMax: 118 + power * 10,
            age: 0,
            life: 0.48,
            lineWidth: 6,
            color: '#fb7185',
            ellipseY: 1,
            hexSeal: false,
            rot: 0,
          },
          {
            x: burst.x,
            y: burst.y,
            r0: 8,
            rMax: 84 + power * 8,
            age: 0,
            life: 0.32,
            lineWidth: 3.8,
            color: pal.spark,
            ellipseY: 1,
            hexSeal: false,
            rot: 0,
          },
        );
        this.stars.push({
          x: burst.x,
          y: burst.y,
          rMax: 74 + power * 8,
          points: 10,
          rot: 0.25,
          age: 0,
          life: 0.23,
          color: '#fda4af',
        });
        for (let s = 0; s < 3; s++) {
          this.slashes.push({
            x: burst.x,
            y: burst.y,
            angle: (Math.PI / 3) * s + 0.28,
            length: 102 + power * 7,
            age: 0,
            life: 0.28,
            color: s === 0 ? '#fff1f2' : pal.glow,
          });
        }
        this.emitJaggedArcs(burst.x, burst.y, 4, 72 + power * 6, '#fda4af');
        this.emitRadialSparks(burst.x, burst.y, Math.min(48, 28 + power * 4), 160, 450, pal, false);
        break;
      }

      case 'SPELL_BURST': {
        this.rings.push(
          {
            x: burst.x,
            y: burst.y,
            r0: 14,
            rMax: 92 + power * 8,
            age: 0,
            life: 0.46,
            lineWidth: 4.5,
            color: pal.glow,
            ellipseY: 1,
            hexSeal: burst.faction === 'LIGHT' || burst.faction === 'SHADOW',
            rot: 0,
          },
          {
            x: burst.x,
            y: burst.y,
            r0: 6,
            rMax: 58,
            age: 0,
            life: 0.3,
            lineWidth: 3,
            color: pal.core,
            ellipseY: 1,
            hexSeal: false,
            rot: 0,
          },
        );
        this.stars.push({
          x: burst.x,
          y: burst.y,
          rMax: 52 + power * 5,
          points: burst.faction === 'ICE' ? 6 : 8,
          rot: 0.4,
          age: 0,
          life: 0.22,
          color: pal.spark,
        });
        if (burst.faction === 'ICE') {
          this.emitRadialSparks(burst.x, burst.y, 14, 110, 310, pal, true);
        } else if (burst.faction === 'SHADOW') {
          this.slashes.push(
            { x: burst.x, y: burst.y, angle: 0.65, length: 92, age: 0, life: 0.28, color: pal.glow },
            { x: burst.x, y: burst.y, angle: -0.65, length: 92, age: 0, life: 0.28, color: pal.spark },
          );
        } else {
          this.emitJaggedArcs(burst.x, burst.y, 3, 64, pal.spark);
        }
        this.emitRadialSparks(burst.x, burst.y, Math.min(34, 18 + power * 3), 120, 340, pal, false);
        break;
      }

      case 'SHATTER': {
        this.rings.push(
          {
            x: burst.x,
            y: burst.y,
            r0: 10,
            rMax: 80,
            age: 0,
            life: 0.4,
            lineWidth: 3.5,
            color: pal.glow,
            ellipseY: 0.85,
            hexSeal: false,
            rot: 0,
          },
          {
            x: burst.x,
            y: burst.y,
            r0: 6,
            rMax: 52,
            age: 0,
            life: 0.28,
            lineWidth: 2.4,
            color: '#fda4af',
            ellipseY: 0.85,
            hexSeal: false,
            rot: 0,
          },
        );
        this.stars.push({
          x: burst.x,
          y: burst.y,
          rMax: 42,
          points: 8,
          rot: 0.15,
          age: 0,
          life: 0.18,
          color: '#fff1f2',
        });
        this.emitRadialSparks(burst.x, burst.y, 20, 100, 290, pal, true);
        this.emitRadialSparks(burst.x, burst.y, 18, 90, 250, pal, false);
        break;
      }

      case 'SUMMON_MYTHIC': {
        this.rings.push(
          {
            x: burst.x,
            y: burst.y + 8,
            r0: 18,
            rMax: 112,
            age: 0,
            life: 0.7,
            lineWidth: 3.4,
            color: '#f0c96e',
            ellipseY: 0.6,
            hexSeal: true,
            rot: 0,
          },
          {
            x: burst.x,
            y: burst.y,
            r0: 12,
            rMax: 88,
            age: 0,
            life: 0.54,
            lineWidth: 4.2,
            color: pal.glow,
            ellipseY: 1,
            hexSeal: false,
            rot: 0,
          },
        );
        this.stars.push({
          x: burst.x,
          y: burst.y,
          rMax: 56,
          points: 8,
          rot: 0,
          age: 0,
          life: 0.24,
          color: '#f0c96e',
        });
        this.emitHorizontalPlumes(burst.x, burst.y + 18, 18, pal);
        this.emitRisingMotes(burst.x, burst.y, 28, pal);
        break;
      }

      case 'SUMMON_HEAVY': {
        this.rings.push({
          x: burst.x,
          y: burst.y + 4,
          r0: 12,
          rMax: 76,
          age: 0,
          life: 0.5,
          lineWidth: 3,
          color: pal.glow,
          ellipseY: 0.66,
          hexSeal: true,
          rot: 0,
        });
        this.emitHorizontalPlumes(burst.x, burst.y + 16, 12, pal);
        this.emitRisingMotes(burst.x, burst.y, 16, pal);
        break;
      }

      case 'SUMMON_STANDARD': {
        this.rings.push({
          x: burst.x,
          y: burst.y,
          r0: 10,
          rMax: 48,
          age: 0,
          life: 0.34,
          lineWidth: 2.2,
          color: pal.glow,
          ellipseY: 0.72,
          hexSeal: false,
          rot: 0,
        });
        this.emitHorizontalPlumes(burst.x, burst.y + 14, 6, pal);
        this.emitRisingMotes(burst.x, burst.y, 8, pal);
        break;
      }
    }

    this.ensureLoop();
  }

  private emitRadialSparks(
    x: number,
    y: number,
    count: number,
    minSpeed: number,
    maxSpeed: number,
    pal: { core: string; glow: string; spark: string },
    shard: boolean,
  ): void {
    for (let i = 0; i < count; i++) {
      if (this.particles.length >= MAX_PARTICLES) {
        this.particles.shift();
      }
      const theta = (Math.PI * 2 * i) / count + (Math.random() - 0.5) * 0.45;
      const speed = minSpeed + Math.random() * (maxSpeed - minSpeed);
      const color = i % 5 === 0 ? pal.core : i % 2 === 0 ? pal.spark : pal.glow;
      this.particles.push({
        x,
        y,
        px: x,
        py: y,
        vx: Math.cos(theta) * speed,
        vy: Math.sin(theta) * speed - (shard ? 65 : 15),
        gravity: shard ? 340 : 140,
        drag: shard ? 0.96 : 0.92,
        size: shard ? 4 + Math.random() * 4.5 : 2 + Math.random() * 2.4,
        alpha: 1,
        age: 0,
        life: (shard ? 0.55 : 0.38) + Math.random() * 0.25,
        color,
        shard,
        rot: Math.random() * Math.PI * 2,
        vRot: (Math.random() - 0.5) * 14,
      });
    }
  }

  private emitConeSparks(
    x: number,
    y: number,
    count: number,
    centerAngle: number,
    spreadRad: number,
    minSpeed: number,
    maxSpeed: number,
    pal: { core: string; glow: string; spark: string },
  ): void {
    for (let i = 0; i < count; i++) {
      if (this.particles.length >= MAX_PARTICLES) {
        this.particles.shift();
      }
      const theta = centerAngle + (Math.random() - 0.5) * spreadRad;
      const speed = minSpeed + Math.random() * (maxSpeed - minSpeed);
      this.particles.push({
        x,
        y,
        px: x,
        py: y,
        vx: Math.cos(theta) * speed,
        vy: Math.sin(theta) * speed,
        gravity: 160,
        drag: 0.92,
        size: 2.2 + Math.random() * 2,
        alpha: 1,
        age: 0,
        life: 0.34 + Math.random() * 0.22,
        color: i % 3 === 0 ? pal.core : i % 2 === 0 ? pal.spark : pal.glow,
        shard: false,
        rot: 0,
        vRot: 0,
      });
    }
  }

  private emitHorizontalPlumes(
    x: number,
    y: number,
    count: number,
    pal: { core: string; glow: string; spark: string },
  ): void {
    for (let i = 0; i < count; i++) {
      if (this.particles.length >= MAX_PARTICLES) {
        this.particles.shift();
      }
      const dir = i % 2 === 0 ? 1 : -1;
      const speed = 95 + Math.random() * 190;
      this.particles.push({
        x: x + dir * 10,
        y,
        px: x + dir * 10,
        py: y,
        vx: dir * speed,
        vy: -14 - Math.random() * 44,
        gravity: 95,
        drag: 0.9,
        size: 2.2 + Math.random() * 2.2,
        alpha: 0.9,
        age: 0,
        life: 0.28 + Math.random() * 0.16,
        color: i % 3 === 0 ? pal.core : pal.spark,
        shard: false,
        rot: 0,
        vRot: 0,
      });
    }
  }

  private emitJaggedArcs(x: number, y: number, count: number, reach: number, color: string): void {
    for (let i = 0; i < count; i++) {
      const baseAngle = (Math.PI * 2 * i) / count + (Math.random() - 0.5) * 0.7;
      const segments: { x: number; y: number }[] = [{ x: 0, y: 0 }];
      const steps = 4;
      for (let s = 1; s <= steps; s++) {
        const dist = (reach * s) / steps;
        const jitter = s < steps ? (Math.random() - 0.5) * 0.52 : 0;
        const theta = baseAngle + jitter;
        segments.push({ x: Math.cos(theta) * dist, y: Math.sin(theta) * dist });
      }
      this.arcs.push({
        x,
        y,
        segments,
        age: 0,
        life: 0.2,
        color,
      });
    }
  }

  private emitRisingMotes(
    x: number,
    y: number,
    count: number,
    pal: { core: string; glow: string; spark: string },
  ): void {
    for (let i = 0; i < count; i++) {
      if (this.particles.length >= MAX_PARTICLES) {
        this.particles.shift();
      }
      const offsetX = (Math.random() - 0.5) * 76;
      const offsetY = (Math.random() - 0.5) * 28;
      this.particles.push({
        x: x + offsetX,
        y: y + offsetY,
        px: x + offsetX,
        py: y + offsetY,
        vx: (Math.random() - 0.5) * 32,
        vy: -95 - Math.random() * 165,
        gravity: -35,
        drag: 0.96,
        size: 1.8 + Math.random() * 2.5,
        alpha: 1,
        age: 0,
        life: 0.48 + Math.random() * 0.34,
        color: i % 3 === 0 ? pal.core : i % 2 === 0 ? pal.spark : pal.glow,
        shard: false,
        rot: 0,
        vRot: 0,
      });
    }
  }

  private ensureLoop(): void {
    if (this.rafId !== null || !this.ctx || typeof window === 'undefined' || this.disposed || this.isPaused) {
      return;
    }
    this.lastTickMs = performance.now();
    const step = (now: number): void => {
      if (this.disposed || this.isPaused) {
        this.rafId = null;
        return;
      }
      const dt = Math.min((now - this.lastTickMs) / 1000, 0.05);
      this.lastTickMs = now;
      this.updateAndDraw(dt);
      if (this.hasActiveFx()) {
        this.rafId = window.requestAnimationFrame(step);
      } else {
        this.rafId = null;
        this.clearCanvas();
      }
    };
    this.rafId = window.requestAnimationFrame(step);
  }

  private stopLoop(): void {
    if (this.rafId !== null && typeof window !== 'undefined') {
      window.cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }
  }

  private clearCanvas(): void {
    const ctx = this.ctx;
    if (!ctx) return;
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, this.width * this.dpr, this.height * this.dpr);
  }

  private updateAndDraw(dt: number): void {
    const ctx = this.ctx;
    if (!ctx) return;

    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, this.width * this.dpr, this.height * this.dpr);
    ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
    ctx.globalCompositeOperation = 'lighter';

    for (let i = this.rings.length - 1; i >= 0; i--) {
      const r = this.rings[i];
      r.age += dt;
      if (r.age >= r.life) {
        this.rings.splice(i, 1);
        continue;
      }
      const t = r.age / r.life;
      const eased = 1 - Math.pow(1 - t, 3);
      const radius = r.r0 + (r.rMax - r.r0) * eased;
      const alpha = (1 - t) * (1 - t) * 0.9;
      r.rot += dt * 1.4;

      ctx.save();
      ctx.translate(r.x, r.y);
      ctx.scale(1, r.ellipseY);
      ctx.globalAlpha = alpha;
      ctx.strokeStyle = r.color;
      ctx.lineWidth = Math.max(0.8, r.lineWidth * (1 - t * 0.72));

      ctx.beginPath();
      ctx.arc(0, 0, radius, 0, Math.PI * 2);
      ctx.stroke();

      if (r.hexSeal) {
        ctx.rotate(r.rot);
        this.drawHexagram(ctx, radius * 0.88);
      }
      ctx.restore();
    }

    for (let i = this.stars.length - 1; i >= 0; i--) {
      const st = this.stars[i];
      st.age += dt;
      if (st.age >= st.life) {
        this.stars.splice(i, 1);
        continue;
      }
      const t = st.age / st.life;
      const scale = t < 0.28 ? 0.35 + (t / 0.28) * 0.65 : 1 + (t - 0.28) * 0.22;
      const alpha = 1 - t * t;
      ctx.save();
      ctx.translate(st.x, st.y);
      ctx.rotate(st.rot + t * 0.25);
      ctx.globalAlpha = alpha * 0.9;
      ctx.fillStyle = st.color;
      this.drawStarPolygon(ctx, st.rMax * scale, st.rMax * scale * 0.24, st.points);
      ctx.globalAlpha = alpha;
      ctx.fillStyle = '#ffffff';
      this.drawStarPolygon(ctx, st.rMax * scale * 0.56, st.rMax * scale * 0.14, st.points);
      ctx.restore();
    }

    for (let i = this.arcs.length - 1; i >= 0; i--) {
      const a = this.arcs[i];
      a.age += dt;
      if (a.age >= a.life) {
        this.arcs.splice(i, 1);
        continue;
      }
      const t = a.age / a.life;
      ctx.save();
      ctx.translate(a.x, a.y);
      ctx.globalAlpha = (1 - t) * 0.95;
      ctx.strokeStyle = a.color;
      ctx.lineWidth = Math.max(1, 3.2 * (1 - t));
      ctx.lineJoin = 'round';
      ctx.beginPath();
      for (let idx = 0; idx < a.segments.length; idx++) {
        const pt = a.segments[idx];
        if (idx === 0) ctx.moveTo(pt.x, pt.y);
        else ctx.lineTo(pt.x, pt.y);
      }
      ctx.stroke();

      ctx.strokeStyle = '#ffffff';
      ctx.lineWidth = Math.max(0.6, 1.4 * (1 - t));
      ctx.stroke();
      ctx.restore();
    }

    for (let i = this.slashes.length - 1; i >= 0; i--) {
      const s = this.slashes[i];
      s.age += dt;
      if (s.age >= s.life) {
        this.slashes.splice(i, 1);
        continue;
      }
      const t = s.age / s.life;
      const progress = Math.min(1, t * 2.5);
      const alpha = 1 - t;
      const half = s.length * 0.5 * progress;
      const bulge = 14 * (1 - t * 0.45);

      ctx.save();
      ctx.translate(s.x, s.y);
      ctx.rotate(s.angle);
      ctx.globalAlpha = alpha * 0.94;
      ctx.fillStyle = s.color;
      ctx.beginPath();
      ctx.moveTo(-half, 0);
      ctx.quadraticCurveTo(0, -bulge, half, 0);
      ctx.quadraticCurveTo(0, -bulge * 0.28, -half, 0);
      ctx.closePath();
      ctx.fill();

      ctx.fillStyle = '#ffffff';
      ctx.beginPath();
      ctx.moveTo(-half * 0.8, 0);
      ctx.quadraticCurveTo(0, -bulge * 0.72, half * 0.8, 0);
      ctx.quadraticCurveTo(0, -bulge * 0.36, -half * 0.8, 0);
      ctx.closePath();
      ctx.fill();
      ctx.restore();
    }

    for (let i = this.particles.length - 1; i >= 0; i--) {
      const p = this.particles[i];
      p.age += dt;
      if (p.age >= p.life) {
        this.particles.splice(i, 1);
        continue;
      }
      p.px = p.x;
      p.py = p.y;
      const dragPow = Math.pow(p.drag, dt * 60);
      p.vx *= dragPow;
      p.vy = p.vy * dragPow + p.gravity * dt;
      p.x += p.vx * dt;
      p.y += p.vy * dt;
      p.rot += p.vRot * dt;

      const t = p.age / p.life;
      const alpha = (1 - t) * p.alpha;
      ctx.save();
      ctx.globalAlpha = alpha;
      if (p.shard) {
        ctx.translate(p.x, p.y);
        ctx.rotate(p.rot);
        ctx.fillStyle = p.color;
        ctx.beginPath();
        ctx.moveTo(-p.size, -p.size * 0.6);
        ctx.lineTo(p.size, 0);
        ctx.lineTo(-p.size * 0.35, p.size * 0.9);
        ctx.closePath();
        ctx.fill();
      } else {
        ctx.strokeStyle = p.color;
        ctx.lineCap = 'round';
        ctx.lineWidth = Math.max(1, p.size * (1 - t * 0.5));
        ctx.beginPath();
        ctx.moveTo(p.px, p.py);
        ctx.lineTo(p.x, p.y);
        ctx.stroke();

        ctx.fillStyle = '#ffffff';
        ctx.beginPath();
        ctx.arc(p.x, p.y, Math.max(0.6, p.size * 0.42 * (1 - t)), 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.restore();
    }
  }

  private drawStarPolygon(
    ctx: CanvasRenderingContext2D,
    outerRadius: number,
    innerRadius: number,
    points: number,
  ): void {
    const total = points * 2;
    ctx.beginPath();
    for (let i = 0; i < total; i++) {
      const r = i % 2 === 0 ? outerRadius : innerRadius;
      const theta = (Math.PI * i) / points;
      const px = Math.cos(theta) * r;
      const py = Math.sin(theta) * r;
      if (i === 0) ctx.moveTo(px, py);
      else ctx.lineTo(px, py);
    }
    ctx.closePath();
    ctx.fill();
  }

  private drawHexagram(ctx: CanvasRenderingContext2D, r: number): void {
    for (let tri = 0; tri < 2; tri++) {
      ctx.beginPath();
      for (let v = 0; v < 3; v++) {
        const ang = (Math.PI * 2 * v) / 3 + tri * (Math.PI / 3);
        const px = Math.cos(ang) * r;
        const py = Math.sin(ang) * r;
        if (v === 0) ctx.moveTo(px, py);
        else ctx.lineTo(px, py);
      }
      ctx.closePath();
      ctx.stroke();
    }
  }
}
