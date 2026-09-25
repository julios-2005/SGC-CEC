import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { AlertService } from '../core/alert.service';
import { Planificacion } from '../core/models';
import { LatestRequest } from '../core/latest-request';
import { DOCUMENT_PATTERN, EMAIL_PATTERN, isValidPhone, fileValidationMessage } from '../core/form-validation';

@Component({ changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-public-header',
  standalone: true,
  imports: [RouterLink],
  template: `@if (!auth.isAuthenticated()) { <header class="topbar"><div class="header-left"><div class="marca-superior"><img src="/assets/img/CEC_LOGO_BLANCO.png" alt="Centro de Educación Continua - UPSE" class="logo-topbar" /></div></div><div class="header-right"><a routerLink="/login" class="enlace-acceso-personal">Iniciar Sesión</a></div></header> }`,
})
export class PublicHeaderComponent {
  constructor(readonly auth: AuthService) {}
}

@Component({ changeDetection: ChangeDetectionStrategy.Eager,
  standalone: true,
  imports: [CommonModule, RouterLink, PublicHeaderComponent],
  templateUrl: './templates/public-courses.component.html',
  styleUrl: './original-styles/public-courses.css',
})
export class PublicCoursesComponent implements OnInit, OnDestroy {
  private readonly listRequest = new LatestRequest();
  planes: Planificacion[] = [];
  loading = true;
  error = '';

  constructor(private readonly api: ApiService) {}

  ngOnInit(): void {
    this.listRequest.run(this.api.get<Planificacion[]>('planificaciones/vigentes'), {
      next: data => { this.planes = data; this.loading = false; },
      error: error => { this.error = this.api.errorMessage(error); this.loading = false; },
    });
  }
  ngOnDestroy(): void { this.listRequest.cancel(); }

  courseImage(plan: Planificacion): string {
    return plan.curso.foto
      ? this.api.url(`cursos/archivos/${plan.curso.foto}`)
      : '/assets/img/Portada_SGC.jpeg';
  }

  modalityLabel(value: string): string {
    return ({ PRESENCIAL: 'Presencial', VIRTUAL: 'Virtual', HIBRIDO: 'Híbrido' } as Record<string, string>)[value] || value;
  }
}

@Component({ changeDetection: ChangeDetectionStrategy.Eager,
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, PublicHeaderComponent],
  templateUrl: './templates/enrollment.component.html',
  styleUrl: './original-styles/enrollment.css',
})
export class EnrollmentComponent implements OnInit, OnDestroy {
  private readonly optionsRequest = new LatestRequest();
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
  ngOnDestroy(): void { this.optionsRequest.cancel(); }
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
    return this.discounts[this.model.tipoUsuario] || 0;
  }

  get discountAmount(): number {
    return this.courseCost * this.discountPercentage / 100;
  }

  get totalToPay(): number {
    return this.courseCost - this.discountAmount;
  }

  openPayment(): void {
    if (this.ready && this.selectedPlan && this.model.tipoUsuario) window.open('/pago-seguro', '_blank', 'noopener');
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
  }

  submit(ngForm: NgForm, element: HTMLFormElement): void {
    if (this.loading) return;
    if (!this.ready) { this.fail('No se pudieron cargar los cursos y descuentos. Recarga la página antes de inscribirte.'); return; }
    if (ngForm.invalid || !this.selectedPlan || !String(this.model.nombreCompleto).trim() || !String(this.model.direccion).trim()) {
      ngForm.control.markAllAsTouched(); this.fail('Completa correctamente todos los campos obligatorios.'); return;
    }
    if (!DOCUMENT_PATTERN.test(String(this.model.cedula).trim())) { this.fail('El documento debe tener entre 5 y 20 letras o números.'); return; }
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
        this.files = {};
        element.reset();
        ngForm.resetForm(this.model);
        this.loadOptions(false);
        this.alerts.success('Inscripción registrada correctamente. Quedó pendiente de revisión.');
      },
      error: error => {
        this.loading = false;
        this.alerts.error(this.api.errorMessage(error));
      },
    });
  }
  private fail(message: string): void { this.alerts.error(message); }
  private empty(): any { return { idPlanificacion: null, tipoUsuario: '', nombreCompleto: '', cedula: '', telefono: '', correoElectronico: '', direccion: '', sexo: '' }; }
}

@Component({ changeDetection: ChangeDetectionStrategy.Eager,
  standalone: true,
  imports: [RouterLink, PublicHeaderComponent],
  templateUrl: './templates/payment.component.html',
  styleUrl: './original-styles/payment.css',
})
export class PaymentComponent {
  constructor(readonly api: ApiService) {}
}
