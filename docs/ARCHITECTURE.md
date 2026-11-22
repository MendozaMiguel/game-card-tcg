# Architecture Technique — L'Ordre des Arcanes Online

> **Document vivant** : mettre à jour ce fichier à chaque ajout de module, modification du schéma SQL/Flyway, changement d'API REST ou STOMP, ou évolution des règles métier. Consigner chaque livraison dans [`docs/CHANGELOG.md`](./CHANGELOG.md).

## 1. Vue d'ensemble & Stack

| Couche | Technologies vérifiées | Point d'entrée |
| :--- | :--- | :--- |
| **Front-End** | Angular 22.2 (`standalone`, `signals`, `@if`/`@for`), Tailwind CSS 4.3, SCSS, GSAP 3.15, `@stomp/stompjs` 7.5 | [`frontend/package.json`](../frontend/package.json) |
| **Reverse Proxy** | `nginxinc/nginx-unprivileged:alpine` (écoute `8080`, exposé `${WEB_PORT:-80}`) | [`nginx/default.conf`](../nginx/default.conf) |
| **Back-End** | Java 21 LTS (`Virtual Threads`), Spring Boot 4.1.2, Spring Security, STOMP WebSocket, Hibernate 7.4 (`ddl-auto: validate`) | [`backend/pom.xml`](../backend/pom.xml) |
| **Base de données** | PostgreSQL 17 Alpine (`arcane_online`), migrations séquentielles Flyway (`V1` → `V5`) | [`backend/src/main/resources/db/migration/`](../backend/src/main/resources/db/migration) |

---

## 2. Organisation des Modules Back-End

Racine Java : `com.ordredesarcane.online` (`backend/src/main/java/com/ordredesarcane/online/`).

