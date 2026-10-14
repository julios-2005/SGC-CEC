# Frontend Angular del SGC-CEC — v6

Consulta `../README.md` para iniciar PostgreSQL y el backend con Java 17 en
Visual Studio Code. Esta carpeta contiene solo la interfaz Angular.

Con Node 24.15.0 o posterior de la rama 24, desde esta carpeta:

```powershell
npm ci
npm start
```

Abre `http://localhost:4200` y deja el comando ejecutándose. No es necesario
instalar Angular CLI globalmente. `public/config.js` define la URL de la API.

Para comprobar y compilar:

```powershell
npm test
npm run build
```

`Application bundle generation complete` es un mensaje de éxito, no un error.
La compilación de producción queda en `dist/frontend-angular/browser/`.
Las pruebas usan Vitest y jsdom; no hay una suite `ng e2e` configurada.

Los componentes conservan los CSS extraídos de las páginas originales. Se
declara explícitamente la detección de cambios `Eager` con Zone.js para que
los resultados HTTP actualicen la pantalla sin necesidad de hacer clic.
No cambies estos componentes a OnPush sin adaptar primero sus campos a
signals/async pipe o notificar explícitamente cada cambio asíncrono.

En producción, configura `config.js` para apuntar a tu WebService HTTPS, y
`FRONTEND_ORIGINS` en el backend para permitir el origen exacto de Angular.
No pongas credenciales de base de datos, correo ni el secreto JWT en Angular.
