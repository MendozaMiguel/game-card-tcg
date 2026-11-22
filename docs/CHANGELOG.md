# Journal des Évolutions (Changelog) — L'Ordre des Arcanes Online

> **Règle de maintenance continue** : chaque nouvelle fonctionnalité, migration SQL, durcissement sécurité, optimisation ou correction de régression doit ajouter une entrée datée en tête de ce journal avec les fichiers clés et les vérifications exécutées.

---

## [2026-11-22] — Didacticiel Interactif Nouvel Apprenti & Bande-Son *Witcher 3* + Bruitages Combat

### Objectif
1. Accompagner automatiquement chaque nouvel inscrit étape par étape depuis le Sanctuaire jusqu'aux bases d'un duel réel (Mulligan, plateau, Énergie, postures `ATQ`/`DÉF`, Trêve du Tour 1, Combat/Survol et 4 Lois Anti-Méta) avec mémorisation en base.
2. Ajouter un thème musical de fond inspiré de *The Witcher 3* (Ré dorien procédural adaptatif) et des bruitages distincts d'attaque, de défense/bouclier, d'invocation et de tour.

### Back-End (Java 21 / Flyway `V5` / IA Automate)
- **Persistance d'initiation (`V5`)** :
  - Ajout de la migration [`V5__add_user_tutorial_flag.sql`](../backend/src/main/resources/db/migration/V5__add_user_tutorial_flag.sql) ajoutant `tutorial_completed BOOLEAN NOT NULL DEFAULT false` sur `user_profiles` (rétro-rempli à `true` pour les comptes existants, `false` pour toute nouvelle inscription).
  - Mise à jour de [`UserProfileEntity.java`](../backend/src/main/java/com/ordredesarcane/online/user/UserProfileEntity.java), [`UserProfileDto.java`](../backend/src/main/java/com/ordredesarcane/online/user/dto/UserProfileDto.java), [`ProfileService.java`](../backend/src/main/java/com/ordredesarcane/online/user/ProfileService.java) (`markTutorialCompleted`) et [`ProfileController.java`](../backend/src/main/java/com/ordredesarcane/online/user/ProfileController.java) (`POST /api/profile/tutorial/complete`).
- **Correction robustesse IA (`MageAutomateBot`)** :
  - Correction dans [`BotLethalSolver.java`](../backend/src/main/java/com/ordredesarcane/online/game/engine/BotLethalSolver.java) et [`BotTacticalPlanner.java`](../backend/src/main/java/com/ordredesarcane/online/game/engine/BotTacticalPlanner.java) empêchant l'émission d'attaques `DIRECT_PLAYER` simulées lorsque des actions préparatoires ne sont pas encore réglées ou bloquées par `CombatResolver.validateAttackLegality`.
- **Tests JUnit** : ajout de [`ProfileTutorialTests.java`](../backend/src/test/java/com/ordredesarcane/online/ProfileTutorialTests.java) (`42/42` tests `mvn test` passés).

### Front-End (Angular 22 / Web Audio API / WCAG 2.2 AA & 1.4.2)
- **Didacticiel pas-à-pas** :
  - [`auth.service.ts`](../frontend/src/app/core/services/auth.service.ts) (`completeTutorial()`) & [`arcane.models.ts`](../frontend/src/app/core/models/arcane.models.ts) (`tutorialCompleted?: boolean`).
  - [`lobby-page.component.ts`](../frontend/src/app/features/lobby/lobby-page.component.ts) / [`.html`](../frontend/src/app/features/lobby/lobby-page.component.html) : bannière d'initiation non bloquante pour `tutorialCompleted === false`, lancement direct en 1v1 IA Faible (`?tutorial=1`), bouton « Passer » persistant et bouton permanent « Didacticiel ».
  - [`ArenaTutorialCoachComponent`](../frontend/src/app/shared/components/arena-tutorial-coach/arena-tutorial-coach.component.ts) + [`arena-tutorial-steps.ts`](../frontend/src/app/shared/components/arena-tutorial-coach/arena-tutorial-steps.ts) : progression automatique synchronisée à `PlayerGameView` (Étape 1 intégrée dans `#mulliganDialog`, Étapes 2–6 dans `<dialog popover="manual" role="dialog">`), cibles tactiles `>= 44px` et prise en charge `prefers-reduced-motion` / `forced-colors`.
- **Moteur Audio Procédural (`Web Audio API`)** :
  - [`ArcaneAudioService`](../frontend/src/app/shared/services/arcane-audio.service.ts) : thème boucle Ré dorien (`~116 BPM`, bourdon, luth/bouzouki en `3+3+2`, vielle slave avec vibrato + réverbération de salle et tambour *Bodhrán*) en mode `'CALM'` (Sanctuaire) et `'COMBAT'` (Arène), plus bruitages (`ATTACK_SWING`, `HIT_*`, `DIRECT_HIT`, `DEFENSE_BLOCK`, `DEFENSE_BROKEN`, `POSTURE_DEFENSE`, `POSTURE_ATTACK`, `SUMMON_*`, `SPELL_CAST`, `YOUR_TURN`, `VICTORY`, `DEFEAT`).
  - [`ArenaAudioControlComponent`](../frontend/src/app/shared/components/arena-audio-control/arena-audio-control.component.ts) : contrôles indépendants **Musique**, **SFX** et **Volume** (`localStorage`, WCAG 1.4.2 Audio Control) intégrés aux bandeaux Sanctuaire et Arène.
- **Optimisation Budget Initial (`< 500 kB`) & Synchronisation GitHub (`origin/main` `1ea678c`)** :
  - Extraction de [`league-labels.ts`](../frontend/src/app/shared/utils/league-labels.ts) aligné 1:1 avec `LeagueTier` (`APPRENTI`, `INITIE`, `ADEPTE`, `MAGE`, `ARCHIMAGE`, `GRAND_MAITRE`) pour éviter d'inclure tout [`faction-theme.ts`](../frontend/src/app/shared/utils/faction-theme.ts) dans `main` via [`AppHeaderComponent`](../frontend/src/app/shared/components/app-header/app-header.component.ts) ; fusion propre avec `origin/main` (`1ea678c` : refonte deux vues Grimoire, `LeagueTier`, `start-docker.ps1`) sans perte de fonctionnalité.
- **Complétion Illustrations 120 / 120 cartes (Decks Ombres Occultes & Lumière Astrale)** :
  - Génération et export en `640×360` JPEG dans [`frontend/public/cards/`](../frontend/public/cards/) de `10` nouvelles illustrations style TCG : `shadow-wolf.jpg`, `shadow-lich.jpg`, `shadow-harpy.jpg`, `shadow-abyss.jpg`, `shadow-familiar.jpg`, `light-unicorn.jpg`, `light-dragon.jpg`, `light-griffin.jpg`, `light-familiar.jpg` et `light-spell.jpg` (`27` visuels au total).
  - Câblage complet dans [`card-artwork.ts`](../frontend/src/app/shared/utils/card-artwork.ts) : **`120/120` cartes** couvertes (`FIRE` `30/30`, `ICE` `30/30`, `SHADOW` `30/30`, `LIGHT` `30/30`).
- **Tests, Build & Contrôle Live (post-fusion `origin/main` `1ea678c`)** :
  - Ajout de [`arena-tutorial-coach.component.spec.ts`](../frontend/src/app/shared/components/arena-tutorial-coach/arena-tutorial-coach.component.spec.ts), [`arcane-audio.service.spec.ts`](../frontend/src/app/shared/services/arcane-audio.service.spec.ts), test `120/120` dans [`card-artwork-and-vfx.spec.ts`](../frontend/src/app/shared/utils/card-artwork-and-vfx.spec.ts) et mise à jour de [`lobby-page.component.spec.ts`](../frontend/src/app/features/lobby/lobby-page.component.spec.ts) & [`arcane-card.component.spec.ts`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.spec.ts) (`ngc` OK ; `ng test --watch=false` : `18` fichiers / `45/45` tests Vitest passés ; `ng build` : **`490.71 kB`** initial sans dépassement de budget ; `mvn test` : `42/42` tests passés).
  - Stack Docker (`docker compose up -d --build` → `arcane-db`, `arcane-server` `healthy`, `arcane-web` actif) et Chrome DevTools live sur `http://localhost/` : `/lobby` (`1425 <= 1440` desktop, `390 === 390` mobile), `/deckbuilder` deux vues `LIST` + `EDITOR` (`150` cartes rendues, `27` JPEG `200 OK`, `0` repli SVG, `1425 <= 1440`) et `/arena?tutorial=1` (`1440×900` et `390×844`, `impactCanvasMounted: true`, `threeReady: true`, `0` image cassée, `0` erreur console).
  - Documentation [`docs/ARCHITECTURE.md`](./ARCHITECTURE.md), [`docs/DESIGN_SYSTEM.md`](./DESIGN_SYSTEM.md) et [`docs/INFRASTRUCTURE_AND_SECURITY.md`](./INFRASTRUCTURE_AND_SECURITY.md) mise à jour.

---

## [2026-11-21] — Refonte Grimoire & Decks en Deux Vues (`LIST` + `EDITOR`) selon Maquettes

- **Front-End ([`deckbuilder-page.component.ts`](../frontend/src/app/features/deckbuilder/deckbuilder-page.component.ts), [`deckbuilder-page.component.html`](../frontend/src/app/features/deckbuilder/deckbuilder-page.component.html), [`deckbuilder-page.component.scss`](../frontend/src/app/features/deckbuilder/deckbuilder-page.component.scss))** :
  - **Vue Collection (`LIST` / `deck1.png`)** : affichage par défaut des decks sauvegardés avec médaillon de faction (`FIRE`/`ICE`/`SHADOW`/`LIGHT`), action directe de favori/suppression en pied de carte, et emplacement vide dédié **`Nouveau`** à côté des decks pour démarrer une création.
  - **Vue Éditeur (`EDITOR` / `deckSuite.png`)** : colonne gauche regroupant les cartes ajoutées au deck en grille visuelle 3 colonnes dans le cadre violet (avec emplacements vides jusqu'à `6` puis défilement jusqu'à `30`, jauge 4 Lois Anti-Méta) et le bouton vert **Valider** centré en bas ; colonne droite regroupant en tête le bloc rouge de recherche & filtres (`Recherche`, `Faction`, `Type`, `Coût Énergie`, `Mot-clé / Ramp`, bouton `Réinitialiser`) suivi des cartes juxtaposées à leur cadre bleu de descriptif mécanique intégral (`KEYWORD_DESCRIPTIONS`, `+ Ajouter`, `Aperçu 3D`).
  - Retour automatique vers la vue `LIST` après validation d'un deck et mise à jour de [`docs/DESIGN_SYSTEM.md`](./DESIGN_SYSTEM.md).
- **Tests & Vérifications** :
  - Création de [`deckbuilder-page.component.spec.ts`](../frontend/src/app/features/deckbuilder/deckbuilder-page.component.spec.ts) (`3` tests couvrant `LIST`, `Nouveau` → `EDITOR`, filtres, validation 30 cartes, favoris et édition).
  - Build & Suite Front-End sous conteneur `node:24-alpine` : `ng build` (`499.71 kB` initial, `0` warning, `0` erreur) et `ng test --watch=false` (`9` fichiers, `27/27` tests passés).
  - Déploiement live : reconstruction et redémarrage du conteneur `arcane-web` (`docker compose up -d --build arcane-web`).

---

## [2026-11-20] — Effets de Combat & Animations Inspirés de *Cross Blitz*

### Objectif
Donner au plateau d'arène le « *juice* » visuel et tactile d'un RPG-TCG nerveux type **Cross Blitz** (élan + *hit-stop*, *hit-flash*, entailles croisées, étoiles de choc Canvas 2D, panaches de pose, secousse directionnelle et chiffres flottants non rognés) sans dépasser le budget CSS initial (`499.42 kB / 500 kB`).

### Modifications Front-End (Angular 22 / GSAP 3.15 / Canvas 2D)
- **Chorégraphie GSAP *Cross Blitz* (`motion-presets.ts`)** :
  - [`animateCardAttackLunge`](../frontend/src/app/shared/utils/motion-presets.ts) : phase d'armement (*wind-up* `60 ms` avec bascule 3D inverse), ruée rapide vers la cible (`125 ms`), micro ***hit-stop*** d'impact synchronisé à `185 ms` (`hitStrength` `light`/`heavy`/`critical`) couplé au recul + *squash-and-stretch* du défenseur (et garde métallique en blocage), puis rebond élastique.
  - [`animateCardSummonSlam`](../frontend/src/app/shared/utils/motion-presets.ts) & [`animateBoardShake`](../frontend/src/app/shared/utils/motion-presets.ts) : pose *drop-slam* avec compression d'atterrissage sur le plateau et secousse directionnelle orientée selon le vecteur `(dx, dy)` de l'impact.
- **Popups Arcade Flottants Non Rognés (`ArenaCombatPopupsComponent`)** :
  - Création du composant autonome [`ArenaCombatPopupsComponent`](../frontend/src/app/shared/components/arena-combat-popups/arena-combat-popups.component.ts) projetant `-X`, `-X PV`, `K.O.!`, `BRISÉ!`, `PARÉ!`, `BLOQUÉ!`, `+X ATQ` / `+X DÉF` hors du cadre des cartes avec ressort CSS `linear()` (fallback `cubic-bezier(0.34, 1.56, 0.64, 1)`), contour sombre à contraste élevé et prise en charge `prefers-reduced-motion: reduce` / `forced-colors`.
