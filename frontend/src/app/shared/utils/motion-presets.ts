import { gsap } from 'gsap';
import { animate } from 'motion/mini';

const VICTORY_COLORS = ['#d9a441', '#f0c96e', '#38bdf8', '#f4efe6'];

/**
 * Retourne vrai si l'utilisateur demande une réduction des mouvements
 * (accessibilité WCAG / prefers-reduced-motion).
 */
export function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return true;
  }
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

function supportsWaapi(el: Element): boolean {
  return typeof (el as HTMLElement).animate === 'function';
}

/**
 * Anime l'apparition séquencée (stagger) d'une collection de cartes sur le thread GPU.
 * Reste totalement silencieux sous JSDOM (tests) ou reduced-motion.
 */
export function animateCardGridEntrance(
  elements: Iterable<HTMLElement>,
  maxAnimatedCount = 16,
): () => void {
  if (prefersReducedMotion()) {
    return () => {};
  }

  const list = Array.from(elements).slice(0, maxAnimatedCount).filter(supportsWaapi);
  if (list.length === 0) {
    return () => {};
  }

  const controls = list.map((el, idx) =>
    animate(
      el,
      {
        opacity: [0, 1],
        transform: ['translateY(10px) scale(0.98)', 'translateY(0px) scale(1)'],
      },
      {
        duration: 0.24,
        delay: Math.min(idx * 0.025, 0.35),
        ease: [0.22, 1, 0.36, 1],
      },
    ),
  );

  return () => {
    for (const c of controls) {
      c.stop();
    }
  };
}

/**
 * Impulse cinétique courte sur une carte récemment posée ou un compteur PV modifié.
 * N'utilise que transform/opacity et respecte la limite WCAG 2.3.1 (aucun flash).
 */
export function pulseElementImpact(
  el: HTMLElement | undefined | null,
  kind: 'damage' | 'heal' | 'summon' | 'defense' | 'attack',
): void {
  if (!el || prefersReducedMotion()) {
    return;
  }

  gsap.killTweensOf(el);
  if (kind === 'damage') {
    gsap.fromTo(
      el,
      { x: -4, scale: 1.04 },
      { x: 0, scale: 1, duration: 0.28, ease: 'elastic.out(1, 0.45)', clearProps: 'transform' },
    );
    return;
  }

  if (kind === 'defense') {
    gsap.fromTo(
      el,
      { y: 3, scale: 0.96 },
      { y: 0, scale: 1, duration: 0.34, ease: 'elastic.out(1, 0.5)', clearProps: 'transform' },
    );
    return;
  }

  if (kind === 'attack') {
    gsap.fromTo(
      el,
      { y: -6, scale: 1.06 },
      { y: 0, scale: 1, duration: 0.26, ease: 'power3.out', clearProps: 'transform' },
    );
    return;
  }

  if (kind === 'heal') {
    gsap.fromTo(
      el,
      { y: -3, scale: 1.05 },
      { y: 0, scale: 1, duration: 0.28, ease: 'power2.out', clearProps: 'transform' },
    );
    return;
  }

  gsap.fromTo(
    el,
    { opacity: 0.85, y: 8, scale: 0.96 },
    { opacity: 1, y: 0, scale: 1, duration: 0.24, ease: 'power3.out', clearProps: 'transform,opacity' },
  );
}

/**
 * Chorégraphie GSAP d'assaut physique inspirée Cross Blitz (Monstre vs Monstre ou Attaque Directe) :
 * la carte se bande en arrière, fonce avec inclinaison 3D le long de (dx, dy), marque un bref hit-stop
 * d'impact à 185 ms tandis que la cible encaisse recul + squash-and-stretch, puis claque en place.
 */
