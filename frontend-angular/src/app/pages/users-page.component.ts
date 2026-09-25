import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { AlertService } from '../core/alert.service';
import { Coordinador, PageResponse, Usuario } from '../core/models';
import { LatestRequest } from '../core/latest-request';

/** Los tres roles reales del sistema, más '' para "sin seleccionar" (filtro vacío o formulario recién abierto). */
type RolUsuario = '' | 'ADMIN_GENERAL' | 'COORDINADOR' | 'COBROS';

interface FiltrosUsuario {
  texto: string;
  rol: RolUsuario;
  estado: '' | 'true' | 'false';
  sort: string;
}

interface FormularioUsuario {
  nombreUsuario: string;
  rol: RolUsuario;
  estado: boolean;
  /** null = sin coordinador vinculado (admin independiente); obligatorio solo si rol === 'COORDINADOR'. */
  idCoordinador: number | null;
  /** Solo se usa cuando no hay coordinador vinculado (ver showNombreCompleto). */
  nombreCompleto: string;
}

@Component({ changeDetection: ChangeDetectionStrategy.Eager, standalone: true, imports: [CommonModule, FormsModule], templateUrl: './templates/users.component.html', styleUrl: './original-styles/users.css' })
export class UsersComponent implements OnInit, OnDestroy {
  private filterTimer?: ReturnType<typeof setTimeout>;
  private readonly listRequest = new LatestRequest();
  private readonly optionsRequest = new LatestRequest();
  loading = false;
  saving = false;
  items: Usuario[] = []; coordinators: Coordinador[] = [];
  filters: FiltrosUsuario = { texto: '', rol: '', estado: '', sort: 'fechaCreacion,desc' };
  page = 0; totalPages = 0; editing?: Usuario; temporaryPassword = ''; error = ''; formModalOpen = false; passwordModalOpen = false; form: FormularioUsuario = this.empty();
  constructor(readonly api: ApiService, private readonly alerts: AlertService) {}
  ngOnInit(): void {
    this.load();
    this.optionsRequest.run(this.api.get<Coordinador[]>('coordinadores/activos'), { next: data => this.coordinators = data, error: error => this.error = this.api.errorMessage(error) });
  }
  ngOnDestroy(): void { clearTimeout(this.filterTimer); this.listRequest.cancel(); this.optionsRequest.cancel(); }
  scheduleLoad(): void { clearTimeout(this.filterTimer); this.filterTimer = setTimeout(() => this.load(), 300); }
  load(page = 0): void {
    clearTimeout(this.filterTimer); this.page = page; this.items = []; this.error = ''; this.loading = true;
    this.listRequest.run(this.api.get<PageResponse<Usuario>>('usuarios', { ...this.filters, page, size: 20 }), {
      next: data => {
        if (page > 0 && page >= data.totalPages) { this.load(Math.max(0, data.totalPages - 1)); return; }
        this.items = data.content; this.totalPages = data.totalPages; this.loading = false;
      },
      error: error => { this.error = this.api.errorMessage(error); this.loading = false; },
    });
  }
  clearFilters(): void { this.filters = { texto: '', rol: '', estado: '', sort: 'fechaCreacion,desc' }; this.load(); }
  open(item?: Usuario): void { this.editing = item; this.error = ''; this.form = item ? { nombreUsuario: item.nombreUsuario, rol: item.rol as RolUsuario, estado: item.estado, idCoordinador: item.coordinadorId || null, nombreCompleto: (item.rol !== 'COORDINADOR' && !item.coordinadorId) ? item.nombre : '' } : this.empty(); this.formModalOpen = true; }
  closeForm(): void { this.formModalOpen = false; }

