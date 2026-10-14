# Integración con la API institucional de TIC

Actualización: 9 de octubre de 2026.

## Funcionamiento

El formulario Angular llama a una API propia del CEC: `POST /api/validacion-institucional`.
El backend Spring Boot consulta por HTTPS el servicio `buscarInformacionCEC/{identificacion}` de TIC mediante `RestClient` y añade la cabecera `Authorization: Bearer …`.
Angular recibe únicamente el resultado de la categoría solicitada; no recibe el token, el registro institucional completo, el correo institucional ni detalles de discapacidad.

Una persona puede cumplir varias condiciones. Se comprueba la categoría que el participante elige, conservando el modelo actual de una categoría por inscripción y los porcentajes configurados en el CEC.
El backend vuelve a consultar TIC al registrar la inscripción, antes de consumir cupos o guardar archivos. No acepta una marca `verificado` enviada por el navegador como prueba.

`RestClient` es la alternativa a `RestTemplate` incorporada en Spring Framework 6.1 y disponible con el Spring Boot 3.2.5 de este proyecto. Referencia oficial: https://spring.io/blog/2023/07/13/new-in-spring-6-1-restclient.

## Configuración local

1. Descomprime el proyecto. Abre una terminal dentro de `backend-springboot`.
2. Si no tienes configuración local, copia `.env.example` a `.env.local`. Conserva tus credenciales reales de PostgreSQL, correo y JWT.
3. Añade o completa en `.env.local`:

   ```properties
   TICS_API_TOKEN=PEGA_AQUI_EL_TOKEN_RECIBIDO
   TICS_API_BASE_URL=https://sga.upse.edu.ec/restsiia/api/public
   TICS_CONNECT_TIMEOUT=3s
   TICS_READ_TIMEOUT=8s
   TICS_CONSULTAS_POR_MINUTO=30
   ```

   Pega solo el token: sin el prefijo `Bearer`, sin comillas y sin espacios adicionales. El valor real no está incluido en este ZIP.
4. Ejecuta `mvn spring-boot:run` desde `backend-springboot`. El backend carga `.env.local` automáticamente. En un IDE configura ese mismo directorio de trabajo o sus variables de entorno. Reinicia después de cambiar la configuración.
5. En otra terminal, entra a `frontend-angular`, ejecuta `npm ci` y después `npm start`. Abre `http://localhost:4200`.

Alternativa: copia `configuracion-tic.properties.example` a `configuracion-tic.properties`, dentro de `backend-springboot`, y completa `app.tics.token`. Este archivo también permite ajustar los catálogos de cargos. Si utilizas ambos archivos, evita duplicar opciones: los valores `app.tics.*` del archivo específico tienen prioridad sobre los valores importados desde `.env.local`.

Ambos archivos privados están excluidos de Git. No agregues el token en Angular, en `application.properties`, en capturas ni en documentación.

En producción se puede usar `deployment/backend.env.example` como referencia para las variables del servicio. Conserva el mecanismo de despliegue existente y configura el secreto en el servidor. No se requiere una migración de base de datos para esta integración.

## Uso del formulario

1. Selecciona la categoría, el curso y escribe el documento de identidad.
2. Para estudiante, docente, administrativo o maestrando, pulsa **Verificar categoría**. Graduado y Persona con discapacidad no tienen ese botón: se revisan con el documento de respaldo (carnet de discapacidad o certificado de graduado) y el formulario muestra su descuento de inmediato.
3. Si se confirma, el formulario muestra el descuento y los datos de pago del curso. Completa los campos y adjunta los documentos que ya exige el sistema.
4. Envía la inscripción. El backend verifica nuevamente la categoría y continúa con el proceso habitual si la confirma.

Cambiar el documento o la categoría elimina la comprobación anterior. Una consulta negativa o un fallo de TIC impide enviar con esa categoría; el formulario permite corregir datos y reintentar. No se cambia a la persona automáticamente a Externo.
Externo mantiene su proceso habitual. Graduado mantiene la revisión documental porque la respuesta disponible no tiene un indicador de graduación.

