---
name: card-game-design
description: Collectible card game (TCG/CCG) UX and templating design covering card frame anatomy, keyword clarity, faction identity (color pie), resource/energy cost readability, and deckbuilder legibility. Use when designing ArcaneCard frames, deckbuilder filters, or card mechanics presentation.
---

# Card Game Design

A digital TCG card is an interactive promise: cost, faction identity, posture, combat stats, keywords, and rules text must be legible even in compact hand/board slots.

## Core Principles for `momo tcg`

1. **Consistent visual scan order**:
   - **Top-left**: Energy cost orb (high contrast gold/amber or obsidian-bronze badge)
   - **Title & rarity/ramp tags**: Epic / Ramp clear icons (SVG), without truncating away card recognition
   - **Faction identity**: Immediate border + subtle surface tint (`FIRE`, `ICE`, `SHADOW`, `LIGHT`) that stays harmonious on dark surfaces
   - **Bottom-right / footer**: `ATQ` / `DÉF` badges in tabular numerals (`tabular-nums`), distinct color/icon shapes (not hue alone)
   - **Posture clarity**: `DEFENSE` posture visibly distinct (tilted shield badge + posture indicator) at a glance
2. **Compact vs Full fidelity**: In compact board/hand mode, prioritize Cost, Title, Faction, Keywords, and `ATQ/DÉF`; reserve full rules text for the Card Inspector / Catalog view unless explicitly requested.
3. **Rule clarity in Deckbuilder**: Surface the 4 Anti-Meta laws visually (`30` cards, `10/10/10` ratios, `14/12/4` cost curve, `<= 2` factions with `>= 15` primary) as scannable progress gauges rather than error walls.

## Reference Library (`omer-metin/skills-for-antigravity`, Apache-2.0)

- **`references/patterns.md`** — TCG resource systems, vanilla-stat curves, templating clarity, and archetype presentation.
- **`references/sharp_edges.md`** — Unreadable keyword soup, muddled color-pie identity, and resource readability traps.
- **`references/validations.md`** — Card templating and deck curve validation criteria.
