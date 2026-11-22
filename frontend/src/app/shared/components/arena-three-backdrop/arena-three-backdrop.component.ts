import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import type * as ThreeNs from 'three';
import { Faction } from '../../../core/models/arcane.models';

export interface ThreeCombatPulse {
  id: string;
  faction: Faction | null;
  kind: 'CLASH' | 'DIRECT' | 'DESTROY' | 'SUMMON_MYTHIC';
  nx?: number;
  ny?: number;
}

interface FactionPalette {
  readonly lightColor: number;
  readonly ringColor: number;
  readonly particleColor: number;
  readonly upwardDrift: number;
}

const FACTION_PALETTES: Readonly<Record<Faction | 'NEUTRAL', FactionPalette>> = {
  FIRE: { lightColor: 0xf97316, ringColor: 0xfb923c, particleColor: 0xfdba74, upwardDrift: 1.35 },
  ICE: { lightColor: 0x38bdf8, ringColor: 0x7dd3fc, particleColor: 0xbae6fd, upwardDrift: 0.7 },
  SHADOW: { lightColor: 0xa855f7, ringColor: 0xc084fc, particleColor: 0xd8b4fe, upwardDrift: 0.9 },
  LIGHT: { lightColor: 0xeab308, ringColor: 0xfde047, particleColor: 0xfef08a, upwardDrift: 0.85 },
  NEUTRAL: { lightColor: 0x38bdf8, ringColor: 0xc8a44d, particleColor: 0x93c5fd, upwardDrift: 0.75 },
};

/**
 * Arrière-plan 3D WebGL (Three.js chargé dynamiquement) derrière le plateau d'arène :
 * massifs d'obsidienne basse-polygonale, anneaux runiques centraux, particules élémentaires
 * adaptées à la faction active et onde de choc lors des lancements/impacts de sorts.
 */
