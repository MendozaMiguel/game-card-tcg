# Tailwind CSS v4.3 Design System Playbook (`@theme` CSS-First)

## 1. CSS-First Token Architecture (`tailwind.css` + `styles.scss`)

In Tailwind CSS v4, tokens live directly in CSS via `@theme`, replacing legacy `tailwind.config.ts` JS objects:

```css
@import "tailwindcss";

@theme {
  /* Typography families */
  --font-display: "Cinzel", Georgia, "Times New Roman", serif;
  --font-sans: "Plus Jakarta Sans", ui-sans-serif, system-ui, sans-serif;

  /* Surface depth hierarchy */
  --color-arcane-void: #070a12;
  --color-arcane-obsidian: #0d1322;
  --color-arcane-slate: #131c31;
  --color-arcane-elevated: #1a2540;

  /* Primary metallic accents */
  --color-arcane-gold: #e5b84b;
  --color-arcane-gold-soft: #f3d27a;
  --color-arcane-bronze: #a87c32;
}
```

Any variable declared inside `@theme` automatically generates utilities such as:
- `bg-arcane-obsidian`, `text-arcane-gold`, `border-arcane-gold/40`, `ring-arcane-gold`, `font-display`.

## 2. Dynamic Class Token Mapping (Purge-Safe)

Never interpolate dynamic Tailwind class names (`border-${color}-500`). Map variants in TypeScript records (`faction-theme.ts`):

```ts
export const VARIANT_SURFACE: Record<'default' | 'primary' | 'danger', string> = {
  default: 'border-arcane-border bg-arcane-surface text-arcane-ink',
  primary: 'border-arcane-gold/60 bg-arcane-gold text-arcane-void',
  danger: 'border-rose-400/50 bg-rose-950/60 text-rose-100',
};
```

## 3. Keeping Angular Component `.scss` Files Tiny (`< 4 kB`)

- Compose layout and spacing with Tailwind utilities directly in component templates.
- Put shared surface classes (`.arcane-panel`, `.arcane-badge`, `.arcane-btn`, `.arcane-input`, `dialog.arcane-dialog`) in `frontend/src/styles.scss`.
- Reserve component `.scss` only for scoped layout rules (e.g., Arena `100dvh` cockpit grid, foil sheen keyframes, rotated defense transforms).

## 4. Tailwind v4 Quick Checklist

- [ ] `@theme` defines typography, surface depth, text hierarchy, semantic & faction colors.
- [ ] New gradients use `bg-linear-to-br` / `bg-linear-to-b` (Tailwind v4 syntax).
- [ ] All flex children containing truncated labels include `min-w-0` + `truncate`.
- [ ] Reduced-motion classes (`motion-reduce:transition-none`, `motion-reduce:animate-none`) applied on transitions/pulses.
- [ ] Contrast ratios verified (`>= 4.5:1` body text, `>= 3:1` borders/large text).
