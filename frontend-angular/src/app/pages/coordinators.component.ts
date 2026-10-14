import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { AlertService } from '../core/alert.service';
import { Coordinador, PageResponse } from '../core/models';
import { LatestRequest } from '../core/latest-request';
import { fileValidationMessage, personValidationMessage } from '../core/form-validation';

@Component({ changeDetection: ChangeDetectionStrategy.Eager,
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './templates/coordinators.component.html',
  styleUrl: './original-styles/coordinators.css',
})
export class CoordinatorsComponent implements OnInit, OnDestroy {
  private filterTimer?: ReturnType<typeof setTimeout>;
  private readonly listRequest = new LatestRequest();
  private photoGeneration = 0;
  loading = false;
  saving = false;
  modalOpen = false;
  items: Coordinador[] = [];
  filters: any = { texto: '', estado: 'true', sort: 'id,desc' };
  page = 0;
  totalPages = 0;
  editing?: Coordinador;
  photo?: File;
  photoPreview = '';
  error = '';
  form: any = this.empty();
  photoUrls: Record<number, string> = {};

  constructor(readonly api: ApiService, readonly auth: AuthService, private readonly alerts: AlertService) {}
  ngOnInit(): void { this.load(); }
  ngOnDestroy(): void { clearTimeout(this.filterTimer); this.listRequest.cancel(); this.releasePhotos(); this.releasePreview(); }
  scheduleLoad(): void { clearTimeout(this.filterTimer); this.filterTimer = setTimeout(() => this.load(), 300); }
  load(page = 0): void {
    clearTimeout(this.filterTimer);
    this.page = page;
    this.loading = true;
    this.error = '';
    this.items = [];
    this.releasePhotos();
    this.listRequest.run(this.api.get<PageResponse<Coordinador>>('coordinadores', { ...this.filters, page, size: 20 }), {
      next: data => {
        if (page > 0 && page >= data.totalPages) { this.load(Math.max(0, data.totalPages - 1)); return; }
        this.items = data.content; this.totalPages = data.totalPages; this.loading = false; this.loadPhotos();
      },
      error: error => { this.error = this.api.errorMessage(error); this.loading = false; },
    });
  }
  open(item?: Coordinador): void {
    this.editing = item;
    this.form = item ? { ...item } : this.empty();
    this.error = '';
    this.photo = undefined;
    this.releasePreview();
    this.modalOpen = true;
  }
  close(): void { this.modalOpen = false; this.releasePreview(); }
  selectPhoto(event: Event): void {
    const input = event.target as HTMLInputElement;
    const photo = input.files?.[0];
    this.error = fileValidationMessage(photo, true);
    this.releasePreview();
    this.photo = this.error ? undefined : photo;
    if (this.error) input.value = '';
    else if (photo) this.photoPreview = URL.createObjectURL(photo);
  }
  save(ngForm: NgForm): void {
    if (this.saving) return;
    this.error = personValidationMessage(this.form);
    if (ngForm.invalid || this.error) { ngForm.control.markAllAsTouched(); this.error ||= 'Completa correctamente los campos obligatorios.'; return; }
    const data = new FormData();
    ['cedula','nombres','apellidos','telefono','correo'].forEach(key => data.append(key, String(this.form[key] || '').trim()));
    if (this.photo) data.append('foto', this.photo);
    const nombreGuardado = `${String(this.form.nombres || '').trim()} ${String(this.form.apellidos || '').trim()}`.trim();
    const editando = !!this.editing;
    this.saving = true;
    const request = this.editing ? this.api.put<Coordinador>(`coordinadores/${this.editing.id}`, data) : this.api.post<Coordinador>('coordinadores', data);
    request.subscribe({
      next: () => {
        this.saving = false; this.close(); this.load(this.page);
        this.alerts.success(editando ? `El coordinador "${nombreGuardado}" se actualizó correctamente.` : `El coordinador "${nombreGuardado}" se creó correctamente.`);
      },
      error: error => { this.saving = false; this.error = this.api.errorMessage(error); },
    });
  }
  toggle(item: Coordinador): void {
    this.api.patch(`coordinadores/${item.id}/estado`, null, { estado: !item.estado }).subscribe({ next: () => this.load(this.page), error: error => this.alerts.error(this.api.errorMessage(error)) });
  }
  async remove(item: Coordinador): Promise<void> {
    if (!await this.alerts.confirm(`¿Eliminar a ${item.nombres} ${item.apellidos}? Esta acción no se puede deshacer.`)) return;
    this.api.delete(`coordinadores/${item.id}`).subscribe({
      next: () => { this.load(this.page); this.alerts.success(`El coordinador "${item.nombres} ${item.apellidos}" se eliminó correctamente.`); },
      error: error => this.alerts.error(this.api.errorMessage(error)),
    });
  }
  clearFilters(): void { this.filters = { texto: '', estado: 'true', sort: 'id,desc' }; this.load(); }
  coordinatorImage(item: Coordinador): string { return this.photoUrls[item.id] || ''; }
  private loadPhotos(): void {
    const generation = this.photoGeneration;
    this.items.filter(item => !!item.foto).forEach(item => this.api.blob(`coordinadores/archivos/${item.foto}`).subscribe({
      next: blob => { if (generation === this.photoGeneration) this.photoUrls[item.id] = URL.createObjectURL(blob); },
      error: () => { /* Una foto ausente no debe impedir consultar el listado. */ },
    }));
  }
  private releasePhotos(): void {
    this.photoGeneration++;
    Object.values(this.photoUrls).forEach(url => URL.revokeObjectURL(url));
    this.photoUrls = {};
  }
  private releasePreview(): void { if (this.photoPreview) URL.revokeObjectURL(this.photoPreview); this.photoPreview = ''; }
  private empty(): any { return { cedula: '', nombres: '', apellidos: '', telefono: '', correo: '' }; }
}
