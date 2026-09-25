import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';

@Component({ changeDetection: ChangeDetectionStrategy.Eager,
  standalone: true,
  imports: [FormsModule],
  templateUrl: './templates/login.component.html',
  styleUrl: './original-styles/login.css',
})
export class LoginComponent {
  usuario = '';
  password = '';
  error = '';
  loading = false;

  constructor(private readonly auth: AuthService, private readonly router: Router, private readonly api: ApiService) {
    if (auth.isAuthenticated()) router.navigateByUrl(auth.passwordChangeRequired() ? '/cambiar-contrasena-obligatoria' : auth.landingRoute());
  }

  submit(ngForm: NgForm): void {
    if (this.loading) return;
    if (ngForm.invalid || !this.usuario.trim()) { ngForm.control.markAllAsTouched(); this.error = 'Ingresa tu usuario y contraseña.'; return; }
    this.loading = true;
    this.error = '';
    this.auth.login(this.usuario.trim(), this.password).subscribe({
      next: response => {
        this.loading = false;
        if (!response.exito) {
          this.error = response.mensaje;
          return;
        }
        this.router.navigateByUrl(response.debeCambiarContraseña ? '/cambiar-contrasena-obligatoria' : this.auth.landingRoute());
      },
      error: error => {
        this.loading = false;
        this.error = this.api.errorMessage(error);
      },
    });
  }
}
