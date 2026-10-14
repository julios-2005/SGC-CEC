import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({ changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-public-header',
  standalone: true,
  imports: [RouterLink],
  template: `@if (!auth.isAuthenticated()) { <header class="topbar"><div class="header-left"><div class="marca-superior"><img src="/assets/img/CEC_LOGO_BLANCO.png" alt="Centro de Educación Continua - UPSE" class="logo-topbar" /></div></div><div class="header-right"><a routerLink="/login" class="enlace-acceso-personal">Iniciar Sesión</a></div></header> }`,
})
export class PublicHeaderComponent {
  constructor(readonly auth: AuthService) {}
}
