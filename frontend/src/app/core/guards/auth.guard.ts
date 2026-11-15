import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { StompService } from '../services/stomp.service';

export const authGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const stomp = inject(StompService);

  const profile = await auth.initSession();
  if (profile) {
    stomp.connect();
    return true;
  }
  return router.createUrlTree(['/auth']);
};

export const guestOnlyGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const stomp = inject(StompService);

  const profile = await auth.initSession();
  if (profile) {
    stomp.connect();
    return router.createUrlTree(['/lobby']);
  }
  return true;
};
