import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { ApiService } from '../core/api.service';
import { AlertService } from '../core/alert.service';
import { Consolidado, Egreso, InformeDetalle, InformeResumen, Ingreso } from '../core/models';
import { LatestRequest } from '../core/latest-request';
import { dateRangeMessage } from '../core/form-validation';

@Component({ changeDetection: ChangeDetectionStrategy.Eager,
  standalone: true, imports: [CommonModule, FormsModule],
  templateUrl: './templates/report.component.html',
  styleUrls: ['./original-styles/report.css', './report-controls.css'],
})
export class ReportComponent implements OnInit, OnDestroy {
  private filterTimer?: ReturnType<typeof setTimeout>;
  private readonly listRequest = new LatestRequest();
  private readonly detailRequest = new LatestRequest();
  loading = false;
  saving = false;
  items: InformeResumen[] = [];
  detail?: InformeDetalle;
  error = '';
  reportYear = 2026;
  filters: any = { texto: '', modalidad: '', fechaDesde: '', fechaHasta: '' };
  totals = { participantes: 0, ingresos: 0, egresos: 0, utilidad: 0, gastosPersonal: 0, utilidadCec: 0 };
  annualView = false;
  get consolidatedCec2026(): boolean { return this.annualView && this.reportYear === 2026; }
  overhead?: Consolidado;
  editingOverhead = false;
  overheadAmount: number | null = null;
  expenseForm?: { id?: number; concepto: string; cantidad: number | null; valorUnitario: number | null };
  // Edición de ingresos: solo se captura la cantidad de participantes; el
  // valor por participante es el del tipo de participante y no se edita.
  incomeForm?: { id: number; detalle: string; cantidad: number | null; valorUnitario: number };