- **`api`** : gestion globale des exceptions (`GlobalExceptionHandler`, `ApiConflictException`, `ResourceNotFoundException`) et sonde santé (`HealthController` → `GET /api/health`).
- **`config`** : configuration sécurité HTTP (`SecurityConfig`), broker/sécurité STOMP (`WebSocketSecurityConfig`), désérialisation JSON stricte (`JacksonStrictConfig`, `fail-on-unknown-properties: true`), support JSON Hibernate (`HibernateJsonConfig`), propriétés typées (`JwtProperties`, `CorsProperties`).
- **`security`** : JWT HS512 en cookie HttpOnly (`JwtService`, `JwtAuthenticationFilter`, `PlayerPrincipal`), limitation de fréquence temps réel (`RateLimiterService`), échappement entrées texte (`XssSanitizer`).
- **`catalog`** : chargement immuable du catalogue officiel de 120 cartes (`cards-catalog-120.json` / table `cards_catalog`), filtres par `Faction` (`FIRE`, `ICE`, `SHADOW`, `LIGHT`) et `CardType`, `CardCatalogController` (`GET /api/cards`).
- **`deck`** : création/édition/favoris/suppression de decks (`DeckController`, `DeckService`, `DeckEntity`, `DeckCardEntity`) et moteur des **4 Lois Anti-Méta** (`AntiMetaDeckValidator`).
- **`game`** :
  - **`engine`** : `GameEngineService` (boucle autoritaire en mémoire `ConcurrentHashMap<UUID, GameRoom>` calculant `primaryFaction` de chaque joueur à l'ouverture), `BoardRuleEvaluator`, `AbilityInterpreter`, `CombatResolver` (émettant des `CombatVisualEvent` sans fuite d'informations privées), `FogOfWarProjector` (transmettant `primaryFaction` et les `visualEvents`), `MageAutomateBot` (secondé par `BotTacticalPlanner`, `BotLethalSolver` et `BotComboKnowledge` pour la planification de combos, chaînes d'énergie, résolution de létal multi-étapes et appariement combat par palier `AiDifficulty` : `EASY`, `MEDIUM`, `HARD`, strictement bornés aux informations publiques/Fog of War).
  - **`model`** : `GameRoom` (avec journal visuel borné à `32` `CombatVisualEvent`), `PlayerState` (avec `primaryFaction` et `boardZoneOf`), `TeamState`, `CardInstance`, `CombatVisualEvent`, `CombatOutcome`, `GamePhase`, `GameActionType`, `GameMode` (`DUEL_1V1`, `TEAM_2V2`), `AiDifficulty` (`EASY`, `MEDIUM`, `HARD`), `EndReason`.
  - **`ws`** : `GameWebSocketController` (`@MessageMapping("/game.action")`), `GameRestController` (`GET /api/game/active`, `GET /api/game/{matchId}/state`), `SessionPresenceService`.
- **`lobby`** : matchmaking 1v1/2v2 classé & amical (`MatchmakingService`), parties contre IA, salons personnalisés (`CustomRoomService`), chat global & duel (`ChatController`), flux global des matchs récents (`LobbyController`).
- **`match`** :
  - Persistance froide PostgreSQL (`MatchEntity` avec `Persistable<UUID>` calé sur `room.getMatchId()`, `MatchParticipantEntity`, `MatchPersistenceService`, `MatchSource`).
  - Tournois Conquest Bo3 à 8 ou 16 joueurs (`TournamentController`, `TournamentService`, `TournamentEntity`, `TournamentParticipantEntity`).
- **`social`** : gestion d'amis (`FriendshipController`, `FriendshipService`, `FriendshipEntity`).
- **`user`** : authentification (`AuthController`, `AuthService`), profil/ligues/cosmétiques, avatars de départ (`STARTER_AVATAR_IDS`) & progression d'initiation (`tutorialCompleted` via `V5__add_user_tutorial_flag.sql` dans `ProfileController`, `ProfileService`, `LeagueTier`), statistiques et historique joueur (`PlayerMatchHistoryService`).

---

## 3. Organisation Front-End (`frontend/src/app/`)

- **`app.routes.ts`** : toutes les routes features sont lazy-loadées via `loadComponent()` et protégées par `authGuard` / `guestOnlyGuard` :
  - `/auth` → `AuthPageComponent`
  - `/lobby` → `LobbyPageComponent`
  - `/deckbuilder` → `DeckbuilderPageComponent`
  - `/tournaments` → `TournamentsPageComponent`
  - `/profile` → `ProfilePageComponent`
  - `/arena/:matchId` → `ArenaPageComponent`
- **`core/`** :
  - `credentials.interceptor.ts` : envoi systématique des cookies `withCredentials`.
  - `stomp.service.ts` : client WebSocket STOMP (`/ws`) + signals réactifs (`connected`, `gameState`, `lastError`, `lobbyNotification`, `globalMessages`, `gameMessages`).
  - `auth.service.ts`, `lobby-api.service.ts`, `announcer.service.ts` (annonces d'accessibilité ARIA `polite`/`assertive` + toasts).
  - `shared/` :
    - `components/app-header/` : navigation, indicateur temps réel, profil rapide, déconnexion.
    - `components/arcane-card/` : rendu d'une carte (statistiques, mots-clés, posture défense, effet foil).
    - `components/arena-tutorial-coach/` : guide d'initiation interactif en 6 étapes (`TUTORIAL_STEPS` / `computeAutoTutorialIndex`) monté sur les duels d'initiation (`/arena/:matchId?tutorial=1` ou nouveau profil).
    - `components/arena-audio-control/` & `services/arcane-audio.service.ts` : synthétiseur Web Audio API sans dépendance (bruitages attaque/défense + thème musical Ré dorien inspiré de *The Witcher 3*) chargé uniquement sur les routes paresseuses Sanctuaire & Arène.
    - `directives/` : `form-aria-sync.directive.ts`, `ime-enter-submit.directive.ts`.
    - `utils/` : `league-labels.ts` (extrait pour préserver le budget initial `< 500 kB`), `faction-theme.ts`, `arena-vfx.ts`, `combat-log-presenter.ts`.

---

## 4. Contrats API REST & Temps Réel STOMP

### REST (`/api/...`)
- **Santé** : `GET /api/health`
- **Auth** : `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`
- **Catalogue** : `GET /api/cards`
- **Decks** : `GET /api/decks`, `POST /api/decks/validate`, `POST /api/decks`, `PUT /api/decks/{deckId}`, `PATCH /api/decks/{deckId}/favorite`, `DELETE /api/decks/{deckId}`
- **Lobby** : `POST /api/lobby/ai-match` (accepte `{ mode, deckId, botFaction, difficulty }`, `difficulty` optionnelle par défaut à `MEDIUM` ; en `TEAM_2V2` + `HARD`, l'allié Automate reste en `MEDIUM` face à deux bots ennemis `HARD`), `POST /api/lobby/queue/join`, `DELETE /api/lobby/queue`, `GET /api/lobby/queue/status`, `GET /api/lobby/custom-rooms`, `POST /api/lobby/custom-rooms`, `POST /api/lobby/custom-rooms/{roomCode}/join`, `POST /api/lobby/custom-rooms/{roomCode}/start`, `GET /api/lobby/matches/history`
- **Jeu** : `GET /api/game/active`, `GET /api/game/{matchId}/state`
- **Tournois** : `GET /api/tournaments`, `POST /api/tournaments`, `POST /api/tournaments/{tournamentId}/join`
- **Amis** : `GET /api/friends`, `POST /api/friends/request`, `POST /api/friends/{friendshipId}/accept`, `DELETE /api/friends/{friendshipId}`
- **Profil & Historique** : `GET /api/profile`, `GET /api/profile/matches?limit=20&source=...`, `PUT /api/profile/cosmetics`, `POST /api/profile/tutorial/complete` (marque `tutorialCompleted = true` sur un profil nouvellement inscrit)

### WebSocket STOMP (`/ws`)
- **Publications client (`/app`)** :
  - `/app/game.action` → `GameWebSocketController.handleGameAction` (`GameActionCommand`)
  - `/app/chat.global` → `ChatController`
  - `/app/chat.game` → `ChatController` (`teamOnly` supporté en 2v2)
- **Abonnements client** :
  - `/user/queue/game-state` (`PlayerGameView` filtrée par `FogOfWarProjector`)
  - `/user/queue/errors` (`GameErrorEvent`)
  - `/user/queue/lobby` (`LobbyNotificationDto`)
  - `/user/queue/chat.team` (`ChatMessageDto` privé d'équipe)
  - `/topic/chat.global` & `/topic/chat.game.{matchId}`
  - `/topic/game.{matchId}.spectate` (vue spectateur anonymisée)

---

## 5. Règles Métier & Modèle de Données Clés

### 4 Lois Anti-Méta (`AntiMetaDeckValidator`)
1. **Unicité (Singleton)** : 30 cartes exactement, maximum 1 exemplaire par `cardId` et par `name`.
2. **Quota Types** : 10 Monstres (au moins 7 standards, max 3 Épiques), 10 Familiers, 10 Sorts/Malédictions/Artéfacts.
3. **Courbe d'Énergie** : 14 cartes coût `1–2`, 12 cartes coût `3–4`, 4 cartes coût `5+`, maximum 3 cartes Rampe.
4. **Allégeance Bi-Faction** : maximum 2 factions, dont au moins 15 cartes pour la faction principale.
- **Tournois Conquest Bo3** : inscription avec 2 decks distincts valides ayant des **factions principales différentes**.

### Règle du Rempart, Survol (`Vol`) & Camouflage (`CombatResolver` / `BoardRuleEvaluator`)
- **Règle du Rempart** : en `1v1` comme en `2v2`, les attaques directes sur les PV de l'équipe adverse (`DIRECT_PLAYER`) sont bloquées s'il existe au moins une créature adverse sans `CAMOUFLAGE` actif sur le terrain ennemi (`GARDIEN` obligatoire en priorité, puis Monstres, puis Familiers).
- **`SURVOL` (`Vol`)** : une créature dotée de `SURVOL` ignore uniquement les **Familiers** adverses (camouflés ou non) ; elle ne contourne **pas** un **Monstre** adverse actif dépourvu de `CAMOUFLAGE` en cours (`isCamouflaged() == true`).
- **`CAMOUFLAGE`** : évalué dynamiquement via `BoardRuleEvaluator.hasKeyword` / `evaluateKeywords` selon l'état actif `creature.isCamouflaged()` (`camouflageUntilNextOwnTurn`). Dès l'expiration au début du tour du propriétaire (`onOwnerTurnStart`), le mot-clé actif `CAMOUFLAGE` est retiré du plateau (`CardViewDto.keywords`) afin que la créature redevienne ciblable en combat et par les sorts.

### Modèle de Persistance des Matchs (`V4`)
- **`matches`** : archive globale immuable par match (`match_id`, `mode_1v1_2v2`, `is_ranked`, `is_ai_match`, `is_custom_room`, `match_source`, `tournament_id`, `tournament_name`, `team_a_ids`, `team_b_ids`, `winner_team`, `end_reason`, `turn_count`, `match_summary` JSONB léger, `replay_log` JSONB complet).
- **`match_participants`** : table relationnelle normalisée par participant (`match_id`, `user_id` nullable `ON DELETE SET NULL`, `username`, `is_bot`, `team_id`, `seat_index`, `won`, `primary_faction`, `final_lp`, `mmr_delta`, `created_at`) permettant d'agréger ratio, série, forme récente, faction favorite et historique des 20 derniers duels sans scanner `replay_log`.
- **Règle de récompenses** : `MatchSource.RANKED` attribue les ajustements MMR/shards complets si la partie n'a pas subi de prise de relais 2v2 et a dépassé le seuil minimal; `AI`, `CUSTOM` et parties abandonnées prématurément n'altèrent pas le MMR (`mmr_delta = 0`).

### Constantes & Phase Mulligan (`GameEngineService` / `PlayerState`)
- Emplacements plateau par joueur : 4 Monstres, 3 Familiers, 2 Sorts/Artéfacts ; limite de main en fin de tour : 6 cartes.
- Minuteries : Mulligan `20s`, Tour 1v1 `60s`, Tour 2v2 `75s`, Fenêtre Réaction `12s`, Défausse excédent `15s`, Grâce déconnexion 1v1 `60s`, Relais allié 2v2 `30s`.
- **Mulligan interactif (`MULLIGAN_REPLACE_CARD` + `MULLIGAN_SWAP`)** : durant `GamePhase.MULLIGAN`, un joueur peut échanger immédiatement n'importe quel emplacement de sa main de départ (1 fois maximum par emplacement, suivi via `PlayerState.mulliganReplacedSlots` masqué aux adversaires dans `FogOfWarProjector`), piocher la nouvelle carte avant de remettre et mélanger l'ancienne dans le deck, puis valider (`MULLIGAN_SWAP`) pour passer au Tour 1.
