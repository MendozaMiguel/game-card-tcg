---
name: arcane-game-engine-and-realtime
description: >-
  Authoritative TCG game engine, 4 Anti-Meta Deck Laws, FogOfWarProjector
  anti-cheat, STOMP WebSocket real-time protocol, 2v2 relay & disconnect grace
  timers, MageAutomateBot, and MatchPersistenceService for L'Ordre des Arcanes
  Online. Use when modifying duel rules, cards/abilities, matchmaking, rooms,
  tournaments, or real-time Arena state sync.
---

# Moteur TCG Autoritaire & Temps Réel (`momo tcg`)

Ce skill protège l'intégrité compétitive, le déterminisme serveur et les invariants temps réel de **L'Ordre des Arcanes Online**.

## 1. Architecture du Moteur & État Autoritaire

- **État chaud en mémoire** : `GameEngineService` gère les salles actives dans `ConcurrentHashMap<UUID, GameRoom>` et indexe l'appartenance joueur via `ConcurrentHashMap<UUID, UUID> userToActiveMatch`.
- **Concurrence** : toute mutation d'une salle ou exécution d'un timer de phase/déconnexion doit synchroniser sur le verrou d'instance `room` (`synchronized (room)`) sans bloquer l'ensemble des autres duels ni conserver d'état mutable non protégé.
- **Minuteries serveur (`GameEngineService`)** :
  - `OPENING_HAND_SIZE = 5`, `MAX_HAND_SIZE_END_TURN = 6`
  - `MULLIGAN_SECONDS = 20`, `TURN_SECONDS_1V1 = 60`, `TURN_SECONDS_2V2 = 75`
  - `REACTION_SECONDS = 12`, `DISCARD_SECONDS = 15`
  - `DISCONNECT_GRACE_1V1_SECONDS = 60`, `DISCONNECT_RELAY_2V2_SECONDS = 30`
- **Emplacements plateau (`PlayerState`)** :
  - `MAX_MONSTER_SLOTS = 4`
  - `MAX_FAMILIAR_SLOTS = 3`
  - `MAX_SPELL_ARTIFACT_SLOTS = 2`

---

## 2. Protocole Temps Réel STOMP (`/ws`)

Vérifier la cohérence stricte entre [`GameWebSocketController`](../../../backend/src/main/java/com/ordredesarcane/online/game/ws/GameWebSocketController.java), [`ChatController`](../../../backend/src/main/java/com/ordredesarcane/online/lobby/ChatController.java) et [`StompService`](../../../frontend/src/app/core/services/stomp.service.ts) :
- **Envois client** : `/app/game.action` (`GameActionCommand`), `/app/chat.global`, `/app/chat.game`.
- **Flux serveur → client** :
  - `/user/queue/game-state` : vue personnalisée `PlayerGameView` par joueur.
  - `/user/queue/errors` : `GameErrorEvent`.
  - `/user/queue/lobby` : `LobbyNotificationDto` (`MATCH_FOUND`, `CUSTOM_ROOM_STARTED`, etc.).
  - `/user/queue/chat.team` et `/topic/chat.game.{matchId}`, `/topic/chat.global`.
  - `/topic/game.{matchId}.spectate` : vue spectateur sans privilèges de contrôle ni fuites de mains privées.

---

## 3. Règles Anti-Méta, Brouillard de Guerre & Persistance

1. **4 Lois Anti-Méta ([`AntiMetaDeckValidator`](../../../backend/src/main/java/com/ordredesarcane/online/deck/AntiMetaDeckValidator.java))** :
   - `30` cartes exactement, Singleton strict (`cardId` et `name` uniques).
   - `10` Monstres (`>= 7` standards, `<= 3` Épiques), `10` Familiers, `10` Sorts/Malédictions/Artéfacts.
   - Courbe : `14` cartes coût `1–2`, `12` coût `3–4`, `4` coût `5+`, maximum `3` Rampe.
   - Bi-faction max (`<= 2`), avec au moins `15` cartes de la faction principale.
   - Tournois Conquest Bo3 (`TournamentService`) : `8` ou `16` joueurs avec `2` decks valides de factions principales différentes.
2. **Anti-Triche ([`FogOfWarProjector`](../../../backend/src/main/java/com/ordredesarcane/online/game/engine/FogOfWarProjector.java))** :
   - Ne jamais exposer les cartes en main adverse, la pioche exacte, ni les sorts réactifs/malédictions cachés tant qu'ils ne sont pas révélés.
   - Filtrer `eligibleReactiveIds` et `PendingModalChoice` uniquement pour les joueurs autorisés (ou leur contrôleur relais 2v2 via `canBeControlledBy(viewerUserId)`).
3. **Persistance & Fin de Partie ([`MatchPersistenceService`](../../../backend/src/main/java/com/ordredesarcane/online/match/MatchPersistenceService.java))** :
   - Nettoyer systématiquement les timers (`phaseTimers`, `disconnectTimers`) et libérer `userToActiveMatch` dès que `GamePhase.FINISHED` est atteint.
   - Enregistrer `MatchSource` (`RANKED`, `CASUAL`, `CUSTOM`, `AI`, `TOURNAMENT`), `matchSummary` compact et tous les `MatchParticipantEntity` (humains et bots).
   - Préserver la règle : `AI`, `CUSTOM`, parties trop courtes ou avec prise de relais 2v2 n'accordent pas de gain/perte de MMR.

---

## 4. Non-Régression Obligatoire
Après toute touche au moteur, aux cartes ou au temps réel :
1. Exécuter `CatalogAndAntiMetaValidatorTests`, `GameEngineTests`, `MatchHistoryTests` et `SecurityAndValidationUnitTests` via Maven.
2. En cas d'ajout de carte ou d'action `GameActionType`, couvrir par au moins un test JUnit déterministe dans `backend/src/test/java/com/ordredesarcane/online/`.
3. Mettre à jour [`docs/ARCHITECTURE.md`](../../../docs/ARCHITECTURE.md) et [`docs/CHANGELOG.md`](../../../docs/CHANGELOG.md).
