---
name: arcane-infra-security-ops
description: >-
  Infrastructure, Docker Compose, Nginx CSP/reverse-proxy, Spring Security
  HS512 cookie auth, STOMP WebSocket rate limiting, XSS sanitization, and the
  mandatory non-regression verification pipeline for L'Ordre des Arcanes Online.
  Use when modifying Docker, Nginx, security filters, environment configs, SQL
  migrations, CI/build scripts, or running full-stack regression verification.
---

# Infrastructure, Sécurité & Pipeline de Non-Régression (`momo tcg`)

Ce skill encadre les opérations Docker, la sécurité HTTP/WebSocket et la validation sans régression du projet **L'Ordre des Arcanes Online**.

## 1. Topologie Infrastructure Exacte

- **Compose** ([`docker-compose.yml`](../../../docker-compose.yml)) sur réseau `arcane-net` :
  - `arcane-db` : `postgres:17-alpine`, port local `127.0.0.1:${POSTGRES_PORT:-5432}:5432`, volume `arcane_pg_data`, healthcheck `pg_isready`.
  - `arcane-server` : build multi-stage Maven (`temurin:21-alpine` → JRE 21 user `arcane`), flags JVM `-XX:+UseZGC -XX:+ZGenerational`, healthcheck `/actuator/health`, port `127.0.0.1:8080:8080`.
  - `arcane-web` : build multi-stage Node 24 → `nginxinc/nginx-unprivileged:alpine`, port `${WEB_PORT:-80}:8080`, monte `./nginx/default.conf:ro`.
- **Scripts racine** :
  - `start-docker.ps1` : initialise `.env` depuis `.env.example` si absent et lance `docker compose up -d --build`.
  - `start-dev.ps1` : lance uniquement `arcane-db` en Docker + backend `mvn spring-boot:run` + front `ng serve` sur `:4200` (`proxy.conf.json` vers `:8080`).

---

## 2. Invariants de Sécurité à Préserver Absolument

### Nginx & CSP ([`nginx/default.conf`](../../../nginx/default.conf))
- En-têtes actifs : `server_tokens off`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy`.
- **CSP stricte** : `script-src 'self'`, `object-src 'none'`, `frame-ancestors 'none'`, `connect-src 'self' ws: wss:`, `font-src 'self' data:`.
  - Conséquence front-end : **zéro script inline**, **zéro requête vers des polices/CDN externes** non déclarés dans la CSP ; embarquer les ressources dans `frontend/public/` ou CSS local.

### HTTP & Auth ([`SecurityConfig.java`](../../../backend/src/main/java/com/ordredesarcane/online/config/SecurityConfig.java), [`JwtService.java`](../../../backend/src/main/java/com/ordredesarcane/online/security/JwtService.java))
- Session `STATELESS`, cookie `ARCANE_SESSION` signé `HS512` (`HttpOnly`, `SameSite=Strict`, `Secure` piloté par `ARCANE_COOKIE_SECURE`).
- Mots de passe hachés par `BCryptPasswordEncoder(12)`.
- Désérialisation Jackson stricte (`fail-on-unknown-properties: true`) + annotations `@Valid` sur chaque DTO entrant REST et `@Payload` WebSocket.
- Endpoints publics limités à `/api/health`, `/actuator/health`, `/api/auth/register|login|logout`, `GET /api/cards/**`, `/ws/**` (protégé ensuite par handshake interceptor).

### WebSocket STOMP & Anti-Abus ([`WebSocketSecurityConfig.java`](../../../backend/src/main/java/com/ordredesarcane/online/config/WebSocketSecurityConfig.java))
- Handshake `/ws` : exige un cookie `ARCANE_SESSION` valide + origine présente dans `ARCANE_ALLOWED_ORIGINS`.
- Canal entrant : rejette tout frame `CONNECT`/`SEND`/`SUBSCRIBE` sans `PlayerPrincipal`.
- `RateLimiterService` : fenêtre glissante `1000 ms` bornant à **5 actions jeu / seconde** (`/app/game.*`) et **1 message chat / seconde** (`/app/chat*`).
- `XssSanitizer` : filtre caractères de contrôle + troncature + `HtmlUtils.htmlEscape` sur chat, noms de salons et tournois.

---

## 3. Protocole de Non-Régression & Déploiement Live

Exécuter ces étapes dans l'ordre après modification :

### Étape A — Tests Unitaires & Intégration Back-End
Depuis `backend/` :
```powershell
& "$env:USERPROFILE\.gemini\antigravity-ide\scratch\tools\apache-maven-3.9.9\bin\mvn.cmd" test
```
*(Utiliser `mvn test` si Maven est directement dans le PATH.)*
- Exiger `Failures: 0, Errors: 0, Skipped: 0` sur `CatalogAndAntiMetaValidatorTests`, `GameEngineTests`, `MatchHistoryTests`, `SecurityAndValidationUnitTests` et toute nouvelle classe de test.

### Étape B — Tests & Budget Build Front-End
Depuis `frontend/` :
```powershell
npm.cmd test -- --watch=false
npm.cmd run build
```
- Vérifier que Vitest exécute 100 % des `.spec.ts` sans échec et que `ng build` reste sous les budgets `angular.json` (`initial < 500kB`, `anyComponentStyle < 4kB` warning / `8kB` error).

### Étape C — Rebuild Docker & Vérification Live
Si la modification impacte la prod locale Docker (`arcane-server`, `arcane-web`, `nginx/default.conf`, SQL) :
1. Reconstruire et redémarrer :
   `docker compose up -d --build` (ou ciblé `arcane-server arcane-web`).
2. Vérifier la santé des services et les logs Flyway/Hibernate :
   `docker compose ps` et `docker logs arcane-server --tail 60` (`ddl-auto: validate` sans erreur).
3. Vérifier en base PostgreSQL si une migration a été ajoutée :
   `docker exec arcane-db psql -U arcane -d arcane_online -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"`
4. Vérifier l'application en direct sur `http://localhost/` via Chrome DevTools (contrôle visuel + zéro erreur console).
5. Mettre à jour `docs/INFRASTRUCTURE_AND_SECURITY.md` et `docs/CHANGELOG.md`.