  // El selector de coordinador aplica a COORDINADOR (obligatorio) y a
  // ADMIN_GENERAL (opcional, para heredar nombre/foto del coordinador).
  get showCoordinatorSelect(): boolean { return this.form.rol === 'COORDINADOR' || this.form.rol === 'ADMIN_GENERAL'; }
  get coordinatorRequired(): boolean { return this.form.rol === 'COORDINADOR'; }
  // El campo de nombre completo solo tiene sentido si no hay un coordinador vinculado.
  get showNombreCompleto(): boolean { return this.form.rol !== 'COORDINADOR' && !this.form.idCoordinador; }
  roleChanged(): void { if (!this.showCoordinatorSelect) this.form.idCoordinador = null; }
  get missingCoordinator(): Usuario | undefined { return this.editing?.coordinadorId && !this.coordinators.some(item => item.id === this.editing!.coordinadorId) ? this.editing : undefined; }
  save(ngForm: NgForm): void {
    console.log('SAVE LLAMADO', { invalido: ngForm.invalid, form: this.form, saving: this.saving });
    if (this.saving) return;
    this.error = '';
    if (ngForm.invalid || !String(this.form.nombreUsuario).trim() || (this.coordinatorRequired && !this.form.idCoordinador)) {
      ngForm.control.markAllAsTouched(); this.error = 'Completa correctamente los campos obligatorios.'; return;
    }
    const payload = { ...this.form, nombreUsuario: this.form.nombreUsuario.trim(), nombreCompleto: this.showNombreCompleto ? String(this.form.nombreCompleto || '').trim() : '', idCoordinador: this.showCoordinatorSelect && this.form.idCoordinador ? Number(this.form.idCoordinador) : null };
    this.saving = true;
    const request = this.editing ? this.api.put<Usuario>(`usuarios/${this.editing.id}`, payload) : this.api.post<Usuario>('usuarios', payload);
    const editando = !!this.editing;
    request.subscribe({
      next: data => {
        this.saving = false; this.closeForm(); this.load(this.page);
        if (data.contraseñaTemporal) this.showPassword(data.contraseñaTemporal);
        this.alerts.success(editando ? `El usuario "${data.nombreUsuario}" se actualizó correctamente.` : `El usuario "${data.nombreUsuario}" se creó correctamente.`);
      },
      error: error => { this.saving = false; this.error = this.api.errorMessage(error); },
    });
  }
  async toggle(item: Usuario): Promise<void> {
    if (!await this.alerts.confirm(`¿${item.estado ? 'Desactivar' : 'Activar'} la cuenta ${item.nombreUsuario}?`, item.estado ? 'Desactivar' : 'Activar')) return;
    const request = item.estado ? this.api.delete(`usuarios/${item.id}`) : this.api.put(`usuarios/${item.id}/activar`, {});
    request.subscribe({
      next: () => { this.load(this.page); this.alerts.success(`La cuenta "${item.nombreUsuario}" se ${item.estado ? 'desactivó' : 'activó'} correctamente.`); },
      error: error => this.alerts.error(this.api.errorMessage(error)),
    });
  }
  async resetPassword(item: Usuario): Promise<void> {
    if (!await this.alerts.confirm(`¿Restablecer la contraseña de ${item.nombreUsuario}?`, 'Restablecer')) return;
    this.api.post<Usuario>(`usuarios/${item.id}/restablecer-contraseña`, {}).subscribe({
      next: data => { if (data.contraseñaTemporal) this.showPassword(data.contraseñaTemporal); this.alerts.success(`La contraseña de "${item.nombreUsuario}" se restableció correctamente.`); },
      error: error => this.alerts.error(this.api.errorMessage(error)),
    });
  }
  async remove(item: Usuario): Promise<void> {
    if (!await this.alerts.confirm(`¿Eliminar permanentemente la cuenta ${item.nombreUsuario}?`)) return;
    this.api.delete(`usuarios/${item.id}/eliminar`).subscribe({
      next: () => { this.load(this.page); this.alerts.success(`El usuario "${item.nombreUsuario}" se eliminó correctamente.`); },
      error: error => this.alerts.error(this.api.errorMessage(error)),
    });
  }
  showPassword(value: string): void { this.temporaryPassword = value; this.passwordModalOpen = true; }
  async copyPassword(): Promise<void> {
    try { await navigator.clipboard.writeText(this.temporaryPassword); this.alerts.success('Contraseña copiada al portapapeles.'); }
    catch { this.alerts.error('El navegador no permitió copiar automáticamente. Selecciona la contraseña y cópiala manualmente.'); }
  }
  private empty(): FormularioUsuario { return { nombreUsuario: '', rol: '', estado: true, idCoordinador: null, nombreCompleto: '' }; }
}