export function animateCardAttackLunge(
  attackerEl: HTMLElement | null | undefined,
  defenderEl: HTMLElement | null | undefined,
  dx: number,
  dy: number,
  blocked: boolean,
  hitStrength: 'light' | 'heavy' | 'critical' = 'heavy',
): () => void {
  if (prefersReducedMotion()) {
    return () => {};
  }

  const timelines: gsap.core.Timeline[] = [];
  const dist = Math.hypot(dx, dy) || 1;
  const nx = dx / dist;
  const ny = dy / dist;
  const lungeDist = Math.min(dist * 0.62, 195);
  const lungeX = nx * lungeDist;
  const lungeY = ny * lungeDist;
  const tiltDeg = Math.max(-8.5, Math.min(8.5, nx * 9));
  const hitStopSec = hitStrength === 'critical' ? 0.062 : hitStrength === 'heavy' ? 0.044 : 0.015;
  const apexImpactSec = 0.185;

  if (attackerEl) {
    gsap.killTweensOf(attackerEl);
    const tl = gsap.timeline({
      onComplete: () => {
        gsap.set(attackerEl, { clearProps: 'transform,zIndex' });
      },
    });
    tl.set(attackerEl, { zIndex: 55 })
      .to(attackerEl, {
        x: -nx * 14,
        y: -ny * 12,
        scaleX: 1.07,
        scaleY: 0.92,
        rotation: -tiltDeg * 0.75,
        rotationX: -8,
        transformPerspective: 760,
        duration: 0.06,
        ease: 'power2.out',
      })
      .to(attackerEl, {
        x: lungeX,
        y: lungeY,
        scaleX: 1.18,
        scaleY: 1.16,
        rotation: tiltDeg * 1.25,
        rotationX: 11,
        duration: 0.125,
        ease: 'power4.in',
      })
      .to(attackerEl, {
        scaleX: 1.14,
        scaleY: 1.11,
        duration: hitStopSec,
        ease: 'none',
      })
      .to(attackerEl, {
        x: 0,
        y: 0,
        scaleX: 1,
        scaleY: 1,
        rotation: 0,
        rotationX: 0,
        duration: 0.25,
        ease: 'back.out(1.75)',
      });
    timelines.push(tl);
  }

  if (defenderEl && defenderEl !== attackerEl) {
    gsap.killTweensOf(defenderEl);
    const defTl = gsap.timeline({
      delay: apexImpactSec,
      onComplete: () => {
        gsap.set(defenderEl, { clearProps: 'transform' });
      },
    });
    if (blocked) {
      defTl
        .to(defenderEl, {
          x: nx * 4,
          y: ny * 4,
          scaleX: 1.15,
          scaleY: 0.87,
          duration: 0.075,
          ease: 'power2.out',
        })
        .to(defenderEl, {
          x: 0,
          y: 0,
          scaleX: 1,
          scaleY: 1,
          duration: 0.26,
          ease: 'elastic.out(1, 0.48)',
        });
    } else {
      defTl
        .to(defenderEl, {
          x: nx * 16,
          y: ny * 14,
          scaleX: 0.9,
          scaleY: 1.12,
          rotation: tiltDeg * 1.1,
          duration: 0.075,
          ease: 'power3.out',
        })
        .to(defenderEl, {
          x: nx * 15,
          y: ny * 13,
          duration: hitStopSec * 0.75,
          ease: 'none',
        })
        .to(defenderEl, {
          x: 0,
          y: 0,
          scaleX: 1,
          scaleY: 1,
          rotation: 0,
          duration: 0.28,
          ease: 'elastic.out(1, 0.44)',
        });
    }
    timelines.push(defTl);
  }

  return () => {
    for (const tl of timelines) {
      tl.kill();
    }
    if (attackerEl) gsap.set(attackerEl, { clearProps: 'transform,zIndex' });
    if (defenderEl) gsap.set(defenderEl, { clearProps: 'transform' });
  };
}

/**
 * Secousse directionnelle GPU (caméra d'arène façon Cross Blitz) lors des coups critiques,
 * chocs violents, frappes directes ou invocations mythiques.
 */
export function animateBoardShake(
  boardEl: HTMLElement | null | undefined,
  strength: 'light' | 'heavy' | 'critical',
  dirX = 0,
  dirY = 0,
): () => void {
  if (!boardEl || prefersReducedMotion()) {
    return () => {};
  }

  const amp = strength === 'critical' ? 8.5 : strength === 'heavy' ? 5 : 2.5;
  const rot = strength === 'critical' ? 0.45 : strength === 'heavy' ? 0.22 : 0.1;
  const dLen = Math.hypot(dirX, dirY);
  const kx = dLen > 1 ? (dirX / dLen) * amp : -amp;
  const ky = dLen > 1 ? (dirY / dLen) * amp * 0.85 : amp * 0.6;

  gsap.killTweensOf(boardEl);
  const tl = gsap.timeline({
    onComplete: () => {
      gsap.set(boardEl, { clearProps: 'transform' });
    },
  });
  tl.to(boardEl, { x: kx, y: ky, rotation: -rot, scale: 1.007, duration: 0.04, ease: 'power1.out' })
    .to(boardEl, { x: -kx * 0.78, y: -ky * 0.72, rotation: rot, scale: 1.004, duration: 0.045, ease: 'power1.inOut' })
    .to(boardEl, { x: kx * 0.46, y: -amp * 0.35, rotation: -rot * 0.45, duration: 0.045, ease: 'power1.inOut' })
    .to(boardEl, { x: -amp * 0.25, y: amp * 0.2, rotation: rot * 0.2, duration: 0.045, ease: 'power1.inOut' })
    .to(boardEl, { x: 0, y: 0, rotation: 0, scale: 1, duration: 0.085, ease: 'power2.out' });

  return () => {
    tl.kill();
    gsap.set(boardEl, { clearProps: 'transform' });
  };
}

/**
 * Chute lourde (Table Slam + Squash-and-Stretch Cross Blitz) d'une carte invoquée sur la table de duel.
 */
