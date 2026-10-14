import { Routes } from '@angular/router';
import { authGuard, adminGuard, authenticatedMatchGuard } from './core/auth.guards';
import { ShellComponent } from './shared/shell.component';

export const routes: Routes = [
  // Conserva enlaces y QR del frontend original al migrar las rutas a Angular.
  ...Object.entries({
    'index.html': 'cursos-disponibles',
    'Login.html': 'login',
    'cursos-disponibles.html': 'cursos-disponibles',
    'inscripcion-form.html': 'inscripcion',
    'pago-seguro.html': 'pago-seguro',
    'cursos.html': 'cursos',
    'especialistas.html': 'especialistas',
    'coordinadores.html': 'coordinadores',
    'Planificaciones.html': 'planificaciones',
    'inscripciones.html': 'inscripciones',
    'panel-admin.html': 'panel-inscripciones',
    'informe-economico.html': 'informe-economico',
    'usuarios.html': 'usuarios',
    'consulta-especialistas.html': 'consulta-especialistas',
    'consulta-coordinadores.html': 'consulta-coordinadores',
    'cambio-contrasena-obligatorio.html': 'cambiar-contrasena-obligatoria',
  }).map(([path, redirectTo]) => ({ path, redirectTo, pathMatch: 'full' as const })),
  { path: '', pathMatch: 'full', redirectTo: 'cursos-disponibles' },
  {
    path: 'cursos-disponibles',
    component: ShellComponent,
    canMatch: [authenticatedMatchGuard],
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', title: 'Cursos disponibles | CEC UPSE', loadComponent: () => import('./pages/public-courses.component').then(m => m.PublicCoursesComponent) },
    ],
  },
  {
    path: 'inscripcion',
    component: ShellComponent,
    canMatch: [authenticatedMatchGuard],
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', title: 'Inscripción | CEC UPSE', loadComponent: () => import('./pages/enrollment.component').then(m => m.EnrollmentComponent) },
    ],
  },
  {
    path: 'pago-seguro',
    component: ShellComponent,
    canMatch: [authenticatedMatchGuard],
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', title: 'Pago seguro | CEC UPSE', loadComponent: () => import('./pages/payment.component').then(m => m.PaymentComponent) },
    ],
  },
  { path: 'cursos-disponibles', title: 'Cursos disponibles | CEC UPSE', loadComponent: () => import('./pages/public-courses.component').then(m => m.PublicCoursesComponent) },
  { path: 'inscripcion', title: 'Inscripción | CEC UPSE', loadComponent: () => import('./pages/enrollment.component').then(m => m.EnrollmentComponent) },
  { path: 'pago-seguro', title: 'Pago seguro | CEC UPSE', loadComponent: () => import('./pages/payment.component').then(m => m.PaymentComponent) },
  { path: 'login', title: 'Iniciar sesión | CEC UPSE', loadComponent: () => import('./pages/login.component').then(m => m.LoginComponent) },
  { path: 'cambiar-contrasena-obligatoria', canActivate: [authGuard], loadComponent: () => import('./pages/password-change.component').then(m => m.PasswordChangeComponent) },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    children: [
      { path: 'cursos', loadComponent: () => import('./pages/courses.component').then(m => m.CoursesComponent) },
      { path: 'especialistas', loadComponent: () => import('./pages/specialists.component').then(m => m.SpecialistsComponent) },
      { path: 'coordinadores', loadComponent: () => import('./pages/coordinators.component').then(m => m.CoordinatorsComponent) },
      { path: 'consulta-especialistas', loadComponent: () => import('./pages/specialist-query.component').then(m => m.SpecialistQueryComponent) },
      { path: 'consulta-coordinadores', loadComponent: () => import('./pages/coordinator-query.component').then(m => m.CoordinatorQueryComponent) },
      { path: 'planificaciones', loadComponent: () => import('./pages/planning-page.component').then(m => m.PlanningComponent) },
      { path: 'inscripciones', loadComponent: () => import('./pages/registrations.component').then(m => m.RegistrationsComponent) },
      { path: 'panel-inscripciones', loadComponent: () => import('./pages/registration-review.component').then(m => m.RegistrationReviewComponent) },
      { path: 'informe-economico', loadComponent: () => import('./pages/report-page.component').then(m => m.ReportComponent) },
      { path: 'usuarios', canActivate: [adminGuard], loadComponent: () => import('./pages/users-page.component').then(m => m.UsersComponent) },
      { path: 'cambiar-contrasena', loadComponent: () => import('./pages/password-change.component').then(m => m.PasswordChangeComponent) },
    ],
  },
  { path: '**', redirectTo: 'cursos-disponibles' },
];