### Un solo descuento por inscripción

Los descuentos no se acumulan: se aplica únicamente el de la categoría elegida. Una persona puede cumplir varias condiciones (por ejemplo, ser Estudiante UPSE y tener discapacidad); debe elegir **una** categoría, preferiblemente la de mayor descuento.

Por eso Persona con discapacidad **no se confirma con TIC**: quien consta en la base de la universidad elige la categoría institucional que más le convenga (que sí se verifica), y quien no consta acredita su discapacidad con el carnet, que revisa el CEC manualmente.

El formulario muestra bajo «Tipo de Usuario» un aviso de descuento único. Para Graduado y Persona con discapacidad añade una sugerencia con las categorías que tengan un descuento mayor, tomando los porcentajes de los descuentos activos del CEC (no están escritos en el código; si el CEC los cambia, el aviso se actualiza solo).

## Reglas y contrato observado

TIC devolvió HTTP 200 con un arreglo JSON y una identificación textual por registro. El cliente exige ese formato y comprueba que todos los registros correspondan al documento solicitado. Un arreglo vacío significa que la consulta no permite confirmar la categoría. Un error HTTP o un formato inesperado se informa como fallo del servicio.

| Categoría CEC | Condición de confirmación |
| --- | --- |
| `ESTUDIANTE_UPSE` | `estudianteGrado` y `matriculaGradoActiva` deben ser booleanos `true`. |
| `MAESTRANDO` | `estudiantePostgrado` y `matriculaPostgradoActiva` deben ser booleanos `true`. |
| `DOCENTE_UPSE` | `tipoUsuario` coincide con el catálogo de tipos docentes, o es `Personal upse` y `cargo` coincide con el catálogo docente. |
| `ADMINISTRATIVO_UPSE` | `tipoUsuario` coincide con el catálogo administrativo, o es `Personal upse` y `cargo` coincide con el catálogo administrativo. |
| `PERSONA_DISCAPACIDAD` | Revisión del carnet de respaldo; no se consulta a TIC (ver «Un solo descuento por inscripción»). |
| `GRADUADO` | Revisión del documento de respaldo; no se deduce de una matrícula inactiva. |
| `EXTERNO` | Conserva la inscripción sin validación institucional. |

La matrícula activa es el criterio implementado para estudiante y maestrando. La correspondencia entre cualquier posgrado activo y la categoría existente `MAESTRANDO` debe revisarse con el CEC si también admite otros tipos de posgrado. La API no determina por sí sola una política de descuentos.

### Valores que deben confirmarse con TIC

Se comprobó la conexión real y un registro administrativo con `tipoUsuario=Personal upse` y `cargo=ANALISTA 2 DE DESARROLLO DE SISTEMAS`. Ese ejemplo incluía banderas académicas booleanas. No se contó con muestras reales positivas de estudiante, docente o posgrado.

Los tipos docentes/administrativos y los cargos docentes del archivo de ejemplo son equivalencias iniciales propuestas. Solicita a TIC el catálogo completo y ajústalo en `configuracion-tic.properties` antes de usar esta clasificación en producción:

```properties
app.tics.tipos-docente=DOCENTE,DOCENTE UPSE,PERSONAL ACADEMICO
app.tics.tipos-administrativo=ADMINISTRATIVO,ADMINISTRATIVO UPSE,PERSONAL ADMINISTRATIVO
app.tics.cargos-docente=DOCENTE,PROFESOR,PROFESORA
app.tics.cargos-administrativo=ANALISTA 2 DE DESARROLLO DE SISTEMAS
```

Las listas se separan con comas y reemplazan sus valores predeterminados. Las coincidencias son exactas tras normalizar mayúsculas, acentos, espacios, guiones y guiones bajos. No se clasifica automáticamente como administrativo a todo el personal que no sea docente.


## Probar tu API en Postman

Con el backend iniciado, crea una petición:

