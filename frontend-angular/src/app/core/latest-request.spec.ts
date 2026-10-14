import { Observable, Subject, of } from 'rxjs';
import { LatestRequest } from './latest-request';

describe('LatestRequest', () => {
  it('cancela la consulta anterior y no permite sobrescribir el último filtro', () => {
    const requests = new LatestRequest();
    const oldResponse = new Subject<string>();
    const newResponse = new Subject<string>();
    const values: string[] = [];
    requests.run(oldResponse, { next: value => values.push(value) });
    requests.run(newResponse, { next: value => values.push(value) });
    newResponse.next('VIRTUAL');
    oldResponse.next('PRESENCIAL');
    expect(values).toEqual(['VIRTUAL']);
    expect(oldResponse.observed).toBe(false);
    requests.cancel();
    expect(newResponse.observed).toBe(false);
  });

  it('conserva la cancelación incluso si una respuesta sincrónica inicia otra consulta', () => {
    const requests = new LatestRequest();
    let cancelled = false;
    requests.run(of(1), { next: () => requests.run(new Observable(() => () => { cancelled = true; }), {}) });
    requests.cancel();
    expect(cancelled).toBe(true);
  });
});
