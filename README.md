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
├── deployment/           # Archivos de configuración para despliegue (Nginx, systemd, etc.)
└── database/             # Scripts / recursos de base de datos
```

## Requisitos previos

- Java 17
- Maven
- Node.js y Angular CLI
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

## Configuración del frontend

1. Ve a `frontend-angular/`.
2. Instala las dependencias:
   ```bash
   npm install
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
