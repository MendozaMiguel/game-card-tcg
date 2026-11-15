# Infrastructure, Sécurité & Opérations — L'Ordre des Arcanes Online

> **Document vivant** : mettre à jour ce fichier lors de toute évolution Docker, Nginx/CSP, variables d'environnement, filtres de sécurité, migrations Flyway ou scripts d'exploitation. Consigner la modification dans [`docs/CHANGELOG.md`](./CHANGELOG.md).

## 1. Topologie Docker Compose

Définie dans [`docker-compose.yml`](../docker-compose.yml) sur le réseau bridge privé `arcane-net` :

| Conteneur | Image / Build | Exposition réseau | Healthcheck |
| :--- | :--- | :--- | :--- |
| **`arcane-db`** | `postgres:17-alpine` | `127.0.0.1:${POSTGRES_PORT:-5432}:5432` (volume `arcane_pg_data`) | `pg_isready -U arcane -d arcane_online` |
| **`arcane-server`** | Multi-stage Maven (`temurin:21-alpine` → JRE 21 unprivileged `arcane`, GC `ZGC` + `ZGenerational`) | `127.0.0.1:8080:8080` | `wget -qO- http://127.0.0.1:8080/actuator/health` |
| **`arcane-web`** | Multi-stage Node 24 build → `nginxinc/nginx-unprivileged:alpine` | `${WEB_PORT:-80}:8080` (monte `./nginx/default.conf:ro`) | Démarre après `arcane-server` sain |

### Variables d'environnement ([`.env.example`](../.env.example))
- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT`
- `ARCANE_JWT_SECRET` (**obligatoire >= 64 octets aléatoires pour HS512 en production**)
- `ARCANE_COOKIE_SECURE` (`false` en HTTP local, **`true` impératif derrière HTTPS en production**)
- `ARCANE_ALLOWED_ORIGINS` (liste stricte des origines CORS/WebSocket autorisées)
- `WEB_PORT` (`80` par défaut)
- Surcharges optionnelles Spring Boot hors Docker : `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`

---

## 2. Architecture de Sécurité Multicouche

### 2.1 Reverse Proxy & En-têtes HTTP ([`nginx/default.conf`](../nginx/default.conf))
- `server_tokens off;`
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Permissions-Policy: geolocation=(), microphone=(), camera=()`
- **Content-Security-Policy (CSP)** :
  `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self' ws: wss:; font-src 'self' data:; object-src 'none'; frame-ancestors 'none'; base-uri 'self';`
  > Note : ne jamais injecter de scripts tiers/inline sans mettre à jour la CSP et privilégier les styles compilés et SVG inline/locaux.
- Proxies : `/api/` (`proxy_read_timeout 60s`), `/ws` (Upgrade WebSocket, `3600s`), cache immutable `30d` sur assets hashés, fallback SPA `/index.html`.

