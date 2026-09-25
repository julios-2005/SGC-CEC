export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface SessionUser {
  id: number;
  nombreUsuario: string;
  nombre: string;
  foto?: string | null;
  rol: 'ADMIN_GENERAL' | 'COORDINADOR' | 'COBROS';
}

export interface LoginResponse {
  exito: boolean;
  mensaje: string;
  usuario?: SessionUser;
  debeCambiarContraseña?: boolean;
  token?: string;
  tipoToken?: string;
  expiraEnSegundos?: number;
}

export interface Curso {
  ambito?: 'NACIONAL' | 'INTERNACIONAL';
  idCurso: number;
  nombre: string;
  codigo: string;
  horas: number;
  costo: number;
  cuposTotales: number;
  cuposRestantes: number;
  modalidad: string;
  estado: string;
  foto?: string;
}

export interface Coordinador {
  id: number;
  cedula: string;
  nombres: string;
  apellidos: string;
  telefono?: string;
  correo?: string;
  foto?: string;
  estado: boolean;
}

export interface Especialista extends Coordinador {
  especialidad?: string;
  areaConocimiento?: string;
  paisNacionalidad?: string;
  nombrePaisNacionalidad?: string;
  gentilicioNacionalidad?: string;
}

export interface Planificacion {
  honorariosDocentes?: HonorarioDocente[];
  docentes?: Docente[];
  nombresDocentes?: string;
  id: number;
  curso: {
    idCurso: number;
    nombre: string;
    codigo: string;
    horas: number;
    costo: number;
    cuposRestantes: number;
    foto?: string;
    ambito?: string;
  };
  coordinador: { id: number; nombres: string; apellidos: string; cedula: string; correo?: string };
  especialista: { id: number; nombres: string; apellidos: string; cedula: string; especialidad?: string; paisNacionalidad?: string };
  fechaInicio: string;
  fechaFin: string;
  horario: string;
  modalidad: string;
  fechaRegistro: string;
  costoEspecialista?: number;
  activa?: boolean;
}

export interface Inscripcion {
  id: number;
  tipoUsuario: string;
  nombreCompleto: string;
  planificacion: { id: number; nombreCurso: string; fechaInicio: string; fechaFin: string; horario: string };
  cedula: string;
  telefono: string;
  correoElectronico: string;
  direccion: string;
  sexo: string;
  comprobantePago: string;
  copiaCedula: string;
  documentoAdicional?: string;
  comprobanteMatriculaPdf?: string;
  fechaRegistro: string;
  estado: string;
}

export interface Usuario {
  id: number;
  nombreUsuario: string;
  rol: string;
  estado: boolean;
  debeCambiarContraseña: boolean;
  nombre: string;
  foto?: string;
  coordinadorId?: number;
  coordinadorNombre?: string;
  coordinadorApellido?: string;
  contraseñaTemporal?: string;
}

export interface InformeResumen {
  idPlanificacion: number | null;
  idInforme?: number;
  anio?: number;
  cifrasPendientes?: boolean;
  excluido2026?: boolean;
  observacion?: string;
  nombreCurso: string;
  modalidad: string | null;
  fechaInicio: string | null;
  participantes: number;
  ingresos: number;
  egresos: number;
  utilidad: number;
}

export interface InformeDetalle {
  idPlanificacion: number | null;
  idInforme?: number;
  anio?: number;
  cifrasPendientes?: boolean;
  excluido2026?: boolean;
  observacion?: string;
  nombreCurso: string;
  modalidad: string | null;
  especialista: string;
  coordinador: string;
  fechaInicio: string | null;
  fechaFin: string | null;
  participantes: number;
  ingresos: Ingreso[];
  ingresosTotal: number;
  egresos: Egreso[];
  egresosTotal: number;
  utilidad: number;
}

export interface Docente { id: number; nombres: string; apellidos: string; paisNacionalidad?: string }
export interface HonorarioDocente { idEspecialista: number; nombre: string; paisNacionalidad?: string; honorario: number; costoTransferencia: number; pagoNeto: number }
export interface Ingreso {
  id?: number; tipoUsuario?: string; detalle: string; cantidad: number;
  valorUnitario: number | null; total: number; editable?: boolean; cantidadManual?: boolean;
}
export interface Egreso { id?: number; concepto: string; cantidad: number; valorUnitario: number; total: number; editable?: boolean }
export interface Consolidado {
  anio: number; participantes: number; ingresos: number; egresos: number; utilidad: number;
  gastosPersonal: number; utilidadCec: number; version: number;
}
