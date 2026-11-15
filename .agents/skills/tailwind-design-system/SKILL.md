---
name: tailwind-design-system
description: Build scalable design systems with Tailwind CSS v4.3 CSS-first @theme tokens, semantic CSS custom properties, utility-first variants, container queries, and strict component CSS budgets. Use when structuring themes, shared UI primitives, or Tailwind utilities in frontend/.
---

# Tailwind CSS v4.3 Design System

This project uses **Tailwind CSS 4.3** (`@import "tailwindcss";` + CSS-first `@theme` tokens in `frontend/src/tailwind.css`) alongside global CSS custom properties in `frontend/src/styles.scss`.

## Rules for Tailwind CSS v4 (`momo tcg`)

1. **CSS-first `@theme`, never `tailwind.config.js`**: Declare brand, surface, ink, border, and faction colors inside `@theme { --color-*: ...; }` in `frontend/src/tailwind.css` so Tailwind auto-generates `bg-*`, `text-*`, `border-*`, `ring-*` utilities.
2. **Three-tier token flow**:
   - **Layer 1 (Primitives)** → Raw OKLCH / hex obsidian, gold, parchment, and elemental hues.
   - **Layer 2 (Semantic)** → Surface depth (`canvas`, `surface`, `elevated`), text (`ink`, `ink-secondary`, `ink-muted`), borders, states (`primary`, `success`, `danger`, `info`).
   - **Layer 3 (Tailwind `@theme` + `.arcane-*` utilities)** → Reused component shells without inflating per-component `.scss` files past `angular.json` budgets (`4 kB` warn / `8 kB` err).
3. **Modern Tailwind v4 utilities only**:
   - Use `bg-linear-to-*` (not deprecated v3 `bg-gradient-to-*` in new code).
   - Keep full static utility class strings in maps (`FACTION_META`), never template string concatenation like `` `bg-${color}-500` `` which Tailwind tree-shakes away.
   - Prefer `size-*`, `shrink-0`, `min-w-0`, `text-balance`, `motion-reduce:*`, and `@container` where applicable.
4. **Mobile-first progressive breakpoints (`320px+` → `sm:`/`md:`/`lg:`/`xl:`)**:
   - Write base utilities unprefixed for narrow portrait screens (`grid-cols-1`, compact `px-3`, touch-sized buttons, swipeable horizontal rails with `overflow-x-auto snap-x snap-mandatory`), then scale up with `min-width` variants (`sm:`, `md:`, `lg:`, `xl:`).
   - Use `order-first lg:order-none` (paired with `lg:order-last`) when primary builder/summary controls in a desktop right `<aside>` must appear before long card catalogs on mobile.
   - Always pair `dvh`/`dvw` with `min-w-0` and `whitespace-nowrap` on tabular numeric counters (`PV`, timers, MMR) so cards and HUD badges never split digits across lines on `320px–390px` screens.

## Resources

- `resources/implementation-playbook.md` — Three-tier Tailwind v4 `@theme` patterns and component variant conventions.
