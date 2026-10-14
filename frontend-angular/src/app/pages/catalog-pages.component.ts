import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { AlertService } from '../core/alert.service';
import { Coordinador, Curso, Especialista, PageResponse } from '../core/models';
import { LatestRequest } from '../core/latest-request';
import { fileValidationMessage, personValidationMessage } from '../core/form-validation';

@Component({ changeDetection: ChangeDetectionStrategy.Eager,
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './templates/courses.component.html',
  styleUrl: './original-styles/courses.css',
})
export class CoursesComponent implements OnInit, OnDestroy {
  private filterTimer?: ReturnType<typeof setTimeout>;
  private errorTimer?: ReturnType<typeof setTimeout>;
  @ViewChild('courseError') private courseError?: ElementRef<HTMLElement>;
  private readonly listRequest = new LatestRequest();
  loading = false;
  saving = false;
  modalOpen = false;
  items: Curso[] = [];
  filters: any = { texto: '', modalidad: '', estado: 'EN_ESPERA', sort: 'fechaRegistro,desc' };
  page = 0;
  totalPages = 0;
  editing?: Curso;
  photo?: File;
  photoPreview = '';
  error = '';
  form: any = this.empty();

  constructor(readonly api: ApiService, private readonly alerts: AlertService) {}
  ngOnInit(): void { this.load(); }
  ngOnDestroy(): void { clearTimeout(this.filterTimer); clearTimeout(this.errorTimer); this.listRequest.cancel(); this.releasePreview(); }
  scheduleLoad(): void { clearTimeout(this.filterTimer); this.filterTimer = setTimeout(() => this.load(), 300); }
  load(page = 0): void {
    clearTimeout(this.filterTimer);
    this.page = page;
    this.loading = true;
    this.error = '';
    this.items = [];
    this.listRequest.run(this.api.get<PageResponse<Curso>>('cursos', { ...this.filters, page, size: 20 }), {
      next: data => {
        if (page > 0 && page >= data.totalPages) { this.load(Math.max(0, data.totalPages - 1)); return; }
        this.items = data.content; this.totalPages = data.totalPages; this.loading = false;
      },
      error: error => { this.error = this.api.errorMessage(error); this.loading = false; },
    });
  }
  open(item?: Curso): void {
    this.editing = item;
    this.error = '';
    this.photo = undefined;
    this.releasePreview();
    this.form = item ? { ...item } : this.empty();
    this.modalOpen = true;
  }
  close(): void { this.modalOpen = false; clearTimeout(this.errorTimer); this.releasePreview(); }
  onBackdropClick(event: MouseEvent): void {
    // Un listener de Angular que devuelve false cancela la acción nativa
    // del clic. El fondo nunca debe cancelar el submit del botón Guardar.
    if (event.target === event.currentTarget) this.close();
  }
  selectPhoto(event: Event): void {
    const input = event.target as HTMLInputElement;
    const photo = input.files?.[0];
    this.error = fileValidationMessage(photo, true);
    this.releasePreview();
    this.photo = this.error ? undefined : photo;
    if (this.error) { input.value = ''; this.showFormError(this.error); }
    else if (photo) this.photoPreview = URL.createObjectURL(photo);
  }
  save(ngForm: NgForm): void {
    if (this.saving) return;
    this.error = '';
    if (ngForm.invalid) {
      ngForm.control.markAllAsTouched(); this.showFormError(this.validationMessage(ngForm)); return;
    }
    if (!String(this.form.nombre ?? '').trim() || !String(this.form.codigo ?? '').trim()) {
      ngForm.control.markAllAsTouched(); this.showFormError('El nombre y el código del curso no pueden estar vacíos ni contener solamente espacios.'); return;
    }
    if (!Number.isInteger(this.form.horas) || !Number.isInteger(this.form.cuposTotales)) {
      this.showFormError('Las horas y los cupos deben ser números enteros.'); return;
    }
    const remaining = this.form.cuposRestantes;
    if (remaining !== '' && remaining != null && (!Number.isInteger(remaining) || remaining < 0 || remaining > this.form.cuposTotales)) {
      this.showFormError('Los cupos restantes deben ser enteros entre 0 y los cupos totales.'); return;
    }
    const data = new FormData();
    if (this.form.ambito) data.append('ambito', this.form.ambito);
    ['nombre','codigo','horas','costo','cuposTotales','modalidad','estado'].forEach(key => data.append(key, String(this.form[key] ?? '').trim()));
    if (remaining !== '' && remaining != null) data.append('cuposRestantes', String(remaining));
    if (this.photo) data.append('foto', this.photo);
    const nombreGuardado = String(this.form.nombre ?? '').trim();
    const editando = !!this.editing;
    this.saving = true;
    const request = this.editing ? this.api.put<Curso>(`cursos/${this.editing.idCurso}`, data) : this.api.post<Curso>('cursos', data);
    request.subscribe({
      next: () => {
        this.saving = false; this.close(); editando ? this.load(this.page) : this.clearFilters();
        this.alerts.success(editando ? `El curso "${nombreGuardado}" se actualizó correctamente.` : `El curso "${nombreGuardado}" se creó correctamente.`);
      },
      error: error => { this.saving = false; this.showFormError(this.api.errorMessage(error) || 'No se pudo guardar el curso.'); },
    });
  }
  private validationMessage(ngForm: NgForm): string {
    const labels: Record<string, string> = {nombre: 'Nombre del curso', codigo: 'Código del curso',
      horas: 'Horas', costo: 'Costo', cuposTotales: 'Cupos totales', cuposRestantes: 'Cupos restantes',
      modalidad: 'Modalidad', estado: 'Estado', ambito: 'Ámbito del curso'};
    for (const [name, control] of Object.entries(ngForm.controls)) {
      if (!control.invalid) continue;
      const label = labels[name] || name;
      if (control.hasError('required')) return `Completa el campo «${label}».`;
      if (control.hasError('min')) return `«${label}» debe ser mayor o igual a ${control.getError('min').min}.`;
      if (control.hasError('max')) return `«${label}» no puede superar ${control.getError('max').max}.`;
      if (control.hasError('maxlength')) return `«${label}» admite hasta ${control.getError('maxlength').requiredLength} caracteres.`;
      return `Revisa el campo «${label}».`;
    }
    return 'Completa correctamente los campos obligatorios.';
  }
  private showFormError(message: string): void {
    this.error = message;
    clearTimeout(this.errorTimer);
    // Espera al mensaje renderizado y lo hace visible aunque Guardar esté
    // al final de la ventana desplazable. Conserva el estilo original.
    this.errorTimer = setTimeout(() => {
      if (this.modalOpen && this.error) this.courseError?.nativeElement.focus();
    }, 0);
  }
  async remove(item: Curso): Promise<void> {
    if (!await this.alerts.confirm(`¿Eliminar el curso "${item.nombre}"? Esta acción no se puede deshacer.`)) return;
    this.api.delete(`cursos/${item.idCurso}`).subscribe({
      next: () => { this.load(this.page); this.alerts.success(`El curso "${item.nombre}" se eliminó correctamente.`); },
      error: error => this.alerts.error(this.api.errorMessage(error)),
    });
  }
  clearFilters(): void { this.filters = { texto: '', modalidad: '', estado: 'EN_ESPERA', sort: 'fechaRegistro,desc' }; this.load(); }
  courseImage(item: Curso): string { return this.api.url(`cursos/archivos/${item.foto}`); }
  modalityLabel(value: string): string { return ({ PRESENCIAL: 'Presencial', VIRTUAL: 'Virtual', HIBRIDO: 'Híbrido' } as Record<string,string>)[value] || value; }
  stateLabel(value: string): string { return ({ EN_ESPERA: 'En espera', EN_PROCESO: 'En proceso', FINALIZADO: 'Finalizado' } as Record<string,string>)[value] || value; }
  occupancy(item: Curso): number { return item.cuposTotales ? Math.min(100, Math.round(((item.cuposTotales - item.cuposRestantes) / item.cuposTotales) * 100)) : 0; }
  private releasePreview(): void { if (this.photoPreview) URL.revokeObjectURL(this.photoPreview); this.photoPreview = ''; }
  private empty(): any { return { nombre: '', codigo: '', horas: null, costo: null, cuposTotales: null, cuposRestantes: null, modalidad: '', estado: 'EN_ESPERA', ambito: 'NACIONAL' }; }
  /** true cuando el curso no tiene costo para los participantes (costo en 0). */
  get freeCourse(): boolean {
    const value = this.form?.costo;
    return value !== null && value !== undefined && value !== '' && Number(value) === 0;
  }
  /** Marcar "Curso gratuito" deja el costo en 0; desmarcarlo lo vacía para escribirlo. */
  toggleFreeCourse(free: boolean): void { this.form.costo = free ? 0 : null; }
}

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
