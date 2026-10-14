import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../core/api.service';
import { AlertService } from '../core/alert.service';
import { Planificacion, ValidacionInstitucional } from '../core/models';
import { LatestRequest } from '../core/latest-request';
import { DOCUMENT_PATTERN, EMAIL_PATTERN, isValidPhone, fileValidationMessage } from '../core/form-validation';
import { PublicHeaderComponent } from './public-header.component';

@Component({ changeDetection: ChangeDetectionStrategy.Eager,
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, PublicHeaderComponent],
  templateUrl: './templates/enrollment.component.html',
  styleUrl: './original-styles/enrollment.css',
})
export class EnrollmentComponent implements OnInit, OnDestroy {
  private readonly optionsRequest = new LatestRequest();
  private readonly institutionalRequest = new LatestRequest();
  private validatedKey = '';
  institutionalLoading = false;
  institutionalResult?: ValidacionInstitucional;
  institutionalError = '';
  planes: Planificacion[] = [];
  discounts: Record<string, number> = {};
  loading = false;
  optionsLoading = true;
  ready = false;
  files: Record<string, File | null> = {};
  model: any = this.empty();
  readonly tipos = [
    { value: 'EXTERNO', label: 'Externo' },
    { value: 'ESTUDIANTE_UPSE', label: 'Estudiante UPSE' },
    { value: 'DOCENTE_UPSE', label: 'Docente UPSE' },
    { value: 'ADMINISTRATIVO_UPSE', label: 'Administrativo UPSE' },
    { value: 'MAESTRANDO', label: 'Maestrando' },
    { value: 'GRADUADO', label: 'Graduado UPSE' },
    { value: 'PERSONA_DISCAPACIDAD', label: 'Persona con discapacidad' },
  ];

  constructor(private readonly api: ApiService, private readonly route: ActivatedRoute, private readonly alerts: AlertService) {}

  ngOnInit(): void { this.loadOptions(); }
  ngOnDestroy(): void { this.optionsRequest.cancel(); this.institutionalRequest.cancel(); }

  /**
   * Categorías que se confirman con la información institucional (TIC): muestran el botón "Verificar categoría".
   * Graduado y Persona con discapacidad NO están aquí: se revisan con el documento de respaldo. Así quien es
   * Estudiante UPSE y además tiene discapacidad elige una sola categoría (la más favorable), y quien no consta
   * en la base de la universidad puede acreditar su discapacidad con el carnet.
   */
  get institutionalRequired(): boolean {
    return ['ESTUDIANTE_UPSE', 'DOCENTE_UPSE', 'ADMINISTRATIVO_UPSE', 'MAESTRANDO', 'GRADUADO'].includes(this.model.tipoUsuario);
  }

  get documentaryReview(): boolean {
    return this.model.tipoUsuario == 'PERSONA_DISCAPACIDAD';
  }

  /** Los descuentos no se acumulan: aplica solo el de la categoría elegida. Los porcentajes salen de la configuración del CEC. */
  get singleDiscountNotice(): string {
    if (!this.model.tipoUsuario || this.model.tipoUsuario === 'EXTERNO') return '';
    return 'Se aplica un solo descuento por inscripción: el de la categoría que elijas. Los descuentos no se acumulan.';
  }

  /**
   * Para las categorías que se revisan con documento, sugiere otra categoría con un descuento MAYOR si la persona
   * también cumple esa condición (por ejemplo, un graduado que además tiene discapacidad, o una persona con
   * discapacidad que es estudiante UPSE). Vacío si no hay una alternativa mejor.
   */
  get betterDiscountHint(): string {
    if (!this.ready || !this.documentaryReview) return '';
    const current = this.discounts[this.model.tipoUsuario] || 0;
    const better = this.tipos
      .filter(tipo => tipo.value !== this.model.tipoUsuario && (this.discounts[tipo.value] || 0) > current)
      .sort((a, b) => (this.discounts[b.value] || 0) - (this.discounts[a.value] || 0))
      .map(tipo => `${tipo.label} (${this.discounts[tipo.value]}%)`);
    if (!better.length) return '';
    return `Si además cumples la condición de ${this.joinOr(better)}, selecciona esa categoría: tiene un descuento mayor y no se puede sumar al de esta.`;
  }

