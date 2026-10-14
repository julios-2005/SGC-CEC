import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { AlertService } from '../core/alert.service';
import { Curso, PageResponse } from '../core/models';
import { LatestRequest } from '../core/latest-request';
import { fileValidationMessage } from '../core/form-validation';

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
