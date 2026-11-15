# Système de Design & Identité Visuelle — L'Ordre des Arcanes Online

> **Document vivant** : consigner ici les tokens CSS/Tailwind, règles typographiques, composants d'arène/cartes et décisions d'ergonomie à mesure que l'interface évolue. Journaliser chaque refonte UI dans [`docs/CHANGELOG.md`](./CHANGELOG.md).

## 1. Intention Esthétique & Thème « Obsidienne Runique & Laiton Doré »

**Direction artistique** : Dark-Fantasy Tactique / Grimoire d'Arcanes — une table de duel feutrée en obsidienne graduée, réhaussée par un **laiton antique bruni** (action, focus, autorité) et une encre **parchemin chaud** (`#f4efe6`) moins fatigante qu'un blanc froid `#ffffff` sur fond sombre. Chaque couleur porte un rôle unique : aucune collision entre **danger/erreur** (rose corail `#fb7185`), **Feu Ardent** (orange braise `#f97316`), **laiton CTA** (`#d9a441`) et **Lumière Astrale** (champagne céleste `#fef08a`).

### Règles Non-Négociables
1. **Architecture de tokens à 3 niveaux** : primitives `--arcane-palette-*` → tokens sémantiques `--arcane-bg-*`, `--arcane-ink-*`, `--arcane-border-*`, `--arcane-accent-*`, `--arcane-status-*`, `--arcane-faction-*` dans [`styles.scss`](../frontend/src/styles.scss) + miroir `@theme` dans [`tailwind.css`](../frontend/src/tailwind.css).
2. **Hiérarchie par le contraste, l'échelle et la profondeur** : distinguer nettement l'arrière-plan abyssal (`void #060811` / `primary #0a0e1a`), les puits encastrés (`recessed #080c17` via `.arcane-well`), les panneaux (`surface #121829` via `.arcane-panel`) et le relief actif (`elevated #1b233a`).
3. **Typographie auto-hébergée (CSP `font-src 'self' data:`)** :
   - **Display (`.arcane-display`)** : [`Cinzel`](../frontend/public/fonts/cinzel-latin.woff2) (graisses variables `600..700`) réservée à la marque, titres d'écrans/sections et libellés nobles — jamais aux descriptions denses de cartes.
   - **UI & Corps** : [`Plus Jakarta Sans`](../frontend/public/fonts/plus-jakarta-sans-latin.woff2) (graisses variables `400..700`) pour toute l'interface et les effets de cartes.
   - **Chiffres d'arène (`.arcane-num`)** : `font-variant-numeric: tabular-nums` obligatoire sur PV, MMR, chronomètres, coûts d'énergie et statistiques ATQ/DÉF.
4. **Syntaxe Tailwind v4 & Budget CSS Angular** : utiliser `bg-linear-to-*` (jamais `bg-gradient-to-*`), respecter les budgets [`angular.json`](../frontend/angular.json) (`4 kB` warning / `8 kB` erreur par composant) en centralisant les classes de surface réutilisables dans [`styles.scss`](../frontend/src/styles.scss).
5. **Iconographie SVG unifiée & Accessibilité WCAG 2.2 AA** :
   - Remplacer progressivement les émojis structurels par des SVG homogènes (`aria-hidden="true"` avec texte, `aria-label` explicite sinon).
   - Anneau de focus visible doré brillant (`--arcane-border-focus: #f0c96e`), jamais supprimé (`:focus-visible` + `@media (forced-colors: active)` + `color-scheme: dark`).
   - Cibles tactiles `>= 40–44px` (`min-block-size: 2.5rem` via `.arcane-btn`).
   - Mouvements & animations GSAP strictement conditionnés à `prefers-reduced-motion: no-preference`.

---

## 2. Palette & Tokens « Obsidienne Runique & Laiton Doré »

### Surfaces & Encres ([`styles.scss`](../frontend/src/styles.scss) & [`tailwind.css`](../frontend/src/tailwind.css))

