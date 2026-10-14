import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { HttpParams } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { ApiService } from '../core/api.service';
import { AlertService } from '../core/alert.service';
import { Coordinador, Curso, Especialista, PageResponse, Planificacion } from '../core/models';
import { LatestRequest } from '../core/latest-request';
import { dateRangeMessage } from '../core/form-validation';

@Component({ changeDetection: ChangeDetectionStrategy.Eager,
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './templates/planning.component.html',
  styleUrl: './original-styles/planning.css',
})
export class PlanningComponent implements OnInit, OnDestroy {
  private filterTimer?: ReturnType<typeof setTimeout>;
  private readonly listRequest = new LatestRequest();
  private readonly optionsRequest = new LatestRequest();
  loading = false;
  saving = false;
  modalOpen = false;
  items: Planificacion[] = [];
  courses: Curso[] = [];
  coordinators: Coordinador[] = [];
  specialists: Especialista[] = [];
  filters: any = { texto: '', modalidad: '', fechaDesde: '', fechaHasta: '', estadoPlanificacion: 'ACTIVA', sort: 'fechaRegistro,desc' };
  page = 0;
  totalPages = 0;
  editing?: Planificacion;
  error = '';
  form: any = this.empty();
  docenteIds: Array<number | null> = [null];
  teacherCount = 1;
  honorariosIndividuales = false;
  honorariosDocentes: Array<number | null> = [0];

