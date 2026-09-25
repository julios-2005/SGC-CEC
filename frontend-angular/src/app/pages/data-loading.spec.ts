import { NgZone, Type, provideZoneChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NEVER, Observable, Subject, of } from 'rxjs';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { CoursesComponent } from './courses.component';
import { CoordinatorsComponent } from './coordinators.component';
import { SpecialistsComponent } from './specialists.component';
import { PlanningComponent } from './planning-page.component';
import { ReportComponent } from './report-page.component';
import { RegistrationsComponent } from './registrations.component';
import { RegistrationReviewComponent } from './registration-review.component';
import { UsersComponent } from './users-page.component';
import { PublicCoursesComponent } from './public-courses.component';

const course = { idCurso: 1, nombre: 'Curso guardado previamente', codigo: 'CEC-01', horas: 20, costo: 50, cuposTotales: 30, cuposRestantes: 25, modalidad: 'VIRTUAL', estado: 'EN_ESPERA' };
const person = { id: 1, nombres: 'Persona guardada', apellidos: 'Anteriormente', cedula: '1234567890', estado: true };
const plan = { id: 1, curso: course, coordinador: person, especialista: person, fechaInicio: '2026-09-01', fechaFin: '2026-10-01', horario: '18:00', modalidad: 'VIRTUAL' };
const registration = { id: 1, nombreCompleto: 'Estudiante guardado', planificacion: { id: 1, nombreCurso: course.nombre, horario: '18:00' }, cedula: '1234567890', estado: 'PENDIENTE', tipoUsuario: 'EXTERNO', fechaRegistro: '2026-08-01T12:00:00' };
const page = (content: unknown[]) => ({ content, number: 0, size: 20, totalElements: content.length, totalPages: 1 });

describe('Los datos asíncronos se pintan sin hacer clic', () => {
  const cases: Array<{ name: string; component: Type<unknown>; endpoint: string; payload: unknown; text: string; allPages?: boolean }> = [
    { name: 'cursos', component: CoursesComponent, endpoint: 'cursos', payload: page([course]), text: course.nombre },
    { name: 'especialistas', component: SpecialistsComponent, endpoint: 'especialistas', payload: page([person]), text: person.nombres },
    { name: 'coordinadores', component: CoordinatorsComponent, endpoint: 'coordinadores', payload: page([person]), text: person.nombres },
    { name: 'planificaciones', component: PlanningComponent, endpoint: 'planificaciones', payload: page([plan]), text: course.nombre },
    { name: 'informe económico', component: ReportComponent, endpoint: 'informes-economicos', payload: [{ idPlanificacion: 1, nombreCurso: course.nombre, modalidad: 'VIRTUAL', fechaInicio: '2026-09-01', participantes: 2, ingresos: 100, egresos: 30, utilidad: 70 }], text: course.nombre },
    { name: 'inscripciones', component: RegistrationsComponent, endpoint: 'inscripciones', payload: page([registration]), text: registration.nombreCompleto },
    { name: 'revisión', component: RegistrationReviewComponent, endpoint: 'inscripciones', payload: [registration], text: registration.nombreCompleto, allPages: true },
    { name: 'usuarios', component: UsersComponent, endpoint: 'usuarios', payload: page([{ id: 1, nombreUsuario: 'usuario.guardado', rol: 'COORDINADOR', estado: true }]), text: 'usuario.guardado' },
    { name: 'catálogo público', component: PublicCoursesComponent, endpoint: 'planificaciones/vigentes', payload: [plan], text: course.nombre },
  ];

  for (const scenario of cases) {
    it(scenario.name, async () => {
      const response = new Subject<unknown>();
      const api = {
        get: (path: string): Observable<unknown> => path === scenario.endpoint ? response : of(path.endsWith('nacionalidades') ? {} : []),
        allPages: () => response,
        blob: () => NEVER,
        url: (path: string) => `http://localhost:8080/api/${path}`,
        errorMessage: () => 'Error de prueba',
      };
      TestBed.configureTestingModule({
        imports: [scenario.component],
        providers: [provideZoneChangeDetection({ eventCoalescing: true }), provideRouter([]), { provide: ApiService, useValue: api }, { provide: AuthService, useValue: { isAuthenticated: () => true, isAdmin: () => true, isCobros: () => false } }],
      });
      const fixture = TestBed.createComponent(scenario.component);
      fixture.autoDetectChanges();
      await fixture.whenStable();
      expect(fixture.nativeElement.textContent).not.toContain(scenario.text);
      TestBed.inject(NgZone).run(() => setTimeout(() => { response.next(scenario.payload); response.complete(); }, 0));
      await fixture.whenStable();
      // No detectChanges(), clics, botones ni eventos simulados tras la respuesta.
      expect(fixture.nativeElement.textContent).toContain(scenario.text);
      fixture.destroy();
    });
  }
});