| Token Sémantique / Tailwind | Hex | Contraste sur `void` / `primary` / `surface` / `elevated` | Rôle |
| :--- | :--- | :--- | :--- |
| `--arcane-bg-void` (`bg-arcane-void`) | `#060811` | — | En-tête collant, dégradés d'encadrement, texte sombre sur or (`8.89:1` sur `#d9a441`, `12.65:1` sur `#f0c96e`) |
| `--arcane-bg-primary` (`bg-arcane-primary`) | `#0a0e1a` | — | Fond global avec halo ambiant discret (laiton + brume astrale) |
| `--arcane-bg-recessed` (`bg-arcane-recessed`) | `#080c17` | — | Champs `.arcane-input`, piste scrollbar, puits `.arcane-well` |
| `--arcane-bg-surface` (`bg-arcane-surface`) | `#121829` | — | Panneaux `.arcane-panel`, cartes standard, modales `dialog.arcane-dialog` |
| `--arcane-bg-elevated` (`bg-arcane-elevated`) | `#1b233a` | — | Surfaces actives, survols, barres de progression |
| `--arcane-ink-primary` (`text-arcane-ink`) | `#f4efe6` | `17.45` / `16.81` / `15.43` / `13.60` | Parchemin chaud — texte principal haute lisibilité |
| `--arcane-ink-secondary` (`text-arcane-ink-secondary`) | `#cbd5e1` | `13.46` / `12.97` / `11.90` / `10.49` | Corps descriptif, navigation inactive, libellés de formulaire |
| `--arcane-ink-muted` (`text-arcane-ink-muted`) | `#94a3b8` | `7.80` / `7.51` / `6.89` / `6.08` | Métadonnées, légendes, horodatages |
| `--arcane-accent-gold` (`arcane-gold`) | `#d9a441` | `8.89` / `8.56` / `7.86` / `6.93` | Laiton antique — CTA principal, filets prestige, jauge active |
| `--arcane-accent-gold-bright` (`arcane-gold-bright`) | `#f0c96e` | `12.65` / `12.18` / `11.18` / `9.85` | Or clair — titres de marque, focus ring `:focus-visible`, orbes d'énergie |
| `--arcane-accent-bronze` (`arcane-bronze`) | `#8c621d` | — | Laiton patiné pour dos de cartes et dégradés métalliques |
| `--arcane-status-success` (`arcane-success`) | `#34d399` | `10.40` / `10.02` / `9.19` | Victoire, statut prêt, temps réel actif |
| `--arcane-status-danger` (`arcane-danger`) | `#fb7185` | `7.43` / `7.15` / `6.57` | Défaite, erreurs de validation (`#fda4af` sur texte d'erreur), alerte La Corde |
| `--arcane-status-info` (`arcane-info`) | `#60a5fa` | `7.86` / `7.57` / `6.95` | Repères informatifs, statut spectateur/aide |
| `--arcane-status-warning` (`arcane-warning`) | `#fbbf24` | `11.97` / `11.53` / `10.59` | Avertissements de phase et rappels |

### Couleurs & Identité des 4 Factions ([`faction-theme.ts`](../frontend/src/app/shared/utils/faction-theme.ts))
- **`FIRE` (Feu Ardent)** : braise vermillon-orange (`#f97316`, texte clair `orange-200` / `#fb923c` `7.81:1` sur `surface`), séparé du rose/rouge d'erreur.
- **`ICE` (Glace Éternelle)** : givre boréal (`#38bdf8`, texte clair `sky-200` / `#7dd3fc` `10.60:1` sur `surface`).
- **`SHADOW` (Ombres Occultes)** : améthyste occulte (`#a855f7`, texte clair `purple-200` / `#d8b4fe` `10.00:1` sur `surface`).
- **`LIGHT` (Lumière Astrale)** : ivoire champagne céleste (`#fef08a`, `15.18:1` sur `surface`), distinct du laiton CTA `#d9a441`.

---

## 3. Ergonomie Spécifique par Vue

- **Socle Mobile-First & Tactile (`320px+`, `@media (pointer: coarse)`)** :
  - `viewport-fit=cover` dans [`index.html`](../frontend/src/index.html) combiné aux zones sûres `.arcane-safe-top` / `.arcane-safe-bottom` (`env(safe-area-inset-*)`) dans [`styles.scss`](../frontend/src/styles.scss) et aux notifications toast ancrées sur `env(safe-area-inset-bottom)` dans [`app.html`](../frontend/src/app/app.html).
  - Jeton `--arcane-target-size` (`2.5rem` pointeur fin, `2.75rem` / `44px` sous `@media (pointer: coarse)`) appliqué aux boutons `.arcane-btn` et aux actions tactiles d'Arène (`.arena-action-mini`), lien logo `min-h-11` (`44px`), unités `dvh`/`dvw`, `overflow-wrap: anywhere`, `text-wrap: balance` (`h1`–`h3`), `text-wrap: pretty` (`p`, `li`) et `min-w-0` (`min-inline-size: 0`) sur toutes les colonnes CSS Grid (`lg:col-span-*`, `xl:col-span-*`) pour contenir les tableaux et rails horizontaux sans élargir le viewport.
  - **En-tête compact ([`app-header.component.html`](../frontend/src/app/shared/components/app-header/app-header.component.html))** : un seul repère `<nav aria-label="Navigation principale">` avec `ariaCurrentWhenActive="page"`, repliable sur `< lg` (`lucideMenu`/`lucideX`, fermeture `Escape` et changement de route) pour libérer le viewport mobile sans duplication DOM.
- **Arène (`/arena/:matchId`)** :
  - **Cockpit universel zéro-scroll (`min-height: 35rem`)** : `.app-shell:has(app-arena-page)` verrouille le viewport à `100svh` (`overflow: hidden`) sur desktop comme sur mobile ; sous `80rem` (`< xl`), l'en-tête global `> app-header` est replié pour consacrer 100 % de l'écran au duel.
  - **Desktop (`xl+`, `>= 1280px`)** : grille `#arenaGrid` (`9` cols jeu + `3` cols barre latérale `Détail de la Carte` + onglets `Journal` / `Chat`), barre de duel complète avec corde de phase, Rive Nord (`.arcane-arena-north`), ligne de front (`.arcane-clash-line` avec bouton d'attaque directe `onSelectDirectAttack`), Rive Sud (`.arcane-arena-south`) à actions en ligne (`hidden xl:flex`), main 6 colonnes et modale Mulligan 5 colonnes (`lg:grid-cols-5` dans `.arcane-dialog-wide`).
  - **Mobile & tablette tactile (`< xl`, vérifié à `390×844`)** : bandeau HUD compact + corde filaire et déclencheurs tiroir `Journal/Chat` & menu ; terrains adverse et allié affichés simultanément en grilles `4` colonnes (Monstres `M1`–`M4`) et `5` colonnes (Soutien `S1`–`S2`, `F1`–`F3`) grâce aux **micro-jetons container-query** (`@container arcane-card (max-width: 6.75rem)` masquant `.arcane-card-ramp`, `.arcane-card-badges` et `.arcane-status-text` pour garder coût, nom tronqué, posture, icône de statut et capsules ATQ/DÉF sans rognage) ; barre d'actions rapides non-occlusive sur une ligne (`Attaquer`, `Posture`, `Effet`, lecteur zoomé, désélection) ; dock inférieur à portée du pouce (`ATQ`/`DÉF`, `Combat`, `Fin Tour` + `.arcane-hand-rail` aimanté) ; tiroir tactique coulissant `.arcane-mobile-drawer` (`@starting-style` + `transition-behavior: allow-discrete`), fiche rapide `.arcane-quick-sheet` et lecteur plein format (`mobileReaderOpen`).
- **Composants partagés & Vues du Sanctuaire (`/auth`, `/lobby`, `/deckbuilder`, `/tournaments`, `/profile`)** :
  - Sélecteurs `select.arcane-input` stylés avec chevron doré SVG (`appearance: none`), retour tactile `.arcane-btn:active` (`scale(0.97)`), balises de statut animées (`.arcane-status-dot--online/--loading/--offline`), cartes de lancement tactiques (`.arcane-mode-card`) et cartes mobiles de duels récents sur `/lobby`, onglets de decks horizontaux & icônes Lucide sur `/deckbuilder`, barres de maîtrise par faction & filtres swipeables sur `/profile`, portail d'astrolabe SVG sur `/auth`.
  - Les modales critiques (mulligan, réaction, choix modal, défausse, fin de duel, abandon) utilisent l'élément natif `<dialog class="arcane-dialog">` avec `overscroll-behavior: contain` et `::backdrop`.
- **Sanctuaire (`/lobby`)**, **Grimoire (`/deckbuilder`)**, **Tournois (`/tournaments`)**, **Profil (`/profile`)** :
  - **Sanctuaire (`/lobby`)** : paramètres communs (`Deck sélectionné`, `Format du Match`), boutons PvP (`File Classée`, `Duel Amical`), puis bloc dédié **Entraînement · Mage Automate (IA)** regroupant `Faction IA`, sélecteur segmenté accessible `.arcane-seg-group` / `.arcane-seg-option` (`<fieldset>` + `<legend>` + boutons `<input type="radio">` masqués visuellement via `.arcane-sr-radio`, options **Faible** · **Moyen** · **Fort** à cible tactile `var(--arcane-target-size)`, `:focus-visible` doré et `@media (forced-colors: active)`), et bouton `Affronter Mage Automate (IA)` reflété dans les badges d'Arène (`AI_DIFFICULTY_META` : `IA · Faible`, `IA · Moyen`, `IA · Fort`).
  - **Grimoire (`/deckbuilder`)** : résumé de validation Anti-Méta & bouton « Sauvegarder » placés avant le catalogue sur `< lg` (`order-1 lg:order-2`) avec liste des cartes repliable (`showDraftListOnMobile`) et grille catalogue fluide (`grid-cols-1 min-[440px]:grid-cols-2 md:grid-cols-3 xl:grid-cols-4`).
  - **Tableaux & accords** : wrappers scrollables clavier (`tabindex="0" role="region"`) dans `/lobby`, accordéons natifs `<details>`/`<summary>` sans imbrication de titres dans `<summary>`, et états d'erreur/live ARIA (`AnnouncerService`).

---

## 4. Architecture Graphique des Cartes, Iconographie & Animations (`V2`)

### 4.1 Illustrations Procédurales & Bascule Image Prête à l'Emploi
- **Résolution centralisée ([`card-artwork.ts`](../frontend/src/app/shared/utils/card-artwork.ts))** :
  - `CARD_ARTWORK_REGISTRY` associe un `cardId` (ex. `FIRE_M_01`) à une ressource locale statique (`/cards/FIRE_M_01.webp`) pour éviter toute requête HTTP 404 tant que le fichier n'existe pas.
  - En l'absence d'image enregistrée, `getProceduralArtTheme(faction, cardType)` génère un cadre d'illustration vectoriel local (halos radiaux par faction, double cercle runique et icône SVG typée), avec bascule automatique sur `<img>` dès qu'un visuel est fourni dans `frontend/public/cards/`.
- **Effet Foil Holographique & Inclinaison GPU ([`ArcaneCardComponent`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.ts))** :
  - Propriétés `artworkUrl`, `showArtwork`, `interactiveTilt` (`±7deg` en `rotateX`/`rotateY` + `--foil-x`/`--foil-y`) et `foilOverride`.
  - Animation `@keyframes arcane-foil-drift` (`7s`) et transitions d'inclinaison activées uniquement sous `@media (prefers-reduced-motion: no-preference)`.
- **Cadre Collectionnable & Container Queries (`V2.1`)** :
  - `container-type: inline-size; container-name: arcane-card` sur `:host` ([`arcane-card.component.scss`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.scss)) : sous `12.5rem` (ex. grilles Mulligan 5 colonnes ou emplacements compacts), `.arcane-emblem-label` et `.arcane-posture-text` s'effacent proprement au profit des icônes afin de dégager le sigil central et le titre. `:host` conserve sa hauteur intrinsèque par défaut (étiré uniquement via la classe utilitaire `h-full` dans la grille catalogue) pour éviter tout chevauchement dans les piles verticales mobiles (ex. modale d'aperçu).
  - **Proportions Uniformes des Cartes Pleines (`!compact()`)** : `.arcane-card-standard` (`min-block-size: 20.5rem`), en-tête et bandeau d'illustration figés (`shrink-0`), bande type/mots-clés `min-h-6` à badges `whitespace-nowrap`, encadré de règles extensible (`flex-1`) borné par `.arcane-line-clamp-4` (`-webkit-line-clamp: 4`, `line-clamp: 4`, débridé par `[unclampDescription]="true"` en modale d'aperçu), et pied de carte ancré (`mt-auto` : capsules `ATQ`/`DÉF` ou sceau Faction/Type équivalent sur les Sorts, Malédictions et Artéfacts).
  - **Alignement CSS `subgrid` Catalogue (`/deckbuilder`)** : `.arcane-catalog-grid` + `.arcane-catalog-tile` (`grid-row: span 2; grid-template-rows: 1fr auto; grid-template-rows: subgrid;` dans [`styles.scss`](../frontend/src/styles.scss)) alignent toutes les cartes d'une même rangée à une hauteur identique (`328px` sur desktop 4 colonnes) et verrouillent les barres `+ Ajouter` / aperçu sur exactement la même ligne horizontale, indépendamment de la longueur du texte.
  - Bordure de cadre fixe `1px` (`.arcane-card-frame`) combinée à `outline` + `box-shadow` non décalants (`.arcane-card-selected`, `.arcane-card-targetable`), orbe d'énergie sculpté `.arcane-orb-energy`, capsules `ATQ`/`DÉF` en `.arcane-num` et entrée `showPosture` (`false` en main, Mulligan et défausse).

