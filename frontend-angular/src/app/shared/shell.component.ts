import { ChangeDetectionStrategy, Component, HostListener, OnDestroy, effect } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { ApiService } from '../core/api.service';

@Component({ changeDetection: ChangeDetectionStrategy.Eager,
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="topbar">
      <div class="header-left">
        <button class="btn-menu-hamburguesa" type="button" title="Menú" (click)="sidebarOpen = !sidebarOpen">
          <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round">
            <line x1="3" y1="6" x2="21" y2="6"></line><line x1="3" y1="12" x2="21" y2="12"></line><line x1="3" y1="18" x2="21" y2="18"></line>
          </svg>
        </button>
        <div class="marca-superior">
          <img src="/assets/img/CEC_LOGO_BLANCO.png" alt="Centro de Educación Continua - UPSE" class="logo-topbar" />
        </div>
      </div>
      <div class="header-right">
        <div class="usuario-actual">
          <div class="usuario-info" (click)="dropdownOpen = !dropdownOpen">
            <div class="avatar-usuario"><img [src]="photoUrl || defaultAvatar" alt="Avatar" /></div>
            <span class="nombre-usuario">{{ auth.user()?.nombre || auth.user()?.nombreUsuario }}</span>
            <span class="dropdown-toggle">▼</span>
          </div>
          <div class="dropdown-menu" [class.activo]="dropdownOpen">
            <a routerLink="/cambiar-contrasena" (click)="dropdownOpen = false">Cambiar Contraseña</a>
            <button type="button" (click)="auth.logout()">Cerrar Sesión</button>
          </div>
        </div>
      </div>
    </header>

    <div class="contenedor-principal">
      <aside class="sidebar" [class.oculto]="!sidebarOpen">
        <div class="sidebar-contenido">
          @if (!auth.isCobros()) {
          <section class="seccion-sidebar">
            <div class="seccion-titulo" [class.activa]="inicioOpen" (click)="inicioOpen = !inicioOpen">
              <span class="seccion-icono"></span><span>INICIO</span><span class="seccion-toggle">▼</span>
            </div>
            <div class="seccion-items" [class.abierta]="inicioOpen">
              <a routerLink="/cursos-disponibles" routerLinkActive="activo" class="item-menu">Cursos Disponibles</a>
              <a routerLink="/inscripcion" routerLinkActive="activo" class="item-menu">Inscribir Estudiante</a>
            </div>
          </section>
          }

          <section class="seccion-sidebar">
            <div class="seccion-titulo" [class.activa]="gestionOpen" (click)="gestionOpen = !gestionOpen">
              <span class="seccion-icono"></span><span>GESTIÓN</span><span class="seccion-toggle">▼</span>
            </div>
            <div class="seccion-items" [class.abierta]="gestionOpen">
              @if (!auth.isCobros()) {
              <a routerLink="/cursos" routerLinkActive="activo" class="item-menu">Cursos</a>
              <a routerLink="/especialistas" routerLinkActive="activo" class="item-menu">Especialistas</a>
              <a routerLink="/coordinadores" routerLinkActive="activo" class="item-menu">Coordinadores</a>
              <a routerLink="/planificaciones" routerLinkActive="activo" class="item-menu">Planificaciones</a>
              <a routerLink="/inscripciones" routerLinkActive="activo" class="item-menu">Inscripciones</a>
              } @else {
              <a routerLink="/panel-inscripciones" routerLinkActive="activo" class="item-menu">Inscripciones</a>
              }
            </div>
          </section>

          @if (!auth.isCobros()) {
          <section class="seccion-sidebar">
            <div class="seccion-titulo" [class.activa]="reportesOpen" (click)="reportesOpen = !reportesOpen">
              <span class="seccion-icono"></span><span>REPORTES</span><span class="seccion-toggle">▼</span>
            </div>
            <div class="seccion-items" [class.abierta]="reportesOpen">
              <a routerLink="/informe-economico" routerLinkActive="activo" class="item-menu">Reporte Económico</a>
            </div>
          </section>
          }

          <section class="seccion-sidebar">
            <div class="seccion-titulo" [class.activa]="seguridadOpen" (click)="seguridadOpen = !seguridadOpen"><span class="seccion-icono"></span><span>SEGURIDAD</span><span class="seccion-toggle">▼</span></div>
            <div class="seccion-items" [class.abierta]="seguridadOpen">
              @if (auth.isAdmin()) {
                <a routerLink="/usuarios" routerLinkActive="activo" class="item-menu">Gestión de Usuarios</a>
              }
              <a routerLink="/cambiar-contrasena" routerLinkActive="activo" class="item-menu">Cambiar Contraseña</a>
            </div>
          </section>
        </div>
      </aside>
      <main class="contenido-principal" style="padding: 0"><router-outlet /></main>
    </div>
    <div class="sidebar-fondo" [class.oculto]="!sidebarOpen" (click)="sidebarOpen = false"></div>
  `,
})
export class ShellComponent implements OnDestroy {
  private readonly navigation: Subscription;
  private mobile = innerWidth <= 768;
  sidebarOpen = innerWidth > 768;
  dropdownOpen = false;
  inicioOpen = true;
  gestionOpen = true;
  reportesOpen = true;
  seguridadOpen = true;
  photoUrl = '';
  readonly defaultAvatar = 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 24 24%22 fill=%22%23999%22%3E%3Ccircle cx=%2212%22 cy=%228%22 r=%224%22/%3E%3Cpath d=%22M 12 14 C 7 14 3 16.5 3 20 v 2 h 18 v -2 c 0 -3.5 -4 -6 -9 -6%22/%3E%3C/svg%3E';

  constructor(readonly auth: AuthService, api: ApiService, router: Router) {
    this.navigation = router.events.subscribe(event => {
      if (event instanceof NavigationEnd) { this.dropdownOpen = false; if (this.mobile) this.sidebarOpen = false; }
    });
    effect(onCleanup => {
      const photo = auth.user()?.foto;
      this.photoUrl = '';
      let url = '';
      const request = photo ? api.blob(`coordinadores/archivos/${photo}`).subscribe({
        next: blob => { url = URL.createObjectURL(blob); this.photoUrl = url; }, error: () => {},
      }) : undefined;
      onCleanup(() => { request?.unsubscribe(); if (url) URL.revokeObjectURL(url); });
    });
  }

  ngOnDestroy(): void { this.navigation.unsubscribe(); }
  @HostListener('document:click', ['$event'])
  closeDropdownOutside(event: MouseEvent): void {
    if (event.target instanceof Element && !event.target.closest('.usuario-actual')) this.dropdownOpen = false;
  }
  @HostListener('window:resize')
  resized(): void {
    const mobile = innerWidth <= 768;
    if (mobile !== this.mobile) { this.mobile = mobile; this.sidebarOpen = !mobile; }
  }
}
