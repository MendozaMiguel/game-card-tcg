# Instructions Projet — L'Ordre des Arcanes Online (`momo tcg`)

Tu travailles sur **L'Ordre des Arcanes Online**, un jeu de cartes à collectionner (TCG) temps réel autoritaire serveur :
- **Back-End** : Java 21 LTS (`Virtual Threads`) + Spring Boot 4.1.2 + Hibernate 7.4 (`ddl-auto: validate`, `open-in-view: false`) + Flyway + PostgreSQL 17 (`backend/`)
- **Front-End** : Angular 22.2 (`standalone`, `signals`, `@if`/`@for`) + Tailwind CSS 4.3 + SCSS + GSAP 3.15 + STOMP `@stomp/stompjs` (`frontend/`)
- **Infra / Reverse Proxy** : Docker Compose (`arcane-db`, `arcane-server`, `arcane-web`) + Nginx unprivileged (`nginx/default.conf`)

---

## 1. Principes d'Ingénierie Non-Négociables

- **Clean Code & Modularité** : petites fonctions focalisées, zéro logique dupliquée, retour anticipé, séparation stricte contrôleur / service / domaine / projection, gestion d'erreurs explicite (aucun `catch` muet). Respecter les frontières de packages (`catalog`, `deck`, `game`, `lobby`, `match`, `security`, `social`, `user`).
- **Best Practices Modernes & Zéro Dette** : exploiter exclusivement les APIs à jour sans dépréciation (`record` Java 21, Spring Boot 4.1 / Jakarta Validation, Angular 22 `standalone` + `signals` + `OnPush` + `@if`/`@for`, Tailwind v4 `bg-linear-to-*`), consulter `modern-web-guidance` avant toute implémentation HTML/CSS/DOM, et exiger des dépendances maintenues à `0` vulnérabilité.
- **Performance & Scalabilité** :
  - Conserver l'état chaud des duels en mémoire autoritaire (`ConcurrentHashMap<UUID, GameRoom>`) et n'écrire en PostgreSQL qu'aux bornes persistantes (démarrage/fin de match, `match_participants`, `match_summary` léger sans charger `replay_log` dans les listes).
  - Éviter les requêtes N+1 (`@EntityGraph` / requêtes agrégées / index ciblés) et surveiller la taille des bundles Angular (`500kB` warn initial, `4kB` warn / `8kB` err CSS par composant dans `frontend/angular.json`).
- **Sécurité par défaut** :
  - Ne jamais déplacer le JWT hors cookie `HttpOnly` `SameSite=Strict` (`ARCANE_SESSION`).
  - Ne jamais contourner `FogOfWarProjector`, `AntiMetaDeckValidator`, `RateLimiterService`, `XssSanitizer`, ni assouplir `fail-on-unknown-properties: true` ou la CSP dans `nginx/default.conf`.
- **Aucune Régression Silencieuse** : toute modification back, front ou SQL doit passer la batterie de tests et le build correspondant avant réponse finale.

---

## 2. Protocole Obligatoire de Documentation Vivante (`docs/`)

**À la fin de chaque fonctionnalité, migration SQL, refonte UI, correction de bug ou évolution infra/sécurité**, mets à jour la documentation projet au fur et à mesure :
1. **`docs/CHANGELOG.md`** : ajouter une entrée datée résumant le changement, les fichiers touchés et les vérifications passées.
2. **`docs/ARCHITECTURE.md`** : mettre à jour si modules, endpoints REST/STOMP, tables/colonnes Flyway ou règles métier évoluent.
3. **`docs/INFRASTRUCTURE_AND_SECURITY.md`** : mettre à jour si Docker, Nginx/CSP, variables `.env`, configuration sécurité ou commandes de test/déploiement changent.
4. **`docs/DESIGN_SYSTEM.md`** : mettre à jour si tokens CSS, composants partagés, polices, animations GSAP ou ergonomie des vues changent.

---

## 3. Portes de Vérification (Avant de Clore une Tâche)

- **Back-End (`backend/`)** :
  - `mvn test` (ou sous Windows si `mvn` absent du PATH : `& "$env:USERPROFILE\.gemini\antigravity-ide\scratch\tools\apache-maven-3.9.9\bin\mvn.cmd" test`)
- **Front-End (`frontend/`)** :
  - `npm.cmd test -- --watch=false`
  - `npm.cmd run build` (vérifier absence de dépassement budget CSS `4kB`/`8kB` ou TS error)
- **Runtime Docker (si serveur, SQL, Nginx ou UI modifié pour livraison live)** :
  - `docker compose up -d --build` (ou service ciblé) + vérification logs Flyway/Hibernate et santé conteneurs + contrôle visuel/console sur `http://localhost/` (desktop `1440×900` **et** émulation mobile-first tactile `390×844` sans débordement horizontal).

---

## 4. Routage des Skills Spécialisés

Active le(s) skill(s) pertinent(s) selon la tâche :
- Architecture, modules, clean code, SQL/Flyway ou mise à jour `docs/` → **`arcane-architecture-and-docs`** (+ `architecture-patterns`, `code-refactoring-refactor-clean`, `code-review-excellence`, `postgresql`, `sql-optimization-patterns`, `database-migrations-sql-migrations`)
- Infra Docker, Nginx/CSP, JWT/WebSocket security, tests & CI locale → **`arcane-infra-security-ops`** (+ `backend-security-coder`, `frontend-security-coder`, `performance-engineer`, `javascript-testing-patterns`, `e2e-testing-patterns`, `java-pro`)
- Moteur TCG temps réel, règles Anti-Méta, Fog of War, STOMP, IA Automate, persistance match → **`arcane-game-engine-and-realtime`** (+ `card-game-design`, `java-pro`)
- Design UI/UX, thème « Obsidienne Runique & Laiton Doré », cartes/arène, tokens Tailwind v4/SCSS, typographie/couleurs, GSAP, accessibilité → **`arcane-ui-design-studio`**, **`frontend-design`**, **`ui-ux-pro-max`**, **`design-systems`**, **`color-theory`**, **`typography`**, **`motion-design`**, **`icon-design`**, **`game-ui-design`**, **`card-game-design`**, **`angular`**, **`tailwind-design-system`**, **`wcag-audit-patterns`**, **`modern-web-guidance`**, et **`ui-visual-validator`** pour valider par capture d'écran.
