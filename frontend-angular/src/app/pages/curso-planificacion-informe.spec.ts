import { provideZoneChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NgForm } from '@angular/forms';
import { of } from 'rxjs';
import { ApiService } from '../core/api.service';
import { AlertService } from '../core/alert.service';
import { CoursesComponent } from './courses.component';
import { PlanningComponent } from './planning-page.component';
import { ReportComponent } from './report-page.component';
const valid = () => ({invalid: false, control: {markAllAsTouched: vi.fn()}}) as unknown as NgForm;
const mockAlerts = () => ({ success: vi.fn(), error: vi.fn(), close: vi.fn(), confirm: vi.fn(() => Promise.resolve(true)) }) as any;
const teachers = Array.from({length: 5}, (_, i) => ({id: i + 1, nombres: 'Especialista ' + i, apellidos: 'Prueba'}));
const course = {idCurso: 10, nombre: 'Diplomado internacional', codigo: 'CEC-T', horas: 40, costo: 100,
  cuposTotales: 30, cuposRestantes: 30, modalidad: 'VIRTUAL', estado: 'EN_ESPERA',
  ambito: 'INTERNACIONAL' as const};
const plan = {id: 20, curso: course, coordinador: teachers[0], especialista: teachers[0],
  docentes: teachers, fechaInicio: '2026-11-01', fechaFin: '2026-11-30', horario: '18:00', modalidad: 'VIRTUAL',
  costoEspecialista: 200};
const report = {idPlanificacion: 20, nombreCurso: course.nombre, fechaInicio: '2026-11-01',
  modalidad: 'VIRTUAL', participantes: 2, ingresos: 150, egresos: 200, utilidad: -50};

describe('Curso, planificación e informe económico', () => {
  it('el formulario real de curso envía el ámbito, sin ningún campo de especialistas', async () => {
    const api = {
      get: (path: string) => of(path === 'cursos' ? {content: [], totalPages: 0} : teachers),
      post: vi.fn(() => of(course)), errorMessage: () => 'Error',
    };
    TestBed.configureTestingModule({imports: [CoursesComponent],
      providers: [provideZoneChangeDetection(), {provide: ApiService, useValue: api}, {provide: AlertService, useValue: mockAlerts()}]});
    const fixture = TestBed.createComponent(CoursesComponent);
    fixture.autoDetectChanges(); await fixture.whenStable();
    const c = fixture.componentInstance;
    c.open(); c.form = {...course};
    fixture.detectChanges(); await fixture.whenStable();
    // Los especialistas ya no se eligen en el formulario de curso (se
    // eligen al registrar la planificación), así que no debe quedar
    // ningún <select> de docente/especialista aquí: solo modalidad, estado
    // y ámbito.
    expect(fixture.nativeElement.querySelectorAll('form select').length).toBe(3);
    fixture.ngZone!.run(() => fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', {bubbles: true, cancelable: true})));
    await fixture.whenStable();
    expect(api.post).toHaveBeenCalledOnce();
    const body = (api.post.mock.calls[0] as unknown as [string, FormData])[1];
    expect(body.get('ambito')).toBe('INTERNACIONAL');
    expect(body.has('docenteIds')).toBe(false);
    expect(c.modalOpen).toBe(false); fixture.destroy();
  });

  it('elegir un curso para una planificación nueva parte de un especialista vacío, sin copiar nada del curso', () => {
    const api = {post: vi.fn(() => of(plan)), get: () => of({content: [], totalPages: 0}), errorMessage: () => ''};
    const c = new PlanningComponent(api as unknown as ApiService, mockAlerts());
    c.courses = [course]; c.form = {idCurso: 10, idCoordinador: 1, idEspecialista: null,
      fechaInicio: plan.fechaInicio, fechaFin: plan.fechaFin, horario: plan.horario, costoEspecialista: 200};
    c.chooseCourse();
    expect(c.docenteIds).toEqual([null]);
    expect(c.teacherCount).toBe(1);
    c.docenteIds = [1]; c.save(valid());
    const body = (api.post.mock.calls[0] as unknown as [string, import('@angular/common/http').HttpParams])[1];
    expect(body.getAll('docenteIds')).toEqual(['1']);
    expect(body.get('idEspecialista')).toBe('1'); expect(body.get('costoEspecialista')).toBe('200');
    expect(c.error).toBe('');
  });

  it('el consolidado suma cursos nuevos sin depender del número de filas ni importes fijos', () => {
    const api = {get: (path: string) => of(path.includes('consolidado') ? {gastosPersonal: 25.50, version: 2} :
      [report, {...report, idPlanificacion: 21, participantes: 1, ingresos: 80.25, egresos: 10},
        {...report, idPlanificacion: 22, fechaInicio: '2025-01-01', ingresos: 9999}])};
    const c = new ReportComponent(api as ApiService, mockAlerts()); c.load();
    // La vista anual (sin filtros) solo muestra los cursos del año
    // seleccionado: el tercer curso (2025) queda fuera de la tabla, tal
    // como explica la nota de la pantalla ("Los cursos de años anteriores
    // no se muestran en esta tabla").
    expect(c.items.length).toBe(2);
    expect(c.totals).toEqual({participantes: 3, ingresos: 230.25, egresos: 210, utilidad: 20.25,
      gastosPersonal: 25.5, utilidadCec: -5.25});
    c.filters.modalidad = 'VIRTUAL'; c.load();
    expect(c.annualView).toBe(false); expect(c.totals.gastosPersonal).toBe(0);
  });

  it('guardar gastos usa la versión leída y actualiza la utilidad, sin alterar los egresos del curso', () => {
    const api = {get: (path: string) => of(path.includes('consolidado') ? {gastosPersonal: 25, version: 3} : [report]),
      put: vi.fn(() => of({gastosPersonal: 10, version: 4})), errorMessage: () => ''};
    const c = new ReportComponent(api as unknown as ApiService, mockAlerts()); c.load(); c.beginOverhead();
    c.overheadAmount = 10; c.saveOverhead(valid());
    expect(api.put).toHaveBeenCalledWith('informes-economicos/gastos-cec/2026', {importe: 10, version: 3});
    expect(c.totals.egresos).toBe(200); expect(c.totals.utilidadCec).toBe(-60);
  });

  it('guardar egresos usa el informe abierto y refleja el total devuelto por el servidor', () => {
    const detail = {...report, especialista: 'Especialista', coordinador: 'Coord', fechaFin: '2026-11-30',
      ingresos: [], ingresosTotal: 150, egresos: [], egresosTotal: 225, utilidad: -75};
    const api = {post: vi.fn(() => of(detail)), errorMessage: () => ''};
    const c = new ReportComponent(api as unknown as ApiService, mockAlerts()); c.detail = {...detail, egresosTotal: 200};
    c.beginExpense(); c.expenseForm = {concepto: 'Materiales', cantidad: 2, valorUnitario: 12.5};
    c.saveExpense(valid());
    expect(api.post).toHaveBeenCalledWith('informes-economicos/20/egresos', c.expenseForm || {concepto: 'Materiales', cantidad: 2, valorUnitario: 12.5});
    expect(c.detail.egresosTotal).toBe(225); expect(c.expenseForm).toBeUndefined();
  });
});