  constructor(readonly api: ApiService, private readonly alerts: AlertService) {}
  ngOnInit(): void { this.load(); this.loadOptions(); }
  ngOnDestroy(): void { clearTimeout(this.filterTimer); this.listRequest.cancel(); this.optionsRequest.cancel(); }
  scheduleLoad(): void { clearTimeout(this.filterTimer); this.filterTimer = setTimeout(() => this.load(), 300); }
  load(page = 0): void {
    clearTimeout(this.filterTimer);
    this.listRequest.cancel();
    this.page = page;
    this.items = [];
    this.error = dateRangeMessage(this.filters.fechaDesde, this.filters.fechaHasta);
    this.loading = !this.error;
    if (this.error) { this.totalPages = 0; return; }
    this.listRequest.run(this.api.get<PageResponse<Planificacion>>('planificaciones', { ...this.filters, page, size: 20 }), {
      next: data => {
        if (page > 0 && page >= data.totalPages) { this.load(Math.max(0, data.totalPages - 1)); return; }
        this.items = data.content; this.totalPages = data.totalPages; this.loading = false;
      },
      error: error => { this.error = this.api.errorMessage(error); this.loading = false; },
    });
  }
  loadOptions(): void {
    this.optionsRequest.run(forkJoin({
      courses: this.api.get<Curso[]>('planificaciones/cursos'),
      coordinators: this.api.get<Coordinador[]>('planificaciones/coordinadores'),
      specialists: this.api.get<Especialista[]>('planificaciones/especialistas'),
    }), {
      next: data => { this.courses = data.courses; this.coordinators = data.coordinators; this.specialists = data.specialists; },
      error: error => this.error = this.api.errorMessage(error),
    });
  }
  get missingCourse() { return this.editing && !this.courses.some(item => item.idCurso === this.editing!.curso.idCurso) ? this.editing.curso : null; }
  get missingCoordinator() { return this.editing && !this.coordinators.some(item => item.id === this.editing!.coordinador.id) ? this.editing.coordinador : null; }
  get missingSpecialist() { return this.editing && !this.specialists.some(item => item.id === this.editing!.especialista.id) ? this.editing.especialista : null; }
  open(item?: Planificacion): void {
    this.editing = item;
    this.error = '';
    this.form = item ? {
      idCurso: item.curso.idCurso,
      idCoordinador: item.coordinador.id,
      idEspecialista: item.especialista.id,
      fechaInicio: item.fechaInicio,
      fechaFin: item.fechaFin,
      horario: item.horario,
      modalidad: item.modalidad,
      costoEspecialista: item.costoEspecialista ?? 0,
    } : this.empty();
    this.docenteIds = item ? (item.docentes?.length ? item.docentes : [item.especialista]).map(d => d.id) : [null];
    this.teacherCount = this.docenteIds.length;
    this.honorariosIndividuales = !item || this.docenteIds.length === 1 || !!item.honorariosDocentes?.length;
    this.honorariosDocentes = this.docenteIds.map(id => item?.honorariosDocentes?.find(h => h.idEspecialista === id)?.honorario
      ?? (this.docenteIds.length === 1 ? (item?.costoEspecialista ?? 0) : item ? null : 0));
    this.updateTotal();
    this.modalOpen = true;
  }
  resizeTeachers(): void {
    if (!Number.isInteger(this.teacherCount) || this.teacherCount < 1 || this.teacherCount > 100) return;
    this.docenteIds = Array.from({length: this.teacherCount}, (_, i) => this.docenteIds[i] ?? null);
    this.honorariosDocentes = Array.from({length: this.teacherCount}, (_, i) => this.honorariosDocentes[i] ?? 0);
    this.updateTotal();
  }
  chooseCourse(): void {
    if (this.editing) return;
    const course = this.courses.find(c => c.idCurso === this.form.idCurso);
    this.docenteIds = [null];
    this.teacherCount = 1;
    this.honorariosDocentes = [0];
    this.updateTotal();
    if (course) this.form.modalidad = course.modalidad;
  }
  get teacherOptions() {
    const retained = this.editing?.docentes || (this.editing ? [this.editing.especialista] : []);
    return [...this.specialists, ...retained.filter(d => !this.specialists.some(s => s.id === d.id))];
  }
  close(): void { if (!this.saving) this.modalOpen = false; }
  updateTotal(): void {
    if (this.honorariosIndividuales) this.form.costoEspecialista = this.honorariosDocentes
      .reduce<number>((sum, value) => sum + Math.round(Number(value || 0) * 100), 0) / 100;
  }
  /** true cuando esta edición no tiene honorarios que pagar (costo total en 0). */
  get freePlanning(): boolean {
    const value = this.form?.costoEspecialista;
    return value !== null && value !== undefined && value !== '' && Number(value) === 0;
  }
  /** Marcar "Planificación gratuita" pone en 0 el total y el honorario de cada especialista. */
  toggleFreePlanning(free: boolean): void {
    if (!free) { this.form.costoEspecialista = null; return; }
    this.honorariosDocentes = this.honorariosDocentes.map(() => 0);
    this.form.costoEspecialista = 0;
  }
  transfer(index: number): number {
    const country = this.teacherOptions.find(d => d.id === this.docenteIds[index])?.paisNacionalidad;
    const cents = Math.round(Number(this.honorariosDocentes[index] || 0) * 100);
    return country && country.toUpperCase() !== 'EC' ? Math.round(cents * 0.25) / 100 : 0;
  }
  /** Costo total que asume el CEC por este especialista: honorario bruto + transferencia (si aplica). */
  net(index: number): number {
    return (Math.round(Number(this.honorariosDocentes[index] || 0) * 100) + Math.round(this.transfer(index) * 100)) / 100;
  }
  save(ngForm: NgForm): void {
    if (this.saving) return;
    this.error = dateRangeMessage(this.form.fechaInicio, this.form.fechaFin);
    if (ngForm.invalid || !String(this.form.horario).trim() || this.error) {
      ngForm.control.markAllAsTouched(); this.error ||= 'Completa correctamente los campos obligatorios.'; return;
    }
    let body = new HttpParams();
    if (this.docenteIds.some(id => id == null) || new Set(this.docenteIds).size !== this.docenteIds.length) {
      this.error = 'Selecciona al menos un especialista, sin repetir nombres.'; return;
    }
    if (!Number.isInteger(this.teacherCount) || this.teacherCount < 1 || this.teacherCount > 100) {
      this.error = 'La cantidad de especialistas debe ser un entero entre 1 y 100.'; return;
    }
    if (this.honorariosIndividuales) {
      if (this.honorariosDocentes.length !== this.docenteIds.length || this.honorariosDocentes.some(v =>
        v == null || !Number.isFinite(v) || v < 0 || v > 99999999.99 || Math.abs(v * 100 - Math.round(v * 100)) > 0.00001)) {
        this.error = 'Indica el honorario bruto de cada especialista, con hasta dos decimales.'; return;
      }
      this.updateTotal();
      this.honorariosDocentes.forEach(value => body = body.append('honorariosDocentes', String(value)));
    }
    this.form.idEspecialista = this.docenteIds[0];
    this.docenteIds.forEach(id => body = body.append('docenteIds', String(id)));
    Object.entries(this.form).forEach(([key, value]) => { if (value !== null && value !== '') body = body.set(key, String(value).trim()); });
    const editando = !!this.editing;
    this.saving = true;
    const request = this.editing ? this.api.put<Planificacion>(`planificaciones/${this.editing.id}`, body) : this.api.post<Planificacion>('planificaciones', body);
    request.subscribe({
      next: () => {
        this.saving = false; this.close(); editando ? this.load(this.page) : this.clearFilters();
        this.alerts.success(editando ? 'La planificación se actualizó correctamente.' : 'La planificación se creó correctamente.');
      },
      error: error => { this.saving = false; this.error = this.api.errorMessage(error); },
    });
  }
  async remove(item: Planificacion): Promise<void> {
    if (!await this.alerts.confirm(`¿Eliminar la planificación de "${item.curso.nombre}"? Esta acción no se puede deshacer.`)) return;
    this.api.delete(`planificaciones/${item.id}`).subscribe({
      next: () => { this.load(this.page); this.alerts.success(`La planificación "${item.curso.nombre}" se eliminó correctamente.`); },
      error: error => this.alerts.error(this.api.errorMessage(error)),
    });
  }
  clearFilters(): void { this.filters = { texto: '', modalidad: '', fechaDesde: '', fechaHasta: '', estadoPlanificacion: 'ACTIVA', sort: 'fechaRegistro,desc' }; this.load(); }
  modalityLabel(value: string): string { return ({ PRESENCIAL: 'Presencial', VIRTUAL: 'Virtual', HIBRIDO: 'Híbrido' } as Record<string,string>)[value] || value; }
  private empty(): any { return { idCurso: null, idCoordinador: null, idEspecialista: null, fechaInicio: '', fechaFin: '', horario: '', modalidad: '', costoEspecialista: null }; }
}