- Método: `POST`.
- URL local: `http://localhost:8080/api/validacion-institucional`.
- Authorization: **No Auth**. Este endpoint atiende el formulario público; el token institucional permanece en la configuración del backend.
- Body → raw → JSON:

```json
{
  "identificacion": "1234567890",
  "tipoUsuario": "ESTUDIANTE_UPSE"
}
```

El número es ficticio. Reemplázalo con un documento autorizado para tus pruebas y escoge una categoría de la tabla.

Ejemplo ilustrativo de respuesta positiva:

```json
{
  "tipoUsuario": "ESTUDIANTE_UPSE",
  "requiereConsulta": true,
  "verificado": true,
  "estado": "CONFIRMADO",
  "mensaje": "La categoría seleccionada fue confirmada por la información institucional."
}
```

HTTP 200 también puede contener `verificado=false`: revisa ese campo y `estado`, no solo el código HTTP. Los estados posibles son `CONFIRMADO`, `NO_CONFIRMADO`, `SIN_REGISTRO`, `NO_REQUERIDA` y `REVISION_DOCUMENTAL`.

Para probar directamente la API externa en Postman, usa `GET https://sga.upse.edu.ec/restsiia/api/public/buscarInformacionCEC/{identificacion}` y Authorization → **Bearer Token**, con el token que te entregaron. Ese método sirve para verificar el contrato con TIC; Angular consume siempre la API propia.

## Fallos y límites

| Resultado | Significado / acción |
| --- | --- |
| `400` | Solicitud inválida; al inscribir también puede indicar que la categoría no fue confirmada. Revisar el mensaje. |
| `429`, `TIC_LIMITE_CONSULTAS` | Límite local de consultas por IP alcanzado. Esperar el tiempo indicado en `Retry-After`. |
| `503`, `TIC_NO_CONFIGURADA` | Falta el token del backend. Completarlo y reiniciar. |
| `503`, `TIC_ACCESO_RECHAZADO` | TIC respondió 401/403. Revisar el token y su habilitación con TIC. |
| `503`, `TIC_NO_DISPONIBLE` | Fallo de conexión, tiempo de espera o respuesta temporal de TIC. Reintentar más tarde. |
| `502`, `TIC_RESPUESTA_INVALIDA` | Formato/campos incompatibles, documento distinto o estado HTTP inesperado, incluido 404. Revisar el contrato con TIC. |

Se usan tiempos de conexión/lectura configurables de 3 y 8 segundos, un máximo de respuesta de 256 KiB y sin seguimiento de redirecciones. No se guardan las respuestas de TIC ni se registran el token o su cuerpo. La API propia devuelve `Cache-Control: no-store`.

El límite predeterminado es de 30 consultas por minuto por IP y por instancia del backend. La vista previa y el guardado consumen una consulta cada uno. Detrás de Nginx, la IP que ve Java puede ser la del proxy: adapta el límite al volumen esperado y configura el tratamiento de IPs únicamente para proxies de confianza. El código no confía por sí solo en un `X-Forwarded-For` recibido del cliente.

## Verificación realizada

- Backend: `mvn test`, **343 pruebas aprobadas**, incluidas respuestas positivas/negativas, errores de TIC, identidad incorrecta, límite de consultas, validación de entrada, seguridad del endpoint y revalidación al guardar.
- Frontend: `npm test -- --watch=false`, **71 pruebas aprobadas**. Se comprueban descuentos, bloqueo del envío, cambios de identidad/categoría, respuestas tardías y errores del servicio.
- Frontend: `npm run build`, compilación de producción correcta.
- Consulta real: el nuevo cliente Java `RestClient` confirmó el caso administrativo proporcionado. No se incorporaron a las pruebas ni al ZIP su identificación, respuesta personal ni token reales.
- Los escenarios de otras categorías y errores usan datos ficticios y respuestas simuladas. No equivalen a validar todos los valores reales del catálogo de TIC.

Las pruebas de persistencia usan H2. La conexión a tu PostgreSQL, el envío real de correo y el despliegue en tu servidor deben comprobarse en tu entorno con sus credenciales.