### 4.2 Modes d'Aperçu des Cartes
1. **Grimoire & Decks (`/deckbuilder`)** : le clic sur la tuile conserve l'ajout/retrait rapide du deck, tandis qu'un bouton frère dédié (« Aperçu grand format », `lucideEye`) ouvre une fenêtre `<dialog class="arcane-dialog">` présentant la carte interactive 3D, un sélecteur d'édition **Foil / Standard**, la navigation Précédent/Suivant et le détail des mots-clés.
2. **Arène Temps Réel (`/arena/:matchId`)** : le survol ou clic sur n'importe quelle carte visible alimente l'inspecteur latéral « Détail de la carte » (`[compact]="true" [showArtwork]="true" [interactiveTilt]="true"`), gardant la visibilité permanente sans Scroll global desktop.

### 4.3 Iconographie SVG & Bibliothèques d'Animation CSP-Safe
| Rôle | Bibliothèque / Mécanisme | Configuration & Garde-fous |
| :--- | :--- | :--- |
| **Icônes UI & Factions** | `@ng-icons/core@^36.1.0` + `@ng-icons/lucide@^36.1.0` | `provideNgIconsConfig({ strokeWidth: 1.75 }, withContentSecurityPolicy())` dans [`app.config.ts`](../frontend/src/app/app.config.ts) ; SVG décoratifs marqués `aria-hidden="true"` |
| **Transitions de Pages** | Angular Router `withViewTransitions()` | Fondu `0.2s cubic-bezier(0.22, 1, 0.36, 1)` dans [`styles.scss`](../frontend/src/styles.scss), désactivé si `prefers-reduced-motion: reduce` |
| **Entrée en Grille** | `motion@^14.14.0` (`motion/mini`) | [`animateCardGridEntrance`](../frontend/src/app/shared/utils/motion-presets.ts) : cascade GPU (`opacity`, `transform`), plafond à `16` cartes et garde JSDOM (`typeof el.animate === 'function'`) |
| **Impulsions d'Arène** | `gsap@^3.15.1` | [`pulseElementImpact`](../frontend/src/app/shared/utils/motion-presets.ts) sur variations PV (`damage`/`heal`) et pulsation de plateau ; sans flash (`opacity`/`transform` seuls) |
| **Célébration Victoire** | `canvas-confetti@^1.9.4` | [`launchVictoryConfetti`](../frontend/src/app/shared/utils/motion-presets.ts) importé paresseusement (`0` o dans le bundle initial), `disableForReducedMotion: true` et cleanup sur `DestroyRef` |

