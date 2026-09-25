import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { EMPTY, Observable, expand, reduce } from 'rxjs';
import { PageResponse } from './models';
import { AlertService } from './alert.service';

declare global {
  interface Window {
    CEC_CONFIG?: { apiBaseUrl?: string; paymentUrl?: string };
  }
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly baseUrl = (window.CEC_CONFIG?.apiBaseUrl || 'http://localhost:8080/api').replace(/\/$/, '');
  readonly paymentUrl = window.CEC_CONFIG?.paymentUrl || 'https://ep.upse.edu.ec/index.php/formularios/formulario-pagos';

  constructor(private readonly http: HttpClient, private readonly alerts: AlertService) {}

  url(path: string): string {
    return `${this.baseUrl}/${path.replace(/^\//, '')}`;
  }

  isApiUrl(url: string): boolean {
    const base = new URL(this.baseUrl, window.location.origin);
    const target = new URL(url, window.location.origin);
    return target.origin === base.origin &&
      (target.pathname === base.pathname || target.pathname.startsWith(`${base.pathname}/`));
  }

  get<T>(path: string, params?: Record<string, unknown>): Observable<T> {
    return this.http.get<T>(this.url(path), { params: this.params(params) });
  }

  allPages<T>(path: string, params: Record<string, unknown> = {}): Observable<T[]> {
    const page = (number: number) => this.get<PageResponse<T>>(path, { ...params, page: number, size: 200 });
    return page(0).pipe(
      expand(result => result.number + 1 < result.totalPages ? page(result.number + 1) : EMPTY),
      reduce((items, result) => items.concat(result.content), [] as T[]),
    );
  }

  blob(path: string): Observable<Blob> {
    return this.http.get(this.url(path), { responseType: 'blob' });
  }

  post<T>(path: string, body: unknown): Observable<T> {
    return this.http.post<T>(this.url(path), body);
  }

  put<T>(path: string, body: unknown): Observable<T> {
    return this.http.put<T>(this.url(path), body);
  }

  patch<T>(path: string, body: unknown = null, params?: Record<string, unknown>): Observable<T> {
    return this.http.patch<T>(this.url(path), body, { params: this.params(params) });
  }

  delete<T>(path: string): Observable<T> {
    return this.http.delete<T>(this.url(path));
  }

  openFile(path: string): void {
    // Abrir la pestaña durante el clic evita el bloqueo de ventanas emergentes
    // cuando la respuesta autenticada tarda en llegar.
    const tab = window.open('', '_blank');
    if (tab) {
      tab.opener = null;
      tab.document.title = 'Cargando documento…';
    }
    this.http.get(this.url(path), { responseType: 'blob' }).subscribe({
      next: blob => {
        if (tab && !tab.closed) {
          const url = URL.createObjectURL(blob);
          tab.location.href = url;
          setTimeout(() => URL.revokeObjectURL(url), 60_000);
        } else {
          const name = path.split('/').pop() || 'documento';
          this.saveBlob(blob, blob.type === 'application/pdf' && !name.endsWith('.pdf') ? `${name}.pdf` : name);
        }
      },
      error: error => {
        tab?.close();
        void this.showFileError(error);
      },
    });
  }

  download(path: string, filename: string, params?: Record<string, unknown>): void {
    this.http.get(this.url(path), { params: this.params(params), responseType: 'blob' }).subscribe({
      next: blob => this.saveBlob(blob, filename),
      error: error => void this.showFileError(error),
    });
  }

  errorMessage(error: any): string {
    const fieldErrors = error?.error?.errores;
    if (fieldErrors && typeof fieldErrors === 'object') {
      return Object.values(fieldErrors).filter(value => typeof value === 'string').join(' ');
    }
    if (error?.status === 0) return 'No se pudo conectar con el WebService. Comprueba que el backend esté iniciado y que config.js tenga la dirección correcta.';
    if (error?.status === 403 && !error?.error?.mensaje) return 'No tienes permiso para realizar esta operación.';
    return error?.error?.mensaje || error?.error?.message || error?.message || 'No se pudo completar la operación.';
  }

  private saveBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  private async showFileError(error: any): Promise<void> {
    if (error?.error instanceof Blob) {
      try {
        const payload = JSON.parse(await error.error.text());
        this.alerts.error(this.errorMessage({ ...error, error: payload }));
        return;
      } catch {
        // Si el servidor no respondió JSON se conserva el mensaje HTTP.
      }
    }
    this.alerts.error(this.errorMessage(error));
  }

  private params(values?: Record<string, unknown>): HttpParams {
    let params = new HttpParams();
    Object.entries(values || {}).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') params = params.set(key, String(value));
    });
    return params;
  }
}
