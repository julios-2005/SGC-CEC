import { provideHttpClient, HttpParams } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZoneChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { SpecialistsComponent } from './catalog-pages.component';
import { PlanningComponent } from './planning-page.component';

const page = {content: [], totalPages: 0};
describe('Botones Guardar y honorarios v14 en los formularios reales', () => {
  let fixture: ComponentFixture<any>;
  let http: HttpTestingController;
  async function init(component: typeof SpecialistsComponent | typeof PlanningComponent) {
    TestBed.configureTestingModule({imports: [component], providers: [provideRouter([]),
      provideZoneChangeDetection({eventCoalescing: true}), provideHttpClient(), provideHttpClientTesting()]});
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(component as any);
    fixture.autoDetectChanges();
    await fixture.whenStable();
  }
  function reply(path: string, body: any, method = 'GET') {
    const req = http.expectOne(r => r.url.endsWith('/' + path) && r.method === method);
    fixture.ngZone!.run(() => req.flush(body));
    return req;
  }
  async function click(selector: string) {
    fixture.ngZone!.run(() => fixture.nativeElement.querySelector(selector).click());
    await fixture.whenStable();
  }
  async function field(name: string, value: string, selectIndex?: number) {
    let el = fixture.nativeElement.querySelector(`[name="${name}"]`);
    const dynamic = /^(docente|honorario)(\d+)$/.exec(name);
    if (!el && dynamic) {
      const n = Number(dynamic[2]) + 1;
      const label = dynamic[1] === 'docente' ? `Especialista ${n}` : `Honorario bruto del especialista ${n} ($)`;
      const group = (Array.from(fixture.nativeElement.querySelectorAll('.campo-form')) as HTMLElement[])
        .find(g => g.querySelector('label')?.textContent === label)!;
      el = group.querySelector(dynamic[1] === 'docente' ? 'select' : 'input');
    }
    fixture.ngZone!.run(() => {
      el.value = selectIndex == null ? value : el.options[selectIndex].value;
      el.dispatchEvent(new Event(el.tagName === 'SELECT' ? 'change' : 'input', {bubbles: true}));
    });
    await fixture.whenStable();
  }
  afterEach(() => { http.verify(); fixture.destroy(); });

  it('envía un especialista extranjero con el clic nativo en Guardar', async () => {
    await init(SpecialistsComponent);
    reply('especialistas', page);
    reply('especialistas/nacionalidades', {EC: ['Ecuador'], CO: ['Colombia']});
    await click('#btnAgregar');
    await field('cedula', 'PAS12345'); await field('nombres', 'Ana'); await field('apellidos', 'Prueba');
    await field('paisNacionalidad', 'CO');
    await click('#btnGuardar');
    const req = http.expectOne(r => r.method === 'POST' && r.url.endsWith('/especialistas'));
    expect((req.request.body as FormData).get('paisNacionalidad')).toBe('CO');
    expect((req.request.body as FormData).get('nombres')).toBe('Ana');
    fixture.ngZone!.run(() => req.flush({id: 8}));
    reply('especialistas', page);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#modalFondo')).toBeNull();
  });

  async function planning() {
    await init(PlanningComponent);
    reply('planificaciones', page);
    reply('planificaciones/cursos', [{idCurso: 90, nombre: 'Curso prueba', modalidad: 'VIRTUAL'}]);
    reply('planificaciones/coordinadores', [{id: 3, nombres: 'Coordinadora', apellidos: 'CEC'}]);
    reply('planificaciones/especialistas', [{id: 4, nombres: 'Ana', apellidos: 'CO', paisNacionalidad: 'CO'},
      {id: 5, nombres: 'Luis', apellidos: 'EC', paisNacionalidad: 'EC'}]);
    await click('#btnAgregar');
    await field('curso', '', 1); await field('coordinador', '', 1);
    await field('fechaInicio', '2026-11-01'); await field('fechaFin', '2026-11-30');
    await field('horario', '18:00 a 20:00');
  }
  it('guarda varios especialistas y envía los brutos individuales sumando la transferencia como costo adicional', async () => {
    await planning(); await field('cantidadDocentes', '2');
    await field('docente0', '', 1); await field('docente1', '', 2);
    await field('honorario0', '1000'); await field('honorario1', '600');
    // Ana (CO, extranjera): honorario $1000 + 25% de transferencia ($250,
    // costo adicional del CEC) = $1250 total. Luis (EC, nacional): sin
    // transferencia, $600 sin cambios.
    expect(fixture.nativeElement.textContent).toContain('250.00');
    expect(fixture.nativeElement.textContent).toContain('1,250.00');
    await click('#btnGuardar');
    const req = http.expectOne(r => r.method === 'POST' && r.url.endsWith('/planificaciones'));
    const body = req.request.body as HttpParams;
    expect(body.getAll('docenteIds')).toEqual(['4', '5']);
    expect(body.getAll('honorariosDocentes')).toEqual(['1000', '600']);
    expect(body.get('costoEspecialista')).toBe('1600');
    fixture.ngZone!.run(() => req.flush({id: 91})); reply('planificaciones', page);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('#modalFondo')).toBeNull();
  });
  it('muestra los datos incompletos y mantiene abierto el formulario', async () => {
    await planning(); await field('docente0', '', 1); await field('honorario0', '');
    await click('#btnGuardar');
    http.expectNone(r => r.method === 'POST');
    expect(fixture.nativeElement.querySelector('#mensajeFormError').textContent).toContain('obligatorios');
    expect(fixture.nativeElement.querySelector('#modalFondo')).not.toBeNull();
  });
});
