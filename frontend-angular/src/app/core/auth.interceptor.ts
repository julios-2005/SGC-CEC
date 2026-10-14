import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { ApiService } from './api.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const api = inject(ApiService);
  if (!api.isApiUrl(request.url)) return next(request);
  const token = auth.token();
  const authenticatedRequest = token
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;

  return next(authenticatedRequest).pipe(
    catchError(error => {
      if (error.status === 401 && token) {
        auth.clearSession();
        router.navigateByUrl('/login');
      } else if (error.status === 403 && error.error?.codigo === 'CAMBIO_CONTRASENA_REQUERIDO') {
        auth.setPasswordChangeRequired(true);
        router.navigateByUrl('/cambiar-contrasena-obligatoria');
      }
      return throwError(() => error);
    }),
  );
};
