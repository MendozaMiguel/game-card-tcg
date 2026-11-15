---
name: angular
description: Modern Angular 22 architecture covering standalone components, Signals (signal, computed, input, output), built-in @if/@for control flow, OnPush, lazy loadComponent routes, typed reactive forms, and RxJS/signal interop. Use when building or refactoring Angular views, components, and services.
---

# Modern Angular Architecture (`Angular 22`)

Build crisp, zoneless-ready, high-performance Angular features cleanly separated between smart state services and focused presentational components.

## Core Rules for `momo tcg`

1. **Standalone + Modern APIs only**: Never introduce `NgModule`, `*ngIf`, or `*ngFor`. Use `@if`, `@for (...; track stableId)`, `@let`, `input()`, `output()`, and `inject()`.
2. **Signals at the core**: Store mutable UI state in `signal()`, derive computed views in `computed()`, and bridge STOMP/HTTP streams at service boundaries cleanly. Prefer `ChangeDetectionStrategy.OnPush` for pure display components (such as `ArcaneCardComponent`).
3. **Smart vs Presentational split**: Keep HTTP/STOMP orchestration inside `core/services/`, keep feature containers thin, and avoid querying raw DOM directly (`document.querySelector`) when template bindings suffice.
4. **Budget discipline**: Respect `frontend/angular.json` limits (`500kB` initial warning, `4kB` warn / `8kB` err CSS per component). Push shared styles and tokens into `styles.scss` / `tailwind.css`.
5. **Strict cleanup**: Unregister GSAP timelines, timers, and listeners using `DestroyRef.onDestroy()`.

## Reference Library (`omer-metin/skills-for-antigravity`, Apache-2.0)

- **`references/patterns.md`** — Standalone structure, signals/computed composition, lazy routing, reactive forms, and service patterns.
- **`references/sharp_edges.md`** — Subscription leaks, `track $index` DOM thrashing, effect misuse, and change-detection pitfalls.
- **`references/validations.md`** — Angular architecture and runtime checks.

*(Adapted to Angular 22 conventions paired with `ui-ux-pro-max/data/stacks/angular.csv`.)*