  constructor(readonly api: ApiService, private readonly alerts: AlertService) {}
  ngOnInit(): void { this.load(); }
  ngOnDestroy(): void { clearTimeout(this.filterTimer); this.listRequest.cancel(); this.detailRequest.cancel(); }
  scheduleLoad(): void { clearTimeout(this.filterTimer); this.filterTimer = setTimeout(() => this.load(), 300); }
  load(): void {
    clearTimeout(this.filterTimer);
    this.listRequest.cancel(); this.detailRequest.cancel();
    this.detail = undefined; this.expenseForm = undefined; this.incomeForm = undefined; this.editingOverhead = false; this.items = [];
    this.totals = { participantes: 0, ingresos: 0, egresos: 0, utilidad: 0, gastosPersonal: 0, utilidadCec: 0 };
    this.annualView = false;
    this.error = dateRangeMessage(this.filters.fechaDesde, this.filters.fechaHasta);
    if (!Number.isInteger(this.reportYear) || this.reportYear < 2000 || this.reportYear > 2100)
      this.error = 'Selecciona un año entre 2000 y 2100.';
    this.loading = !this.error;
    if (this.error) return;
    this.listRequest.run(forkJoin({
      data: this.api.get<InformeResumen[]>('informes-economicos', { ...this.filters, anio: this.reportYear }),
      overhead: this.api.get<Consolidado>('informes-economicos/consolidado/' + this.reportYear),
    }), {
      next: ({data, overhead}) => {
        this.loading = false; this.overhead = overhead; this.annualView = this.isUnfiltered();
        const visible = data.filter(item => !(this.reportYear === 2026 && item.excluido2026));
        this.items = this.annualView ? visible.filter(item => this.yearOf(item) === this.reportYear) : visible;
        this.calculateTotals();
      },
      error: error => { this.error = this.api.errorMessage(error); this.loading = false; },
    });
  }
  private calculateTotals(): void {
    const rows = this.annualView ? this.items.filter(item => this.yearOf(item) === this.reportYear) : this.items;
    const ingresos = this.sumMoney(rows.map(item => item.ingresos));
    const egresos = this.sumMoney(rows.map(item => item.egresos));
    const utilidad = this.roundMoney(ingresos - egresos);
    const gastosPersonal = this.annualView ? Number(this.overhead?.gastosPersonal || 0) : 0;
    this.totals = { participantes: rows.reduce((sum, item) => sum + Number(item.participantes), 0),
      ingresos, egresos, utilidad, gastosPersonal,
      utilidadCec: this.annualView ? this.roundMoney(utilidad - gastosPersonal) : 0 };
  }
  open(item: InformeResumen): void {
    this.error = ''; this.expenseForm = undefined; this.incomeForm = undefined;
    this.detailRequest.run(this.api.get<InformeDetalle>(this.reportPath(item)), {
      next: data => this.detail = data, error: error => this.error = this.api.errorMessage(error),
    });
  }
  back(): void { if (!this.saving) this.load(); }
  onOverheadBackdrop(event: MouseEvent): void { if (event.target === event.currentTarget) this.editingOverhead = false; }
  onIncomeBackdrop(event: MouseEvent): void { if (event.target === event.currentTarget) this.incomeForm = undefined; }
  onExpenseBackdrop(event: MouseEvent): void { if (event.target === event.currentTarget) this.expenseForm = undefined; }
  beginOverhead(): void { this.overheadAmount = this.totals.gastosPersonal; this.editingOverhead = true; }
  saveOverhead(form: NgForm): void {
    if (this.saving) return;
    if (form.invalid || !this.validMoney(this.overheadAmount, 9999999999.99)) {
      form.control.markAllAsTouched(); this.error = 'Ingresa un gasto válido, no negativo y con hasta dos decimales.'; return;
    }
    this.saving = true;
    this.api.put<Consolidado>('informes-economicos/gastos-cec/' + this.reportYear,
      {importe: this.overheadAmount, version: this.overhead?.version}).subscribe({
      next: result => { this.overhead = result; this.saving = false; this.editingOverhead = false; this.error = ''; this.calculateTotals(); this.alerts.success('Los gastos de personal CEC ' + this.reportYear + ' se guardaron correctamente.'); },
      error: error => { this.saving = false; this.error = this.api.errorMessage(error); },
    });
  }
  // ── Ingresos: solo se edita la cantidad de participantes ─────────────────
  beginIncome(line: Ingreso): void {
    if (!line.editable || line.id == null || line.valorUnitario == null) return;
    this.error = ''; this.expenseForm = undefined;
    this.incomeForm = {id: line.id, detalle: line.detalle, cantidad: line.cantidad, valorUnitario: line.valorUnitario};
  }
  /** Botones +/- del contador de participantes en el modal de ingresos. */
  incrementIncome(): void {
    if (!this.incomeForm) return;
    const next = Number(this.incomeForm.cantidad ?? 0) + 1;
    if (next <= 1000000) this.incomeForm.cantidad = next;
  }
  decrementIncome(): void {
    if (!this.incomeForm) return;
    const next = Number(this.incomeForm.cantidad ?? 0) - 1;
    if (next >= 0) this.incomeForm.cantidad = next;
  }
  /** Subtotal en vivo mientras se escribe la cantidad: cantidad × valor del tipo de participante. */
  get incomePreview(): number {
    const line = this.incomeForm;
    if (!line || !Number.isInteger(line.cantidad)) return 0;
    return this.roundMoney(Number(line.cantidad) * line.valorUnitario);
  }
  /** Total que quedaría en el informe al guardar esta línea (las demás no cambian). */
  get incomeTotalPreview(): number {
    const line = this.incomeForm, detail = this.detail;
    if (!line || !detail) return 0;
    const actual = detail.ingresos.find(row => row.id === line.id);
    return this.roundMoney(Number(detail.ingresosTotal) - Number(actual?.total || 0) + this.incomePreview);
  }
  saveIncome(form: NgForm): void {
    const line = this.incomeForm;
    if (this.saving || !line || !this.detail || this.detail.cifrasPendientes) return;
    if (form.invalid || !Number.isInteger(line.cantidad) || Number(line.cantidad) < 0 || Number(line.cantidad) > 1000000) {
      form.control.markAllAsTouched();
      this.error = 'Ingresa una cantidad de participantes entera, desde 0 y hasta 1.000.000.'; return;
    }
    this.saving = true;
    this.api.put<InformeDetalle>(this.reportPath(this.detail) + '/ingresos/' + line.id, {cantidad: line.cantidad}).subscribe({
      next: data => { this.detail = data; this.incomeForm = undefined; this.saving = false; this.error = ''; this.alerts.success('La cantidad de "' + line.detalle + '" se actualizó correctamente.'); },
      error: error => { this.saving = false; this.error = this.api.errorMessage(error); },
    });
  }
  async resetIncome(line: Ingreso): Promise<void> {
    if (this.saving || !this.detail || line.id == null || !line.cantidadManual) return;
    if (!await this.alerts.confirm('¿Restablecer la cantidad de "' + line.detalle + '" al conteo de inscripciones aceptadas?', 'Restablecer')) return;
    this.saving = true;
    this.api.delete<InformeDetalle>(this.reportPath(this.detail) + '/ingresos/' + line.id).subscribe({
      next: data => { this.detail = data; this.incomeForm = undefined; this.saving = false; this.error = ''; this.alerts.success('La cantidad de "' + line.detalle + '" se restableció correctamente.'); },
      error: error => { this.saving = false; this.error = this.api.errorMessage(error); },
    });
  }