### 4.4 Illustrations Thématiques par Description & VFX Temps Réel d'Arène (`V2.2`)
- **Moteur d'illustration sémantique ([`card-artwork.ts`](../frontend/src/app/shared/utils/card-artwork.ts), [`ArcaneCardArtComponent`](../frontend/src/app/shared/components/arcane-card-art/arcane-card-art.component.ts))** :
  - `buildCardIllustrationSpec(card)` dérive pour chacune des 120 cartes (sans dépendance HTTP ni `<linearGradient>` dupliqué pour le rendu catalogue/CSP) une scène `viewBox="0 0 240 135"` combinant :
    1. L'atmosphère élémentaire par faction (`FIRE` braises & collines volcaniques, `ICE` pics cristallins & givre, `SHADOW` halo astral pourpre & volutes d'ombre, `LIGHT` rayons solaires dorés & sanctuaire).
    2. `20` silhouettes dédiées (`dragon`, `phoenix`, `griffin`, `golem`, `guardian`, `beast`, `owl`, `serpent`, `wisp`, `reaper`, `seraph`, `campfire`, `fireball`, `vortex`, `chains`, `shield`, `blade`, `eclipse`, `siphon`, `rune`).
    3. `12` surcouches d'effet (`DAMAGE`, `DESTROY`, `SHIELD`, `ATK_BUFF`, `HEAL`, `SLOW`, `STEALTH`, `ENERGY`, `SURVOL`, `PEEK`, `REACTIVE`, `CURSE`) et une pastille concise `.arcane-art-effect-chip` alignée sur les capacités réelles (ex. `IMMUNISÉ SORTS`, `RALENTIT`, `SOIN +2 PV`, `+2 DÉF · +1 ⚡`, `MUR PROTECTEUR`).
  - Sur les cartes compactes de main et de plateau (`!displayArt`), un badge discret de type/effet dans [`ArcaneCardComponent`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.html) garde une lecture instantanée Feu/Bouclier/Givre/Soin, et le panneau latéral « Détail de la carte » en Arène affiche désormais la scène miniature `[showArtwork]="true"`.
  - Conserve 100 % de compatibilité avec `resolveCardArtworkUrl` + [`CARD_ARTWORK_REGISTRY`](../frontend/src/app/shared/utils/card-artwork.ts) pour basculer automatiquement sur vos visuels `.webp` ultérieurs.
