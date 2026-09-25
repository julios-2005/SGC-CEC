import { NgZone, provideZoneChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NgForm } from '@angular/forms';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { fileValidationMessage, isValidPhone } from '../core/form-validation';
import { CoursesComponent } from './courses.component';
import { EnrollmentComponent } from './enrollment.component';

const validForm = () => ({ invalid: false, control: { markAllAsTouched: vi.fn() }, resetForm: vi.fn() }) as unknown as NgForm;
const mockAlerts = () => ({ success: vi.fn(), error: vi.fn(), close: vi.fn(), confirm: vi.fn(() => Promise.resolve(true)) }) as any;
const course = { idCurso: 1, nombre: 'Curso de prueba', codigo: 'CEC-01', horas: 20, costo: 100, cuposTotales: 30, cuposRestantes: 30, estado: 'EN_ESPERA', modalidad: 'VIRTUAL' };
const plan = { id: 1, curso: course, coordinador: { id: 1, nombres: 'Coord', apellidos: 'Prueba' }, especialista: { id: 1, nombres: 'Esp', apellidos: 'Prueba' }, fechaInicio: '2026-09-01', fechaFin: '2026-09-30', horario: '18:00', modalidad: 'VIRTUAL' };

describe('Validaciones y contratos de formularios', () => {
  it('acepta los mismos formatos de WhatsApp que el backend', () => {
    for (const value of ['+593 99 123 4567', '0991234567', '00593991234567', '+593 (99) 123-4567']) expect(isValidPhone(value)).toBe(true);
    for (const value of ['', 'abc', '++593991234567']) expect(isValidPhone(value)).toBe(false);
  });
  it('cupos restantes vacíos se omiten para que el backend use los cupos totales', () => {
    const api = { post: vi.fn(() => of(course)), get: () => of({ content: [], totalPages: 0 }), errorMessage: () => '' };
    const component = new CoursesComponent(api as unknown as ApiService, mockAlerts());
    component.form = { ...course, nombre: ' Curso de prueba ', cuposRestantes: null };
    component.save(validForm());
    const sent = api.post.mock.calls[0] as unknown as [string, FormData];
    expect(sent[1].has('cuposRestantes')).toBe(false);
    expect(sent[1].get('cuposTotales')).toBe('30');
    expect(sent[1].get('nombre')).toBe('Curso de prueba');
  });

  it('no envía formularios incompletos ni cupos fraccionarios', () => {
    const api = { post: vi.fn() };
    const component = new CoursesComponent(api as unknown as ApiService, mockAlerts());
    const form = validForm();
    component.form = { ...course, cuposTotales: 1.5 };
    component.save(form);
    expect(api.post).not.toHaveBeenCalled();
    expect(component.error).toContain('enteros');
  });

  it('bloquea archivos vacíos, ejecutables y documentos de más de 5 MB', () => {
    expect(fileValidationMessage(new File([], 'vacio.pdf'))).not.toBe('');
    expect(fileValidationMessage(new File(['test'], 'programa.exe'))).not.toBe('');
    expect(fileValidationMessage(new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'grande.pdf'))).not.toBe('');
    expect(fileValidationMessage(new File(['test'], 'prueba.pdf'))).toBe('');
  });

  it('los cursos y descuentos de inscripción aparecen sin hacer clic y acepta enlaces antiguos', async () => {
    const plans = new Subject<unknown>();
    const discounts = new Subject<unknown>();
    TestBed.configureTestingModule({
      imports: [EnrollmentComponent],
      providers: [provideZoneChangeDetection(), provideRouter([]),
        { provide: ApiService, useValue: { get: (path: string) => path === 'planificaciones/vigentes' ? plans : discounts, errorMessage: () => 'Error' } },
        { provide: AuthService, useValue: { isAuthenticated: () => false } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ idPlanificacion: '1' }) } } },
      ],
    });
    const fixture = TestBed.createComponent(EnrollmentComponent);
    fixture.autoDetectChanges();
    await fixture.whenStable();
    TestBed.inject(NgZone).run(() => setTimeout(() => {
      plans.next([plan]); plans.complete(); discounts.next([{ tipoUsuario: 'ESTUDIANTE_UPSE', porcentaje: 20 }]); discounts.complete();
    }, 0));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#idPlanificacion').textContent).toContain(course.nombre);
    expect(fixture.componentInstance.model.idPlanificacion).toBe(1);
    expect(fixture.componentInstance.ready).toBe(true);
    fixture.componentInstance.model.tipoUsuario = 'ESTUDIANTE_UPSE';
    expect(fixture.componentInstance.totalToPay).toBe(80);
    fixture.destroy();
  });

  it('no muestra un total sin descuento ni permite enviar si falla su consulta', () => {
    const api = { get: (path: string) => path.endsWith('vigentes') ? of([plan]) : throwError(() => new Error()), errorMessage: () => 'No se cargaron los descuentos', post: vi.fn() };
    const alerts = mockAlerts();
    const component = new EnrollmentComponent(api as unknown as ApiService, { snapshot: { queryParamMap: convertToParamMap({}) } } as ActivatedRoute, alerts);
    component.ngOnInit();
    expect(component.ready).toBe(false);
    expect(alerts.error).toHaveBeenCalledWith(expect.stringContaining('descuentos'));
    component.submit(validForm(), document.createElement('form'));
    expect(api.post).not.toHaveBeenCalled();
  });

  it('limpia el respaldo al cambiar el tipo de participante', () => {
    const component = new EnrollmentComponent({} as ApiService, {} as ActivatedRoute, mockAlerts());
    component.files['documentoAdicional'] = new File(['x'], 'respaldo.pdf');
    component.typeChanged('EXTERNO', document.createElement('input'));
    expect(component.files['documentoAdicional']).toBeNull();
    expect(component.documentLabel).toBe('');
  });

  it('exige el respaldo del estudiante, pero mantiene opcional el del docente UPSE', () => {
    const api = { post: vi.fn(() => new Subject()), errorMessage: () => '' };
    const alerts = mockAlerts();
    const component = new EnrollmentComponent(api as unknown as ApiService, {} as ActivatedRoute, alerts);
    component.planes = [plan as any]; component.ready = true;
    component.model = { idPlanificacion: 1, tipoUsuario: 'ESTUDIANTE_UPSE', nombreCompleto: 'Persona de Prueba', cedula: '1234567890', telefono: '+593991234567', correoElectronico: 'prueba@example.test', direccion: 'Dirección', sexo: 'FEMENINO' };
    component.files = { comprobantePago: new File(['pdf'], 'pago.pdf'), copiaCedula: new File(['pdf'], 'cedula.pdf') };
    component.submit(validForm(), document.createElement('form'));
    expect(api.post).not.toHaveBeenCalled();
    expect(alerts.error).toHaveBeenCalledWith(expect.stringContaining('respaldo'));
    component.model.tipoUsuario = 'DOCENTE_UPSE';
    component.submit(validForm(), document.createElement('form'));
    expect(api.post).toHaveBeenCalledTimes(1);
  });
});
