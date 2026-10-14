import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { AlertService } from '../core/alert.service';

@Component({ changeDetection: ChangeDetectionStrategy.Eager,
  standalone: true,
  imports: [FormsModule],
  templateUrl: './templates/password.component.html',
  styleUrl: './original-styles/password.css',
})
export class PasswordChangeComponent {
  model = { contraseñaActual: '', contraseñaNueva: '', confirmarContraseña: '' };
  message = '';
  error = false;
  loading = false;
  obligatorio = location.pathname.includes('obligatoria');

  constructor(private readonly api: ApiService, private readonly router: Router, private readonly auth: AuthService, private readonly alerts: AlertService) {}

  get lengthOk(): boolean { return this.model.contraseñaNueva.length >= 8; }
  get uppercaseOk(): boolean { return /[A-Z]/.test(this.model.contraseñaNueva); }
  get numberOk(): boolean { return /[0-9]/.test(this.model.contraseñaNueva); }

  submit(): void {
    if (this.loading) return;
    if (!this.model.contraseñaActual) { this.message = 'Ingresa tu contraseña actual o temporal.'; this.error = true; return; }
    if (this.model.contraseñaNueva !== this.model.confirmarContraseña) {
      this.message = 'La confirmación no coincide con la nueva contraseña.';
      this.error = true;
      return;
    }
    if (!this.lengthOk || !this.uppercaseOk || !this.numberOk) {
      this.message = 'La contraseña no cumple todos los requisitos.';
      this.error = true;
      return;
    }
    this.loading = true;
    const endpoint = this.obligatorio ? 'auth/cambiar-contraseña-obligatoria' : 'auth/cambiar-contraseña';
    this.api.post<any>(endpoint, this.model).subscribe({
      next: () => {
        this.loading = false;
        this.message = '';
        this.error = false;
        this.auth.setPasswordChangeRequired(false);
        this.alerts.success('Tu contraseña se actualizó correctamente.');
        this.router.navigateByUrl(this.auth.landingRoute());
      },
      error: error => {
        this.loading = false;
        this.error = true;
        this.message = this.api.errorMessage(error);
      },
    });
  }
}
