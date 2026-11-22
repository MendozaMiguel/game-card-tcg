# Journal des Évolutions (Changelog) — L'Ordre des Arcanes Online

> **Règle de maintenance continue** : chaque nouvelle fonctionnalité, migration SQL, durcissement sécurité, optimisation ou correction de régression doit ajouter une entrée datée en tête de ce journal avec les fichiers clés et les vérifications exécutées.

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