- **Calque Canvas 2D & Projectiles (`ArenaImpactCanvasComponent`, `ArenaProjectileOverlayComponent`, `ArenaSlotVfxComponent`)** :
  - [`ArenaImpactCanvasComponent`](../frontend/src/app/shared/components/arena-impact-canvas/arena-impact-canvas.component.ts) : ajout des étoiles de choc 8/10 branches (`StarBurst`), entailles croisées effilées en croissant, arcs électriques fissurés (`JaggedArc`), traînées cinétiques `TRAIL_STREAK`, déflagrations par faction `SPELL_BURST` (`FIRE`/`ICE`/`SHADOW`/`LIGHT`), panaches horizontaux de pose (`emitHorizontalPlumes`) et pause automatique hors-écran/onglet masqué (`contentvisibilityautostatechange`, `visibilitychange`).
  - [`ArenaProjectileOverlayComponent`](../frontend/src/app/shared/components/arena-projectile-overlay/arena-projectile-overlay.component.ts) : éclair d'origine `.arcane-proj-origin`, vol resserré à `220 ms` synchronisé sur `PROJECTILE_IMPACT_DELAY_MS = 185` et étoile de choc `.arcane-proj-star-svg` / fissure de garde `DEFENSE_BROKEN`.
  - [`ArenaSlotVfxComponent`](../frontend/src/app/shared/components/arena-slot-vfx/arena-slot-vfx.component.ts) : unique impulsion de flash d'impact `.arcane-vfx-hit-flash` (WCAG 2.3.1 <= 3 flashs/s) et double entaille SVG `.arcane-vfx-cross-svg`.
- **Intégration & Extraction VFX (`arena-vfx.ts`, `arena-page.component.ts/.html`)** :
  - Synchronisation unifiée à `185 ms` entre projectile, ruée, popups arcade (`popups`), traînées, starburst Canvas, éclats de sorts et secousse du plateau.
- **Tests & Vérifications** :
  - Nouveaux tests Vitest dans [`arena-combat-popups.component.spec.ts`](../frontend/src/app/shared/components/arena-combat-popups/arena-combat-popups.component.spec.ts), [`card-artwork-and-vfx.spec.ts`](../frontend/src/app/shared/utils/card-artwork-and-vfx.spec.ts) et [`arena-impact-canvas.component.spec.ts`](../frontend/src/app/shared/components/arena-impact-canvas/arena-impact-canvas.component.spec.ts) (`15` fichiers, `35/35` tests passés ; `ngc` OK ; `ng build` `499.42 kB` initial sans dépassement CSS).
  - Reconstruction Docker `arcane-web` (`docker compose up -d --build arcane-web`) + contrôle Chrome DevTools en duel réel Desktop `1440×900` (`scrollWidth === 1440`, `scrollHeight === 900`) et Mobile `390×844` (`scrollWidth === 390`) avec `0` erreur console ; serveur LAN `http://192.168.1.66:4200/` `HTTP 200`.

### Documentation
- Mise à jour de [`docs/DESIGN_SYSTEM.md`](./DESIGN_SYSTEM.md) (§4.3 et §4.4) et [`docs/CHANGELOG.md`](./CHANGELOG.md).

---

## [2026-11-20] — Mulligan Interactif Instantané avec Animation 3D & Validation Explicite

### Contexte
Lors de la phase de Mulligan, le joueur cochait ses cartes à remplacer et devait attendre la validation finale pour voir sa nouvelle main. Désormais, cliquer sur **`↻ Remplacer`** sous une carte l'échange **immédiatement** sur le serveur avec une animation 3D de retournement avant de lancer la partie via **`Valider et commencer`**.

### Back-End (Java 21 / Spring Boot / Règles & Sécurité)
- **Nouvelle action autoritaire (`MULLIGAN_REPLACE_CARD`)** : Ajout de `GameActionType.MULLIGAN_REPLACE_CARD` dans [`GameActionType.java`](../backend/src/main/java/com/ordredesarcane/online/game/model/GameActionType.java) et du gestionnaire verrouillé `handleMulliganReplaceCardLocked` dans [`GameEngineService.java`](../backend/src/main/java/com/ordredesarcane/online/game/engine/GameEngineService.java).
- **Anti-triche & Brouillard de Guerre** : Suivi par emplacement initial (`0..4`) dans [`PlayerState.java`](../backend/src/main/java/com/ordredesarcane/online/game/model/PlayerState.java) (`mulliganReplacedSlots`, `hasMulliganReplacedSlot`, `markMulliganSlotReplaced`, `getMulliganReplacedSlots()`). Chaque emplacement ne peut être remplacé qu'une seule fois ; la nouvelle carte est piochée avant réinsertion et mélange de l'ancienne carte afin d'empêcher tout cyclage ou re-pioche immédiate de la même carte. Projection restreinte au propriétaire / relais allié dans [`PlayerBoardViewDto.java`](../backend/src/main/java/com/ordredesarcane/online/game/dto/PlayerBoardViewDto.java) et [`FogOfWarProjector.java`](../backend/src/main/java/com/ordredesarcane/online/game/engine/FogOfWarProjector.java).
- **Tests JUnit** : Ajout de `instantMulliganReplaceSwapsCardInPlaceOncePerSlotBeforeConfirming` dans [`GameEngineTests.java`](../backend/src/test/java/com/ordredesarcane/online/GameEngineTests.java) (`40/40` tests passés via `mvn test`).

### Front-End (Angular 22 / GSAP 3.15 / Accessibilité WCAG 2.2 AA)
- **Modèles & Guide** : Ajout de `'MULLIGAN_REPLACE_CARD'` et `mulliganReplacedSlots?: number[]` dans [`arcane.models.ts`](../frontend/src/app/core/models/arcane.models.ts), et mise à jour de l'aide dans [`arena-tactical-guide.ts`](../frontend/src/app/shared/utils/arena-tactical-guide.ts).
- **Animation 3D GPUs (`transform` + `opacity`)** : Ajout de `animateMulliganCardOut` et `animateMulliganCardIn` dans [`motion-presets.ts`](../frontend/src/app/shared/utils/motion-presets.ts) avec support strict `prefers-reduced-motion` et nettoyage `clearProps`.
- **Modale Mulligan (`dialog#mulliganDialog`)** : Refactorisation de [`arena-page.component.ts`](../frontend/src/app/features/arena/arena-page.component.ts) et [`arena-page.component.html`](../frontend/src/app/features/arena/arena-page.component.html) (`@for (card of me.hand; track $index)`, compteur `X / 5 remplacée(s)`, bouton `↻ Remplacer` → `Échange…` → `✓ Remplacée` désactivé sans déplacement des boutons adjacents, pastille `✨ Nouvelle`, marge `pt-2.5` évitant le rognage mobile `390×844`, annonces `AnnouncerService` et validation `Valider et commencer` via `MULLIGAN_SWAP`).
- **Tests & Build** : Nouveau test Vitest [`arena-page.component.spec.ts`](../frontend/src/app/features/arena/arena-page.component.spec.ts) (`14` fichiers, `34/34` tests passés ; `ng build` à `499.48 kB` initial sans dépassement de budget ; validation live Desktop & Mobile `390×844` sur `http://localhost/` sans erreur console ni débordement horizontal).

### Documentation
- Mise à jour de [`docs/ARCHITECTURE.md`](./ARCHITECTURE.md) (§5), [`docs/DESIGN_SYSTEM.md`](./DESIGN_SYSTEM.md) (§3 et §4.3) et [`docs/CHANGELOG.md`](./CHANGELOG.md).

---

## [2026-11-20] — Correctif Moteur `CAMOUFLAGE` Expiré & Clarifications `SURVOL` (`Vol`) / Attaque Directe

- **Cause identifiée** : dans [`BoardRuleEvaluator`](../backend/src/main/java/com/ordredesarcane/online/game/engine/BoardRuleEvaluator.java), `hasKeyword(..., "CAMOUFLAGE")` et `evaluateKeywords(...)` retombaient sur les mots-clés statiques du catalogue (`definition.keywords()`) lorsque `creature.isCamouflaged()` était `false`, maintenant à tort le badge `CAMOUFLAGE` sur le plateau après expiration (`UNTIL_NEXT_OWN_TURN`) et bloquant les attaques/sorts contre des Monstres exposés comme `Ombre Rampante` alors même que le Front-End bloquait les attaques directes face à ce Monstre non camouflé (`Rempart` — `SURVOL` ne contournant que les Familiers).
- **Back-End ([`BoardRuleEvaluator.java`](../backend/src/main/java/com/ordredesarcane/online/game/engine/BoardRuleEvaluator.java), [`GameEngineTests.java`](../backend/src/test/java/com/ordredesarcane/online/GameEngineTests.java))** :
  - `hasKeyword(room, creature, "CAMOUFLAGE")` retourne désormais strictement `creature.isCamouflaged()`, et `evaluateKeywords` retire `"CAMOUFLAGE"` de la projection plateau tant que le camouflage n'est pas actif (`!creature.isCamouflaged()`).
  - Ajout du test unitaire `expiredCamouflageStopsHidingKeywordAndSurvolBypassesFamiliarsButNotMonsters` dans [`GameEngineTests`](../backend/src/test/java/com/ordredesarcane/online/GameEngineTests.java) (`11/11` tests passés).
- **Front-End ([`arena-page.component.ts`](../frontend/src/app/features/arena/arena-page.component.ts), [`arena-page.component.html`](../frontend/src/app/features/arena/arena-page.component.html))** :
  - Précision du message `directAttackStatus()` nommant le Monstre ou Gardien bloquant (en 1v1 et 2v2) et rappelant que `Vol`/`SURVOL` contourne uniquement les Familiers ; prise en charge des mots-clés `SURVOL` et `VOL`.
  - Ajout d'un bouton accessible sur l'orbe PV adverse `#enemyLpBox` lorsque l'attaque directe est autorisée (`pickingTarget()?.actionType === 'DECLARE_ATTACK' && directAttackStatus().allowed`) en complément du bouton central `Attaque Directe (PV Adv)`.
- **Documentation & Vérifications** :
  - Mise à jour de [`docs/ARCHITECTURE.md`](./ARCHITECTURE.md) (§5 Règle du Rempart, Survol & Camouflage).
  - Back-End (`backend/`) : `mvn.cmd test` → `39/39` tests passés (`0` échec).
  - Front-End (`frontend/`) : `npx.cmd ngc -p tsconfig.app.json --noEmit` OK ; `npm.cmd test -- --watch=false` (`13` fichiers, `33/33` tests passés) ; `npm.cmd run build` (`Initial total: 498.88 kB`, `0` erreur, `0` avertissement CSS).

---

## [2026-11-20] — Représentation Fidèle à la Maquette des Cartes Tabletop & Illustrations Sur Plateau/Main

- **Objectif** : faire apparaître l'illustration de chaque carte directement sur les emplacements du plateau (Monstres, Familiers, Sorts/Artéfacts) et dans la main en proportions portrait TCG fidèles à la maquette d'Arène, tout en maintenant un cockpit zéro-scroll sur desktop (`1440×900`) et mobile (`390×844`).
- **Mode Tabletop Portrait ([`ArcaneCardComponent`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.ts), [`arcane-card.component.html`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.html), [`arcane-card.component.scss`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.scss), [`arcane-card.component.spec.ts`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.spec.ts))** :
  - Ajout de l'entrée `[tabletop]="true"` activant `:host(.arcane-card-tabletop-host)` (`block-size: 100%`, `min-block-size: 0`), l'affichage automatique de la fenêtre d'art `.arcane-art-frame--tabletop` (`flex-1 min-h-0`, chargement `eager` sur plateau/main), les indicateurs d'état minimalistes `.arcane-tabletop-statuses` / `.arcane-tstat` superposés en coin d'image et un pied compact ATQ/DÉF ou type/mot-clé (tout en conservant l'aperçu `aspect-16/9` dans l'inspecteur latéral « Détail de la carte »).
  - Ajout de `2` tests unitaires dans [`arcane-card.component.spec.ts`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.spec.ts) vérifiant le mode `[tabletop]="true"` (artwork peint `<img>`, masquage du texte long, aura épique et repli SVG procédural).
- **Proportions Maquette & Zéro-Scroll Arène ([`arena-page.component.html`](../frontend/src/app/features/arena/arena-page.component.html), [`arena-page.component.scss`](../frontend/src/app/features/arena/arena-page.component.scss))** :
  - Activation de `[tabletop]="true"` sur les 10 emplacements de plateau (adverse, allié 2v2, joueur) et la main, avec actions rapides `.arena-slot-actions` ancrées en surcouche (`hover` / `focus-within` / `[data-selected='true']`) pour préserver 100 % de la hauteur d'illustration.
  - Recentrage maquette des zones (`arena-w-front: 26.5rem`, `arena-w-support: 35rem`, `arena-w-board: 46rem`, `arena-w-dock: 62rem` mono-ligne) et grille main `auto-cols-[minmax(0,1fr)]` à `7+` cartes pour empêcher tout chevauchement avec la pile Deck.
- **Documentation & Vérifications** :
  - Mise à jour de [`docs/DESIGN_SYSTEM.md`](./DESIGN_SYSTEM.md) (§4.2, §4.6).
  - Front-End (`frontend/`) : `npx.cmd ngc -p tsconfig.app.json --noEmit` OK ; `npm.cmd test -- --watch=false` (`13` fichiers, `33/33` tests passés) ; `npm.cmd run build` (`Initial total: 498.58 kB`, `0` erreur, `0` avertissement de budget CSS `<= 4.00 kB`).
  - Docker & Chrome DevTools live (`docker compose up -d --build arcane-web`, `arcane-db` et `arcane-server` Healthy) : duel live vs IA (`Manche 6 · Tour 11`) inspecté sur `1440×900` (`scrollHeight === 900`, `battlefield clientH/scrollH: 506/506`, `heroDockH: 50`, illustrations plateau `84.6×50.1 px` Monstres / `91×37.7 px` Familiers) et `390×844` mobile tactile (`scrollWidth === 390`, `scrollHeight === 844`, `0` image cassée, `0` erreur console).

