---
name: arcane-ui-design-studio
description: >-
  Project-specific UI/UX craft, Dark-Fantasy tactical aesthetic elevation,
  Angular 22 + Tailwind v4 + SCSS + GSAP 3.15 architecture, component CSS
  budget control, WCAG 2.2 AA accessibility, and zero-scroll Arena ergonomics for
  L'Ordre des Arcanes Online. Use whenever designing, restyling, or improving
  frontend views (/lobby, /arena, /deckbuilder, /tournaments, /profile, /auth,
  shared cards/header).
---

# Studio UI/UX & Refonte Visuelle (`momo tcg`)

Ce skill guide l'élévation visuelle de **L'Ordre des Arcanes Online** autour du thème **« Obsidienne Runique & Laiton Doré »** (un véritable TCG Dark-Fantasy tactique, lisible et sans latence visuelle), en synergie avec les skills globaux `frontend-design`, `ui-ux-pro-max`, `design-systems`, `color-theory`, `typography`, `motion-design`, `icon-design`, `ui-visual-validator`, `modern-web-guidance` et les skills projet `game-ui-design`, `card-game-design`, `angular`, `tailwind-design-system` et `wcag-audit-patterns`.

## 1. Workflow Avant Codage UI

1. **Consulter les guides UI pertinents** :
   - Exécuter `modern-web-guidance` pour tout pattern HTML/CSS/JS moderne (`dialog`, `popover`, `accordion`, `aria`, `color`, `dark-mode`, `typography`, `animations`, Core Web Vitals).
   - Appliquer les principes éditoriaux de `frontend-design`, `color-theory`, `typography` et `game-ui-design` (un point fort visuel par vue, séparation sémantique stricte entre surfaces d'obsidienne, laiton CTA et factions élémentaires).
   - Consulter [`docs/DESIGN_SYSTEM.md`](../../../docs/DESIGN_SYSTEM.md) et la base locale `ui-ux-pro-max` (`data/stacks/angular.csv`, `data/stacks/html-tailwind.csv`, `data/motion.csv`, `references/quick-reference.md`).
2. **Planifier brièvement** avant de modifier le template/CSS : hiérarchie de lecture, CTA primaire unique par zone, contraste texte/fond (`>= 4.5:1`), et état vide/chargement/erreur.

---

## 2. Règles Spécifiques au Front-End `momo tcg`

### Budget CSS, Tailwind v4 & Architecture Angular 22
- Respecter les budgets stricts de [`frontend/angular.json`](../../../frontend/angular.json) : `maximumWarning: 4kB` / `maximumError: 8kB` par fichier `.scss` de composant.
- Placer les `@font-face`, variables 3-tiers (`--arcane-palette-*` → tokens sémantiques), classes de surface (`.arcane-panel`, `.arcane-well`, `.arcane-display`, `.arcane-num`), modales `dialog.arcane-dialog`, champs `.arcane-input` et utilitaires d'accessibilité dans [`frontend/src/styles.scss`](../../../frontend/src/styles.scss) et `@theme` dans [`frontend/src/tailwind.css`](../../../frontend/src/tailwind.css).
- Utiliser Tailwind CSS v4 idiomatique (`bg-linear-to-*`, classes tokens `bg-arcane-surface`, `text-arcane-ink`, `text-arcane-gold-bright`) et jamais `bg-gradient-to-*` ou de noms de classes dynamiques non détectables par le scanner.
- Utiliser exclusivement Angular `standalone`, `signals`/`computed`, `@if`/`@for` avec `track` par ID stable, `ChangeDetectionStrategy.OnPush`, et `inject()`.

### Identité « Obsidienne Runique & Laiton Doré » & Anti-Cliché
- **Typographie auto-hébergée (CSP `font-src 'self' data:`)** : [`Cinzel`](../../../frontend/public/fonts/cinzel-latin.woff2) via `.arcane-display` pour l'autorité/marque/titres nobles ; [`Plus Jakarta Sans`](../../../frontend/public/fonts/plus-jakarta-sans-latin.woff2) pour la lisibilité UI et le texte des cartes ; `.arcane-num` (`tabular-nums`) sur tous les PV, MMR, coûts d'énergie, ATQ/DÉF et chronomètres. Jamais de CDN externe bloqué par `nginx/default.conf`.
- **Remplacer les émojis structurels** (`⚔️`, `🤖`, `✦`, `●`) dans les boutons, onglets, navigations et badges par des icônes SVG inline nettes (`stroke-width: 1.75px`, `aria-hidden="true"` si texte adjacent).
- **Contraste, Profondeur & Color-Pie TCG** : distinguer `void` (`#060811`), `primary` (`#0a0e1a`), `recessed` (`#080c17`), `surface` (`#121829`), `elevated` (`#1b233a`) et le laiton `#d9a441`/`#f0c96e` ; maintenir la séparation sémantique de [`faction-theme.ts`](../../../frontend/src/app/shared/utils/faction-theme.ts) (`FIRE` orange braise `#f97316`, `ICE` `#38bdf8`, `SHADOW` `#a855f7`, `LIGHT` champagne `#fef08a`) vis-à-vis de `--arcane-danger` (`#fb7185`) et du laiton CTA.
- **Zero-Scroll Cockpit Arène** : préserver le verrouillage desktop `100dvh` (`.app-shell:has(app-arena-page)`) sur `min-width: 80rem` et `min-height: 42rem` ; les zones défilantes locales (`arena-battlefield`, `arena-tab-list-scroll`) utilisent `overscroll-behavior: contain` et `scrollbar-gutter: stable`.

### Ergonomie Mobile-First & Tactile (`320px+`, `@media (pointer: coarse)`)
- **Progression Mobile-First stricte** : concevoir d'abord la mise en page sans préfixe pour `320px–639px` (1 colonne fluide, `min-w-0` sur les enfants flex, `overflow-wrap: anywhere` sur le corps), puis enrichir aux paliers `sm:`, `md:`, `lg:`, `xl:`. Tolérance zéro sur le débordement horizontal (`documentElement.scrollWidth <= window.innerWidth`).
- **En-tête compact sur mobile (`<= 64px`)** : sous `lg:`, regrouper la navigation et le tiroir joueur derrière un bouton menu accessible (`>= 44×44px`, `aria-expanded`, fermeture par Escape/navigation) tout en conservant un unique repère `<nav>` pour mobile et desktop. Ne jamais laisser un header empilé de 250–350px bloquer la vue sur téléphone.
- **Cibles tactiles `>= 44×44 CSS px`** : utiliser `--arcane-target-size` (`2.5rem` desktop, `2.75rem` / `44px` sous `@media (pointer: coarse)`) sur `.arcane-btn`, sélecteurs, boutons d'action du Grimoire (`Ajouter` / `Aperçu`) et contrôles d'Arène (`.arena-action-mini`).
- **Zones sûres & `dvh`/`dvw`** : déclarer `viewport-fit=cover` dans `index.html`, appliquer `env(safe-area-inset-*)` aux barres collantes en bord d'écran et utiliser `dvh` plutôt que `vh`.
- **Hiérarchie mobile par écran** :
  - **Grimoire (`/deckbuilder`)** : placer la synthèse Anti-Méta + sauvegarde en tête sur mobile (`order-first lg:order-last`) avec liste de deck repliable, devant les 120 cartes.
  - **Arène (`/arena`)** : PV sur une ligne (`whitespace-nowrap`), emplacements de terrain respirants (`grid-cols-2 sm:grid-cols-4` / `grid-cols-2 sm:grid-cols-5`), main en bandeau horizontal tactile (`overflow-x-auto snap-x` → grille `xl:grid`), et fiche d'aperçu accessible au tap sans dépendre de `:hover`.
  - **Tableaux (`/lobby`)** : envelopper les `<table>` dans `<div tabindex="0" role="region" aria-label="...">` avec `overflow-x-auto`.

### Mouvement GSAP 3.15 & Performance
- Respecter `window.matchMedia('(prefers-reduced-motion: reduce)')` avant toute animation GSAP non essentielle.
- Animer uniquement des propriétés compositées (`transform`, `opacity`), jamais `width`/`height`/`top`/`left`, et toujours nettoyer les tweens/timelines/listeners via `DestroyRef.onDestroy()`.

---

## 3. Contrôle Qualité & Validation Visuelle

1. Lancer `npm.cmd test -- --watch=false` et `npm.cmd run build` dans `frontend/` (aucun dépassement de budget `4kB` / `8kB`).
2. Reconstruire `arcane-web` si vérification Docker (`docker compose up -d --build arcane-web`).
3. Vérifier en navigateur réel via Chrome DevTools (`ui-visual-validator`) :
   - Zéro erreur console (`list_console_messages`).
   - Inspection de capture d'écran desktop (`1440×900`+) **et** émulation mobile tactile (`390×844`, `mobile, touch`) : vérifier `scrollWidth <= innerWidth`, hauteur de l'en-tête mobile repliée (`<= 72px`), taille des cibles tactiles (`>= 44px`), visibilité du focus clavier et contraste.
4. Mettre à jour [`docs/DESIGN_SYSTEM.md`](../../../docs/DESIGN_SYSTEM.md) et [`docs/CHANGELOG.md`](../../../docs/CHANGELOG.md).
