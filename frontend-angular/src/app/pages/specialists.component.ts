import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AlertService } from '../core/alert.service';
import { Especialista, PageResponse } from '../core/models';
import { LatestRequest } from '../core/latest-request';
import { personValidationMessage } from '../core/form-validation';

@Component({ changeDetection: ChangeDetectionStrategy.Eager,
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './templates/specialists.component.html',
  styleUrl: './original-styles/specialists.css',
})
export class SpecialistsComponent implements OnInit, OnDestroy {
  private filterTimer?: ReturnType<typeof setTimeout>;
  private readonly listRequest = new LatestRequest();
  loading = false;
  saving = false;
  modalOpen = false;
  items: Especialista[] = [];
  countries: Array<{ code: string; name: string }> = [];
  filters: any = { texto: '', estado: 'true', nacionalidad: '', sort: 'id,desc' };
  page = 0;
  totalPages = 0;
  editing?: Especialista;
  error = '';
  form: any = this.empty();

  constructor(readonly api: ApiService, private readonly alerts: AlertService) {}
  ngOnInit(): void {
    this.load();
    this.api.get<Record<string, string[]>>('especialistas/nacionalidades').subscribe({
      next: data => this.countries = Object.entries(data).map(([code, value]) => ({ code, name: value[0] })).sort((a,b) => a.name.localeCompare(b.name)),
      error: error => this.error = this.api.errorMessage(error),
    });
  }
  ngOnDestroy(): void { clearTimeout(this.filterTimer); this.listRequest.cancel(); }
  scheduleLoad(): void { clearTimeout(this.filterTimer); this.filterTimer = setTimeout(() => this.load(), 300); }
  load(page = 0): void {
    clearTimeout(this.filterTimer);
    this.page = page;
    this.loading = true;
    this.error = '';
    this.items = [];
    this.listRequest.run(this.api.get<PageResponse<Especialista>>('especialistas', { ...this.filters, page, size: 20 }), {
      next: data => {
        if (page > 0 && page >= data.totalPages) { this.load(Math.max(0, data.totalPages - 1)); return; }
        this.items = data.content; this.totalPages = data.totalPages; this.loading = false;
      },
      error: error => { this.error = this.api.errorMessage(error); this.loading = false; },
    });
  }
  open(item?: Especialista): void {
    this.editing = item;
    this.error = '';
    this.form = item ? { ...item } : this.empty();
    this.modalOpen = true;
  }
  close(): void { this.modalOpen = false; }
  save(ngForm: NgForm): void {
    if (this.saving) return;
    this.error = personValidationMessage(this.form);
    if (ngForm.invalid || this.error) { ngForm.control.markAllAsTouched(); this.error ||= 'Completa correctamente los campos obligatorios.'; return; }
    const data = new FormData();
    ['cedula','nombres','apellidos','telefono','correo','especialidad','areaConocimiento','paisNacionalidad'].forEach(key => data.append(key, String(this.form[key] || '').trim()));
    const nombreGuardado = `${String(this.form.nombres || '').trim()} ${String(this.form.apellidos || '').trim()}`.trim();
    const editando = !!this.editing;
    this.saving = true;
    const request = this.editing ? this.api.put<Especialista>(`especialistas/${this.editing.id}`, data) : this.api.post<Especialista>('especialistas', data);
    request.subscribe({
      next: () => {
        this.saving = false; this.close(); this.load(this.page);
        this.alerts.success(editando ? `El especialista "${nombreGuardado}" se actualizó correctamente.` : `El especialista "${nombreGuardado}" se creó correctamente.`);
      },
      error: error => { this.saving = false; this.error = this.api.errorMessage(error); },
    });
  }
  async remove(item: Especialista): Promise<void> {
    if (!await this.alerts.confirm(`¿Eliminar a ${item.nombres} ${item.apellidos}? Esta acción no se puede deshacer.`)) return;
    this.api.delete(`especialistas/${item.id}`).subscribe({
      next: () => { this.load(this.page); this.alerts.success(`El especialista "${item.nombres} ${item.apellidos}" se eliminó correctamente.`); },
      error: error => this.alerts.error(this.api.errorMessage(error)),
    });
  }
  clearFilters(): void { this.filters = { texto: '', estado: 'true', nacionalidad: '', sort: 'id,desc' }; this.load(); }
  private empty(): any { return { cedula: '', nombres: '', apellidos: '', telefono: '', correo: '', especialidad: '', areaConocimiento: '', paisNacionalidad: 'EC' }; }
}
