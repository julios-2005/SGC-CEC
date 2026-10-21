import { Router } from '@angular/router';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  beforeEach(() => sessionStorage.clear());

  it('reemplaza el token de la sesión sin tocar al usuario (cambio de contraseña)', () => {
    const auth = new AuthService({} as ApiService, {} as Router);
    sessionStorage.setItem('cec_access_token', 'viejo');
    sessionStorage.setItem('cec_user', JSON.stringify({ id: 1, rol: 'COORDINADOR' }));

    auth.replaceToken('nuevo');

    expect(auth.token()).toBe('nuevo');
    expect(JSON.parse(sessionStorage.getItem('cec_user')!).id).toBe(1);
  });

  it('clearSession elimina token, usuario y bandera de cambio obligatorio', () => {
    const auth = new AuthService({} as ApiService, {} as Router);
    sessionStorage.setItem('cec_access_token', 't');
    auth.setPasswordChangeRequired(true);

    auth.clearSession();

    expect(auth.token()).toBeNull();
    expect(auth.passwordChangeRequired()).toBe(false);
  });
});