- **VFX Temps Réel d'Arène ([`arena-vfx.ts`](../frontend/src/app/shared/utils/arena-vfx.ts), [`ArenaSlotVfxComponent`](../frontend/src/app/shared/components/arena-slot-vfx/arena-slot-vfx.component.ts), [`styles.scss`](../frontend/src/styles.scss))** :
  - `extractArenaVfxEvents(prevView, nextView, catalogByName)` compare de manière déterministe chaque transition d'état autoritaire `PlayerGameView` et `combatLog` :
    - **Emplacements de terrain** (`.arcane-vfx-slot`, via `<app-arena-slot-vfx>` sur tous les emplacements adverses, alliés 2v2 et personnels) : anneau d'aura, entaille oblique `.arcane-vfx-cleave` (`-X DÉF`, `DÉTRUIT`, `+X ATQ`), dôme protecteur `.arcane-vfx-dome` (`+X DÉF`, `BOUCLIER`, `RÉVÉLATION`), barres de givre `.arcane-vfx-frost-bars` (`RALENTIT`), invocation (`INVOCATION` / `SCEAU POSÉ`) et pastille montante `.arcane-vfx-badge`.
    - **PV Équipes** (`.arcane-vfx-lp-badge` sur `#enemyLpBox` et `#allyLpBox`) : pastilles `-X PV` / `+X PV` teintées selon la faction déclencheuse.
    - **Vague de Sort / Réaction** (`.arcane-vfx-spell-wave`) : bannière supérieure non-bloquante (`pointer-events: none`) lors des événements `CAST_SPELL`, `REACTION_TRIGGERED`, `ACTIVATE_EFFECT` et `PLAY_PERSISTENT_SPELL`.
  - **Emplacements d'Avatar Joueurs ([`ArcaneAvatarComponent`](../frontend/src/app/shared/components/arcane-avatar/arcane-avatar.component.ts))** :
    - Médaillons SVG vectoriels sombres & laiton (`avatar_initiate`, `avatar_pyromancer`, `avatar_cryomancer`, `avatar_shadowmage`, `avatar_luminarch`, `avatar_automaton`) déclinés en tailles `xs` (en-tête), `sm` (Arène), `md` (sélecteur `/profile`) et `lg` (en-tête profil), avec aura élémentaire (`FIRE`/`ICE`/`SHADOW`/`LIGHT`), bordure de ligue et pastilles `bot`/`offline` `aria-hidden="true"`.
    - Surcouche d'impact d'avatar (`.arcane-avatar-impact-fx`, `.arcane-avatar-shield-svg`, `.arcane-avatar-hit-ring`, `.arcane-avatar-impact-badge`) affichant les blocages (`BLOQUÉ`) et dégâts directs ou de renvoi (`-X PV`).
  - **Projectiles Élémentaires & Défense Encaissée ([`ArenaProjectileOverlayComponent`](../frontend/src/app/shared/components/arena-projectile-overlay/arena-projectile-overlay.component.ts), [`ArenaSlotVfxComponent`](../frontend/src/app/shared/components/arena-slot-vfx/arena-slot-vfx.component.ts), [`styles.scss`](../frontend/src/styles.scss))** :
    - Calque plein plateau `.arcane-projectile-layer` animant des traits directionnels GPU par faction du deck attaquant (`FIRE` braise, `ICE` cristal, `SHADOW` volutes occultes, `LIGHT` rayon solaire) via `--proj-x`, `--proj-y`, `--proj-dx`, `--proj-dy` et `--proj-angle` résolus entre `[data-board-slot]` / `[data-player-avatar]`.
    - À l'impact (`260ms`, ou `0ms` si `prefers-reduced-motion: reduce`), `DEFENSE_BLOCK` / `DEFENSE_BROKEN` déclenchent le bouclier hexagonal runique (`.arcane-vfx-hex-shield`), les rayons de déflexion (`.arcane-vfx-deflect-rays`), le recul défensif [`pulseElementImpact(..., 'defense')`](../frontend/src/app/shared/utils/motion-presets.ts) et les badges `ENCAISSÉ · DÉF`, `RENVOI -X PV`, `PARÉ -X PV` ou `BRISÉ · DÉF`.
  - **Conformité Performance, Accessibilité & Motion** :
    - Tous les calques VFX et SVG décoratifs portent `pointer-events: none` et `aria-hidden="true"` pour éviter tout bruit sur lecteurs d'écran ou interception tactile.
    - Animations purement **GPU-composited** (`transform`, `opacity`), sans stroboscopie (<= 3 flashs/s WCAG 2.3.1), ressorts `--arcane-spring-easing` / `--arcane-bounce-easing` (`cubic-bezier` par défaut + `linear(...)` sous `@supports`) activés uniquement sous `@media (prefers-reduced-motion: no-preference)`.

