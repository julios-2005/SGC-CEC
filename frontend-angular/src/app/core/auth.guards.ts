import { inject } from '@angular/core';
import { CanActivateFn, CanMatchFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** El rol COBROS solo puede ver el panel de inscripciones y cambiar su contraseña. */
const RUTAS_PERMITIDAS_COBROS = ['/panel-inscripciones', '/cambiar-contrasena', '/cambiar-contrasena-obligatoria'];

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated()) return router.createUrlTree(['/login']);
  if (auth.passwordChangeRequired() && !state.url.startsWith('/cambiar-contrasena-obligatoria')) {
    return router.createUrlTree(['/cambiar-contrasena-obligatoria']);
  }
  if (auth.isCobros() && !RUTAS_PERMITIDAS_COBROS.some(ruta => state.url.startsWith(ruta))) {
    return router.createUrlTree(['/panel-inscripciones']);
  }
  return true;
};

/** Permite usar el diseño con menú en rutas que también tienen versión pública. */
export const authenticatedMatchGuard: CanMatchFn = () => inject(AuthService).isAuthenticated();

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (!auth.isAuthenticated()) return inject(Router).createUrlTree(['/login']);
  return auth.isAdmin() ? true : inject(Router).createUrlTree(['/cursos']);
};
