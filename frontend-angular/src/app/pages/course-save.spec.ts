import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { provideZoneChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CoursesComponent } from './courses.component';

const course = {idCurso: 90, nombre: 'Curso de prueba del formulario', codigo: 'CEC-GUARDAR',
  horas: 40, costo: 125.5, cuposTotales: 30, cuposRestantes: 30,
  modalidad: 'VIRTUAL', estado: 'EN_ESPERA', ambito: 'NACIONAL'};
const page = (content: unknown[] = []) => ({content, totalPages: content.length ? 1 : 0});

describe('Guardar cursos desde sus controles y el botón de la interfaz', () => {
  let fixture: ComponentFixture<CoursesComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({imports: [CoursesComponent], providers: [
      provideZoneChangeDetection({eventCoalescing: true}), provideHttpClient(), provideHttpClientTesting(),
    ]});
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CoursesComponent);
    fixture.autoDetectChanges();
    await fixture.whenStable();
    reply(http.expectOne(r => r.url.endsWith('/cursos')), page());
    await click('#btnAgregar');
    await fixture.whenStable();
  });

  afterEach(() => { http.verify(); fixture.destroy(); });

  function reply(request: TestRequest, body: Parameters<TestRequest['flush']>[0], status = 200, statusText = 'OK') {
    // Las respuestas HTTP del navegador llegan dentro de NgZone.
    fixture.ngZone!.run(() => request.flush(body, {status, statusText}));
  }

  async function click(selector: string) {
    fixture.ngZone!.run(() => (fixture.nativeElement.querySelector(selector) as HTMLElement).click());
    await fixture.whenStable();
  }

  async function input(name: string, value: string) {
    const element = fixture.nativeElement.querySelector(`[name="${name}"]`) as HTMLInputElement | HTMLSelectElement;
    fixture.ngZone!.run(() => {
      element.value = value;
      element.dispatchEvent(new Event(element.tagName === 'SELECT' ? 'change' : 'input', {bubbles: true}));
    });
    await fixture.whenStable();
  }

  async function fillCourse() {
    for (const [name, value] of Object.entries({nombre: course.nombre, codigo: course.codigo,
      horas: '40', costo: '125.50', cuposTotales: '30', modalidad: 'VIRTUAL'})) await input(name, value);
  }

  it('guarda con los cupos restantes opcionales vacíos, sin ningún campo de especialistas', async () => {
    await fillCourse();
    await click('#btnGuardar');
    const request = http.expectOne(r => r.method === 'POST' && r.url.endsWith('/cursos'));
    const body = request.request.body as FormData;
    expect(body.get('nombre')).toBe(course.nombre);
    expect(body.get('costo')).toBe('125.5');
    expect(body.has('docenteIds')).toBe(false);
    expect(body.has('cuposRestantes')).toBe(false);
    reply(request, course, 201, 'Created');
    reply(http.expectOne(r => r.method === 'GET' && r.url.endsWith('/cursos')), page([course]));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#modalFondo')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain(course.nombre);
  });

  it('identifica el campo inválido y lleva el foco al mensaje, sin un clic silencioso', async () => {
    await fillCourse();
    await input('modalidad', '');
    await click('#btnGuardar');
    http.expectNone(r => r.method === 'POST');
    const message = fixture.nativeElement.querySelector('#mensajeFormError');
    expect(message.textContent).toContain('Modalidad');
    expect(document.activeElement).toBe(message);
    expect(fixture.nativeElement.querySelector('#btnGuardar').disabled).toBe(false);
  });

  it('muestra el rechazo del servidor y permite corregir el código y volver a guardar', async () => {
    await fillCourse();
    await click('#btnGuardar');
    const first = http.expectOne(r => r.method === 'POST');
    expect(fixture.nativeElement.querySelector('#btnGuardar').disabled).toBe(true);
    reply(first, {mensaje: 'Ya existe un curso registrado con ese código.'}, 400, 'Bad Request');
    await fixture.whenStable();
    const message = fixture.nativeElement.querySelector('#mensajeFormError');
    expect(message.textContent).toContain('ese código');
    expect(document.activeElement).toBe(message);
    expect(fixture.nativeElement.querySelector('#btnGuardar').disabled).toBe(false);
    expect(fixture.nativeElement.querySelector('[name="nombre"]').value).toBe(course.nombre);
    await input('codigo', 'CEC-GUARDAR-2');
    await click('#btnGuardar');
    const retry = http.expectOne(r => r.method === 'POST');
    expect((retry.request.body as FormData).get('codigo')).toBe('CEC-GUARDAR-2');
    reply(retry, {...course, codigo: 'CEC-GUARDAR-2'}, 201, 'Created');
    reply(http.expectOne(r => r.method === 'GET' && r.url.endsWith('/cursos')), page([{...course, codigo: 'CEC-GUARDAR-2'}]));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#modalFondo')).toBeNull();
  });

  it('guarda un diplomado internacional con su ámbito, sin pedir especialistas y evita enviar dos veces', async () => {
    await fillCourse();
    await input('nombre', 'DIPLOMADO DE PRUEBA');
    await input('ambito', 'INTERNACIONAL');
    await click('#btnGuardar');
    await click('#btnGuardar');
    const request = http.expectOne(r => r.method === 'POST');
    const body = request.request.body as FormData;
    expect(body.get('ambito')).toBe('INTERNACIONAL');
    expect(body.has('docenteIds')).toBe(false);
    const created = {...course, nombre: 'DIPLOMADO DE PRUEBA', ambito: 'INTERNACIONAL'};
    reply(request, created, 201, 'Created');
    reply(http.expectOne(r => r.method === 'GET' && r.url.endsWith('/cursos')), page([created]));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#modalFondo')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('DIPLOMADO DE PRUEBA');
  });

  it('el mismo botón guarda la edición de un curso por PUT', async () => {
    await click('#cerrarModal');
    await click('#btnLimpiarFiltros');
    reply(http.expectOne(r => r.url.endsWith('/cursos')), page([course]));
    await fixture.whenStable();
    await click('.btn-accion.editar');
    await input('nombre', 'Curso corregido');
    await click('#btnGuardar');
    const request = http.expectOne(r => r.method === 'PUT' && r.url.endsWith('/cursos/90'));
    expect((request.request.body as FormData).get('nombre')).toBe('Curso corregido');
    reply(request, {...course, nombre: 'Curso corregido'});
    reply(http.expectOne(r => r.method === 'GET' && r.url.endsWith('/cursos')), page([{...course, nombre: 'Curso corregido'}]));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#modalFondo')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Curso corregido');
  });

  it('el fondo no cancela los clics internos y sigue cerrando al pulsar fuera del formulario', async () => {
    const event = new MouseEvent('click', {bubbles: true, cancelable: true});
    fixture.ngZone!.run(() => fixture.nativeElement.querySelector('[name="nombre"]').dispatchEvent(event));
    await fixture.whenStable();
    expect(event.defaultPrevented).toBe(false);
    expect(fixture.nativeElement.querySelector('#modalFondo')).not.toBeNull();
    await click('#modalFondo');
    expect(fixture.nativeElement.querySelector('#modalFondo')).toBeNull();
  });
});
