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
