import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiService } from '../core/api.service';
import { InformeResumen } from '../core/models';
import { ReportComponent } from './report-page.component';

describe('Informe económico consolidado CEC 2026', () => {
  it('incluye al participante y los importes de la vigésima programación, y excluye cursos de años anteriores', () => {
    const reports: InformeResumen[] = Array.from({ length: 33 }, (_, index) => ({
      idPlanificacion: index + 1,
      nombreCurso: `Curso ${index + 1} - Primera programación`,
      modalidad: 'VIRTUAL',
      fechaInicio: '2026-01-01',
      participantes: index === 3 ? 1214 : 0,
      ingresos: index === 3 ? 117005 : 0,
      egresos: index === 3 ? 30100 : 0,
      utilidad: index === 3 ? 86905 : 0,
    }));
    reports[0] = { ...reports[0], nombreCurso: 'Suficiencia en Inglés Nivel B2 - Cuarta programación', participantes: 7, ingresos: 675, egresos: 1000, utilidad: -325 };
    reports[1] = { ...reports[1], nombreCurso: 'Agentes Inteligentes Aplicados a la Docencia y la Investigación Científica', participantes: 9, ingresos: 700, egresos: 1100, utilidad: -400 };
    reports[2] = { ...reports[2], nombreCurso: 'Suficiencia en Inglés Nivel A2 - Tercera programación', participantes: 9, ingresos: 870, egresos: 900, utilidad: -30 };
    reports.push(
      { idPlanificacion: 34, nombreCurso: 'Suficiencia en Inglés Nivel B1 - Vigésima programación', modalidad: 'VIRTUAL', fechaInicio: '2026-09-07', participantes: 1, ingresos: 100, egresos: 900, utilidad: -800 },
      { idPlanificacion: 35, nombreCurso: 'Enfermería 2025', modalidad: 'VIRTUAL', fechaInicio: '2025-02-15', participantes: 10, ingresos: 10000, egresos: 3000, utilidad: 7000 },
      { idPlanificacion: 36, nombreCurso: 'Diplomado 2025', modalidad: 'VIRTUAL', fechaInicio: '2025-07-17', participantes: 20, ingresos: 20000, egresos: 8000, utilidad: 12000 },
      { idPlanificacion: 37, nombreCurso: 'Diplomado internacional 2025', modalidad: 'VIRTUAL', fechaInicio: '2025-11-18', participantes: 30, ingresos: 30000, egresos: 12000, utilidad: 18000 },
    );
    const api = {
      get: (path: string) => of(path.includes('/consolidado/') ? {anio: 2026, gastosPersonal: 55960, version: 0} : reports),
      download: () => undefined,
      errorMessage: () => 'Error de prueba',
    };

    TestBed.configureTestingModule({
      imports: [ReportComponent],
      providers: [{ provide: ApiService, useValue: api }],
    });
    const fixture = TestBed.createComponent(ReportComponent);
    fixture.detectChanges();

    // Solo los 34 cursos dictados en 2026 (33 iniciales + la vigésima programación);
    // los 3 registros de 2025 (Enfermería, Diplomado y Diplomado internacional) no
    // deben aparecer en la tabla general del año 2026.
    expect(fixture.componentInstance.items).toHaveLength(34);
    expect(fixture.componentInstance.items.every(item => item.fechaInicio?.startsWith('2026'))).toBe(true);
    expect(fixture.componentInstance.consolidatedCec2026).toBe(true);
    expect(fixture.componentInstance.totals).toEqual({
      participantes: 1240,
      ingresos: 119350,
      egresos: 34000,
      utilidad: 85350,
      gastosPersonal: 55960,
      utilidadCec: 29390,
    });
    expect(fixture.nativeElement.textContent).toContain('TOTAL');
    expect(fixture.nativeElement.textContent).toContain('GASTOS DE PERSONAL CEC');
    expect(fixture.nativeElement.textContent).toContain('UTILIDAD CEC 2026:');
    expect(fixture.nativeElement.textContent).toContain('$85,350.00');
    expect(fixture.nativeElement.textContent).toContain('$29,390.00');
    expect(fixture.nativeElement.textContent).not.toContain('Enfermería 2025');
    expect(fixture.nativeElement.textContent).not.toContain('Diplomado 2025');
    expect(fixture.nativeElement.textContent).not.toContain('Diplomado internacional 2025');
  });
});