---

## [2026-11-20] — Effets de Choc Combat, Table Slam Mythique & VFX Multi-Moteurs (`GSAP` + `Canvas 2D` + `Three.js`)

- **Objectif** : transformer les assauts Monstre contre Monstre, les attaques directes aux PV joueur, les destructions de créatures et l'arrivée sur table de cartes puissantes en animations de duel spectaculaires façon TCG AAA, fluides à 60 FPS et sans bloquer les actions.
- **Moteur d'Analyse & Timelines GSAP ([`arena-vfx.ts`](../frontend/src/app/shared/utils/arena-vfx.ts), [`motion-presets.ts`](../frontend/src/app/shared/utils/motion-presets.ts))** :
  - Ajout de `summonEvents` (`MYTHIC` pour `epic` / `SPELL_ULTIMATE` / coût `>= 5`, `HEAVY` `>= 3`, `STANDARD`), `shatterEvents`, `mythicShowcases` (`INVOCATION ÉPIQUE ★`, `SORT ULTIME DÉCHAÎNÉ ⚡`) et `hitStrength` (`LIGHT`/`HEAVY`/`CRITICAL`) dans `extractArenaVfxEvents(...)` avec impact synchronisé à `220ms` (`PROJECTILE_IMPACT_DELAY_MS`).
  - Ajout des chorégraphies `gsap.timeline()` GPU uniquement (`transform`/`opacity`/`clearProps`) : `animateCardAttackLunge` (élan + frappe le long du vecteur cible + recul défenseur), `animateBoardShake` (`light`/`heavy`/`critical`) et `animateCardSummonSlam` (`STANDARD`/`HEAVY`/`MYTHIC`).
