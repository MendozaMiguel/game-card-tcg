import { Routes } from '@angular/router';
import { authGuard, guestOnlyGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'auth',
    canActivate: [guestOnlyGuard],
    title: "Connexion | L'Ordre des Arcanes Online",
    loadComponent: () =>
      import('./features/auth/auth-page.component').then((m) => m.AuthPageComponent),
  },
  {
    path: 'lobby',
    canActivate: [authGuard],
    title: "Sanctuaire | L'Ordre des Arcanes Online",
    loadComponent: () =>
      import('./features/lobby/lobby-page.component').then((m) => m.LobbyPageComponent),
  },
  {
    path: 'deckbuilder',
    canActivate: [authGuard],
    title: "Grimoire & Decks | L'Ordre des Arcanes Online",
    loadComponent: () =>
      import('./features/deckbuilder/deckbuilder-page.component').then(
        (m) => m.DeckbuilderPageComponent,
      ),
  },
  {
    path: 'tournaments',
    canActivate: [authGuard],
    title: "Tournois Conquest | L'Ordre des Arcanes Online",
    loadComponent: () =>
      import('./features/tournaments/tournaments-page.component').then(
        (m) => m.TournamentsPageComponent,
      ),
  },
  {
    path: 'profile',
    canActivate: [authGuard],
    title: "Profil & Cosmétiques | L'Ordre des Arcanes Online",
    loadComponent: () =>
      import('./features/profile/profile-page.component').then((m) => m.ProfilePageComponent),
  },
  {
    path: 'arena/:matchId',
    canActivate: [authGuard],
    title: "Arène de Duel | L'Ordre des Arcanes Online",
    loadComponent: () =>
      import('./features/arena/arena-page.component').then((m) => m.ArenaPageComponent),
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'lobby',
  },
  {
    path: '**',
    redirectTo: 'lobby',
  },
];
