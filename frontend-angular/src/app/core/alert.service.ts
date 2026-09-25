import { Injectable, signal } from '@angular/core';

export type AlertType = 'exito' | 'error';
export interface AlertState { tipo: AlertType; mensaje: string; }
export interface ConfirmState { mensaje: string; textoAceptar: string; textoCancelar: string; }

/**
 * Reemplaza a los alert()/confirm() del navegador tras crear, editar o
 * eliminar un registro (cursos, especialistas, coordinadores,
 * planificaciones...), igual que la "alerta central"/"confirmarCentral" que
 * tenían los HTML originales (cursos.html, especialistas.html,
 * Planificaciones.html). Es un único servicio global: basta con un
 * <app-alert-central /> en la raíz de la app para que cualquier componente
 * pueda llamar a success()/error()/confirm().
 */
@Injectable({ providedIn: 'root' })
export class AlertService {
  readonly state = signal<AlertState | null>(null);
  readonly confirmState = signal<ConfirmState | null>(null);
  private confirmResolver?: (value: boolean) => void;

  success(mensaje: string): void { this.state.set({ tipo: 'exito', mensaje }); }
  error(mensaje: string): void { this.state.set({ tipo: 'error', mensaje }); }
  close(): void { this.state.set(null); }

  /** Reemplaza a window.confirm(mensaje): resuelve true (Aceptar) o false (Cancelar/fondo). */
  confirm(mensaje: string, textoAceptar = 'Eliminar', textoCancelar = 'Cancelar'): Promise<boolean> {
    this.confirmResolver?.(false);
    return new Promise<boolean>(resolve => {
      this.confirmResolver = resolve;
      this.confirmState.set({ mensaje, textoAceptar, textoCancelar });
    });
  }
  resolveConfirm(resultado: boolean): void {
    this.confirmState.set(null);
    this.confirmResolver?.(resultado);
    this.confirmResolver = undefined;
  }
}
