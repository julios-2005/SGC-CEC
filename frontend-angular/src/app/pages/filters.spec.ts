import { Type, provideZoneChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from '../core/auth.service';
import { ApiService } from '../core/api.service';
import { CoursesComponent } from './courses.component';
import { CoordinatorsComponent } from './coordinators.component';
import { SpecialistsComponent } from './specialists.component';
import { PlanningComponent } from './planning-page.component';
import { ReportComponent } from './report-page.component';
import { RegistrationsComponent } from './registrations.component';
import { UsersComponent } from './users-page.component';

const emptyPage = { content: [], number: 0, totalPages: 0, totalElements: 0, size: 20 };

describe('El primer cambio de cada filtro usa el valor que se ve seleccionado', () => {
  const cases: Array<{ name: string; component: Type<unknown>; endpoint: string; selector: number; parameter: string; values: string[]; array?: boolean }> = [
    { name: 'estado del curso', component: CoursesComponent, endpoint: 'cursos', selector: 0, parameter: 'estado', values: ['EN_ESPERA', 'FINALIZADO', 'EN_PROCESO', ''] },
    { name: 'modalidad del curso', component: CoursesComponent, endpoint: 'cursos', selector: 1, parameter: 'modalidad', values: ['VIRTUAL', 'PRESENCIAL', 'HIBRIDO', ''] },
    { name: 'modalidad de planificación', component: PlanningComponent, endpoint: 'planificaciones', selector: 0, parameter: 'modalidad', values: ['VIRTUAL', 'PRESENCIAL', 'HIBRIDO', ''] },
    { name: 'modalidad del informe', component: ReportComponent, endpoint: 'informes-economicos', selector: 0, parameter: 'modalidad', values: ['VIRTUAL', 'PRESENCIAL', 'HIBRIDO', ''], array: true },
    { name: 'estado del especialista', component: SpecialistsComponent, endpoint: 'especialistas', selector: 0, parameter: 'estado', values: ['false', 'true', ''] },
    { name: 'estado del coordinador', component: CoordinatorsComponent, endpoint: 'coordinadores', selector: 0, parameter: 'estado', values: ['false', 'true', ''] },
    { name: 'estado de inscripción', component: RegistrationsComponent, endpoint: 'inscripciones', selector: 0, parameter: 'estado', values: ['ACEPTADA', 'RECHAZADA', 'PENDIENTE', 'RETIRADA', ''] },
    { name: 'tipo de participante', component: RegistrationsComponent, endpoint: 'inscripciones', selector: 1, parameter: 'tipoUsuario', values: ['ESTUDIANTE_UPSE', 'EXTERNO', ''] },
    { name: 'rol del usuario', component: UsersComponent, endpoint: 'usuarios', selector: 0, parameter: 'rol', values: ['COORDINADOR', 'ADMIN_GENERAL', ''] },
    { name: 'estado del usuario', component: UsersComponent, endpoint: 'usuarios', selector: 1, parameter: 'estado', values: ['false', 'true', ''] },
  ];

  for (const scenario of cases) {
    it(scenario.name, async () => {
      TestBed.configureTestingModule({
        imports: [scenario.component],
        providers: [provideZoneChangeDetection(), provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: AuthService, useValue: { isAdmin: () => true } }],
      });
      const http = TestBed.inject(HttpTestingController);
      const fixture = TestBed.createComponent(scenario.component);
      fixture.autoDetectChanges();
      http.match(() => true).forEach(request => request.flush(request.request.url.endsWith(`/${scenario.endpoint}`) ? (scenario.array ? [] : emptyPage) : request.request.url.endsWith('/nacionalidades') ? {} : []));
      await fixture.whenStable();
      const select = fixture.nativeElement.querySelectorAll('select')[scenario.selector] as HTMLSelectElement;
      for (const value of scenario.values) {
        fixture.ngZone!.run(() => { select.value = value; select.dispatchEvent(new Event('change')); });
        const request = http.expectOne(req => req.url.endsWith(`/${scenario.endpoint}`));
        expect(request.request.params.get(scenario.parameter)).toBe(value || null);
        if (!scenario.array) expect(request.request.params.get('page')).toBe('0');
        request.flush(scenario.array ? [] : emptyPage);
        http.match(req => req.url.includes('/informes-economicos/consolidado/')).forEach(r =>
          r.flush({anio: 2026, gastosPersonal: 55960, version: 0}));
        await fixture.whenStable();
      }
      http.verify();
      fixture.destroy();
    });
  }

  it('cancela la respuesta pendiente de PRESENCIAL al seleccionar VIRTUAL', async () => {
    TestBed.configureTestingModule({ imports: [CoursesComponent], providers: [provideZoneChangeDetection(), provideHttpClient(), provideHttpClientTesting()] });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(CoursesComponent);
    fixture.autoDetectChanges();
    http.expectOne(req => req.url.endsWith('/cursos')).flush(emptyPage);
    await fixture.whenStable();
    const component = fixture.componentInstance;
    component.filters.modalidad = 'PRESENCIAL'; component.load();
    const oldRequest = http.expectOne(req => req.params.get('modalidad') === 'PRESENCIAL');
    component.filters.modalidad = 'VIRTUAL'; component.load();
    expect(oldRequest.cancelled).toBe(true);
    http.expectOne(req => req.params.get('modalidad') === 'VIRTUAL').flush(emptyPage);
    http.verify(); fixture.destroy();
  });

  it('rechaza un rango de fechas invertido sin enviar una consulta engañosa', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const component = new ReportComponent(TestBed.inject(ApiService), {success: vi.fn(), error: vi.fn(), close: vi.fn(), confirm: vi.fn(() => Promise.resolve(true))} as any);
    component.filters.fechaDesde = '2026-09-15'; component.filters.fechaHasta = '2026-09-01';
    component.load();
    expect(component.error).not.toBe('');
    expect(component.items).toEqual([]);
    TestBed.inject(HttpTestingController).expectNone(req => req.url.endsWith('/informes-economicos'));
  });
});