@Component({
  selector: 'app-arena-three-backdrop',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [
    `
      :host {
        position: absolute;
        inset: 0;
        z-index: 0;
        display: block;
        pointer-events: none;
        overflow: hidden;
        border-radius: inherit;
        contain: strict;
        content-visibility: auto;
      }

      .arena-three-canvas {
        display: block;
        inline-size: 100%;
        block-size: 100%;
        opacity: 0;
        pointer-events: none;
      }

      .arena-three-canvas[data-ready='true'] {
        opacity: 0.88;
      }

      @media (prefers-reduced-motion: no-preference) {
        .arena-three-canvas {
          transition: opacity 420ms ease-out;
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
    <canvas
      #threeCanvas
      class="arena-three-canvas"
      [attr.data-ready]="webglReady()"
      aria-hidden="true"></canvas>
  `,
})
export class ArenaThreeBackdropComponent {
  readonly faction = input<Faction | null>(null);
  readonly pulseTrigger = input<string | number | null>(null);
  readonly combatPulse = input<ThreeCombatPulse | null>(null);

  private readonly hostRef = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly canvasRef = viewChild<ElementRef<HTMLCanvasElement>>('threeCanvas');
  protected readonly webglReady = signal(false);

  private threeModule: typeof ThreeNs | null = null;
  private renderer: ThreeNs.WebGLRenderer | null = null;
  private scene: ThreeNs.Scene | null = null;
  private camera: ThreeNs.PerspectiveCamera | null = null;
  private accentLight: ThreeNs.PointLight | null = null;
  private innerRingMat: ThreeNs.MeshBasicMaterial | null = null;
  private shockRing: ThreeNs.Mesh<ThreeNs.RingGeometry, ThreeNs.MeshBasicMaterial> | null = null;
  private combatRing: ThreeNs.Mesh<ThreeNs.RingGeometry, ThreeNs.MeshBasicMaterial> | null = null;
  private particleMat: ThreeNs.PointsMaterial | null = null;
  private particleGeo: ThreeNs.BufferGeometry | null = null;
  private outerRing: ThreeNs.Mesh | null = null;
  private innerRing: ThreeNs.Mesh | null = null;
  private particleSpeedFactor = 0.75;
  private particleBoost = 0;
  private cameraZKick = 0;
  private shockwaveProgress = 1;
  private combatRingProgress = 1;
  private combatRingMaxScale = 4.6;
  private disposed = false;
  private cleanupScene: (() => void) | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.disposed = true;
      this.cleanupScene?.();
      this.cleanupScene = null;
    });

    afterNextRender(() => {
      void this.initScene();
    });

    effect(() => {
      this.applyFactionPalette(this.faction());
    });

    effect(() => {
      const trigger = this.pulseTrigger();
      if (trigger && this.webglReady()) {
        this.shockwaveProgress = 0;
        if (this.accentLight) {
          this.accentLight.intensity = 10.5;
        }
        if (this.prefersReducedMotion()) {
          this.renderSingleFrame();
        }
      }
    });

    effect(() => {
      const pulse = this.combatPulse();
      if (!pulse || !this.webglReady()) {
        return;
      }
      const pal = FACTION_PALETTES[pulse.faction ?? this.faction() ?? 'NEUTRAL'];
      if (this.combatRing) {
        const nx = Math.max(-1, Math.min(1, pulse.nx ?? 0));
        const ny = Math.max(-1, Math.min(1, pulse.ny ?? 0));
        this.combatRing.position.set(nx * 5.2, ny * 2.5 + 0.2, -5.2);
        this.combatRing.material.color.setHex(pulse.kind === 'DIRECT' ? 0xfb7185 : pal.ringColor);
        this.combatRingProgress = 0;
        this.combatRingMaxScale = pulse.kind === 'DIRECT' || pulse.kind === 'SUMMON_MYTHIC' ? 5.8 : 4.2;
      }
      if (this.accentLight) {
        this.accentLight.color.setHex(pulse.kind === 'DIRECT' ? 0xf43f5e : pal.lightColor);
        this.accentLight.intensity = pulse.kind === 'DIRECT' || pulse.kind === 'SUMMON_MYTHIC' ? 14 : 11;
      }
      this.particleBoost = pulse.kind === 'DIRECT' || pulse.kind === 'SUMMON_MYTHIC' ? 2.6 : 1.6;
      this.cameraZKick = pulse.kind === 'DIRECT' || pulse.kind === 'SUMMON_MYTHIC' ? 0.55 : 0.3;
      if (this.prefersReducedMotion()) {
        this.renderSingleFrame();
      }
    });
  }

  private async initScene(): Promise<void> {
    if (typeof window === 'undefined' || typeof WebGLRenderingContext === 'undefined') {
      return;
    }
    const canvas = this.canvasRef()?.nativeElement;
    const host = this.hostRef.nativeElement;
    if (!canvas || !host) {
      return;
    }

    let THREE: typeof ThreeNs;
    try {
      THREE = await import('three');
    } catch {
      return;
    }
    if (this.disposed) {
      return;
    }
    this.threeModule = THREE;

    let renderer: ThreeNs.WebGLRenderer;
    try {
      renderer = new THREE.WebGLRenderer({
        canvas,
        alpha: true,
        antialias: true,
        powerPreference: 'low-power',
      });
    } catch {
      return;
    }

    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5));
    renderer.setClearColor(0x000000, 0);

    const scene = new THREE.Scene();
    scene.fog = new THREE.FogExp2(0x070b16, 0.042);

    const width = Math.max(host.clientWidth, 320);
    const height = Math.max(host.clientHeight, 240);
    renderer.setSize(width, height, false);

    const camera = new THREE.PerspectiveCamera(42, width / height, 0.1, 80);
    camera.position.set(0, 1.35, 13.5);
    camera.lookAt(0, 0.25, -4);

    const ambient = new THREE.AmbientLight(0x475569, 1.35);
    const moonLight = new THREE.DirectionalLight(0xbae6fd, 1.65);
    moonLight.position.set(-4.5, 9, 5);
    const rimLight = new THREE.DirectionalLight(0xc8a44d, 0.85);
    rimLight.position.set(7, 4, -5);
    const accentLight = new THREE.PointLight(0x38bdf8, 4.8, 28, 1.6);
    accentLight.position.set(0, 0.8, -5.2);
    scene.add(ambient, moonLight, rimLight, accentLight);

    const mountainGroup = this.buildObsidianPeaks(THREE);
    scene.add(mountainGroup);

    const ringGeoOuter = new THREE.RingGeometry(2.65, 2.74, 48);
    const ringMatOuter = new THREE.MeshBasicMaterial({
      color: 0x94a3b8,
      transparent: true,
      opacity: 0.22,
      side: THREE.DoubleSide,
    });
    const outerRing = new THREE.Mesh(ringGeoOuter, ringMatOuter);
    outerRing.position.set(0, 0.3, -9.2);

    const ringGeoInner = new THREE.RingGeometry(1.72, 1.79, 6);
    const innerRingMat = new THREE.MeshBasicMaterial({
      color: 0xc8a44d,
      transparent: true,
      opacity: 0.28,
      side: THREE.DoubleSide,
    });
    const innerRing = new THREE.Mesh(ringGeoInner, innerRingMat);
    innerRing.position.set(0, 0.3, -9.0);

    const shockGeo = new THREE.RingGeometry(0.92, 1.06, 40);
    const shockMat = new THREE.MeshBasicMaterial({
      color: 0x38bdf8,
      transparent: true,
      opacity: 0,
      side: THREE.DoubleSide,
      blending: THREE.AdditiveBlending,
    });
    const shockRing = new THREE.Mesh(shockGeo, shockMat);
    shockRing.position.set(0, 0.25, -8.5);

    const combatGeo = new THREE.RingGeometry(0.65, 0.9, 32);
    const combatMat = new THREE.MeshBasicMaterial({
      color: 0xfb923c,
      transparent: true,
      opacity: 0,
      side: THREE.DoubleSide,
      blending: THREE.AdditiveBlending,
    });
    const combatRing = new THREE.Mesh(combatGeo, combatMat);
    combatRing.position.set(0, 0.2, -5.2);
    scene.add(outerRing, innerRing, shockRing, combatRing);

    const particleSprite = this.createRadialGlowTexture(THREE);
    const particleCount = 110;
    const positions = new Float32Array(particleCount * 3);
    const speeds = new Float32Array(particleCount);
    for (let i = 0; i < particleCount; i++) {
      positions[i * 3] = (Math.random() - 0.5) * 20;
      positions[i * 3 + 1] = (Math.random() - 0.5) * 10;
      positions[i * 3 + 2] = -2 - Math.random() * 11;
      speeds[i] = 0.28 + Math.random() * 0.55;
    }

    const particleGeo = new THREE.BufferGeometry();
    particleGeo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    const particleMat = new THREE.PointsMaterial({
      color: 0x93c5fd,
      size: 0.28,
      map: particleSprite,
      transparent: true,
      opacity: 0.62,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
    });
    const particles = new THREE.Points(particleGeo, particleMat);
    scene.add(particles);

    this.renderer = renderer;
    this.scene = scene;
    this.camera = camera;
    this.accentLight = accentLight;
    this.outerRing = outerRing;
    this.innerRing = innerRing;
    this.innerRingMat = innerRingMat;
    this.shockRing = shockRing;
    this.combatRing = combatRing;
    this.particleGeo = particleGeo;
    this.particleMat = particleMat;

    this.applyFactionPalette(this.faction());
    this.webglReady.set(true);

    let pointerTargetX = 0;
    let pointerTargetY = 0;
    let rafId: number | null = null;
    let lastTime = performance.now();
    let isInViewport = true;
    let isAutoSkipped = false;
    const reducedQuery = window.matchMedia?.('(prefers-reduced-motion: reduce)') ?? null;
    const finePointer = window.matchMedia?.('(pointer: fine)').matches ?? false;

    const shouldAnimate = (): boolean =>
      !this.disposed && !(reducedQuery?.matches ?? false) && !document.hidden && isInViewport && !isAutoSkipped;

    const stopLoop = (): void => {
      if (rafId !== null) {
        cancelAnimationFrame(rafId);
        rafId = null;
      }
    };

    const tick = (now: number): void => {
      if (!shouldAnimate()) {
        rafId = null;
        return;
      }
      const dt = Math.min((now - lastTime) / 1000, 0.05);
      lastTime = now;

      camera.position.x += (pointerTargetX * 0.42 - camera.position.x) * Math.min(dt * 4, 1);
      camera.position.y += (1.35 + pointerTargetY * 0.22 - camera.position.y) * Math.min(dt * 4, 1);
      this.cameraZKick = Math.max(0, this.cameraZKick - dt * 1.8);
      camera.position.z += ((13.5 - this.cameraZKick) - camera.position.z) * Math.min(dt * 12, 1);
      camera.lookAt(0, 0.25, -4);

      outerRing.rotation.z += dt * 0.08;
      innerRing.rotation.z -= dt * 0.14;

      this.particleBoost = Math.max(0, this.particleBoost - dt * 3.8);
      const effectiveSpeed = this.particleSpeedFactor + this.particleBoost;
      const attr = particleGeo.getAttribute('position') as ThreeNs.BufferAttribute;
      const arr = attr.array as Float32Array;
      for (let i = 0; i < particleCount; i++) {
        const yIdx = i * 3 + 1;
        arr[yIdx] += speeds[i] * effectiveSpeed * dt;
        arr[i * 3] += Math.sin(now * 0.0008 + i) * 0.0025;
        if (arr[yIdx] > 5.6) {
          arr[yIdx] = -5.2;
          arr[i * 3] = (Math.random() - 0.5) * 20;
        }
      }
      attr.needsUpdate = true;

      if (this.shockwaveProgress < 1) {
        this.shockwaveProgress = Math.min(1, this.shockwaveProgress + dt * 1.75);
        const eased = 1 - Math.pow(1 - this.shockwaveProgress, 3);
        const scale = 1 + eased * 4.4;
        shockRing.scale.set(scale, scale, 1);
        shockRing.material.opacity = (1 - this.shockwaveProgress) * 0.52;
        accentLight.intensity = 4.8 + (1 - this.shockwaveProgress) * 5.2;
      } else {
        shockRing.material.opacity = 0;
        accentLight.intensity += (4.8 - accentLight.intensity) * Math.min(dt * 5, 1);
      }

      if (this.combatRingProgress < 1) {
        this.combatRingProgress = Math.min(1, this.combatRingProgress + dt * 2.1);
        const eased = 1 - Math.pow(1 - this.combatRingProgress, 3);
        const scale = 0.65 + eased * this.combatRingMaxScale;
        combatRing.scale.set(scale, scale, 1);
        combatRing.material.opacity = (1 - this.combatRingProgress) * 0.68;
      } else {
        combatRing.material.opacity = 0;
      }

      renderer.render(scene, camera);
      rafId = requestAnimationFrame(tick);
    };

    const syncLoop = (): void => {
      if (shouldAnimate()) {
        if (rafId === null) {
          lastTime = performance.now();
          rafId = requestAnimationFrame(tick);
        }
      } else {
        stopLoop();
        if (!this.disposed && (reducedQuery?.matches ?? false) && !document.hidden) {
          this.renderSingleFrame();
        }
      }
    };

    const onPointerMove = (ev: PointerEvent): void => {
      pointerTargetX = (ev.clientX / Math.max(window.innerWidth, 1) - 0.5) * 2;
      pointerTargetY = (0.5 - ev.clientY / Math.max(window.innerHeight, 1)) * 2;
    };

    const onVisibilityChange = (): void => syncLoop();
    const onReducedMotionChange = (): void => syncLoop();
    const onAutoStateChange = (ev: Event): void => {
      isAutoSkipped = Boolean((ev as Event & { skipped?: boolean }).skipped);
      syncLoop();
    };

    const resizeObserver =
      typeof ResizeObserver !== 'undefined'
        ? new ResizeObserver((entries) => {
            const rect = entries[0]?.contentRect;
            if (!rect || rect.width < 10 || rect.height < 10) return;
            camera.aspect = rect.width / rect.height;
            camera.updateProjectionMatrix();
            renderer.setSize(rect.width, rect.height, false);
            if (!shouldAnimate()) {
              this.renderSingleFrame();
            }
          })
        : null;
    resizeObserver?.observe(host);

    const intersectionObserver =
      typeof IntersectionObserver !== 'undefined'
        ? new IntersectionObserver((entries) => {
            isInViewport = entries[0]?.isIntersecting ?? true;
            syncLoop();
          })
        : null;
    intersectionObserver?.observe(host);

    if (finePointer) {
      window.addEventListener('pointermove', onPointerMove, { passive: true });
    }
    document.addEventListener('visibilitychange', onVisibilityChange);
    reducedQuery?.addEventListener?.('change', onReducedMotionChange);
    host.addEventListener('contentvisibilityautostatechange', onAutoStateChange);

    this.renderSingleFrame();
    syncLoop();

    this.cleanupScene = (): void => {
      stopLoop();
      resizeObserver?.disconnect();
      intersectionObserver?.disconnect();
      if (finePointer) {
        window.removeEventListener('pointermove', onPointerMove);
      }
      document.removeEventListener('visibilitychange', onVisibilityChange);
      reducedQuery?.removeEventListener?.('change', onReducedMotionChange);
      host.removeEventListener('contentvisibilityautostatechange', onAutoStateChange);

      particleSprite.dispose();
      const geometries = new Set<ThreeNs.BufferGeometry>();
      const materials = new Set<ThreeNs.Material>();
      scene.traverse((obj) => {
        const mesh = obj as ThreeNs.Mesh;
        if (mesh.geometry) geometries.add(mesh.geometry);
        const mat = mesh.material;
        if (Array.isArray(mat)) {
          mat.forEach((m) => materials.add(m));
        } else if (mat) {
          materials.add(mat);
        }
      });
      geometries.forEach((g) => g.dispose());
      materials.forEach((m) => m.dispose());
      renderer.dispose();
      this.renderer = null;
      this.scene = null;
      this.camera = null;
    };
  }

  private buildObsidianPeaks(THREE: typeof ThreeNs): ThreeNs.Group {
    const group = new THREE.Group();
    const rockMat = new THREE.MeshStandardMaterial({
      color: 0x131d33,
      roughness: 0.86,
      metalness: 0.24,
      flatShading: true,
    });
    const edgeMat = new THREE.LineBasicMaterial({
      color: 0xc8a44d,
      transparent: true,
      opacity: 0.16,
    });

    const peaks: ReadonlyArray<{ x: number; y: number; z: number; r: number; h: number; seg: number }> = [
      { x: -7.4, y: -1.1, z: -10.5, r: 3.1, h: 5.4, seg: 6 },
      { x: -4.2, y: -1.5, z: -12.0, r: 3.4, h: 6.4, seg: 5 },
      { x: 4.4, y: -1.4, z: -11.6, r: 3.3, h: 6.0, seg: 5 },
      { x: 7.5, y: -1.0, z: -10.2, r: 3.0, h: 5.2, seg: 6 },
      { x: -7.8, y: -4.2, z: -6.8, r: 2.5, h: 3.4, seg: 5 },
      { x: 7.8, y: -4.2, z: -6.8, r: 2.5, h: 3.5, seg: 5 },
    ];

    for (const p of peaks) {
      const geo = new THREE.ConeGeometry(p.r, p.h, p.seg);
      const mesh = new THREE.Mesh(geo, rockMat);
      mesh.position.set(p.x, p.y, p.z);
      mesh.rotation.y = p.x * 0.18;
      group.add(mesh);

      const edges = new THREE.EdgesGeometry(geo, 18);
      const wire = new THREE.LineSegments(edges, edgeMat);
      wire.position.copy(mesh.position);
      wire.rotation.copy(mesh.rotation);
      group.add(wire);
    }
    return group;
  }

  private createRadialGlowTexture(THREE: typeof ThreeNs): ThreeNs.DataTexture {
    const size = 32;
    const data = new Uint8Array(size * size * 4);
    const half = (size - 1) / 2;
    for (let y = 0; y < size; y++) {
      for (let x = 0; x < size; x++) {
        const dx = (x - half) / half;
        const dy = (y - half) / half;
        const dist = Math.min(1, Math.hypot(dx, dy));
        const alpha = Math.round(Math.pow(1 - dist, 2.2) * 255);
        const idx = (y * size + x) * 4;
        data[idx] = 255;
        data[idx + 1] = 255;
        data[idx + 2] = 255;
        data[idx + 3] = alpha;
      }
    }
    const texture = new THREE.DataTexture(data, size, size, THREE.RGBAFormat);
    texture.needsUpdate = true;
    return texture;
  }

  private applyFactionPalette(faction: Faction | null): void {
    const palette = FACTION_PALETTES[faction ?? 'NEUTRAL'];
    this.particleSpeedFactor = palette.upwardDrift;
    if (this.accentLight) {
      this.accentLight.color.setHex(palette.lightColor);
    }
    if (this.innerRingMat) {
      this.innerRingMat.color.setHex(palette.ringColor);
    }
    if (this.shockRing) {
      this.shockRing.material.color.setHex(palette.ringColor);
    }
    if (this.particleMat) {
      this.particleMat.color.setHex(palette.particleColor);
    }
    if (this.prefersReducedMotion()) {
      this.renderSingleFrame();
    }
  }

  private prefersReducedMotion(): boolean {
    return (
      typeof window !== 'undefined' &&
      (window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches ?? false)
    );
  }

  private renderSingleFrame(): void {
    if (this.renderer && this.scene && this.camera && !this.disposed && this.threeModule) {
      this.renderer.render(this.scene, this.camera);
    }
  }
}