### 4.5 Lisibilité de Gameplay par Faction, Clarté du Tour & Chronique Tactique (`V2.3`)
- **Clarté d'état de Tour & Territoires de Faction ([`arena-page.component.html`](../frontend/src/app/features/arena/arena-page.component.html), [`arena-page.component.ts`](../frontend/src/app/features/arena/arena-page.component.ts), [`styles.scss`](../frontend/src/styles.scss))** :
  - Indicateur univoque `À VOUS DE JOUER` (`À VOUS` en viewport `< sm`) vs `Tour de {adversaire}` dans le bandeau HUD, badge `Votre tour` / `Joue` / `Relais` sur le bandeau joueur actif, et balise de début de tour non-bloquante `.arcane-turn-beacon` (`2100ms`, animée uniquement sous `prefers-reduced-motion: no-preference`, accompagnée d'une annonce lecteur d'écran via `AnnouncerService`).
  - Territoires nord/allié/sud teintés selon `primaryFaction` (`.arcane-arena-north[data-faction]`, `.arcane-arena-south[data-faction]`), avec renforcement de bordure sur `[data-my-turn='true']` et `[data-active-turn='true']`, pastilles de faction compactes et support `forced-colors: active` via `Highlight` / `CanvasText`.
- **Guidage Tactique Unifié & Affordances de Main/Combat ([`arena-tactical-guide.ts`](../frontend/src/app/shared/utils/arena-tactical-guide.ts), [`ArcaneCardComponent`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.ts))** :
  - `buildTacticalGuidance(...)` fusionne en une ligne claire (`.arcane-tactical-bar[data-tone]`) le contexte d'action courant (Mulligan, Sélection d'emplacement, Ciblage de sort/attaque, Trêve T1, main jouable ou créatures prêtes au combat).
  - `evaluateHandCardPlayability(view, me, card)` reflète les limites autoritaires serveur (`4` Monstres, `3` Familiers, `2` Sorts/Artéfacts, `1` Monstre Épique, verrou de sort) avec bordure `[playable]` + badge textuel hors-couleur `-top-2 right-1.5` (`Jouable`, `-X ⚡`, `4/4 Plein`, `Verrouillé`) sans masquer le titre ni ajouter d'attente silencieuse au clic.
  - En `COMBAT_PHASE`, le clic direct sur une créature `[attackerReady]="true"` (`PRÊT`) déclenche immédiatement le ciblage d'attaque.
