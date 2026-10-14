import { provideZoneChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NgForm } from '@angular/forms';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ValidacionInstitucional } from '../core/models';
import { EnrollmentComponent } from './enrollment.component';

describe('Verificación institucional en la inscripción', () => {
  const confirmed = (tipoUsuario = 'DOCENTE_UPSE', verificado = true): ValidacionInstitucional => ({
    tipoUsuario, requiereConsulta: true, verificado,
    estado: verificado ? 'CONFIRMADO' : 'NO_CONFIRMADO', mensaje: 'Resultado institucional',
  });
  function setup() {
    const api = { validarParticipante: vi.fn(() => of(confirmed())), post: vi.fn(() => new Subject().asObservable()), errorMessage: () => 'Servicio no disponible' };
    const alerts = { error: vi.fn(), success: vi.fn() };
    const component = new EnrollmentComponent(api as unknown as ApiService, {} as ActivatedRoute, alerts as any);
    component.ready = true;
    component.planes = [{ id: 1, curso: { costo: 100 } } as any];
    component.discounts = { DOCENTE_UPSE: 20 };
    component.model = { idPlanificacion: 1, tipoUsuario: 'DOCENTE_UPSE', nombreCompleto: 'Persona de Prueba', cedula: '1234567890', telefono: '+593991234567', correoElectronico: 'prueba@example.test', direccion: 'Dirección', sexo: 'FEMENINO' };
    component.files = { comprobantePago: new File(['pdf'], 'pago.pdf'), copiaCedula: new File(['pdf'], 'cedula.pdf') };
    return { component, api, alerts };
  }
  const form = () => ({ invalid: false, control: { markAllAsTouched: vi.fn() } }) as unknown as NgForm;

  it('exige una confirmación antes de mostrar descuento, pagar o enviar', () => {
    const { component, api } = setup();
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    expect(component.participantReady).toBe(false);
    expect(component.totalToPay).toBe(100);
    component.openPayment();
    component.submit(form(), document.createElement('form'));
    expect(open).not.toHaveBeenCalled();
    expect(api.post).not.toHaveBeenCalled();
    component.verifyParticipant();
    expect(api.validarParticipante).toHaveBeenCalledWith('1234567890', 'DOCENTE_UPSE');
    expect(component.participantReady).toBe(true);
    expect(component.totalToPay).toBe(80);
    component.openPayment();
    expect(open).toHaveBeenCalledOnce();
    component.submit(form(), document.createElement('form'));
    expect(api.post).toHaveBeenCalledOnce();
    const body = (api.post.mock.calls[0] as unknown as [string, FormData])[1];
    expect(body.has('verificado')).toBe(false);
    open.mockRestore();
  });

  it('no acepta una respuesta negativa ni una confirmación para otra categoría', () => {
    const { component, api } = setup();
    api.validarParticipante.mockReturnValue(of(confirmed('DOCENTE_UPSE', false)));
    component.verifyParticipant();
    expect(component.participantReady).toBe(false);
    api.validarParticipante.mockReturnValue(of(confirmed('ADMINISTRATIVO_UPSE')));
    component.verifyParticipant();
    expect(component.participantReady).toBe(false);
    expect(component.institutionalError).toContain('confirmar');
  });

  it('invalida la confirmación al cambiar cédula o categoría', () => {
    const { component } = setup();
    component.verifyParticipant();
    component.identityChanged('9999999999');
    expect(component.participantVerified).toBe(false);
    expect(component.institutionalResult).toBeUndefined();
    component.verifyParticipant();
    component.typeChanged('ESTUDIANTE_UPSE', document.createElement('input'));
    expect(component.participantVerified).toBe(false);
  });

  it('descarta respuestas tardías de una cédula anterior', () => {
    const { component, api } = setup();
    const pending = new Subject<ValidacionInstitucional>();
    api.validarParticipante.mockReturnValue(pending);
    component.verifyParticipant();
    expect(component.institutionalLoading).toBe(true);
    component.identityChanged('9999999999');
    pending.next(confirmed());
    expect(component.institutionalLoading).toBe(false);
    expect(component.participantVerified).toBe(false);
    expect(component.institutionalResult).toBeUndefined();
  });

  it('permite reintentar después de un fallo de TIC sin convertir a externo', () => {
    const { component, api } = setup();
    api.validarParticipante.mockReturnValue(throwError(() => new Error()));
    component.verifyParticipant();
    expect(component.institutionalError).toBe('Servicio no disponible');
    expect(component.model.tipoUsuario).toBe('DOCENTE_UPSE');
    expect(component.participantReady).toBe(false);
    expect(component.canVerifyParticipant).toBe(true);
  });

  it('requiere actualizar la consulta si el backend rechaza el guardado', () => {
    const { component, api } = setup();
    component.verifyParticipant();
    api.post.mockReturnValue(throwError(() => new Error()));
    component.submit(form(), document.createElement('form'));
    expect(component.loading).toBe(false);
    expect(component.participantVerified).toBe(false);
  });

  it('conserva el proceso de Externo y la revisión documental de Graduado y Persona con discapacidad', () => {
    const { component, api } = setup();
    for (const tipo of ['EXTERNO', 'GRADUADO', 'PERSONA_DISCAPACIDAD']) {
      component.typeChanged(tipo, document.createElement('input'));
      expect(component.institutionalRequired).toBe(false);
      expect(component.canVerifyParticipant).toBe(false);
      component.verifyParticipant();
      expect(component.participantReady).toBe(true);
      expect(component.participantVerified).toBe(false);
    }
    expect(api.validarParticipante).not.toHaveBeenCalled();
  });

  it('una persona con discapacidad se inscribe sin verificar, con su descuento y con el carnet como respaldo', () => {
    const { component, api, alerts } = setup();
    component.discounts = { PERSONA_DISCAPACIDAD: 30 };
    component.typeChanged('PERSONA_DISCAPACIDAD', document.createElement('input'));
    expect(component.totalToPay).toBe(70);

    component.submit(form(), document.createElement('form'));
    expect(api.post).not.toHaveBeenCalled(); // sin carnet no se envía
    expect(alerts.error).toHaveBeenCalledWith(expect.stringContaining('Carnet de discapacidad'));

    component.files['documentoAdicional'] = new File(['pdf'], 'carnet.pdf');
    component.submit(form(), document.createElement('form'));
    expect(api.post).toHaveBeenCalledOnce();
    expect(api.validarParticipante).not.toHaveBeenCalled();
  });

  describe('aviso de descuento único', () => {
    const configured = { EXTERNO: 0, ESTUDIANTE_UPSE: 50, DOCENTE_UPSE: 25, ADMINISTRATIVO_UPSE: 25, MAESTRANDO: 0, GRADUADO: 25, PERSONA_DISCAPACIDAD: 30 };
    function withDiscounts(tipo: string, discounts: Record<string, number> = configured) {
      const { component } = setup();
      component.discounts = discounts;
      component.typeChanged(tipo, document.createElement('input'));
      return component;
    }

    it('indica que no se acumulan, salvo en Externo o sin categoría', () => {
      expect(withDiscounts('').singleDiscountNotice).toBe('');
      expect(withDiscounts('EXTERNO').singleDiscountNotice).toBe('');
      for (const tipo of ['ESTUDIANTE_UPSE', 'GRADUADO', 'PERSONA_DISCAPACIDAD']) {
        expect(withDiscounts(tipo).singleDiscountNotice).toContain('un solo descuento');
      }
    });

    it('a quien tiene discapacidad le sugiere Estudiante UPSE con el porcentaje configurado', () => {
      const hint = withDiscounts('PERSONA_DISCAPACIDAD').betterDiscountHint;
      expect(hint).toContain('Estudiante UPSE (50%)');
      expect(hint).not.toContain('Docente');
      expect(hint).not.toContain('Graduado');
      expect(withDiscounts('PERSONA_DISCAPACIDAD', { ...configured, ESTUDIANTE_UPSE: 20 }).betterDiscountHint).toBe('');
    });

    it('a un graduado le sugiere las categorías de mayor descuento, de la mejor a la menor', () => {
      const hint = withDiscounts('GRADUADO').betterDiscountHint;
      expect(hint).toContain('Estudiante UPSE (50%) o Persona con discapacidad (30%)');
    });

    it('no sugiere nada si ya eligió el mayor descuento, si se confirma con TIC o si no cargaron los descuentos', () => {
      expect(withDiscounts('ESTUDIANTE_UPSE').betterDiscountHint).toBe('');
      expect(withDiscounts('DOCENTE_UPSE').betterDiscountHint).toBe('');
      const component = withDiscounts('GRADUADO');
      component.ready = false;
      expect(component.betterDiscountHint).toBe('');
    });
  });

  it('en pantalla, el botón Verificar categoría solo aparece donde TIC confirma la categoría', async () => {
    const discounts = [{ tipoUsuario: 'ESTUDIANTE_UPSE', porcentaje: 50 }, { tipoUsuario: 'PERSONA_DISCAPACIDAD', porcentaje: 30 }, { tipoUsuario: 'GRADUADO', porcentaje: 25 }];
    TestBed.configureTestingModule({
      imports: [EnrollmentComponent],
      providers: [provideZoneChangeDetection(), provideRouter([]),
        { provide: ApiService, useValue: { get: (path: string) => of(path === 'planificaciones/vigentes' ? [] : discounts), errorMessage: () => 'Error' } },
        { provide: AuthService, useValue: { isAuthenticated: () => false } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({}) } } }],
    });
    const fixture = TestBed.createComponent(EnrollmentComponent);
    fixture.autoDetectChanges();
    await fixture.whenStable();
    const html: HTMLElement = fixture.nativeElement;
    const show = async (tipo: string) => {
      fixture.componentInstance.typeChanged(tipo, document.createElement('input'));
      fixture.detectChanges(); await fixture.whenStable();
    };

    await show('ESTUDIANTE_UPSE');
    expect(html.querySelector('#btnVerificarParticipante')).not.toBeNull();
    await show('GRADUADO');
    expect(html.querySelector('#btnVerificarParticipante')).toBeNull();
    expect(html.querySelector('#avisoMejorDescuento')?.textContent).toContain('Estudiante UPSE (50%)');
    await show('PERSONA_DISCAPACIDAD');
    expect(html.querySelector('#btnVerificarParticipante')).toBeNull();
    expect(html.querySelector('#avisoMejorDescuento')?.textContent).toContain('Estudiante UPSE (50%)');
    expect(html.textContent).toContain('carnet de respaldo');
    await show('EXTERNO');
    expect(html.querySelector('#avisoDescuento')).toBeNull();
    fixture.destroy();
  });
});