  private joinOr(items: string[]): string {
    return items.length < 2 ? items.join('') : `${items.slice(0, -1).join(', ')} o ${items[items.length - 1]}`;
  }

  private get participantKey(): string {
    return `${String(this.model.cedula).trim()}|${this.model.tipoUsuario}`;
  }

  get participantVerified(): boolean {
    return this.validatedKey === this.participantKey && this.institutionalResult?.verificado === true
      && this.institutionalResult.tipoUsuario === this.model.tipoUsuario;
  }

  get participantReady(): boolean {
    return !!this.model.tipoUsuario && (!this.institutionalRequired || this.participantVerified);
  }

  get canVerifyParticipant(): boolean {
    return this.institutionalRequired 
    && !this.institutionalLoading 
    && !this.loading
    && DOCUMENT_PATTERN.test(String(this.model.cedula).trim());
  }

  identityChanged(value: string): void {
    this.model.cedula = value;
    this.clearInstitutionalValidation();
  }

  private clearInstitutionalValidation(): void {
    this.institutionalRequest.cancel();
    this.validatedKey = '';
    this.institutionalLoading = false;
    this.institutionalResult = undefined;
    this.institutionalError = '';
  }

  verifyParticipant(): void {
    if (!this.canVerifyParticipant) return;
    const key = this.participantKey;
    const tipo = this.model.tipoUsuario;
    this.clearInstitutionalValidation();
    this.institutionalLoading = true;
    this.institutionalRequest.run(this.api.validarParticipante(String(this.model.cedula).trim(), tipo), {
      next: result => {
        if (key !== this.participantKey) return;
        this.institutionalLoading = false;
        if (result.tipoUsuario !== tipo) {
          this.institutionalError = 'No se pudo confirmar la categoría. Inténtalo nuevamente.';
          return;
        }
        this.validatedKey = key;
        this.institutionalResult = result;
      },
      error: error => {
        if (key !== this.participantKey) return;
        this.institutionalLoading = false;
        this.institutionalError = this.api.errorMessage(error);
      },
    });
  }
  loadOptions(preselect = true): void {
    this.ready = false;
    this.optionsLoading = true;
    this.optionsRequest.run(forkJoin({
      plans: this.api.get<Planificacion[]>('planificaciones/vigentes'),
      discounts: this.api.get<Array<{ tipoUsuario: string; porcentaje: number }>>('descuentos/activos'),
    }), {
      next: data => {
        this.planes = data.plans;
        this.discounts = Object.fromEntries(data.discounts.map(item => [item.tipoUsuario, Number(item.porcentaje)]));
        this.ready = true; this.optionsLoading = false;
        const params = this.route.snapshot.queryParamMap;
        const selected = Number(params.get('planificacion') || params.get('idPlanificacion'));
        if (preselect && selected) {
          if (data.plans.some(item => item.id === selected)) this.model.idPlanificacion = selected;
          else this.fail('El curso del enlace ya no está disponible para inscripción. Selecciona otro curso del catálogo.');
        }
      },
      error: error => { this.optionsLoading = false; this.fail(this.api.errorMessage(error)); },
    });
  }

  get selectedPlan(): Planificacion | undefined {
    return this.planes.find(plan => plan.id === Number(this.model.idPlanificacion));
  }

  get courseCost(): number {
    return Number(this.selectedPlan?.curso.costo) || 0;
  }

  get discountPercentage(): number {
    if (!this.participantReady) return 0;
    return this.discounts[this.model.tipoUsuario] || 0;
  }

  get discountAmount(): number {
    return this.courseCost * this.discountPercentage / 100;
  }

  get totalToPay(): number {
    return this.courseCost - this.discountAmount;
  }

  openPayment(): void {
    if (this.ready && this.selectedPlan && this.participantReady) window.open('/pago-seguro', '_blank', 'noopener');
  }