### 2.2 Authentification & HTTP ([`SecurityConfig.java`](../backend/src/main/java/com/ordredesarcane/online/config/SecurityConfig.java), [`JwtService.java`](../backend/src/main/java/com/ordredesarcane/online/security/JwtService.java))
- Politique `SessionCreationPolicy.STATELESS` et entrée `401 Unauthorized` sur routes protégées.
- **JWT HS512** stocké exclusivement dans un cookie `HttpOnly`, `SameSite=Strict`, chemin `/`, expiration `86400s` (`ARCANE_SESSION`). Aucun token JWT n'est stocké dans `localStorage`/`sessionStorage`.
- **Hachage mots de passe** : `BCryptPasswordEncoder(12)`.
- **Désérialisation stricte** : `spring.jackson.deserialization.fail-on-unknown-properties: true` + Jakarta Bean Validation (`@Valid`) sur chaque payload REST et STOMP.
- **ORM sûr** : `spring.jpa.open-in-view: false`, `ddl-auto: validate` (le schéma n'est modifié **que** par Flyway `V1`, `V2`, `V3`, `V4`, ...).

### 2.3 Sécurité WebSocket STOMP & Anti-Abus ([`WebSocketSecurityConfig.java`](../backend/src/main/java/com/ordredesarcane/online/config/WebSocketSecurityConfig.java))
- Handshake `/ws` : vérification obligatoire du cookie JWT avant l'upgrade (`HttpStatus.UNAUTHORIZED` sinon) et origine validée via `corsProperties.allowedOrigins()`.
- Intercepteur entrant `CONNECT` / `SEND` / `SUBSCRIBE` : rejette tout message sans `PlayerPrincipal`.
- **Rate limiting** (`RateLimiterService`, fenêtre glissante `1000 ms`) :
  - `/app/game.*` : max **5 actions/seconde** par joueur (contrôlé dans l'intercepteur entrant de [`WebSocketSecurityConfig`](../backend/src/main/java/com/ordredesarcane/online/config/WebSocketSecurityConfig.java)).
  - `/app/chat*` : max **1 message/seconde** par joueur (contrôlé une seule fois dans [`ChatController`](../backend/src/main/java/com/ordredesarcane/online/lobby/ChatController.java) avec `@MessageExceptionHandler` retournant un `GameErrorEvent` sur `/user/queue/errors` sans fermer la session WebSocket).
- **Anti-XSS** (`XssSanitizer`) : suppression des caractères de contrôle, troncature bornée, encodage UTF-8 `HtmlUtils.htmlEscape` sur noms de salons, tournois et messages de chat.
- **Brouillard de guerre (`FogOfWarProjector`)** : le serveur ne projette jamais au client adverse la main réelle, l'ordre du deck ou les cartes face cachée ; la validation et les transitions d'état sont 100 % autoritaires côté serveur.

---

## 3. Commandes Opérationnelles & Pipeline de Non-Régression

### Développement & Déploiement Local
- **Stack complète Docker** : `./start-docker.ps1` ou `docker compose up -d --build`
- **Mode hybride Dev** : `./start-dev.ps1` (démarre `arcane-db`, backend Maven port 8080, front `ng serve` port 4200 avec `proxy.conf.json`)
- **Test sur téléphone en réseau local (LAN)** : depuis `frontend/`, `npm run start:lan` (`ng serve --host 0.0.0.0 --port 4200 --allowed-hosts`), en ajoutant l'origine locale `http://<IP_LAN>:4200` (ou `http://<IP_LAN>` sur port `80` via Docker) à `ARCANE_ALLOWED_ORIGINS` pour que CORS (`/api/**`) et STOMP WebSocket (`/ws`) autorisent l'origine mobile.

### Vérifications Obligatoires avant Fin de Tâche
1. **Tests Back-End** (depuis `backend/`) :
   - Si `mvn` est dans le PATH : `mvn test`
   - Sinon sous Windows avec Maven portable : `& "$env:USERPROFILE\.gemini\antigravity-ide\scratch\tools\apache-maven-3.9.9\bin\mvn.cmd" test` (ou `<CHEMIN_VERS_MAVEN>\mvn.cmd`)
2. **Tests & Build Front-End** (depuis `frontend/`) :
   - `npm.cmd test -- --watch=false` (Vitest + jsdom)
   - `npm.cmd run build` (vérifier absence d'erreurs TS et respect des budgets `angular.json` : initial `<= 500kB` warn / `1MB` err, CSS par composant `<= 4kB` warn / `8kB` err)
3. **Validation Migrations & Runtime Docker** (si SQL, backend ou frontend modifiés) :
   - `docker compose up -d --build arcane-server arcane-web` (ou `--no-deps` ciblé)
   - Vérifier logs Flyway + Hibernate `ddl-auto: validate` et statut `healthy` sur `arcane-db` / `arcane-server`
   - Vérifier le rendu live et l'absence d'erreurs console navigateur sur `http://localhost/`.