  beginExpense(line?: Egreso): void {
    this.error = ''; this.incomeForm = undefined;
    this.expenseForm = line ? {id: line.id, concepto: line.concepto, cantidad: line.cantidad, valorUnitario: line.valorUnitario}
      : {concepto: '', cantidad: 1, valorUnitario: null};
  }
  saveExpense(form: NgForm): void {
    const line = this.expenseForm;
    if (this.saving || !line || !this.detail || this.detail.cifrasPendientes) return;
    if (form.invalid || !line.concepto.trim() || !Number.isInteger(line.cantidad)
      || !this.validMoney(line.valorUnitario, 99999999.99)) {
      form.control.markAllAsTouched(); this.error = 'Completa el concepto, una cantidad entera positiva y un valor con hasta dos decimales.'; return;
    }
    this.saving = true;
    const editando = !!line.id;
    const path = this.reportPath(this.detail) + '/egresos';
    const request = line.id ? this.api.put<InformeDetalle>(path + '/' + line.id, line) : this.api.post<InformeDetalle>(path, line);
    request.subscribe({
      next: data => { this.detail = data; this.expenseForm = undefined; this.saving = false; this.error = ''; this.alerts.success(editando ? 'El egreso "' + line.concepto + '" se actualizó correctamente.' : 'El egreso "' + line.concepto + '" se agregó correctamente.'); },
      error: error => { this.saving = false; this.error = this.api.errorMessage(error); },
    });
  }
  async removeExpense(line: Egreso): Promise<void> {
    if (this.saving || !this.detail || !line.editable) return;
    if (!await this.alerts.confirm('¿Eliminar el egreso "' + line.concepto + '"? Esta acción no se puede deshacer.')) return;
    this.saving = true;
    this.api.delete<InformeDetalle>(this.reportPath(this.detail) + '/egresos/' + line.id).subscribe({
      next: data => { this.detail = data; this.saving = false; this.error = ''; this.alerts.success('El egreso "' + line.concepto + '" se eliminó correctamente.'); },
      error: error => { this.saving = false; this.error = this.api.errorMessage(error); },
    });
  }
  export(): void { this.api.download('informes-economicos/exportar', 'informe-economico-cec.xlsx', {...this.filters, anio: this.reportYear}); }
  clearFilters(): void { this.filters = { texto: '', modalidad: '', fechaDesde: '', fechaHasta: '' }; this.load(); }
  modalityLabel(value: string | null): string {
    if (!value) return "Sin especificar"; return ({PRESENCIAL:'Presencial',VIRTUAL:'Virtual',HIBRIDO:'Híbrido'} as Record<string,string>)[value] || value; }
  private yearOf(item: InformeResumen): number { return item.anio ?? Number(item.fechaInicio?.slice(0, 4)); }
  private reportPath(item: InformeResumen | InformeDetalle): string {
    return 'informes-economicos/' + (item.idPlanificacion != null ? item.idPlanificacion : 'referencias/' + item.idInforme);
  }
  private validMoney(value: number | null, max: number): boolean {
    return value !== null && Number.isFinite(value) && value >= 0 && value <= max
      && Math.abs(value * 100 - Math.round(value * 100)) < 0.00001;
  }
  private sumMoney(values: Array<number | string | null | undefined>): number {
    return values.reduce<number>((total, value) => total + Math.round(Number(value) * 100), 0) / 100;
  }
  private roundMoney(value: number): number { return Math.round((value + Number.EPSILON) * 100) / 100; }
  private isUnfiltered(): boolean { return !this.filters.texto && !this.filters.modalidad && !this.filters.fechaDesde && !this.filters.fechaHasta; }
}