  get documentLabel(): string {
    return ({
      ESTUDIANTE_UPSE: 'Comprobante de matrícula',
      DOCENTE_UPSE: 'Nombramiento o contrato docente UPSE',
      ADMINISTRATIVO_UPSE: 'Nombramiento o contrato administrativo UPSE',
      MAESTRANDO: 'Certificado de maestría en curso',
      GRADUADO: 'Certificado de título o graduado UPSE',
      PERSONA_DISCAPACIDAD: 'Carnet de discapacidad',
    } as Record<string, string>)[this.model.tipoUsuario] || '';
  }

  get documentOptional(): boolean {
    return ['DOCENTE_UPSE', 'ADMINISTRATIVO_UPSE'].includes(this.model.tipoUsuario);
  }

  file(event: Event, name: string): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    const message = fileValidationMessage(file);
    this.files[name] = message ? null : (file || null);
    if (message) { input.value = ''; this.fail(message); }
  }

  typeChanged(value: string, documentInput: HTMLInputElement): void {
    this.model.tipoUsuario = value;
    this.files['documentoAdicional'] = null;
    documentInput.value = '';
    this.clearInstitutionalValidation();
  }

  submit(ngForm: NgForm, element: HTMLFormElement): void {
    if (this.loading) return;
    if (!this.ready) { this.fail('No se pudieron cargar los cursos y descuentos. Recarga la página antes de inscribirte.'); return; }
    if (ngForm.invalid || !this.selectedPlan || !String(this.model.nombreCompleto).trim() || !String(this.model.direccion).trim()) {
      ngForm.control.markAllAsTouched(); this.fail('Completa correctamente todos los campos obligatorios.'); return;
    }
    if (!DOCUMENT_PATTERN.test(String(this.model.cedula).trim())) { this.fail('El documento debe tener entre 5 y 20 letras o números.'); return; }
    if (this.institutionalRequired && !this.participantVerified) {
      this.fail('Verifica tu categoría con la información institucional antes de continuar.'); return;
    }
    if (!isValidPhone(this.model.telefono)) { this.fail('Escribe un número de WhatsApp válido.'); return; }
    if (!EMAIL_PATTERN.test(String(this.model.correoElectronico).trim())) { this.fail('Escribe un correo electrónico válido.'); return; }
    if (!this.files['comprobantePago'] || !this.files['copiaCedula']) {
      this.fail('Adjunta el comprobante de pago y la copia de tu documento.'); return;
    }
    if (this.documentLabel && !this.documentOptional && !this.files['documentoAdicional']) { this.fail(`Adjunta el documento de respaldo: ${this.documentLabel}.`); return; }
    const invalidFile = Object.values(this.files).map(file => fileValidationMessage(file || undefined)).find(Boolean);
    if (invalidFile) { this.fail(invalidFile); return; }
    const form = new FormData();
    Object.entries(this.model).forEach(([key, value]) => form.append(key, String(value).trim()));
    Object.entries(this.files).forEach(([key, value]) => { if (value && (key !== 'documentoAdicional' || this.documentLabel)) form.append(key, value); });
    this.loading = true;
    this.api.post('inscripciones', form).subscribe({
      next: () => {
        this.loading = false;
        this.model = this.empty();
        this.clearInstitutionalValidation();
        this.files = {};
        element.reset();
        ngForm.resetForm(this.model);
        this.loadOptions(false);
        this.alerts.success('Inscripción registrada correctamente. Quedó pendiente de revisión.');
      },
      error: error => {
        this.loading = false;
        // El backend vuelve a consultar al guardar. Si rechaza la inscripción,
        // la persona debe actualizar la comprobación antes de reintentar.
        if (this.institutionalRequired) this.clearInstitutionalValidation();
        this.alerts.error(this.api.errorMessage(error));
      },
    });
  }
  private fail(message: string): void { this.alerts.error(message); }
  private empty(): any { return { idPlanificacion: null, tipoUsuario: '', nombreCompleto: '', cedula: '', telefono: '', correoElectronico: '', direccion: '', sexo: '' }; }
}
