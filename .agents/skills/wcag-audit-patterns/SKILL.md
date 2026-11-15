---
name: wcag-audit-patterns
description: Conduct WCAG 2.2 AA accessibility audits and build inclusive web UIs covering contrast pairing, keyboard focus management, native semantic HTML, ARIA state discipline, screen readers, Forced Colors Mode, and reduced motion. Use when auditing UI views or building accessible controls.
---

# WCAG 2.2 AA Audit & Inclusive Design Patterns

Accessibility is a non-negotiable engineering baseline across **L'Ordre des Arcanes Online**, especially across interactive TCG cards, Arena phase controls, dialogs, and auth/deckbuilder forms.

## Mandatory Baseline Checks

1. **Perceivable contrast & non-color redundancy**:
   - Normal text `>= 4.5:1`, large headings (`>= 24px` or `>= 18.66px bold`) & UI borders/icons `>= 3:1`.
   - Never communicate status (Win/Loss, Attack/Defense, Ready/Exhausted, Error) by color alone—pair text, icon, or badge geometry.
2. **Operable keyboard & visible focus**:
   - Every interactive action (`button`, `a`, `<dialog>`, card slots) reachable via Tab/Enter/Space.
   - Maintain a high-contrast `:focus-visible` ring (`3px` `--arcane-accent-gold`) plus `@media (forced-colors: active)` fallback.
   - Comfortable target sizes (`>= 44×44 CSS px` under `@media (pointer: coarse)` via `--arcane-target-size` / `.arcane-btn` and `.arena-action-mini`).
3. **Semantic HTML first**: Use native `<button>`, `<a href>`, `<dialog>`, `<main id="main-content" tabindex="-1">`, and `<label for>` before reaching for `role` overrides.
4. **Reduced motion & text reflow**: Wrap non-essential CSS/GSAP motion in `prefers-reduced-motion`, use `rem` typography, and avoid clipping at 200% zoom.

## Reference Guides

- **`references/patterns.md`** (`omer-metin/accessibility-design`, Apache-2.0) — Semantic HTML, focus rings, screen reader patterns, and color+icon rules.
- **`references/sharp_edges.md`** — Focus traps, hidden div-click anti-patterns, focus loss on modal close, and low-contrast states.
- **`references/validations.md`** — Complete WCAG 2.2 verification criteria.
- **`resources/implementation-playbook.md`** — Audit workflow and remediation checklist.
