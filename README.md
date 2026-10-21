# SGC-CEC

Sistema de Gestión de Cursos del Centro de Educación Continua (CEC) - UPSE.

App sobre la gestión de inscripciones y capacitaciones del Centro de Educación Continua - UPSE.

## Tecnologías

- **Backend:** Java 17 / Spring Boot 3.2.5 (Maven)
- **Frontend:** Angular 22.1.x
- **Base de datos:** PostgreSQL

## Estructura del repositorio

```
SGC-CEC/
├── backend-springboot/   # API REST en Spring Boot
├── frontend-angular/     # Aplicación Angular
├── deployment/           # Configuración de despliegue (Nginx del frontend y del API, systemd, variables)
├── Documentos-tecnicos/  # Documentación técnica
├── INTEGRACION_TIC.md    # Integración con la API institucional de TIC
└── uploads/              # Archivos subidos por los usuarios (datos personales; nunca se versiona)
```

Las migraciones de base de datos están en `backend-springboot/src/main/resources/db/migration` (Flyway).

## Requisitos previos

- Java 17
- Maven
- Node.js 22.22.3 o superior (ver `frontend-angular/.nvmrc`)
- PostgreSQL

## Configuración del backend

1. Ve a `backend-springboot/`.
2. Copia `.env.example` a `.env.local` y completa los valores reales:
   - Credenciales de la base de datos PostgreSQL (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`)
   - Credenciales de correo (Gmail App Password)
   - `JWT_SECRET` (mínimo 32 caracteres, genera uno propio en producción)
   - `FRONTEND_ORIGINS` con la URL del frontend
   - `TICS_API_TOKEN` con el token recibido de TIC, sin escribir `Bearer`
   El backend carga `.env.local` al arrancar desde `backend-springboot/`. Usa líneas `CLAVE=valor`, sin `export` ni comillas envolventes.
3. **Nunca subas `.env.local` ni `application-local.properties` al repositorio** (ya están excluidos en `.gitignore`).
4. Ejecuta el backend:
   ```bash
   mvn spring-boot:run
   ```
5. Crea el primer administrador: la aplicación no siembra usuarios. Genera una contraseña temporal y su hash con
   `python3 scripts/generar_usuario_admin.py` (requiere `pip install bcrypt`) y úsalo en
   `src/main/resources/plantilla_usuario_admin.sql.example`. El usuario deberá cambiar la contraseña en su primer ingreso.

### Pruebas

```bash
mvn verify                                   # backend
cd ../frontend-angular && npm ci && npx ng test --watch=false   # frontend
```

### Producción detrás de un proxy

Arquitectura de dominio único (`https://sgccec.upse.edu.ec`): Nginx sirve Angular y reenvía `/api` al backend
(ver `deployment/nginx-frontend.conf`). Variables de entorno relevantes (ver `deployment/backend.env.example`):
`FORWARD_HEADERS_STRATEGY=native`, `TRUSTED_PROXIES` (IP del Nginx) y `SERVER_ADDRESS` (IP privada del backend). Sin ellas, todas las peticiones
parecen venir de la IP del proxy y los límites de intentos de login y de consultas a TIC se comparten entre todos los usuarios.
`SHOW_SQL` debe quedar en `false`.

### Sesiones

El JWT lleva un claim `tv` (versión de token del usuario). Al cambiar o restablecer la contraseña la versión sube y las
sesiones anteriores dejan de ser válidas; el cambio devuelve un token nuevo para la sesión actual.

## Configuración del frontend

1. Ve a `frontend-angular/`.
2. Instala las dependencias:
   ```bash
   npm ci
   ```
3. Levanta el servidor de desarrollo:
   ```bash
   ng serve
   ```
4. Abre `http://localhost:4200`.

## Despliegue

Los archivos de referencia para producción (Nginx, servicio systemd, configuración del frontend) están en la carpeta `deployment/`.

## Integración institucional TIC

La inscripción verifica la categoría seleccionada mediante `POST /api/validacion-institucional`.
Spring Boot consume el servicio de TIC con `RestClient` y un token configurado únicamente en el backend.
La categoría se vuelve a comprobar al guardar la inscripción.

Consulta [INTEGRACION_TIC.md](INTEGRACION_TIC.md) para la configuración, las reglas de cada categoría, las pruebas en Postman y los valores del catálogo que debe confirmar TIC.

## Licencia

Proyecto privado — uso interno del Centro de Educación Continua (CEC) - UPSE.