export function animateCardSummonSlam(
  el: HTMLElement | null | undefined,
  tier: 'STANDARD' | 'HEAVY' | 'MYTHIC',
): () => void {
  if (!el || prefersReducedMotion()) {
    return () => {};
  }

  gsap.killTweensOf(el);
  if (tier === 'MYTHIC') {
    const tl = gsap.timeline({
      onComplete: () => {
        gsap.set(el, { clearProps: 'transform,opacity,zIndex' });
      },
    });
    tl.set(el, { zIndex: 50 })
      .fromTo(
        el,
        { opacity: 0.7, y: -30, scaleX: 1.32, scaleY: 1.44, rotation: -3, rotationX: 16, transformPerspective: 800 },
        { opacity: 1, y: 4, scaleX: 1.16, scaleY: 0.84, rotation: 0.8, rotationX: 0, duration: 0.15, ease: 'power4.in' },
      )
      .to(el, {
        y: -2,
        scaleX: 0.97,
        scaleY: 1.05,
        rotation: -0.3,
        duration: 0.1,
        ease: 'power2.out',
      })
      .to(el, {
        y: 0,
        scaleX: 1,
        scaleY: 1,
        rotation: 0,
        duration: 0.2,
        ease: 'elastic.out(1.08, 0.52)',
      });
    return () => {
      tl.kill();
      gsap.set(el, { clearProps: 'transform,opacity,zIndex' });
    };
  }

  if (tier === 'HEAVY') {
    const tl = gsap.timeline({
      onComplete: () => {
        gsap.set(el, { clearProps: 'transform,opacity' });
      },
    });
    tl.fromTo(
      el,
      { opacity: 0.78, y: -18, scaleX: 1.2, scaleY: 1.26 },
      { opacity: 1, y: 3, scaleX: 1.1, scaleY: 0.88, duration: 0.12, ease: 'power3.in' },
    ).to(el, {
      y: 0,
      scaleX: 1,
      scaleY: 1,
      duration: 0.22,
      ease: 'back.out(1.75)',
    });
    return () => {
      tl.kill();
      gsap.set(el, { clearProps: 'transform,opacity' });
    };
  }

  const tl = gsap.timeline({
    onComplete: () => {
      gsap.set(el, { clearProps: 'transform,opacity' });
    },
  });
  tl.fromTo(
    el,
    { opacity: 0.85, y: -10, scaleX: 1.08, scaleY: 1.14 },
    { opacity: 1, y: 2, scaleX: 1.05, scaleY: 0.94, duration: 0.09, ease: 'power2.in' },
  ).to(el, {
    y: 0,
    scaleX: 1,
    scaleY: 1,
    duration: 0.16,
    ease: 'power3.out',
  });
  return () => {
    tl.kill();
    gsap.set(el, { clearProps: 'transform,opacity' });
  };
}

/**
 * Escamote la carte remplacée vers le deck lors d'un clic sur « Remplacer » en Mulligan.
 */
export function animateMulliganCardOut(el: HTMLElement | null | undefined): () => void {
  if (!el || prefersReducedMotion()) {
    return () => {};
  }
  gsap.killTweensOf(el);
  const tween = gsap.to(el, {
    opacity: 0.25,
    rotationY: 78,
    y: -14,
    scale: 0.88,
    transformPerspective: 820,
    duration: 0.14,
    ease: 'power2.in',
  });
  return () => {
    tween.kill();
    gsap.set(el, { clearProps: 'transform,opacity' });
  };
}

/**
 * Révèle la nouvelle carte piochée depuis le deck pendant le Mulligan avec un retournement fluide.
 */
export function animateMulliganCardIn(el: HTMLElement | null | undefined): () => void {
  if (!el || prefersReducedMotion()) {
    if (el) {
      gsap.set(el, { clearProps: 'transform,opacity' });
    }
    return () => {};
  }
  gsap.killTweensOf(el);
  const tween = gsap.fromTo(
    el,
    {
      opacity: 0.2,
      rotationY: -84,
      y: -16,
      scale: 0.84,
      transformPerspective: 820,
    },
    {
      opacity: 1,
      rotationY: 0,
      y: 0,
      scale: 1,
      transformPerspective: 820,
      duration: 0.38,
      ease: 'back.out(1.45)',
      clearProps: 'transform,opacity',
    },
  );
  return () => {
    tween.kill();
    gsap.set(el, { clearProps: 'transform,opacity' });
  };
}

/**
 * Salve de confettis dorés/azur lors d'une victoire en duel.
 * Chargement paresseux (0 o dans le bundle initial) + arrêt automatique si reduced-motion.
 */
export async function launchVictoryConfetti(): Promise<() => void> {
  if (typeof window === 'undefined' || prefersReducedMotion()) {
    return () => {};
  }

  const mod = await import('canvas-confetti');
  const confetti = mod.default;

  confetti({
    particleCount: 55,
    spread: 68,
    origin: { y: 0.62 },
    colors: VICTORY_COLORS,
    disableForReducedMotion: true,
  });

  const timer = window.setTimeout(() => {
    confetti({
      particleCount: 35,
      angle: 60,
      spread: 50,
      origin: { x: 0.15, y: 0.65 },
      colors: VICTORY_COLORS,
      disableForReducedMotion: true,
    });
    confetti({
      particleCount: 35,
      angle: 120,
      spread: 50,
      origin: { x: 0.85, y: 0.65 },
      colors: VICTORY_COLORS,
      disableForReducedMotion: true,
    });
  }, 180);

  return () => {
    window.clearTimeout(timer);
    if (typeof confetti.reset === 'function') {
      confetti.reset();
    }
  };
}