- **Chronique Tactique Française ([`combat-log-presenter.ts`](../frontend/src/app/shared/utils/combat-log-presenter.ts))** :
  - `presentCombatLogEntries(...)` remplace les codes techniques (`TEAM_A`/`TEAM_B`, `ATTACK`/`DEFENSE`, `damage(s)`, `->`) par du français naturel relatif au joueur (`Vous`, `à vos PV`, `aux PV adverses`), attribue une catégorie visuelle (`COMBAT`, `DAMAGE`, `DEFENSE`, `HEAL`, `SPELL`, `SUMMON`, `EFFECT`, `TURN`, `MATCH`), un liseré latéral par faction (`.arcane-log-entry[data-faction]`) et un basculement `Temps forts` / `Tout (N)` sous onglets `[aria-selected]` (`Chronique` / `Chat`).

---

## 5. Chantiers Design & Évolutions

1. **Typographie Signature (`Fait — V1`)** : [`Cinzel`](../frontend/public/fonts/cinzel-latin.woff2) (Display) + [`Plus Jakarta Sans`](../frontend/public/fonts/plus-jakarta-sans-latin.woff2) auto-hébergées (`font-src 'self' data:`) et utilitaires `.arcane-display` / `.arcane-num` (`tabular-nums`).
2. **Gravure & Cadre des Cartes (`Fait — V2`)** : cadre SVG procédural multi-faction avec médaillon d'art, effet Foil GPU et modale d'aperçu `/deckbuilder` + inspecteur Arène.
3. **Remplacement des Emojis Structurels (`Fait sur header / ArcaneCard / /lobby / /arena`)** : migration complète via `@ng-icons/lucide` sur l'en-tête, [`ArcaneCardComponent`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.html), le Sanctuaire ([`lobby-page`](../frontend/src/app/features/lobby/lobby-page.component.html)) et l'Arène ([`arena-page`](../frontend/src/app/features/arena/arena-page.component.html)) ; finitions restantes ciblant `/tournaments` et `/profile`.
4. **Feedback Temps Réel & Confettis (`Fait — V2`)** : cascade d'entrée `motion/mini`, impulsions PV GSAP et confettis paresseux de victoire avec nettoyage `DestroyRef` et `prefers-reduced-motion`.
5. **Intégration des Illustrations Définitives (`À venir — V3`)** : déposer les visuels `.webp` fournis dans `frontend/public/cards/` et renseigner [`CARD_ARTWORK_REGISTRY`](../frontend/src/app/shared/utils/card-artwork.ts).