- **Particules Additives Canvas 2D, Vitrine Mythique, 3D Three.js & Aura Carte ([`ArenaImpactCanvasComponent`](../frontend/src/app/shared/components/arena-impact-canvas/arena-impact-canvas.component.ts), [`ArenaSummonShowcaseComponent`](../frontend/src/app/shared/components/arena-summon-showcase/arena-summon-showcase.component.ts), [`ArenaProjectileOverlayComponent`](../frontend/src/app/shared/components/arena-projectile-overlay/arena-projectile-overlay.component.ts), [`ArenaThreeBackdropComponent`](../frontend/src/app/shared/components/arena-three-backdrop/arena-three-backdrop.component.ts), [`ArcaneCardComponent`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.scss), [`arena-page.component.ts`](../frontend/src/app/features/arena/arena-page.component.ts))** :
  - Nouveau calque Canvas 2D additif (`CLASH` entailles & étincelles, `PARRY` déviation hexagonale, `DIRECT_STRIKE` onde de choc joueur + `.arena-impact-vignette`, `SHATTER` éclats de carte, `SUMMON_MYTHIC`/`HEAVY`/`STANDARD` hexagramme & particules ascendantes), plafonné à `220` particules et mis en veille (`0 %` CPU) au repos.
  - Banderole non-bloquante `ArenaSummonShowcaseComponent` à l'invocation de cartes puissantes/Ultimes ; présence vivante `.arcane-card-mythic` & `.arcane-mythic-aura` sur les cartes puissantes (`WCAG 2.3.1` sans stroboscopie, `prefers-reduced-motion` respecté).
  - Réaction 3D positionnelle `[combatPulse]` dans `ArenaThreeBackdropComponent` (anneau d'impact 3D aux coordonnées normalisées du choc, recul Z caméra et boost de braises).
- **Tests, Budget CSS & Vérifications Live** :
  - Nouveaux tests : [`arena-impact-canvas.component.spec.ts`](../frontend/src/app/shared/components/arena-impact-canvas/arena-impact-canvas.component.spec.ts), [`arena-summon-showcase.component.spec.ts`](../frontend/src/app/shared/components/arena-summon-showcase/arena-summon-showcase.component.spec.ts) et extension de [`card-artwork-and-vfx.spec.ts`](../frontend/src/app/shared/utils/card-artwork-and-vfx.spec.ts).
  - Refactorisation CSS : factorisation `--mc` dans `arcane-card.component.scss` (retrait doublon `::before`), déplacement de `.arena-impact-vignette` dans `ArenaImpactCanvasComponent` et retrait `.arena-pip-back` inutilisé — **0 avertissement** sous la barre `4.00 kB`.
  - Front-End (`frontend/`) : `npx.cmd ngc -p tsconfig.app.json --noEmit` OK ; `npm.cmd test -- --watch=false` (`12` fichiers, `31/31` tests passés) ; `npm.cmd run build` (`Initial total: 498.97 kB`, `0` erreur, `0` avertissement).
  - Docker & Chrome DevTools live sur `http://localhost/` : `arcane-web` reconstruit (`arcane-db` et `arcane-server` Healthy), partie réelle vs IA vérifiée avec capture vidéo (attaque directe `Salamandrin`, réplique `Garde Céleste`, lancement `Boule de Feu Arcanique`) à `1440×900` (`impactCanvasMounted: true`, `threeReady: "true"`, `mythicCardsVisible: 6`, `scrollWidth: 1440`) et mobile `390×844` (`scrollWidth === 390`, `0` erreur console/HTTP).

---

## [2026-11-20] — Illustrations TCG Style Yu-Gi-Oh! / Magic & Moteur Vectoriel Multi-Tons

- **Objectif** : rehausser nettement le rendu artistique des Monstres, Familiers et Sorts en combinant illustrations peintes dark-fantasy locales (inspirées de Yu-Gi-Oh! et Magic: The Gathering) et moteur SVG vectoriel multi-tons enrichi.
- **Illustrations Peintes & Registre ([`frontend/public/cards/`](../frontend/public/cards/), [`card-artwork.ts`](../frontend/src/app/shared/utils/card-artwork.ts), [`card-artwork-and-vfx.spec.ts`](../frontend/src/app/shared/utils/card-artwork-and-vfx.spec.ts))** :
  - Production et optimisation (`640×360` JPEG haute qualité, sans texte incrusté) de **17 visuels locaux** dérivés de 12 tableaux dark-fantasy générés (`fire-beast.jpg`, `fire-dragon.jpg`, `fire-fam-cat.jpg`, `fire-fam-drake.jpg`, `fire-golem.jpg`, `fire-phoenix.jpg`, `fire-spell.jpg`, `ice-banshee.jpg`, `ice-dragon.jpg`, `ice-familiar.jpg`, `ice-golem.jpg`, `ice-spell.jpg`, `ice-wolf.jpg`, `ice-wraith.jpg`, `ice-yeti.jpg`, `light-seraph.jpg`, `shadow-eclipse.jpg` ; quota temporaire atteint avant complétion des lots `SHADOW` Monstres/Familiers et `LIGHT` Dragons/Familiers/Sorts).
  - Câblage dans `CARD_ARTWORK_REGISTRY` sur **76 cartes** (`FIRE` `30/30`, `ICE` `30/30`, Sorts `SHADOW_S_01..10` et avant-garde/boucliers `LIGHT` `6/30`), vérifié par test unitaire dédié sur `resolveCardArtworkUrl`.
- **Intégration Carte & Moteur SVG Enrichi ([`ArcaneCardComponent`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.html), [`arcane-card.component.scss`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.scss), [`ArcaneCardArtComponent`](../frontend/src/app/shared/components/arcane-card-art/arcane-card-art.component.ts), [`arcane-card-art.component.html`](../frontend/src/app/shared/components/arcane-card-art/arcane-card-art.component.html))** :
  - Fenêtre d'art `<img width="640" height="360" loading="lazy" decoding="async">` protégée par le dégradé `.arcane-art-scrim` sous `.arcane-art-effect-chip`, `[attr.data-faction]`/`[attr.data-effect]` sur `.arcane-card-root`, et pastille compacte d'icône d'effet en main/plateau.
  - Refonte d'[`ArcaneCardArtComponent`](../frontend/src/app/shared/components/arcane-card-art/arcane-card-art.component.html) pour les `44` cartes sans JPEG : gradients `<defs>` scopés par instance `uid` (`glow-{{uid}}`, `body-{{uid}}`, `rim-{{uid}}`), cercles hexagrammes arcaniques, cryptes gothiques `SHADOW`, cathédrales/rayons solaires `LIGHT` et silhouettes trois tons.
- **Documentation & Vérifications** :
  - Mise à jour de [`docs/DESIGN_SYSTEM.md`](./DESIGN_SYSTEM.md) (§4.1, §4.4, §5) et [`docs/INFRASTRUCTURE_AND_SECURITY.md`](./INFRASTRUCTURE_AND_SECURITY.md) (§2.1).
  - Front-End (`frontend/`) : `npx.cmd ngc -p tsconfig.app.json --noEmit` OK ; `npm.cmd test -- --watch=false` (`10` fichiers, `28/28` tests passés) ; `npm.cmd run build` (`Initial total: 498.82 kB`, `0` erreur, `0` avertissement CSS/TS).
  - Runtime Docker (`docker compose up -d --build arcane-web` : `arcane-db` Healthy, `arcane-server` Healthy, `arcane-web` Up) & Chrome DevTools live sur `http://localhost/` (`/deckbuilder` filtres `Feu Ardent`, `Ombres Occultes`, `Glace Éternelle`, `Lumière Astrale` + `/arena` Mulligan, main, inspecteur détail) à `1440×900` (`scrollWidth: 1425 <= 1440`) et `390×844` mobile tactile (`scrollWidth === 390`) avec `0` erreur console et `0` image cassée.

---

## [2026-11-20] — Pop-up Central d'Annonce de Tour (« C’est à vous de jouer ! »)

- **Objectif** : afficher un grand pop-up central lisible en une phrase (« **C’est à vous de jouer !** ») au début de chaque tour du joueur face à un adversaire, sans bloquer les actions ni voler le focus clavier.
- **Composant & Intégration Arène ([`ArenaTurnPopupComponent`](../frontend/src/app/shared/components/arena-turn-popup/arena-turn-popup.component.ts), [`arena-turn-popup.component.spec.ts`](../frontend/src/app/shared/components/arena-turn-popup/arena-turn-popup.component.spec.ts), [`arena-page.component.ts`](../frontend/src/app/features/arena/arena-page.component.ts), [`arena-page.component.html`](../frontend/src/app/features/arena/arena-page.component.html), [`styles.scss`](../frontend/src/styles.scss))** :
  - Nouveau composant standalone `OnPush` centré sur le plateau (`z-index: 44`, `pointer-events: none`) présentant l'accroche `Tour N · Contre {adversaire}` et la phrase en grand display `Cinzel` (`clamp(1.45rem, 4.4vw, 2.65rem)`), teintée à la faction active et synchronisée avec l'onde de choc WebGL [`ArenaThreeBackdropComponent`](../frontend/src/app/shared/components/arena-three-backdrop/arena-three-backdrop.component.ts).
  - Déclenchement conditionné à un affrontement avec adversaire(s) en `MAIN_PHASE` (`!view.spectator && isMyTurn() && opponentBoards().length > 0`) et suppression immédiate si une modale (`WAITING_FOR_REACTION`, `WAITING_FOR_MODAL_CHOICE`, `WAITING_FOR_DISCARD`, `FINISHED`) ou le tour adverse survient, avec temporisation `2400ms` nettoyée dans `clearVfxTimers()`.
  - Accessibilité & Motion : annonce unique conservée sur `AnnouncerService` (`Tour N : c’est à vous de jouer.`), animation GPU (`opacity`/`transform`) uniquement sous `prefers-reduced-motion: no-preference`, fondu simple sous `prefers-reduced-motion: reduce`, support `forced-colors: active` et retrait des anciennes règles `.arcane-turn-beacon` de `styles.scss`.
- **Documentation & Vérifications** :
  - Mise à jour de [`docs/DESIGN_SYSTEM.md`](./DESIGN_SYSTEM.md) (§4.5).
  - Front-End (`frontend/`) : `npx.cmd ngc -p tsconfig.app.json --noEmit` OK ; `npm.cmd test -- --watch=false` (`10` fichiers, `27/27` tests passés) ; `npm.cmd run build` (`Initial total: 498.81 kB`, `0` erreur, `0` avertissement).
  - Runtime Docker (`docker compose up -d --build arcane-web`) & Chrome DevTools sur `http://localhost/` : apparition live du pop-up vérifiée par enregistrements vidéo sur Desktop (`1440×900`, Tours `5` et `7`) et Mobile (`390×844`, Tour `9`) avec `0` erreur console.

---

## [2026-11-20] — Scénographie Tabletop d'Arène, Toile de Fond 3D WebGL (`three`) & Démarrage Live

- **Objectif** : implémenter la disposition tabletop d'Arène issue de la maquette utilisateur, démarrer la stack complète Docker (`arcane-db`, `arcane-server`, `arcane-web`), installer `three` (`@^0.187.0`) en chargement paresseux pour une toile de fond 3D interactive CSP-safe et valider l'affichage desktop, tablette et mobile tactile.
- **Scène 3D WebGL & Animations ([`ArenaThreeBackdropComponent`](../frontend/src/app/shared/components/arena-three-backdrop/arena-three-backdrop.component.ts), [`arena-three-backdrop.component.spec.ts`](../frontend/src/app/shared/components/arena-three-backdrop/arena-three-backdrop.component.spec.ts))** :
  - Installation de `three@^0.187.0` et `@types/three@^0.187.0` dans [`frontend/package.json`](../frontend/package.json) (`found 0 vulnerabilities`).
  - Import dynamique `await import('three')` isolant `three-module` dans un chunk paresseux (`751.75 kB`, `0` octet ajouté au bundle initial) avec sommets d'obsidienne basse-polygonale, anneaux runiques centraux, particules élémentaires (`DataTexture` radiale en mémoire) adaptées à `[faction]` et onde de choc sur `[pulseTrigger]`.
  - Conformité accessibilité & cycle de vie : mise en pause automatique lorsque hors-écran (`content-visibility: auto` + `contentvisibilityautostatechange` + `IntersectionObserver`) ou onglet masqué (`visibilitychange`), rendu statique sous `prefers-reduced-motion: reduce`, et libération complète géométries/matériaux/textures/renderer via `DestroyRef`.
- **Arène Tabletop, Ressorts CSS & Mobile Micro-Cards ([`arena-page.component.html`](../frontend/src/app/features/arena/arena-page.component.html), [`arena-page.component.scss`](../frontend/src/app/features/arena/arena-page.component.scss), [`arena-page.component.ts`](../frontend/src/app/features/arena/arena-page.component.ts), [`ArcaneCardComponent`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.html), [`ArenaProjectileOverlayComponent`](../frontend/src/app/shared/components/arena-projectile-overlay/arena-projectile-overlay.component.ts), [`styles.scss`](../frontend/src/styles.scss))** :
  - Structuration fidèle des zones visuelles : camp nord miroir (orbe `PV`, avatar, emplacements `[Sort 1 · F1-F3 · Sort 2]` + `4` Monstres, main cachée), ligne frontale `4` Monstres (rouge), sanctuaire `3` Familiers (vert) entouré des `2` Sorts/Artéfacts (rose), main `6` cartes (violet) à ressort CSS `--arcane-spring-easing` (`linear()` + repli `cubic-bezier`), pile Deck/Défausse (orange) et hub joueur `.arena-hero-dock` (bleu : `PV`, avatar, `À VOUS`, énergie, posture `ATQ/DÉF`, `Phase Combat`, `Fin du Tour`).
  - Encapsulation des styles `.arcane-projectile-layer`/`.arcane-proj-*` dans `ArenaProjectileOverlayComponent` et suppression de classes CSS obsolètes dans `styles.scss` pour maintenir le bundle initial sous `500 kB`.
  - Branchement des classes container-query (`.arcane-card-statuses`, `.arcane-status-badge`, `.arcane-status-text`, `.arcane-card-stat-pill`) et ajout du palier `@container arcane-card (max-width: 4.5rem)` sur `ArcaneCardComponent` supprimant tout rognage des capsules `ATQ`/`DÉF` et badges sur mobile `390×844`.
- **Documentation & Vérifications** :
  - Mise à jour de [`docs/DESIGN_SYSTEM.md`](./DESIGN_SYSTEM.md) (§4.2, §4.3, §4.4, §4.6) et [`docs/INFRASTRUCTURE_AND_SECURITY.md`](./INFRASTRUCTURE_AND_SECURITY.md) (§2.1).
  - Typage & Tests Front-End (`frontend/`) : `ngc -p tsconfig.app.json --noEmit` OK ; `npm.cmd test -- --watch=false` (`9` fichiers, `25/25` tests réussis) ; `npm.cmd run build` (`Initial total: 499.57 kB`, `three-module` lazy `751.75 kB`, `0` erreur, `0` avertissement) ; `npm.cmd audit --omit=dev` (`found 0 vulnerabilities`).
  - Stack Docker & Chrome DevTools Live (`http://localhost/`) : `arcane-db` Healthy, `arcane-server` Healthy, `arcane-web` démarré ; rendu et WebGL vérifiés (`threeReady: "true"`, `0` erreur console) à `1440×900` (`scrollWidth === 1440`, `scrollHeight === 900`), `768×1024` (`scrollWidth === 768`, `scrollHeight === 1024`) et `390×844` mobile tactile (`scrollWidth === 390`, `scrollHeight === 844`).

---

## [2026-11-18] — Fiabilisation `start-docker.ps1` & Restauration Accès Navigateur / LAN

- **Outillage Démarrage ([`start-docker.ps1`](../start-docker.ps1))** :
  - Détection automatique de `docker.exe` sous `Program Files\Docker\Docker\resources\bin` si absent du `PATH`, démarrage automatique de Docker Desktop (`docker desktop start` avec attente jusqu'à `90s`) lorsque le moteur Linux est arrêté, puis exécution de `docker compose up -d --build`.
  - Affichage des URLs locale (`http://localhost/`) et LAN active, avec avertissement si l'IP LAN est absente d'`ARCANE_ALLOWED_ORIGINS` ou si le profil réseau Windows actif est en mode `Public`.
- **Environnement & Documentation** :
  - Ajout de `http://192.168.1.170` et `http://192.168.1.170:4200` à `ARCANE_ALLOWED_ORIGINS` dans `.env` (local non suivi par Git) et recréation de `arcane-server`.
  - Mise à jour de [`docs/INFRASTRUCTURE_AND_SECURITY.md`](./INFRASTRUCTURE_AND_SECURITY.md).
- **Vérifications exécutées** :
  - `.\start-docker.ps1` : `arcane-db` (`healthy`), `arcane-server` (`healthy`) et `arcane-web` actifs (`0.0.0.0:80->8080/tcp`).
  - Sondes HTTP après démarrage : `http://localhost/` et `http://192.168.1.170/` (`200 OK`), `/api/health` (`UP`, `virtualThreadsEnabled: true`), `/api/cards` (`120`), assets `main-NF2JQJUB.js` et `styles-EPYO4WK2.css` (`200 OK`).

---

## [2026-11-16] — Alignement Contrat `LeagueTier` Back/Front & Origines LAN `.env`

- **Corrections Front-End (`frontend/`)** :
  - Alignement 1:1 du type [`LeagueTier`](../frontend/src/app/core/models/arcane.models.ts) et du dictionnaire [`LEAGUE_LABELS`](../frontend/src/app/shared/utils/faction-theme.ts) avec l'énumération autoritaire [`LeagueTier.java`](../backend/src/main/java/com/ordredesarcane/online/user/LeagueTier.java) (`APPRENTI`, `INITIE`, `ADEPTE`, `MAGE`, `ARCHIMAGE`, `GRAND_MAITRE`), supprimant les valeurs désynchronisées (`ADEPT`, `GRAND_ARCHIMAGE`, `MAITRE_ARCANES`) qui laissaient des libellés manquants dans l'en-tête et `/profile`.
  - Ajout d'un test unitaire couvrant les `6` paliers `LeagueTier` dans [`faction-theme.spec.ts`](../frontend/src/app/shared/utils/faction-theme.spec.ts).
- **Environnement local** :
  - Mise à jour de `.env` (ignoré par Git) pour remplacer l'ancienne IP LAN `192.168.0.29` par `192.168.0.46` dans `ARCANE_ALLOWED_ORIGINS`.
- **Vérifications exécutées** :
  - Back-End : `mvn test` (`38` exécutés, `0` échec, `0` erreur, `0` ignoré).
  - Typage & Tests Front-End (`node:24-alpine` + `tsc.cmd --noEmit`) : `ng test --watch=false` (`8` fichiers, `24/24` tests passés) ; `ng build` Docker `arcane-web` (`499.63 kB` initial, `0` erreur).
  - Runtime Docker Compose (`start-docker.ps1`) : `arcane-db` (`healthy`), `arcane-server` (`healthy`, Flyway `V1`→`V4` appliquées, Hibernate 7.4 `ddl-auto: validate` OK, `120` cartes en base) et `arcane-web` actifs ; `http://localhost/` (`200 OK`, CSP active), `/api/health` (`UP`, `virtualThreadsEnabled: true`), `/api/cards` (`120`).
  - Note : le contrôle visuel automatisé via `chrome-devtools-mcp` n'a pas pu s'attacher à Google Chrome (remote debugging `DevToolsActivePort` absent sur le poste).

---

## [2026-11-15] — Préparation Dépôt GitHub, Durcissement `.gitignore` & Template `.env.example`

- **Sécurité & Isolation des Secrets ([`.gitignore`](../.gitignore), [`.env.example`](../.env.example))** :
  - Renforcement de `.gitignore` pour exclure systématiquement `.env`, `.env.*` (tout en préservant `!.env.example`), clés/certificats (`*.pem`, `*.key`, `*.p12`, `*.pfx`, `*.jks`, `*.keystore`), artefacts de build (`backend/target/`, `frontend/node_modules/`, `frontend/dist/`, `frontend/.angular/`, `frontend/coverage/`), journaux et caches IDE/OS.
  - Enrichissement du template versionné [`.env.example`](../.env.example) documentant toutes les variables requises (`POSTGRES_*`, `ARCANE_JWT_SECRET`, `ARCANE_COOKIE_SECURE`, `ARCANE_ALLOWED_ORIGINS`, `WEB_PORT` et surcharges optionnelles `SPRING_DATASOURCE_*`).
  - Généralisation des chemins/IPs locaux personnels dans [`docs/INFRASTRUCTURE_AND_SECURITY.md`](./INFRASTRUCTURE_AND_SECURITY.md), [`docs/CHANGELOG.md`](./CHANGELOG.md), [`AGENTS.md`](../AGENTS.md) et ajout du guide racine [`README.md`](../README.md).
- **Vérifications avant Push** :
  - Back-End : `mvn.cmd test` (`38` exécutés, `0` échec, `0` erreur, `0` ignoré).
  - Front-End : `npm.cmd test -- --watch=false` (`8` fichiers, `23/23` tests passés) et `npm.cmd run build` (`499.66 kB` initial, `0` erreur).

---

## [2026-11-15] — Clarté de Tour, Gameplay par Faction & Chronique Tactique d'Arène

- **Objectif** : rendre l'état de tour immédiatement lisible sans surcharge cognitive, différencier visuellement les territoires et l'ergonomie de jeu selon la faction (`FIRE`, `ICE`, `SHADOW`, `LIGHT`), faciliter le jeu en main et l'attaque directe au clic, et transformer le journal brut de match en Chronique française lisible par un joueur.
- **Architecture & Utilitaires Front-End** :
  - Ajout de [`combat-log-presenter.ts`](../frontend/src/app/shared/utils/combat-log-presenter.ts) (+ [`combat-log-presenter.spec.ts`](../frontend/src/app/shared/utils/combat-log-presenter.spec.ts)) : `presentCombatLogEntries(...)` traduit les identifiants serveur (`TEAM_A`/`TEAM_B`, `ATTACK`/`DEFENSE`, unités de dégâts/PV, `->`) en phrases françaises relatives au joueur (`Vous`, `à vos PV`, `aux PV adverses`), classe chaque entrée par catégorie (`COMBAT`, `DAMAGE`, `DEFENSE`, `HEAL`, `SPELL`, `SUMMON`, `EFFECT`, `TURN`, `MATCH`) et expose le filtre `Temps forts` / `Tout`.
  - Ajout de [`arena-tactical-guide.ts`](../frontend/src/app/shared/utils/arena-tactical-guide.ts) (+ [`arena-tactical-guide.spec.ts`](../frontend/src/app/shared/utils/arena-tactical-guide.spec.ts)) : `evaluateHandCardPlayability(...)` applique côté UI les règles serveur (`4` Monstres, `3` Familiers, `2` Sorts/Artéfacts, `1` Monstre Épique, verrou de sort) avec pastilles non-chromatiques (`Jouable`, `-X ⚡`, `4/4 Plein`, `Verrouillé`) et motifs précis au clic refusé ; `buildTacticalGuidance(...)` unifie le bandeau d'aide contextuel en une ligne.
- **UI & Temps Réel Arène ([`arena-page.component.ts`](../frontend/src/app/features/arena/arena-page.component.ts), [`arena-page.component.html`](../frontend/src/app/features/arena/arena-page.component.html), [`ArcaneCardComponent`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.ts), [`styles.scss`](../frontend/src/styles.scss))** :
  - Correction d'erreurs de fusion HTML préexistantes dans [`arena-page.component.html`](../frontend/src/app/features/arena/arena-page.component.html) et restauration de l'emplacement [`ArcaneAvatarComponent`](../frontend/src/app/shared/components/arcane-avatar/arcane-avatar.component.ts) sur la rive sud (`me`).
  - Indicateurs de tour sans ambiguïté (`À VOUS DE JOUER` / `À VOUS` vs `Tour de ...`), pastilles de faction par territoire (`[data-faction]`, `[data-my-turn]`, `[data-active-turn]`), balise animée de début de tour `.arcane-turn-beacon` (`2100ms`, respectant `prefers-reduced-motion`) avec annonce `AnnouncerService`, clic direct sur une créature `PRÊT` (`[attackerReady]`) en Phase de Combat pour démarrer le ciblage, et onglets `[aria-selected]` pour `Chronique` / `Chat` avec support `forced-colors`.
- **Documentation & Vérifications** :
  - Mise à jour de [`docs/DESIGN_SYSTEM.md`](../docs/DESIGN_SYSTEM.md) (section `4.5`).
  - Front-End (après dernière édition de code) : `ng test --watch=false` (`8` fichiers, `23/23` tests réussis) ; `ng build` (`499.66 kB` initial, `0` erreur/avertissement).
  - Runtime Docker (`docker compose up -d --build arcane-web`) & Chrome DevTools : rendu Live vérifié sur `/arena` à `1440×900` (`scrollWidth === 1440`, `scrollHeight === 900`, `0` overflow horizontal, `0` erreur console) et à `390×844` mobile tactile (`scrollWidth === 390`, `0` overflow horizontal, `0` erreur console), plus validation préalable du flux Phase de Combat au clic sur `PRÊT` et filtre `Chronique`.

---

## [2026-11-15] — Emplacements d'Avatar Joueurs, Projectiles Élémentaires par Deck & Animations de Défense

- **Objectif** : afficher des portraits de mages dédiés à chaque joueur (barre d'en-tête, profil et arène), lancer des projectiles visuels stylisés selon la faction dominante du deck (`primaryFaction`) lors des attaques et sorts directs, et renforcer le feedback défensif à l'encaissement d'impact.
- **Back-End** :
  - Ajout de [`CombatOutcome.java`](../backend/src/main/java/com/ordredesarcane/online/game/model/CombatOutcome.java) et du record public [`CombatVisualEvent.java`](../backend/src/main/java/com/ordredesarcane/online/game/model/CombatVisualEvent.java) (coordonnées de plateau, faction source, posture du défenseur, issue d'impact et dégâts directs/de renvoi, sans fuite d'informations cachées).
  - Ajout de `primaryFaction` sur [`PlayerState.java`](../backend/src/main/java/com/ordredesarcane/online/game/model/PlayerState.java) et [`PlayerBoardViewDto.java`](../backend/src/main/java/com/ordredesarcane/online/game/dto/PlayerBoardViewDto.java) (calculé à l'initialisation dans [`GameEngineService.java`](../backend/src/main/java/com/ordredesarcane/online/game/engine/GameEngineService.java)), et d'un tampon borné à `32` événements visuels sur [`GameRoom.java`](../backend/src/main/java/com/ordredesarcane/online/game/model/GameRoom.java) exposé dans [`PlayerGameView.java`](../backend/src/main/java/com/ordredesarcane/online/game/dto/PlayerGameView.java) via [`FogOfWarProjector.java`](../backend/src/main/java/com/ordredesarcane/online/game/engine/FogOfWarProjector.java).
  - Émission des événements d'attaque, de blocage en défense (`DEFENSE_BLOCKED`, `DEFENSE_BROKEN`, `DIRECT_BLOCKED`, `ATTACKER_REPELLED`) et de sorts directs (`SPELL_STRIKE`) dans [`CombatResolver.java`](../backend/src/main/java/com/ordredesarcane/online/game/engine/CombatResolver.java) et [`AbilityInterpreter.java`](../backend/src/main/java/com/ordredesarcane/online/game/engine/AbilityInterpreter.java).
  - Exposition des 5 avatars de départ (`STARTER_AVATAR_IDS` : `avatar_initiate`, `avatar_pyromancer`, `avatar_cryomancer`, `avatar_shadowmage`, `avatar_luminarch`) dans [`AuthService.java`](../backend/src/main/java/com/ordredesarcane/online/user/AuthService.java) et [`ProfileService.java`](../backend/src/main/java/com/ordredesarcane/online/user/ProfileService.java) avec validation serveur dans `verifyOwned()`.
  - Nouveau test JUnit `combatVisualEventsExposeDeckFactionProjectilesAndDefenseTankOutcomes` dans [`GameEngineTests.java`](../backend/src/test/java/com/ordredesarcane/online/GameEngineTests.java).
- **Front-End & UI** :
  - Nouveau composant [`ArcaneAvatarComponent`](../frontend/src/app/shared/components/arcane-avatar/arcane-avatar.component.ts) intégré dans [`AppHeaderComponent`](../frontend/src/app/shared/components/app-header/app-header.component.html), [`ProfilePageComponent`](../frontend/src/app/features/profile/profile-page.component.html) (avec sélecteur rapide « Emplacement d'Avatar du Mage » accessible `[attr.aria-pressed]` / `[attr.aria-disabled]`) et [`ArenaPageComponent`](../frontend/src/app/features/arena/arena-page.component.html) (adversaires, allié 2v2 et joueur actif, ciblables via `[attr.data-player-avatar]`).
  - Nouveau calque [`ArenaProjectileOverlayComponent`](../frontend/src/app/shared/components/arena-projectile-overlay/arena-projectile-overlay.component.ts) propulsant des projectiles GPU (`FIRE`, `ICE`, `SHADOW`, `LIGHT`) selon `visualEvents` synchronisés par `requestAnimationFrame` et temporisation d'impact (`260ms`, `0ms` si `prefers-reduced-motion: reduce`), avec nettoyage `DestroyRef`.
  - Animations défensives dédiées dans [`ArenaSlotVfxComponent`](../frontend/src/app/shared/components/arena-slot-vfx/arena-slot-vfx.component.ts) (bouclier hexagonal runique `.arcane-vfx-hex-shield`, fissure en cas de destruction, rayons de déflexion `.arcane-vfx-deflect-rays`), [`arena-vfx.ts`](../frontend/src/app/shared/utils/arena-vfx.ts) (`DEFENSE_BLOCK`, `DEFENSE_BROKEN`, `AvatarImpactEvent`, pastilles `ENCAISSÉ · DÉF`, `RENVOI -X PV`, `BRISÉ · DÉF`, `BLOQUÉ`), [`motion-presets.ts`](../frontend/src/app/shared/utils/motion-presets.ts) (`'defense'` et `'attack'`), [`faction-theme.ts`](../frontend/src/app/shared/utils/faction-theme.ts) et [`styles.scss`](../frontend/src/styles.scss).
- **Vérifications** :
  - Back-End : `mvn.cmd test` (`38/38` réussis).
  - Front-End : `npm.cmd test -- --watch=false` (`6` fichiers, `18/18` tests passés) ; `npm.cmd run build` (`485.73 kB` initial, `0` erreur/avertissement CSS).
  - Runtime Docker & Navigateur (`Chrome DevTools`) : reconstruction `arcane-server` / `arcane-web` (`healthy`), `/profile` et `/arena` vérifiés à `1440×900` et `390×844` (`0` overflow horizontal, `0` erreur console), changement d'avatar persisté et télémétrie DOM live confirmée (projectiles `FIRE`, `DEFENSE_BLOCKED`, `DEFENSE_BROKEN`, `SPELL_STRIKE` et impacts d'avatar).

---

## [2026-11-15] — Correctif Chat du Sanctuaire & Chat de Partie (`WebSocket STOMP`)

- **Cause racine** : chaque message envoyé sur `/app/chat.global` ou `/app/chat.game` consommait **deux fois** le jeton de `RateLimiterService.allowChatMessage(...)` (`1 message/seconde`) — d'abord dans `WebSocketSecurityConfig.enforceRateLimit`, puis immédiatement dans `ChatController` — provoquant le rejet systématique dès le premier message. De plus, `ChatController` ne possédait pas de `@MessageExceptionHandler` pour remonter proprement les erreurs de validation ou de limite sur `/user/queue/errors`.
- **Corrections Back-End (`backend/`)** :
  - [`WebSocketSecurityConfig`](../backend/src/main/java/com/ordredesarcane/online/config/WebSocketSecurityConfig.java) : maintien du contrôle d'authentification `PlayerPrincipal` et du rate limiting `/app/game.*` (`5 actions/s`), délégation exclusive de la limite `/app/chat*` (`1 msg/s`) à `ChatController` pour éviter de couper la session WebSocket STOMP en cas de dépassement.
  - [`ChatController`](../backend/src/main/java/com/ordredesarcane/online/lobby/ChatController.java) : extraction du helper `enforceChatRateLimit` et ajout de `@MessageExceptionHandler @SendToUser("/queue/errors")` (`handleChatException`) gérant `MethodArgumentNotValidException` et les rejets métier (`GameErrorEvent` `CHAT_ERROR`).
- **Améliorations Front-End (`frontend/`)** :
  - [`StompService`](../frontend/src/app/core/services/stomp.service.ts) : `sendGlobalChat` et `sendGameChat` retournent désormais un booléen indiquant si l'envoi STOMP a abouti.
  - [`LobbyPageComponent`](../frontend/src/app/features/lobby/lobby-page.component.ts), [`lobby-page.component.html`](../frontend/src/app/features/lobby/lobby-page.component.html) & [`ArenaPageComponent`](../frontend/src/app/features/arena/arena-page.component.ts) : préservation du brouillon saisi et tentative de reconnexion si le WebSocket est hors ligne, défilement automatique de `#globalChatList` (`aria-live="polite" aria-relevant="additions"`), attributs `name="globalChatMessage"` et `enterkeyhint="send"`.
- **Tests, Documentation & Vérifications** :
  - Ajout de [`ChatControllerAndWebSocketTests`](../backend/src/test/java/com/ordredesarcane/online/ChatControllerAndWebSocketTests.java) (`2` tests) et d'un test chat dans [`lobby-page.component.spec.ts`](../frontend/src/app/features/lobby/lobby-page.component.spec.ts).
  - Mise à jour de [`docs/INFRASTRUCTURE_AND_SECURITY.md`](./INFRASTRUCTURE_AND_SECURITY.md).
  - `mvn.cmd test` (`backend/`) : `37` tests exécutés (`0` échec, `0` erreur, `0` ignoré).
  - `npm.cmd test -- --watch=false` (`frontend/`) : `6` fichiers / `17` tests passés (`0` échec).
  - `npm.cmd run build` (`frontend/`) : succès (`Initial total: 464.90 kB`, `0` erreur).
  - Rebuild et redémarrage live (`docker compose up -d --build arcane-server arcane-web`) et vérification Chrome DevTools sur `http://localhost/lobby` (`1440×900` et mobile `390×844`, `0` débordement horizontal, `0` erreur console) : envoi via bouton « Envoyer » et touche `Entrée`, toast `Limite chat dépassée (max 1 message/seconde).` lors d'un envoi < 1 s sans coupure WebSocket (`Temps réel actif`), puis nouvel envoi validé après fenêtre de 1 s.

## [2026-11-15] — IA Mage Automate Stratège : Planification de Combos, Détecteur de Létal & Tactique Avancée

- **Contexte** : refonte tactique et stratégique de `MageAutomateBot` (`HARD` et amélioration `MEDIUM`, tout en gardant `EASY` accessible) avec compréhension réelle des combos multi-cartes des 4 factions, séquençage optimal des actions dans le tour, détecteur de létal multi-sources et appariement combat sans aucune violation du Fog of War.
- **Architecture & Algorithmes (`backend/src/main/java/com/ordredesarcane/online/game/engine/`)** :
  - [`BotComboKnowledge`](../backend/src/main/java/com/ordredesarcane/online/game/engine/BotComboKnowledge.java) : inspection dynamique des capacités du catalogue (`GAIN_ENERGY`, `ON_SPELL_CAST`, `HEALED_THIS_TURN`, `ALLY_FAMILIAR_PRESENT`, `ALLY_MONSTER_PRESENT`, `ALLY_EPIC_MONSTER_PRESENT`, `OPPONENT_HAS_FAMILIAR`), calcul de l'énergie libre déblocable sur le tour, évaluation unifiée de menace adverse et valeur de rétention alliée (exclusivement sur zones visibles publiques et main propre du bot).
  - [`BotLethalSolver`](../backend/src/main/java/com/ordredesarcane/online/game/engine/BotLethalSolver.java) : solveur de létal sur le tour complet hors Trêve Tour 1 combinant déblocages d'énergie gratuits, dissipation de `GARDIEN`/bloqueurs par destruction ou chute à `0 DEF`, buffs d'ATQ, invocations d'attaquants depuis la main, retournements de posture `DEFENSE -> ATTACK`, déclenchement `ON_COMBAT_PHASE_START` (`SHADOW_M_09`), appariement de combat et brûlure directe aux PV (avec amorçage par plus petit attaquant face aux sorts face cachée).
  - [`BotTacticalPlanner`](../backend/src/main/java/com/ordredesarcane/online/game/engine/BotTacticalPlanner.java) & façade [`MageAutomateBot`](../backend/src/main/java/com/ordredesarcane/online/game/engine/MageAutomateBot.java) :
    - Pool unifié en `MAIN_PHASE` ordonnant automatiquement activations gratuites, chaîne soin → `Fée Astrale` (`LIGHT_F_04`) / `Esprit de Braise` (`FIRE_F_05`) / `Lutin Sombre` (`SHADOW_F_02`), invocateurs/familiers prérequis avant payoffs, monstres `ON_SPELL_CAST` avant lancement des sorts, et exploitation de `OPPONENT_HAS_FAMILIAR` (`Démon des Abysses` `SHADOW_M_08`) avant nettoyage AoE.
    - Ciblage double `secondaryTargetId` pour `Dragon des Glaces` (`ICE_M_10`), discipline anti-gaspillage de soin/cleanse/buffs éphémères sous Trêve T1, invocation défensive pour `Golem de Glace` (`ICE_M_09`, immunité sorts en `DEFENSE`) et créatures à `DEF >= ATQ`, gestion économique des réactions et choix modaux (`Boule de Feu Arcanique`, `Mini-Dragon`, `Petit Vampire`, `Bouclier Sacré`, `Voile Sombre`, `Lame Céleste`, `Dragon de Braise d'Aurëa`).
- **Tests, Documentation & Vérifications** :
  - Ajout de 7 tests stratégiques dédiés dans [`MageAutomateBotStrategyTests`](../backend/src/test/java/com/ordredesarcane/online/MageAutomateBotStrategyTests.java).
  - Mise à jour de [`docs/ARCHITECTURE.md`](./ARCHITECTURE.md).
  - `mvn.cmd test` (`backend/`) : `35` tests exécutés (`CatalogAndAntiMetaValidatorTests` 8, `GameEngineTests` 9, `MageAutomateBotStrategyTests` 7, `MatchHistoryTests` 4, `SecurityAndValidationUnitTests` 7), `0` échec, `0` erreur, `0` ignoré.
  - Rebuild et redémarrage live de `arcane-server` (`docker compose up -d --build arcane-server`) : `/api/health` `UP` sur `:8080` et via Nginx `http://localhost/api/health` (`Java 21.0.13`, `virtualThreadsEnabled: true`).

## [2026-11-15] — Illustrations Thématiques par Description & VFX Temps Réel en Arène

- **Contexte** : remplacement de l'icône générique de faction dans le médaillon des cartes par des illustrations SVG thématiques fidèles au nom, à la description et au script d'effets de chacune des 120 cartes, accompagné d'effets visuels temps réel en Arène (`/arena`) lors des dégâts, boucliers, soins, ralentissements, destructions et lancements de sorts.
- **Illustrations Thématiques (`frontend/`)** :
  - Enrichissement de [`card-artwork.ts`](../frontend/src/app/shared/utils/card-artwork.ts) (`CardSubjectKind`, `CardEffectVisualKind`, `CardIllustrationSpec`, `buildCardIllustrationSpec`) tout en conservant `CARD_ARTWORK_REGISTRY` et `resolveCardArtworkUrl` pour l'ajout ultérieur de fichiers `.webp`.
  - Création de [`ArcaneCardArtComponent`](../frontend/src/app/shared/components/arcane-card-art/arcane-card-art.component.ts) (`app-arcane-card-art`) rendant un décor par faction (`FIRE`, `ICE`, `SHADOW`, `LIGHT`), `20` silhouettes dédiées, `12` surcouches visuelles d'effet et le badge `.arcane-art-effect-chip`.
  - Intégration dans [`ArcaneCardComponent`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.html) avec attributs `[attr.data-faction]`/`[attr.data-effect]` et icône sémantique d'effet en vue compacte (`!displayArt`), ainsi que `[showArtwork]="true"` sur l'inspecteur latéral « Détail de la carte » d'Arène.
- **VFX Temps Réel d'Arène (`frontend/`)** :
  - Création de [`arena-vfx.ts`](../frontend/src/app/shared/utils/arena-vfx.ts) (`extractArenaVfxEvents`) et [`ArenaSlotVfxComponent`](../frontend/src/app/shared/components/arena-slot-vfx/arena-slot-vfx.component.ts) (`app-arena-slot-vfx`) calculant les deltas de plateau (`DAMAGE`, `SHIELD`, `ATK_BUFF`, `SLOW`, `STEALTH`, `SUMMON`, `DESTROY`, `CURSE`, `REACTIVE`), les variations de PV d'équipe et les vagues de sorts (`CAST_SPELL`, `REACTION_TRIGGERED`, `ACTIVATE_EFFECT`, `PLAY_PERSISTENT_SPELL`).
  - Raccordement dans [`ArenaPageComponent`](../frontend/src/app/features/arena/arena-page.component.ts) via signaux + timers nettoyés dans `DestroyRef.onDestroy` (`untracked` dans `effect()`), calques `aria-hidden="true"` `pointer-events: none` sur `#enemyLpBox`, `#allyLpBox`, tous les emplacements plateau et `.arcane-vfx-spell-wave`.
  - Ajout des styles/keyframes GPU-composited (`transform`, `opacity`, ressorts `linear(...)` progressifs sous `@supports`, activation conditionnelle `@media (prefers-reduced-motion: no-preference)`) dans [`styles.scss`](../frontend/src/styles.scss) afin de préserver les budgets CSS par composant (`0` dépassement).
- **Tests, Documentation & Vérifications** :
  - Ajout de 5 tests unitaires Vitest dans [`card-artwork-and-vfx.spec.ts`](../frontend/src/app/shared/utils/card-artwork-and-vfx.spec.ts).
  - Mise à jour de [`docs/DESIGN_SYSTEM.md`](./DESIGN_SYSTEM.md) (section `4.4`).
  - `npm.cmd test -- --watch=false` (`frontend/`) : `6` fichiers / `16` tests passés (`0` échec).
  - `npm.cmd run build` (`frontend/`) : succès (`Initial total: 464.89 kB` / `102.73 kB` compressé, `0` erreur TS, `0` avertissement budget ou template).
  - Rebuild live `docker compose up -d --build arcane-web` (`arcane-db`, `arcane-server`, `arcane-web` actifs) et validation visuelle Chrome DevTools (`0` erreur console) : `/deckbuilder` (desktop `1440×900` Feu/Glace + aperçu Grand Format `Golem de Glace` + mobile `390×844` sans débordement horizontal) et duel live `/arena` contre IA `Faible` (`390×844` et `1440×900` zéro scroll, apparition observée des calques `.arcane-vfx-lp-badge` `-2 PV` et `.arcane-vfx-slot` `INVOCATION`).

## [2026-11-15] — Niveaux de Difficulté IA (`Faible` / `Moyen` / `Fort`) pour Mage Automate

- **Contexte** : permet de choisir entre 3 niveaux tactiques (`EASY` / `Faible`, `MEDIUM` / `Moyen` par défaut, `HARD` / `Fort`) avant d'affronter le Mage Automate depuis `/lobby`, avec différenciation réelle côté moteur serveur et badge de niveau dans `/arena`.
- **Back-End (`backend/`)** :
  - Ajout de l'énumération [`AiDifficulty`](file:///c:/dev/momo%20tcg/backend/src/main/java/com/ordredesarcane/online/game/model/AiDifficulty.java) (`EASY`, `MEDIUM`, `HARD`) propagée dans [`StartAiMatchRequest`](file:///c:/dev/momo%20tcg/backend/src/main/java/com/ordredesarcane/online/lobby/dto/StartAiMatchRequest.java), [`PlayerState`](file:///c:/dev/momo%20tcg/backend/src/main/java/com/ordredesarcane/online/game/model/PlayerState.java), [`GameEngineService.MatchParticipantSpec`](file:///c:/dev/momo%20tcg/backend/src/main/java/com/ordredesarcane/online/game/engine/GameEngineService.java), [`PlayerBoardViewDto`](file:///c:/dev/momo%20tcg/backend/src/main/java/com/ordredesarcane/online/game/dto/PlayerBoardViewDto.java), [`FogOfWarProjector`](file:///c:/dev/momo%20tcg/backend/src/main/java/com/ordredesarcane/online/game/engine/FogOfWarProjector.java), [`MatchParticipantBuilder`](file:///c:/dev/momo%20tcg/backend/src/main/java/com/ordredesarcane/online/lobby/MatchParticipantBuilder.java) et [`LobbyController`](file:///c:/dev/momo%20tcg/backend/src/main/java/com/ordredesarcane/online/lobby/LobbyController.java) (`/api/lobby/ai-match` ; en `TEAM_2V2` + `HARD`, l'allié Automate reste en `MEDIUM` pour préserver le défi).
  - Spécialisation tactique dans [`MageAutomateBot`](file:///c:/dev/momo%20tcg/backend/src/main/java/com/ordredesarcane/online/game/engine/MageAutomateBot.java) (respect strict du Fog of War sans inspection des cartes cachées) : mulligan adaptatif, priorité au létal et évitement des gaspillages de buffs temporaires sous Trêve T1 ou d'anti-sort sur cibles immunisées, trades favorables / ouverture au `SURVOL`, timing réactif et défausse utilitaire.
  - Tests JUnit ajoutés dans [`GameEngineTests`](file:///c:/dev/momo%20tcg/backend/src/test/java/com/ordredesarcane/online/GameEngineTests.java) et [`SecurityAndValidationUnitTests`](file:///c:/dev/momo%20tcg/backend/src/test/java/com/ordredesarcane/online/SecurityAndValidationUnitTests.java).
- **Front-End (`frontend/`)** :
  - Extension des types/helpers ([`arcane.models.ts`](file:///c:/dev/momo%20tcg/frontend/src/app/core/models/arcane.models.ts), [`faction-theme.ts`](file:///c:/dev/momo%20tcg/frontend/src/app/shared/utils/faction-theme.ts), [`lobby-api.service.ts`](file:///c:/dev/momo%20tcg/frontend/src/app/core/services/lobby-api.service.ts)).
  - Ajout du sélecteur segmenté accessible `<fieldset>`/`<legend>` + `<input type="radio">` (`.arcane-seg-group`, `.arcane-seg-option`, `.arcane-sr-radio` dans [`styles.scss`](file:///c:/dev/momo%20tcg/frontend/src/styles.scss), cibles tactiles `>= 44px`, support `forced-colors` et focus `:has(input:focus-visible)`) dans [`LobbyPageComponent`](file:///c:/dev/momo%20tcg/frontend/src/app/features/lobby/lobby-page.component.html) et badge `IA · Faible` / `IA · Moyen` / `IA · Fort` dans [`ArenaPageComponent`](file:///c:/dev/momo%20tcg/frontend/src/app/features/arena/arena-page.component.html).
  - Test Vitest ajouté dans [`lobby-page.component.spec.ts`](file:///c:/dev/momo%20tcg/frontend/src/app/features/lobby/lobby-page.component.spec.ts).
- **Vérifications passées** :
  - `mvn.cmd test` (`backend/`) : `28` tests exécutés, `0` échec, `0` erreur (`CatalogAndAntiMetaValidatorTests` 8, `GameEngineTests` 9, `MatchHistoryTests` 4, `SecurityAndValidationUnitTests` 7).
  - `npm.cmd test -- --watch=false` (`frontend/`) : `5` fichiers / `11` tests passés.
  - `npm.cmd run build` (`frontend/`) : succès (`Initial total: 457.55 kB`, `0` erreur/avertissement budget).
  - Runtime Docker (`arcane-db`, `arcane-server` `/api/health` UP Java `21.0.13`, `arcane-web`) et contrôle visuel Chrome DevTools sur `http://localhost:4200` (desktop `1440×900` + émulation tactile mobile `390×844` sans débordement sur `/lobby` et `/arena`) ainsi que `http://localhost/lobby` (`0` message/erreur console).

## [2026-11-15] — Script Front `start:lan` (`0.0.0.0:4200`) pour Tests Mobiles sur Réseau Local

- **Configuration Dev Angular ([`frontend/package.json`](../frontend/package.json))** :
  - Ajout du script `"start:lan": "ng serve --host 0.0.0.0 --port 4200 --allowed-hosts"` exposant le serveur Vite/Angular sur toutes les interfaces IPv4 locales (`0.0.0.0:4200`) avec proxy `/api` et `/ws` vers `http://localhost:8080` ([`proxy.conf.json`](../frontend/proxy.conf.json)).
- **Documentation & Vérifications** :
  - [`docs/INFRASTRUCTURE_AND_SECURITY.md`](./INFRASTRUCTURE_AND_SECURITY.md) complété (usage `npm run start:lan` et ajout de l'origine `http://<IP_LAN>:4200` dans `ARCANE_ALLOWED_ORIGINS` pour l'authentification REST et STOMP).
  - Démarrage effectif testé avec réponse `HTTP 200` sur l'IPv4 locale (`<IP_LAN>:4200`), tests Vitest (`5 fichiers / 10 tests`) et `ng build` vérifiés sans régression.

## [2026-11-15] — Modernisation UI/UX Dark-Fantasy, Cockpit Arène Mobile Zéro-Scroll & Validation Chrome DevTools

- **Direction artistique & Système de Design ([`styles.scss`](../frontend/src/styles.scss), [`arcane-card.component.{html,scss}`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.html), [`app-header.component.html`](../frontend/src/app/shared/components/app-header/app-header.component.html), [`app.html`](../frontend/src/app/app.html))** :
  - Sélecteurs `select.arcane-input` à chevron doré SVG (`appearance: none`), retour tactile `.arcane-btn:active` (`scale(0.97)`), balises de connexion animées (`.arcane-status-dot`), cartes de modes `.arcane-mode-card`, séparateur de front `.arcane-clash-line`, tiroir mobile `.arcane-mobile-drawer` & fiche `.arcane-quick-sheet` (`@starting-style` + `transition-behavior: allow-discrete`), lueur `.arcane-card-ready`.
  - **Micro-jetons Arène mobile** (`@container arcane-card (max-width: 6.75rem)` dans `arcane-card.component.scss` + classes dédiées dans `arcane-card.component.html`) : affichage propre sans collision ni rognage des créatures jusque dans les grilles `5` colonnes (`F1`–`F3`, `62 px` de large sur mobile).
- **Vues Sanctuaire, Grimoire, Tournois, Profil & Auth ([`auth-page.component.{ts,html}`](../frontend/src/app/features/auth/auth-page.component.html), [`lobby-page.component.{ts,html}`](../frontend/src/app/features/lobby/lobby-page.component.html), [`deckbuilder-page.component.{ts,html}`](../frontend/src/app/features/deckbuilder/deckbuilder-page.component.html), [`tournaments-page.component.{ts,html}`](../frontend/src/app/features/tournaments/tournaments-page.component.html), [`profile-page.component.{ts,html}`](../frontend/src/app/features/profile/profile-page.component.html))** :
  - Remplacement des glyphes texte bruts par `@ng-icons/lucide`, ajout d'un blason astrolabe SVG sur `/auth`, sélecteurs de lancement tactiques + aperçu de deck sélectionné + cartes mobiles d'historique sur `/lobby`, rail horizontal de decks sur `/deckbuilder`, jauges de maîtrise par faction + filtres swipeables sur `/profile`.
- **Cockpit Arène Mobile-First & Nettoyage du template ([`arena-page.component.{ts,scss,html}`](../frontend/src/app/features/arena/arena-page.component.html))** :
  - Suppression de fragments dupliqués dans `arena-page.component.html` et rétablissement complet des conteneurs `#arenaGrid`, `.arena-play-col`, `.arena-battlefield`, `.arena-side-col` et du bouton d'attaque directe (`directAttackStatus` / `onSelectDirectAttack`).
  - Verrouillage universel `100svh` sur `/arena` (en-tête global replié sous `< xl`), terrains `4`/`5` colonnes visibles simultanément, barre d'actions rapides tactile non-occlusive (`Attaquer`, `Posture`, `Effet`, lecteur grand format `openMobileReader`, `clearInspector`), dock inférieur à portée du pouce (`ATQ`/`DÉF`, `Combat`, `Fin Tour` + `.arcane-hand-rail`), tiroir latéral coulissant `Journal/Chat/Options` (`mobileDrawerOpen`) et carrousels horizontaux dans `#mulliganDialog` et `#discardDialog`.
- **Documentation & Vérifications exécutées** :
  - `docs/DESIGN_SYSTEM.md` mis à jour (Section 3 cockpit mobile/desktop et micro-jetons).
  - Tests front (`cd frontend; npm.cmd test -- --watch=false`) après les dernières modifications de code : **5 fichiers / 10 tests passés** (`0` échec).
  - Build production (`cd frontend; npm.cmd run build`) : **succès (`0` erreur TS, `0` dépassement budget CSS, `Initial total: 452.79 kB` / `100.67 kB` compressé)** puis reconstruction `docker compose up -d --build arcane-web`.
  - Contrôle Chrome DevTools (`1440×900` Desktop & `390×844` Mobile Tactile sur `/auth`, `/lobby`, `/deckbuilder`, `/tournaments`, `/profile`, Mulligan, Défausse excédentaire, Tiroir Tactique, Lecteur grand format et Arène active) : `scrollWidth === innerWidth` partout, `scrollHeight === innerHeight` (`844px`) en Arène mobile (`229px` de marge verticale restante barre d'actions ouverte), `0` erreur console en session authentifiée (`1` requête `401` initiale attendue sur `/auth` invité).

## [2026-11-15] — Uniformisation des Cartes & Alignement CSS `subgrid` du Catalogue (`/deckbuilder`)

- **Objectif** : éliminer les hauteurs de cartes inégales et le décalage vertical des boutons `+ Ajouter` / aperçu entre monstres, sorts et descriptions de longueur variable dans le Grimoire, ainsi qu'un débordement/retour à la ligne dans la modale d'aperçu.
- **Améliorations UI/CSS** :
  - **Grille catalogue `subgrid`** ([`styles.scss`](../frontend/src/styles.scss), [`deckbuilder-page.component.html`](../frontend/src/app/features/deckbuilder/deckbuilder-page.component.html)) : ajout des classes `.arcane-catalog-grid` et `.arcane-catalog-tile` (`grid-row: span 2`, fallback `1fr auto`, puis `grid-template-rows: subgrid`), avec étirement vertical (`h-full`) de `<app-arcane-card>` sur chaque tuile.
  - **Anatomie uniforme des cartes pleines** ([`arcane-card.component.ts`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.ts), [`arcane-card.component.html`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.html), [`arcane-card.component.scss`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.scss)) : ajout de `.arcane-card-standard` (`min-block-size: 20.5rem`), boîte de règles extensible (`flex-1`) bornée à 4 lignes (`.arcane-line-clamp-4` désactivable via `[unclampDescription]="true"` dans `#cardPreviewDialog`), badges `whitespace-nowrap`, et pied de carte systématique (capsules `ATQ`/`DÉF` ou sceau `Faction` + `Type` sur les Sorts, Malédictions et Artéfacts). Correction des balises HTML résiduelles du template et conservation de la hauteur intrinsèque sur `:host` par défaut pour éviter le chevauchement mobile dans la modale d'aperçu.
  - **Modale d'aperçu 3D/Foil** ([`deckbuilder-page.component.html`](../frontend/src/app/features/deckbuilder/deckbuilder-page.component.html)) : passage à `max-w-72 flex flex-col gap-2.5` et `whitespace-nowrap` sur `Précédente` / `Foil` / `Suivante`.
- **Documentation** : mise à jour de [`docs/DESIGN_SYSTEM.md`](./DESIGN_SYSTEM.md).
- **Vérifications exécutées** :
  - Front-End Vitest : `cd frontend; npm.cmd test -- --watch=false` → `5 passed (5)`, `10 passed (10)`.
  - Front-End Build : `cd frontend; npm.cmd run build` → `Initial total: 437.92 kB` (`101.02 kB` transfer), `deckbuilder-page-component: 30.40 kB`, `0` warning CSS / TS.
  - Runtime Docker & Inspection Chrome DevTools (`http://localhost/deckbuilder`) : reconstruction `docker compose up -d --build arcane-web` OK ; desktop `1440×900` (`120` cartes / `30` rangées, `0` rangée désalignée, hauteur uniforme `328px` vérifiée sur `Golem de Glace`, `Dragon des Glaces`, `Givre Protecteur` et `Chaînes de Glace`) ; mobile tactile `390×844` (`0` débordement horizontal, boutons tactiles >= 44px hors lien d'évitement, modale d'aperçu sans chevauchement, `0` erreur console).

---

## [2026-11-15] — Refonte Visuelle « Obsidienne Runique & Laiton Doré » (`ArcaneCard`, `/lobby` Sanctuaire, `/arena` Arène)

### Ajouté & Amélioré
- **Primitives Design System ([`styles.scss`](../frontend/src/styles.scss) & [`card-artwork.ts`](../frontend/src/app/shared/utils/card-artwork.ts))** :
  - Ajout des surfaces sculptées `.arcane-panel` (reflet laiton interne), `.arcane-panel-hero`, `.arcane-card-frame`, `.arcane-card-selected` / `.arcane-card-targetable` (sélection par `outline` + `box-shadow` sans saut de mise en page, avec repli `@media (forced-colors: active)`), `.arcane-orb-energy`, `.arcane-arena-north`, `.arcane-arena-south` et `.arcane-slot-empty` / `.arcane-slot-empty--active`.
  - Enrichissement de `getProceduralArtTheme()` (`accentHex` et halos de faction affinés dans `FACTION_ART_COLORS`).
- **Cartes à Collectionner ([`ArcaneCardComponent`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.ts))** :
  - Activation de `container-type: inline-size; container-name: arcane-card` sur `:host` ([`arcane-card.component.scss`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.scss)) pour masquer proprement `.arcane-emblem-label` et `.arcane-posture-text` sous `12.5rem` sans chevaucher le sigil central ni rogner les titres en grilles denses (Mulligan 5 colonnes, emplacements de plateau).
  - Dos Arcanes géométrique (`.arcane-cardback`), bordures doubles biseautées, orbe d'énergie doré `.arcane-orb-energy`, capsules stats `ATQ`/`DÉF` en `.arcane-num`, entrée `showPosture` (`false` hors plateau en main/Mulligan/défausse) et migration totale des indicateurs vers `@ng-icons/lucide` (`lucideArrowUp`, `lucideCheck`, `lucideClock`, `lucideEyeOff`, `lucideRotateCw`, `lucideSwords`, `lucideShield`, `lucideSkull`, `lucideSparkles`, `lucideZap`, `lucideCrown`).
- **Sanctuaire ([`lobby-page.component.{ts,html}`](../frontend/src/app/features/lobby/lobby-page.component.html))** :
  - Bannière active et panneau d'amorce `.arcane-panel-hero` avec hiérarchie claire (`File Classée` laiton doré, `Duel Amical` céruléen, `Affronter Mage Automate (IA)` émeraude), alerte accessible si aucun deck conforme n'est sélectionné, salons personnalisés `.arcane-well`, champ `#global-chat-input` recalibré à `min-h-11` sans troncature de ligne, lien `Voir mon profil` tactile (`>= 44px`), et suppression complète des émojis structurels au profit de `@ng-icons/lucide`.
- **Arène Temps Réel ([`arena-page.component.{ts,html}`](../frontend/src/app/features/arena/arena-page.component.html))** :
  - Distinction atmosphérique entre plateau adverse (`.arcane-arena-north` cramoisi subtil) et cité alliée (`.arcane-arena-south` laiton-obsidienne), écus PV sculptés, chrono La Corde dégradé, sélecteur segmenté de posture d'invocation (`Attaque` / `Défense (90°)`), emplacements vides `.arcane-slot-empty`, boutons d'actions tactiles (`.arena-action-mini`), uniformisation des 7 `<dialog class="arcane-dialog">` et migration complète vers `@ng-icons/lucide`.
- **Documentation** : mise à jour de [`docs/DESIGN_SYSTEM.md`](./DESIGN_SYSTEM.md) (sections 3, 4 et 5).

### Vérifié
- Frontend Vitest (`npm.cmd test -- --watch=false`) : `5` fichiers / `10` tests passés.
- Build production Angular (`npm.cmd run build`) : `Initial total 437.96 kB` (`100.92 kB` transférés), `0` erreur TypeScript, `0` avertissement de budget CSS.
- Runtime Docker `arcane-web` & contre-visite visuelle Chrome DevTools (`0` erreur/avertissement console) :
  - **Desktop (`1440×900`)** : `/lobby`, `/deckbuilder` (cadres `ArcaneCardComponent`), Mulligan 5 cartes, Arène active tour 7 avec créatures ennemies posées + inspecteur latéral et verrouillage cockpit exact sans scroll (`scrollW: 1440`, `scrollH: 900`).
  - **Mobile tactile (`390×844x2`, `pointer: coarse`)** : `/lobby` (`scrollW === 390`, `smallTouchCount === 0`) et `/arena` (`scrollW === 390`, `headerH: 61px`, `smallTouchCount === 0`).

---

## [2026-11-15] — Ergonomie Mobile-First, Tactile (`44px`) & Mise à Jour des Skills

### Ajouté & Amélioré
- **Skills Projet & Portes de Vérification** :
  - [`arcane-ui-design-studio`](../.agents/skills/arcane-ui-design-studio/SKILL.md), [`tailwind-design-system`](../.agents/skills/tailwind-design-system/SKILL.md), [`game-ui-design`](../.agents/skills/game-ui-design/SKILL.md) et [`AGENTS.md`](../AGENTS.md) enrichis des standards Mobile-First (`320px+`, `@media (pointer: coarse)`, cibles `>= 44×44px`, `safe-area-inset-*`, `dvh`/`dvw`, confinement `min-w-0` sur grilles, et contrôle Chrome DevTools obligatoire en `1440×900` et `390×844` tactile).
- **Socle Global & En-tête Compact** :
  - [`index.html`](../frontend/src/index.html) (`viewport-fit=cover`), [`styles.scss`](../frontend/src/styles.scss) (`--arcane-target-size` `2.5rem` -> `2.75rem` / `44px` sous `@media (pointer: coarse)`, `.arcane-btn`, `.arcane-safe-top`, `.arcane-safe-bottom`, `overflow-wrap: anywhere`, `text-wrap: balance`/`pretty`, `max-inline-size: 100%`) et [`app.html`](../frontend/src/app/app.html) (toasts ancrés sur `env(safe-area-inset-bottom)`).
  - [`app-header.component.{ts,html}`](../frontend/src/app/shared/components/app-header/app-header.component.html) : barre compacte (`61px` repliée sur mobile), un seul `<nav aria-label="Navigation principale">` avec `ariaCurrentWhenActive="page"`, tiroir basculé via `lucideMenu`/`lucideX`, fermeture `Escape` et `NavigationEnd` (`takeUntilDestroyed()`), et lien logo `min-h-11`.
- **Vues Métier Mobile-First** :
  - **Grimoire ([`deckbuilder-page.component.{ts,html}`](../frontend/src/app/features/deckbuilder/deckbuilder-page.component.html))** : synthèse Anti-Méta et bouton d'enregistrement priorisés au-dessus du catalogue sur `< lg` (`order-1 lg:order-2`), liste des cartes repliable (`showDraftListOnMobile`), grille fluide `1 -> 4` colonnes, boutons `.arcane-btn` (`>= 44px`), modale `max-h-[90dvh]` et confinement `min-w-0`.
  - **Arène ([`arena-page.component.{scss,html}`](../frontend/src/app/features/arena/arena-page.component.html))** : confinement `min-inline-size: 0` sur colonnes/rail, `.arena-action-mini` à `2.75rem` sous `pointer: coarse`, compteurs PV `whitespace-nowrap`, emplacements `grid-cols-2` sous `sm`, rail horizontal aimanté `.arena-hand-rail` (`overscroll-behavior-x: contain`) sous `xl`, fiche tactile d'aperçu escamotable (`xl:hidden` quand `!pendingAction()`) et bouton sticky dans `#mulliganDialog`.
  - **Sanctuaire, Tournois & Portail ([`lobby-page`](../frontend/src/app/features/lobby/lobby-page.component.html), [`tournaments-page`](../frontend/src/app/features/tournaments/tournaments-page.component.html), [`auth-page`](../frontend/src/app/features/auth/auth-page.component.html))** : confinement `min-w-0`, CTA empilés `< sm`, `min-h-dvh`, et région scrollable clavier (`tabindex="0" role="region"`) pour l'historique récent des duels.
- **Documentation** : mise à jour de [`docs/DESIGN_SYSTEM.md`](./DESIGN_SYSTEM.md) (Section 3 — Socle Mobile-First & Tactile).

### Vérifié
- Frontend Vitest (`npm.cmd test -- --watch=false`) : `5` fichiers / `10` tests passés.
- Build production Angular (`npm.cmd run build`) : `Initial total 419.10 kB` (`99.25 kB` transférés), `0` erreur TypeScript, `0` avertissement de budget CSS.
- Runtime Docker `arcane-web` & Chrome DevTools sans aucune erreur console :
  - **Mobile tactile (`390×844x2`, `pointer: coarse`)** : `scrollWidth === 390` (zéro débordement horizontal), `headerHeight: 61px` (`navCount: 1`, fermeture `Escape`), `smallTargetsCount: 1` (uniquement le lien masqué d'évitement `1×1` — `0` contrôle visible `< 44px`), ordre `<aside>` avant catalogue sur `/deckbuilder` (`asideTop: 461 < catalogColTop: 929`), rail main `344px` (`sw: 872`) + fiche tactile `CARTE SÉLECTIONNÉE` sur `/arena`.
  - **Desktop (`1440×900`)** : navigation inline (`66px`), `aside` rétabli à droite sur `/deckbuilder`, Arène verrouillée sans scroll vertical (`900 === 900`) et zéro débordement horizontal.

---

## [2026-11-15] — Animations GPU, Iconographie Lucide, Illustrations Procédurales & Aperçu de Cartes

### Ajouté & Amélioré
- **Bibliothèques Frontend CSP-Safe (`0` vulnérabilité)** :
  - [`@ng-icons/core`](../frontend/package.json) (`^36.1.0`) + `@ng-icons/lucide` (`^36.1.0`) configurés avec `provideNgIconsConfig({ strokeWidth: 1.75 }, withContentSecurityPolicy())` dans [`app.config.ts`](../frontend/src/app/app.config.ts) (`lucide-angular` écarté pour incompatibilité peer avec `@angular/core ^22.2.0`).
  - `motion` (`^14.14.0`, API `motion/mini`) pour les cascades d'entrée de grille GPU dans [`motion-presets.ts`](../frontend/src/app/shared/utils/motion-presets.ts), en complément de `gsap` (`^3.15.1`).
  - `canvas-confetti` (`^1.9.4` + `@types/canvas-confetti@^1.9.0`) en import dynamique paresseux sur victoire (`0` o dans le bundle initial).
- **Transitions & Micro-Animations (`prefers-reduced-motion` respecté)** :
  - Activation de `withViewTransitions()` dans [`app.config.ts`](../frontend/src/app/app.config.ts) et des transitions douces dans [`styles.scss`](../frontend/src/styles.scss).
  - Cascade de cartes `animateCardGridEntrance()` dans [`deckbuilder-page.component.ts`](../frontend/src/app/features/deckbuilder/deckbuilder-page.component.ts) (plafond 16 cartes + garde JSDOM).
  - Impulsions PV `pulseElementImpact()` et salve `launchVictoryConfetti()` avec nettoyage `DestroyRef` dans [`arena-page.component.ts`](../frontend/src/app/features/arena/arena-page.component.ts) et [`arena-page.component.html`](../frontend/src/app/features/arena/arena-page.component.html).
- **Illustrations Procédurales Prêtes à Remplacer & Aperçu Grand Format** :
  - [`card-artwork.ts`](../frontend/src/app/shared/utils/card-artwork.ts) (`CARD_ARTWORK_REGISTRY`, `resolveCardArtworkUrl()`, `getProceduralArtTheme()`) et [`arcane-card.component.{ts,html,scss}`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.ts) : médaillon SVG par faction/type sans requête 404, inclinaison GPU (`interactiveTilt` `±7deg`), reflet Foil animé (`7s` sous `prefers-reduced-motion: no-preference`) et bascule automatique vers `<img>` dès ajout de visuels dans `frontend/public/cards/`.
  - Modale native `<dialog class="arcane-dialog">` d'aperçu grand format (bascule Foil/Standard, navigation Précédent/Suivant, caractéristiques complètes) dans [`deckbuilder-page.component.{ts,html}`](../frontend/src/app/features/deckbuilder/deckbuilder-page.component.html) + inspecteur compact illustré dans [`arena-page.component.html`](../frontend/src/app/features/arena/arena-page.component.html).
  - Migration SVG Lucide dans [`app-header.component.{ts,html}`](../frontend/src/app/shared/components/app-header/app-header.component.html), [`lobby-page.component.{ts,html}`](../frontend/src/app/features/lobby/lobby-page.component.html), [`arcane-card`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.html) et [`arena-page`](../frontend/src/app/features/arena/arena-page.component.html).
- **Documentation** : mise à jour de [`docs/DESIGN_SYSTEM.md`](./DESIGN_SYSTEM.md) (sections 4 et 5).

### Vérifié
- Frontend Vitest (`npm.cmd test -- --watch=false`) : `5` fichiers / `10` tests passés.
- Build production Angular (`npm.cmd run build`) : `411.01 kB` initial (`97.75 kB` transférés), chunk `confetti-module` (`10.68 kB` / `3.88 kB`), `0` avertissement de budget CSS.
- Sécurité dépendances (`npm.cmd audit`) : `0` vulnérabilité (`321` paquets audités dans Docker `npm ci`).
- Runtime Docker `arcane-web` & Chrome DevTools (`/lobby`, `/deckbuilder` + modale `#cardPreviewDialog` Foil, `/arena/:matchId` 1v1 IA en `1920x897`) : zéro scroll de page en arène (`docScrollHeight === docClientHeight`), zéro erreur/avertissement console et retour au Sanctuaire validé.

---

## [2026-11-15] — Thème « Obsidienne Runique & Laiton Doré », Typographie CSP-Safe & Refonte des Skills

### Ajouté & Amélioré
- **Skills (`omer-metin/skills-for-antigravity`)** :
  - **Globaux (`~/.gemini/config/skills/`)** : ajout de `design-systems`, `color-theory`, `typography`, `motion-design`, `icon-design` (avec `references/`) ; refonte de `code-refactoring-refactor-clean` fusionnant qualité logicielle et guide de refactoring.
  - **Projet (`.agents/skills/`)** : ajout de `game-ui-design`, `card-game-design`, `angular` (Angular 22 standalone + signals + `OnPush`) ; refonte de `wcag-audit-patterns` et `tailwind-design-system` (migration Tailwind CSS v4.3 `@theme` et `bg-linear-to-*`).
  - Mise à jour du routage [`AGENTS.md`](../AGENTS.md) (`4 822` octets) et de [`arcane-ui-design-studio`](../.agents/skills/arcane-ui-design-studio/SKILL.md).
- **Thème visuel « Obsidienne Runique & Laiton Doré »** :
  - **Polices auto-hébergées (`font-src 'self' data:`)** : [`cinzel-latin.woff2`](../frontend/public/fonts/cinzel-latin.woff2), [`cinzel-latin-ext.woff2`](../frontend/public/fonts/cinzel-latin-ext.woff2), [`plus-jakarta-sans-latin.woff2`](../frontend/public/fonts/plus-jakarta-sans-latin.woff2) et [`plus-jakarta-sans-latin-ext.woff2`](../frontend/public/fonts/plus-jakarta-sans-latin-ext.woff2) déclarées par `@font-face` local dans [`styles.scss`](../frontend/src/styles.scss).
  - **Tokens 3-tiers & Tailwind v4 `@theme`** : primitives `--arcane-palette-*`, tokens sémantiques de profondeur (`void #060811`, `primary #0a0e1a`, `recessed #080c17`, `surface #121829`, `elevated #1b233a`), encre parchemin (`#f4efe6`), laiton bruni (`#d9a441` / `#f0c96e`), classes réutilisables (`.arcane-display`, `.arcane-num`, `.arcane-panel`, `.arcane-well`) dans [`styles.scss`](../frontend/src/styles.scss) et [`tailwind.css`](../frontend/src/tailwind.css).
  - **Séparation chromatique des factions** : refonte de [`faction-theme.ts`](../frontend/src/app/shared/utils/faction-theme.ts) (`FIRE` orange braise `#f97316` distinct du danger `#fb7185`, `LIGHT` champagne astral `#fef08a` distinct du laiton CTA `#d9a441`, dégradés `bg-linear-to-*`) et harmonisation typographique des vues [`app.html`](../frontend/src/app/app.html), [`app-header`](../frontend/src/app/shared/components/app-header/app-header.component.html), [`auth-page`](../frontend/src/app/features/auth/auth-page.component.html), [`lobby-page`](../frontend/src/app/features/lobby/lobby-page.component.html), [`profile-page`](../frontend/src/app/features/profile/profile-page.component.html), [`arcane-card`](../frontend/src/app/shared/components/arcane-card/arcane-card.component.html) et [`arena-page`](../frontend/src/app/features/arena/arena-page.component.html).
- **Documentation** : mise à jour complète de [`docs/DESIGN_SYSTEM.md`](./DESIGN_SYSTEM.md) avec la matrice de contrastes WCAG vérifiés.

### Vérifié
- Audit frontmatter & liens des `30` skills (`15` globaux + `15` projet) et `5` docs (`AGENTS.md` + `docs/*.md`) : `0` erreur.
- Frontend Vitest (`npm.cmd test -- --watch=false`) : `5` fichiers / `10` tests passés.
- Build production Angular (`npm.cmd run build`) : `388.04 kB` initial (`90.75 kB` transférés), `0` avertissement CSS, 4 fichiers `.woff2` empaquetés dans `dist/frontend/browser/fonts/`.
- Docker Compose (`arcane-web` reconstruit) & inspection Chrome DevTools (`/profile`, `/auth`, `/deckbuilder`, `/lobby`) : chargement effectif de `Cinzel:600 700` et `Plus Jakarta Sans:400 700`, `color-scheme: dark`, `tabular-nums`, `0` erreur CSP/console sur vues authentifiées (`401` attendu sur `/auth` anonyme).

---

## [2026-11-15] — Skills Personnalisés, Skills Communautaires & Documentation Vivante

### Ajouté
- **Documentation vivante (`docs/`)** : [`ARCHITECTURE.md`](./ARCHITECTURE.md), [`INFRASTRUCTURE_AND_SECURITY.md`](./INFRASTRUCTURE_AND_SECURITY.md), [`DESIGN_SYSTEM.md`](./DESIGN_SYSTEM.md) et [`CHANGELOG.md`](./CHANGELOG.md).
- **Règles projet toujours actives** : [`AGENTS.md`](../AGENTS.md) (invariants clean code, sécurité, non-régression, mise à jour incrémentale de la documentation).
- **Skills personnalisés projet (`.agents/skills/`)** :
  - `arcane-architecture-and-docs`
  - `arcane-infra-security-ops`
  - `arcane-game-engine-and-realtime`
  - `arcane-ui-design-studio`
- **Skills externes sélectionnés** :
  - **Globaux (`~/.gemini/config/skills/`)** : `frontend-design`, `ui-ux-pro-max`, `ui-visual-validator`, `code-refactoring-refactor-clean`, `code-review-excellence`, `performance-engineer`, `architecture-patterns`, `backend-security-coder`, `frontend-security-coder`.
  - **Projet (`.agents/skills/`)** : `tailwind-design-system`, `wcag-audit-patterns`, `postgresql`, `sql-optimization-patterns`, `database-migrations-sql-migrations`, `java-pro`, `javascript-testing-patterns`, `e2e-testing-patterns`.

---

## [2026-11-15] — Historique des Matchs Joueur, Statistiques & Migration Flyway V4

### Ajouté
- **Base de données** : migration [`V4__player_match_history.sql`](../backend/src/main/resources/db/migration/V4__player_match_history.sql) ajoutant `is_custom_room`, `match_source`, `tournament_id`, `tournament_name`, `match_summary` sur `matches`, créant la table `match_participants` avec index, et rétro-remplissant les parties existantes.
- **Back-End** : [`MatchSource`](../backend/src/main/java/com/ordredesarcane/online/match/MatchSource.java), [`MatchParticipantEntity`](../backend/src/main/java/com/ordredesarcane/online/match/MatchParticipantEntity.java), [`PlayerMatchHistoryService`](../backend/src/main/java/com/ordredesarcane/online/user/PlayerMatchHistoryService.java), endpoint `GET /api/profile/matches`, enrichissement de [`MatchPersistenceService`](../backend/src/main/java/com/ordredesarcane/online/match/MatchPersistenceService.java) et [`LobbyController`](../backend/src/main/java/com/ordredesarcane/online/lobby/LobbyController.java), tests [`MatchHistoryTests`](../backend/src/test/java/com/ordredesarcane/online/MatchHistoryTests.java).
- **Front-End** : bloc **Statistiques & Ratio**, filtres par provenance (`Tous`, `Classé`, `Tournoi`, `Salon`, `Amical`, `IA`) et accordéons d'aperçu léger sur `/profile`, badges de provenance dans le Sanctuaire, tests Vitest sur `/profile` et `formatDurationCompact`.

### Vérifié
- Backend Maven : `25` tests passés (`0` échec).
- Frontend Vitest : `5` fichiers / `10` tests passés ; `ng build` OK (`379.80 kB`).
- Runtime Docker (`arcane-db`, `arcane-server`, `arcane-web`), insertion DB en direct et interface Chrome `/profile` validés sans erreur console.

---

## [2026-11-15] — Ergonomie Arène Zero-Scroll & Navigation Post-Forfait

### Modifié
- Verrouillage cockpit desktop `100dvh` dans [`styles.scss`](../frontend/src/styles.scss) et [`arena-page.component.scss`](../frontend/src/app/features/arena/arena-page.component.scss) pour supprimer le défilement de page en duel.
- Correction de la navigation « Retour au Sanctuaire » après fin de duel/forfait dans l'arène.
