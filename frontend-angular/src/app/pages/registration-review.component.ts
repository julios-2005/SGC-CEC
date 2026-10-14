import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { AlertService } from '../core/alert.service';
import { Inscripcion } from '../core/models';
import { LatestRequest } from '../core/latest-request';
import { labelsType, labelsState, actionVerbs, pastVerbs } from './registration-labels';

@Component({ changeDetection: ChangeDetectionStrategy.Eager, standalone: true, imports: [CommonModule, FormsModule, RouterLink], templateUrl: './templates/registration-review.component.html', styleUrl: './original-styles/registration-review.css' })
export class RegistrationReviewComponent implements OnInit, OnDestroy {
  private readonly listRequest = new LatestRequest();
  readonly updating = new Set<number>();
  items: Inscripcion[] = []; allItems: Inscripcion[] = []; texto = ''; estado = 'PENDIENTE'; error = ''; loading = false;
  constructor(readonly api: ApiService, readonly auth: AuthService, private readonly alerts: AlertService) {}
  ngOnInit(): void { this.load(); }
  ngOnDestroy(): void { this.listRequest.cancel(); }
  load(): void {
    this.loading = true; this.error = ''; this.items = []; this.allItems = [];
    this.listRequest.run(this.api.allPages<Inscripcion>('inscripciones', { sort: 'fechaRegistro,desc' }), {
      next: data => { this.allItems = data; this.applyFilters(); this.loading = false; },
      error: error => { this.error = this.api.errorMessage(error); this.loading = false; },
    });
  }
  applyFilters(): void { const term = this.texto.trim().toLowerCase(); this.items = this.allItems.filter(item => (!this.estado || item.estado === this.estado) && (!term || `${item.nombreCompleto} ${item.cedula} ${item.planificacion?.nombreCurso}`.toLowerCase().includes(term))); }
  openDocument(path?: string): void { if (path) this.api.openFile(`inscripciones/archivos/${path}`); }
  async changeStatus(item: Inscripcion, estado: string): Promise<void> {
    if (this.updating.has(item.id)) return;
    if (!await this.alerts.confirm(`¿${actionVerbs[estado]} la inscripción de ${item.nombreCompleto}?`, actionVerbs[estado])) return;
    this.updating.add(item.id);
    this.api.patch<Inscripcion>(`inscripciones/${item.id}/estado`, null, { estado }).subscribe({
      next: updated => {
        this.updating.delete(item.id);
        this.allItems = this.allItems.map(row => row.id === updated.id ? updated : row);
        this.applyFilters();
        this.alerts.success(`La inscripción de ${updated.nombreCompleto} se ${pastVerbs[estado]} correctamente.`);
      },
      error: error => { this.updating.delete(item.id); this.alerts.error(this.api.errorMessage(error)); },
    });
  }
  typeLabel(value: string): string { return labelsType[value] || value; }
  stateLabel(value: string): string { return labelsState[value] || value; }
}
