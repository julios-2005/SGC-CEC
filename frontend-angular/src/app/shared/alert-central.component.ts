import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AlertService } from '../core/alert.service';

/**
 * Réplica en Angular de la "alerta central" (.alerta-central-fondo /
 * .alerta-central-tarjeta) y de "confirmarCentral" de los HTML originales:
 * un aviso modal de éxito/error tras guardar, editar o eliminar (ícono
 * ✓/✕, botón "Aceptar"), y un diálogo de confirmación con "Cancelar" /
 * "Eliminar" en vez del confirm() nativo del navegador. Se monta una sola
 * vez en app.html.
 */
@Component({ changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-alert-central',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (alerts.confirmState(); as confirmacion) {
      <div class="alerta-central-fondo visible">
        <div class="alerta-central-tarjeta pregunta">
          <div class="alerta-central-icono">?</div>
          <p class="alerta-central-texto">{{ confirmacion.mensaje }}</p>
          <div class="alerta-central-botones">
            <button type="button" class="alerta-central-btn secundario" (click)="alerts.resolveConfirm(false)">{{ confirmacion.textoCancelar }}</button>
            <button type="button" class="alerta-central-btn" (click)="alerts.resolveConfirm(true)">{{ confirmacion.textoAceptar }}</button>
          </div>
        </div>
      </div>
    } @else if (alerts.state(); as alerta) {
      <div class="alerta-central-fondo visible" (click)="onBackdrop($event)">
        <div class="alerta-central-tarjeta" [class.exito]="alerta.tipo === 'exito'" [class.error]="alerta.tipo === 'error'">
          <div class="alerta-central-icono">{{ alerta.tipo === 'exito' ? '✓' : '✕' }}</div>
          <p class="alerta-central-texto">{{ alerta.mensaje }}</p>
          <button type="button" class="alerta-central-btn" (click)="alerts.close()">Aceptar</button>
        </div>
      </div>
    }
  `,
  styles: [`
    .alerta-central-fondo {
      position: fixed; inset: 0; background: rgba(0, 39, 73, 0.45);
      z-index: 9999; display: flex; align-items: center; justify-content: center; padding: 20px;
    }
    .alerta-central-tarjeta {
      background: #fff; border-radius: 12px; padding: 28px 26px; max-width: 380px; width: 100%;
      text-align: center; box-shadow: 0 20px 50px -12px rgba(0, 39, 73, 0.4);
      animation: alertaCentralAparecer 0.18s ease-out;
    }
    @keyframes alertaCentralAparecer {
      from { opacity: 0; transform: scale(0.94); }
      to { opacity: 1; transform: scale(1); }
    }
    .alerta-central-icono {
      width: 52px; height: 52px; border-radius: 50%; margin: 0 auto 14px;
      display: flex; align-items: center; justify-content: center; font-size: 26px; font-weight: 700;
    }
    .alerta-central-tarjeta.exito .alerta-central-icono { background: #e6f4ea; color: var(--success, #15805f); }
    .alerta-central-tarjeta.error .alerta-central-icono { background: #fbeceb; color: var(--danger, #b8323c); }
    .alerta-central-tarjeta.pregunta .alerta-central-icono { background: #fdf3e3; color: #a8710a; }
    .alerta-central-texto { font-size: 14.5px; color: var(--text, #17324d); margin: 0 0 20px; line-height: 1.5; }
    .alerta-central-btn {
      padding: 10px 28px; border: none; border-radius: 8px; font-weight: 700; font-size: 13.5px;
      cursor: pointer; color: #fff;
    }
    .alerta-central-tarjeta.exito .alerta-central-btn { background: var(--success, #15805f); }
    .alerta-central-tarjeta.error .alerta-central-btn { background: var(--danger, #b8323c); }
    .alerta-central-tarjeta.pregunta .alerta-central-botones { display: flex; gap: 10px; justify-content: center; }
    .alerta-central-tarjeta.pregunta .alerta-central-btn { background: var(--danger, #b8323c); }
    .alerta-central-tarjeta.pregunta .alerta-central-btn.secundario { background: #eef2f6; color: var(--text, #17324d); }
  `],
})
export class AlertCentralComponent {
  constructor(readonly alerts: AlertService) {}
  onBackdrop(event: MouseEvent): void { if (event.target === event.currentTarget) this.alerts.close(); }
}
