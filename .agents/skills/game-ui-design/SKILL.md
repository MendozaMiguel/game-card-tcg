---
name: game-ui-design
description: Game HUD and competitive interface design covering instant tactical readability, diegetic vs high-clarity competitive HUDs, safe zones, feedback states, and screen-budget discipline. Use when designing or refining Arena, Lobby, or Match HUD interfaces.
---

# Game UI Design

Great game UI is the invisible bridge between player intent and server response: readable at a glance under turn timers, respectful of screen space, and clear without clutter.

## Core Principles for `momo tcg` Arena & Sanctuary

1. **Instant combat readability**: Life points, active turn owner, phase rope timer, available energy, and ready/exhausted card states must read in under half a second without scanning dense paragraphs.
2. **Spatial & screen discipline**: Reserve central screen real estate (`>= 65%`) for the battlefield & hand; keep sidebars (logs, card inspector, chat) secondary via calm tabs and clean contrast separation.
3. **Every indicator earns its space**: Replace redundant chrome, boxed cards inside cards, and visual noise with clean luminance steps, faction color strips, and crisp feedback rings.
4. **Feedback without fatigue**: Highlight legal targets (`Play`, `Attack`, `Reactive`) unmistakably, use subtle compositor motion (`150–250ms`), and never obscure click/touch targets.
5. **Resilient across viewports & touch-first**: Preserve keyboard & screen-reader parity alongside mobile touch ergonomics (`>= 44×44 CSS px` under `pointer: coarse`), crisp tabular HP/timer badges (`whitespace-nowrap`), readable mobile slot grids (`<= 3` columns before `sm:`), horizontal snap-scroll hand rails on narrow viewports (`< xl`), and tap-to-inspect card details so critical rules never depend solely on `:hover`.

## Reference Library (`omer-metin/skills-for-antigravity`, Apache-2.0)

- **`references/patterns.md`** — Competitive HUD hierarchy, diegetic integration vs tactical clarity, feedback states, and safe margins.
- **`references/sharp_edges.md`** — Cluttered HUD overload, focus deadends, off-screen badges, and sluggish UI transitions.
- **`references/validations.md`** — Readability, target spacing, and feedback verification checks.
