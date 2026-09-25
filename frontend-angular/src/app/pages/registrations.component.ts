import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { Inscripcion, PageResponse } from '../core/models';
import { LatestRequest } from '../core/latest-request';
import { dateRangeMessage } from '../core/form-validation';
import { labelsType, labelsState } from './registration-labels';

@Component({ changeDetection: ChangeDetectionStrategy.Eager, standalone: true, imports: [CommonModule, FormsModule, RouterLink], templateUrl: './templates/registrations.component.html', styleUrl: './original-styles/registrations.css' })
export class RegistrationsComponent implements OnInit, OnDestroy {
  private filterTimer?: ReturnType<typeof setTimeout>;
  private readonly listRequest = new LatestRequest();
  loading = false;
  items: Inscripcion[] = [];
  filters: any = { texto: '', estado: 'PENDIENTE', tipoUsuario: '', fechaDesde: '', fechaHasta: '', sort: 'fechaRegistro,desc' };
  page = 0; totalPages = 0; totalElements = 0; error = '';
  constructor(readonly api: ApiService) {}
  ngOnInit(): void { this.load(); }
  ngOnDestroy(): void { clearTimeout(this.filterTimer); this.listRequest.cancel(); }
  scheduleLoad(): void { clearTimeout(this.filterTimer); this.filterTimer = setTimeout(() => this.load(), 300); }
  load(page = 0): void {
    clearTimeout(this.filterTimer);
    this.listRequest.cancel();
    this.page = page; this.items = []; this.totalElements = 0;
    this.error = dateRangeMessage(this.filters.fechaDesde, this.filters.fechaHasta);
    this.loading = !this.error;
    if (this.error) { this.totalPages = 0; return; }
    this.listRequest.run(this.api.get<PageResponse<Inscripcion>>('inscripciones', { ...this.filters, page, size: 20 }), {
      next: data => { this.items = data.content; this.totalPages = data.totalPages; this.totalElements = data.totalElements; this.loading = false; },
      error: error => { this.error = this.api.errorMessage(error); this.loading = false; },
    });
  }
  clearFilters(): void { this.filters = { texto: '', estado: 'PENDIENTE', tipoUsuario: '', fechaDesde: '', fechaHasta: '', sort: 'fechaRegistro,desc' }; this.load(); }
  typeLabel(value: string): string { return labelsType[value] || value; }
  stateLabel(value: string): string { return labelsState[value] || value; }
}
