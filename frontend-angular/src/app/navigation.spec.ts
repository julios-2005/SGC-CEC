import { provideZoneChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { NEVER, of } from 'rxjs';
import { routes } from './app.routes';
import { ApiService } from './core/api.service';
import { AuthService } from './core/auth.service';

describe('Navegación y conservación de sesión', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideZoneChangeDetection(), provideRouter(routes), { provide: ApiService, useValue: {
      get: (path: string) => of(['cursos', 'especialistas', 'coordinadores', 'inscripciones', 'usuarios', 'planificaciones'].includes(path) ? { content: [], totalPages: 0 } : []),
      blob: () => NEVER, errorMessage: () => 'Error',
    } }] });
  });
  afterEach(() => sessionStorage.clear());

  it('la raíz pública abre el catálogo, no fuerza el login', async () => {
    const harness = await RouterTestingHarness.create('/');
    expect(TestBed.inject(Router).url).toBe('/cursos-disponibles');
    expect(harness.routeNativeElement?.textContent).toContain('Cursos Disponibles');
    expect(harness.routeNativeElement?.querySelector('.sidebar')).toBeNull();
  });

  it('al navegar a catálogo e inscripción conserva token y menú', async () => {
    sessionStorage.setItem('cec_access_token', 'token-ficticio-solo-para-pruebas');
    sessionStorage.setItem('cec_user', JSON.stringify({ id: 1, nombreUsuario: 'prueba', nombre: 'Usuario de prueba', rol: 'ADMIN_GENERAL' }));
    const harness = await RouterTestingHarness.create('/cursos');
    for (const route of ['/cursos-disponibles', '/inscripcion', '/planificaciones', '/informe-economico']) {
      await harness.navigateByUrl(route);
      expect(TestBed.inject(AuthService).token()).toBe('token-ficticio-solo-para-pruebas');
      expect(harness.routeNativeElement?.querySelectorAll('.sidebar').length).toBe(1);
      expect(harness.routeNativeElement?.textContent).toContain('SEGURIDAD');
      expect(harness.routeNativeElement?.textContent).toContain('Gestión de Usuarios');
      expect(harness.routeNativeElement?.textContent).not.toContain('Mi Perfil');
      expect(TestBed.inject(Router).url).toBe(route);
    }
  });

  it('mantiene Seguridad visible para coordinadores sin exponer Gestión de Usuarios', async () => {
    sessionStorage.setItem('cec_access_token', 'token-coordinador');
    sessionStorage.setItem('cec_user', JSON.stringify({ id: 2, nombreUsuario: 'coord', nombre: 'Coordinador', rol: 'COORDINADOR' }));
    const harness = await RouterTestingHarness.create('/cursos');

    expect(harness.routeNativeElement?.textContent).toContain('SEGURIDAD');
    expect(harness.routeNativeElement?.textContent).toContain('Cambiar Contraseña');
    expect(harness.routeNativeElement?.textContent).not.toContain('Gestión de Usuarios');
    expect(harness.routeNativeElement?.textContent).not.toContain('Mi Perfil');
  });

  it('los enlaces .html originales conservan el identificador de inscripción', async () => {
    await RouterTestingHarness.create('/inscripcion-form.html?idPlanificacion=1');
    expect(TestBed.inject(Router).url).toBe('/inscripcion?idPlanificacion=1');
  });

  it('protege las pantallas privadas y el cambio de contraseña obligatorio', async () => {
    const harness = await RouterTestingHarness.create('/usuarios');
    expect(TestBed.inject(Router).url).toBe('/login');
    const auth = TestBed.inject(AuthService);
    sessionStorage.setItem('cec_access_token', 'token-ficticio');
    auth.user.set({ id: 1, nombreUsuario: 'prueba', nombre: 'Prueba', rol: 'COORDINADOR' });
    auth.setPasswordChangeRequired(true);
    await harness.navigateByUrl('/cursos');
    expect(TestBed.inject(Router).url).toBe('/cambiar-contrasena-obligatoria');
  });
});
