import { Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { ApiService } from './api.service';
import { LoginResponse, SessionUser } from './models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private static readonly TOKEN_KEY = 'cec_access_token';
  private static readonly USER_KEY = 'cec_user';
  private static readonly PASSWORD_CHANGE_KEY = 'cec_password_change_required';
  readonly user = signal<SessionUser | null>(this.readUser());
  readonly passwordChangeRequired = signal(sessionStorage.getItem(AuthService.PASSWORD_CHANGE_KEY) === 'true');

  constructor(private readonly api: ApiService, private readonly router: Router) {}

  login(usuario: string, contraseña: string) {
    return this.api.post<LoginResponse>('auth/login', { usuario, contraseña }).pipe(
      tap(response => {
        if (response.exito && response.token && response.usuario) {
          sessionStorage.setItem(AuthService.TOKEN_KEY, response.token);
          sessionStorage.setItem(AuthService.USER_KEY, JSON.stringify(response.usuario));
          this.user.set(response.usuario);
          this.setPasswordChangeRequired(Boolean(response.debeCambiarContraseña));
        }
      }),
    );
  }

  token(): string | null {
    return sessionStorage.getItem(AuthService.TOKEN_KEY);
  }

  isAuthenticated(): boolean {
    return Boolean(this.token() && this.user());
  }

  isAdmin(): boolean {
    return this.user()?.rol === 'ADMIN_GENERAL';
  }

  isCobros(): boolean {
    return this.user()?.rol === 'COBROS';
  }

  /** Ruta a la que se debe llevar al usuario tras iniciar sesión o cambiar su contraseña. */
  landingRoute(): string {
    return this.isCobros() ? '/panel-inscripciones' : '/cursos-disponibles';
  }

  logout(): void {
    const finish = () => {
      this.clearSession();
      this.router.navigateByUrl('/login');
    };
    this.api.post('auth/logout', {}).subscribe({ next: finish, error: finish });
  }

  clearSession(): void {
    sessionStorage.removeItem(AuthService.TOKEN_KEY);
    sessionStorage.removeItem(AuthService.USER_KEY);
    sessionStorage.removeItem(AuthService.PASSWORD_CHANGE_KEY);
    this.user.set(null);
    this.passwordChangeRequired.set(false);
  }

  setPasswordChangeRequired(required: boolean): void {
    this.passwordChangeRequired.set(required);
    sessionStorage.setItem(AuthService.PASSWORD_CHANGE_KEY, String(required));
  }

  private readUser(): SessionUser | null {
    try {
      const value = sessionStorage.getItem(AuthService.USER_KEY);
      return value ? JSON.parse(value) : null;
    } catch {
      return null;
    }
  }
}
