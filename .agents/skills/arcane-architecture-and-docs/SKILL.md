---
name: arcane-architecture-and-docs
description: >-
  Project-specific architecture, Clean Code, PostgreSQL/Flyway schema evolution,
  scalability patterns, and mandatory living documentation workflow for L'Ordre
  des Arcanes Online (momo tcg). Use whenever designing features, refactoring
  backend/frontend modules, adding Flyway migrations, optimizing database
  access, or updating docs/ARCHITECTURE.md and docs/CHANGELOG.md.
---

# Architecture, Clean Code & Documentation Vivante (`momo tcg`)

Ce skill régit l'architecture logicielle, la maintenabilité, la scalabilité des données et la tenue à jour continue de `docs/` pour **L'Ordre des Arcanes Online**.

## 1. Frontières Modulaires & Clean Code

### Back-End (`backend/src/main/java/com/ordredesarcane/online/`)
Conserver un couplage unidirectionnel strict par domaine :
- `config/` + `security/` + `api/` : socle transverse HTTP/STOMP/erreurs (`GlobalExceptionHandler`).
- `catalog/` : lecture immuable des 120 cartes (`CardCatalogService` avec cache en mémoire indexé).
- `deck/` : persistance des decks et validation métier pure dans `AntiMetaDeckValidator`.
- `game/model/` → `game/engine/` → `game/ws/` : modèle d'état sans dépendance Spring Web, moteur autoritaire pur testable unitairement sans contexte HTTP, contrôleurs fins.
- `lobby/` : files d'attente (`MatchmakingService`), salons privés (`CustomRoomService`), chat (`ChatController`), synthèse publique (`LobbyController`).
- `match/` : persistance durable (`MatchEntity`, `MatchParticipantEntity`, `MatchPersistenceService`) et tournois Conquest Bo3 (`TournamentService`).
- `user/` + `social/` : comptes, ligues/cosmétiques, amis, statistiques agrégées (`PlayerMatchHistoryService`).

**Règles de qualité du code & Best Practices** :
1. **Architecture & Responsabilité Unique** : aucun contrôleur REST/STOMP ne doit contenir de logique métier ni d'accès direct aux entités JPA quand un service dédié existe ; privilégier de petites méthodes focalisées avec retours anticipés (*early return*).
2. **Immuabilité & Typage Strict** : utiliser des `record` Java 21 immuables pour les DTOs, commandes et vues projetées, et refléter les types stricts en TypeScript sans `any`.
3. **DRY sans Sur-Abstraction** : factoriser la logique partagée entre flux (`RANKED`, `CASUAL`, `CUSTOM`, `AI`, `TOURNAMENT`) via `MatchSource`, `MatchParticipantBuilder` et surcharges compatibles (en préservant les signatures testées).
4. **Robustesse & Erreurs Explicites** : valider toutes les entrées (`@Valid`, contraintes de domaine), interdire les `catch` silencieux, et gérer proprement les annulations/nettoyages (`DestroyRef.onDestroy`, `finally`, libération des verrous/timers).
5. **Standards Modernes & Zéro Dépréciation** : proscrire les APIs obsolètes (Java, Spring Boot 4, Hibernate 7, Angular `< 22`, Tailwind v3, patterns Web pré-Baseline) ; consulter `modern-web-guidance` avant toute évolution HTML/CSS/DOM et maintenir `0` vulnérabilité connue sur les dépendances.

### Front-End (`frontend/src/app/`)
- **Architecture standalone + Signals** : état serveur/temps réel dans `core/services/` (`AuthService`, `LobbyApiService`, `StompService`, `AnnouncerService`) ; état local dérivé dans les composants via `computed()`.
- **Lazy loading strict** : toute page de route dans `app.routes.ts` doit être chargée via `loadComponent()`.
- **Typage partagé** : centraliser les contrats JSON et DTOs dans `core/models/arcane.models.ts` alignés 1:1 avec les `record` Java.
- **Helpers partagés** : utilitaires de thèmes, provenance (`resolveMatchSource`), raisons de fin (`END_REASON_LABELS`) et formats (`formatDurationCompact`) dans `shared/utils/faction-theme.ts`.

---

## 2. Scalabilité Base de Données & Migrations Flyway

### Règles impératives PostgreSQL 17 + Hibernate 7.4
1. **Ne jamais activer `ddl-auto: update`** : `application.yml` impose `spring.jpa.hibernate.ddl-auto: validate` et `spring.jpa.open-in-view: false`.
2. **Séquence Flyway stricte** : les migrations `V1__init_baseline.sql` → `V4__player_match_history.sql` sont appliquées. Toute évolution de schéma doit créer `V5__<description_snake_case>.sql` (ou incrément suivant) **avant** de démarrer le backend.
3. **Séparation chaud / froid** :
   - Ne jamais lire la colonne `matches.replay_log` (JSONB potentiellement volumineux) sur des endpoints de liste (`/api/profile/matches`, `/api/lobby/matches/history`).
   - Exploiter `matches.match_summary` (JSONB compact) et la table indexée `match_participants(user_id, created_at DESC)` pour O(log N) par joueur.
   - Toujours indexer les colonnes FK et clés de tri fréquentes, sans oublier `user_id` nullable (`ON DELETE SET NULL`) pour supporter les bots/IA.
4. **Concurrence & IDs** : `MatchEntity` implémente `Persistable<UUID>` sans `@GeneratedValue` pour préserver exactement l'UUID généré en mémoire par `GameRoom.getMatchId()`.

---

## 3. Procédure de Mise à Jour Continue de la Documentation

À **chaque** tâche impliquant du code applicatif, SQL, infra ou UI, exécuter cette check-list avant de conclure :

1. Lire les sections impactées dans `docs/`.
2. Mettre à jour les documents concernés :
   - Nouveau endpoint REST, destination STOMP, DTO, module ou table SQL → [`docs/ARCHITECTURE.md`](../../../docs/ARCHITECTURE.md)
   - Nouvelle variable d'env, header Nginx/CSP, règle rate-limit/auth/Docker ou commande test → [`docs/INFRASTRUCTURE_AND_SECURITY.md`](../../../docs/INFRASTRUCTURE_AND_SECURITY.md)
   - Nouveau token CSS, composant visuel partagé, règle de layout ou amélioration design → [`docs/DESIGN_SYSTEM.md`](../../../docs/DESIGN_SYSTEM.md)
3. Ajouter systématiquement une entrée en haut de [`docs/CHANGELOG.md`](../../../docs/CHANGELOG.md) datée (`[AAAA-MM-JJ]`), listant **Ajouté / Modifié / Corrigé / Vérifié** avec les résultats exacts des tests exécutés.

## 4. Validation
- Vérifier que chaque chemin de fichier, endpoint, port ou test cité dans `docs/*.md` existe réellement dans le dépôt au moment de l'édition.
- Exécuter la suite Maven + Vitest + build Angular avant de marquer l'entrée changelog comme vérifiée.
