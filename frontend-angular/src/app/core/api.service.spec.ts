import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiService } from './api.service';

describe('Contrato REST de ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ApiService); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('envía enums literales, false y página cero; omite solamente filtros vacíos', () => {
    api.get('cursos', { estado: 'EN_ESPERA', modalidad: 'VIRTUAL', page: 0, activo: false, texto: '', nulo: null }).subscribe();
    const request = http.expectOne(req => req.url.endsWith('/cursos'));
    expect(request.request.params.get('estado')).toBe('EN_ESPERA');
    expect(request.request.params.get('modalidad')).toBe('VIRTUAL');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('activo')).toBe('false');
    expect(request.request.params.has('texto')).toBe(false);
    expect(request.request.params.has('nulo')).toBe(false);
    request.flush({});
  });

  it('carga todas las páginas del panel de revisión, sin recortar a 1000 registros', () => {
    let result: number[] = [];
    api.allPages<number>('inscripciones').subscribe(data => result = data);
    http.expectOne(req => req.params.get('page') === '0').flush({ number: 0, totalPages: 3, content: [1] });
    http.expectOne(req => req.params.get('page') === '1').flush({ number: 1, totalPages: 3, content: [2] });
    http.expectOne(req => req.params.get('page') === '2').flush({ number: 2, totalPages: 3, content: [3] });
    expect(result).toEqual([1, 2, 3]);
  });

  it('limita la cabecera JWT al origen y ruta de la API configurada', () => {
    expect(api.isApiUrl(api.url('cursos'))).toBe(true);
    expect(api.isApiUrl('https://otro-servidor.example/api/cursos')).toBe(false);
    expect(api.isApiUrl('http://localhost:8080/api-falsa/cursos')).toBe(false);
  });

  it('muestra los mensajes de validación de cada campo del backend', () => {
    expect(api.errorMessage({ error: { mensaje: 'Error', errores: { nombre: 'Nombre requerido.', correo: 'Correo inválido.' } } })).toBe('Nombre requerido. Correo inválido.');
  });
});
