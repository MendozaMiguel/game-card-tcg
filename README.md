# L'Ordre des Arcanes Online

Jeu de cartes à collectionner (TCG) tactique temps réel à moteur autoritaire serveur (`1v1`, `2v2` avec relais/déconnexion, tournois Conquest Bo3 et Mage Automate IA multi-niveaux).

---

## Stack Technique

- **Back-End** (`backend/`) : Java 21 LTS (`Virtual Threads`), Spring Boot 4.1.2, Spring Security (cookie JWT `HttpOnly` HS512), WebSocket STOMP autoritaire, Hibernate 7.4 (`ddl-auto: validate`), Flyway, PostgreSQL 17.
- **Front-End** (`frontend/`) : Angular 22.2 (`standalone`, `signals`, `@if`/`@for`, `OnPush`), Tailwind CSS 4.3, SCSS, GSAP 3.15, Motion, `@ng-icons/lucide`, `@stomp/stompjs`.
- **Infrastructure** : Docker Compose (`arcane-db`, `arcane-server`, `arcane-web`), Nginx unprivileged (`nginx/default.conf` avec CSP stricte).

---

## Configuration des Variables d'Environnement (`.env`)

Le fichier `.env` contenant les secrets et paramètres locaux est volontairement ignoré par Git (`.gitignore`).

1. Copier le template versionné [`.env.example`](./.env.example) vers `.env` :
   ```powershell
   Copy-Item .env.example .env
   ```
   ```bash
   cp .env.example .env
   ```
2. Renseigner des valeurs secrètes robustes avant toute mise en ligne :
   - `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT` : connexion PostgreSQL 17.
   - `ARCANE_JWT_SECRET` : clé secrète **>= 64 octets** obligatoire pour la signature JWT `HS512`.
   - `ARCANE_COOKIE_SECURE` : `false` en HTTP local, **`true` obligatoire en HTTPS**.
   - `ARCANE_ALLOWED_ORIGINS` : liste des origines HTTP/CORS et WebSocket STOMP autorisées.
   - `WEB_PORT` : port d'exposition du reverse proxy Nginx (`80` par défaut).
   - *(Optionnel hors Docker)* `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` : surcharges JDBC pour exécution Spring Boot locale directe.

---

## Démarrage Rapide

### Option A — Stack Docker complète (`PostgreSQL + Back-End + Nginx`)
```powershell
./start-docker.ps1
```
*(Initialise automatiquement `.env` depuis `.env.example` s'il est absent, puis lance `docker compose up -d --build`.)*  
Application accessible sur `http://localhost/` (ou `http://localhost:${WEB_PORT}`).

### Option B — Développement hybride (`PostgreSQL Docker + Maven + Angular CLI`)
```powershell
./start-dev.ps1
```
Front-End accessible sur `http://localhost:4200` (proxy API/WebSocket vers `http://localhost:8080`).  
Pour tester depuis un téléphone sur le même réseau local :
```powershell
cd frontend
npm run start:lan
```
*(Penser à ajouter `http://<IP_LAN>:4200` dans `ARCANE_ALLOWED_ORIGINS` du `.env`.)*

---

## Tests & Vérifications

- **Back-End** (`backend/`) : `mvn test`
- **Front-End** (`frontend/`) :
  - Tests unitaires : `npm run test -- --watch=false`
  - Build production : `npm run build`

---

## Documentation Projet

- [Architecture & Modèle de Domaine](./docs/ARCHITECTURE.md)
- [Infrastructure & Sécurité](./docs/INFRASTRUCTURE_AND_SECURITY.md)
- [Système de Design](./docs/DESIGN_SYSTEM.md)
- [Journal des Évolutions](./docs/CHANGELOG.md)
