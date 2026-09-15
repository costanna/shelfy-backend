# 📚 Shelfy — Backend

> API REST para Shelfy, una biblioteca personal: libros por estado de lectura, categorías propias y reseñas privadas, con autenticación JWT y aislamiento estricto por usuario.

[![API en vivo](https://img.shields.io/badge/API-en%20vivo-brightgreen)](https://shelfy-backend-prw2.onrender.com/actuator/health)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Neon-4169E1?logo=postgresql&logoColor=white)
![JWT](https://img.shields.io/badge/Auth-JWT-000000?logo=jsonwebtokens&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-blue)

Este repositorio es el **backend**. El frontend (Angular) que lo consume vive en
[**shelfy-frontend**](https://github.com/costanna/shelfy-frontend) —
**[🔗 pruébalo en vivo](https://shelfy-frontend-six.vercel.app)** (cuenta de prueba ya cargada con
datos: `demo@shelfy.app` / `shelfy123`).

> Desplegado en el plan gratuito de Render: si lleva un rato dormido, la primera petición puede
> tardar hasta un minuto en responder. Es normal, no un error.

---

## 📖 Qué expone

Registro con verificación por email, login, recuperación de contraseña, CRUD de libros con
filtros y paginación, categorías propias por usuario, reseñas anidadas en cada libro y
estadísticas de lectura (libros terminados por mes, días que ha costado cada uno) — todo con la
garantía de que un usuario nunca puede ver ni tocar los datos de otro.

## ✨ Puntos a destacar

- **Autenticación JWT** de principio a fin (Spring Security), con contraseñas con hash y expiración de token configurable.
- **Aislamiento por usuario a nivel de datos**, no solo de UI: pedir un libro, categoría o reseña ajena devuelve `404`, no `403`, para no revelar siquiera que existe. Se aplica de forma consistente en el `Service`, no confiando en el filtrado del cliente.
- **Filtros y paginación reales** en `GET /api/books` (estado, categoría, texto libre, orden), con `Specification` de Spring Data JPA en vez de *queries* ad hoc por cada combinación de filtros.
- **Manejo de errores centralizado**: un único `@ControllerAdvice` traduce validaciones, duplicados y recursos no encontrados a un formato de error consistente en toda la API.
- **Validación de negocio propia con Bean Validation**: `@HalfStep` (reseñas, solo admite medias estrellas), `@ValidDateRange` (libros, `finishedAt` no puede ser anterior a `startedAt`) y `@NoProfanity` (alias de usuario) son anotaciones a medida con su propio `ConstraintValidator`, igual que `@Min`/`@Max` para cualquier otra regla del dominio — `@ValidDateRange` incluso redirige el error a un campo concreto (`finishedAt`) desde una validación a nivel de clase.
- **Alias de usuario único sin bloquearse a sí mismo**: la comprobación de unicidad vive en el `Service`, no en la anotación de validación — así un usuario puede volver a guardar el alias que ya tenía sin que se rechace como "ya en uso" por chocar contra su propia fila.
- **Listo para producción sin cambiar código**: toda la configuración (BD, JWT, CORS) sale de variables de entorno, con valores por defecto sensatos para desarrollo local.
- **Estadísticas calculadas al vuelo**: `/api/stats` agrupa los libros del usuario en memoria con la Stream API (por mes de `finishedAt`, por estado, etc.) en vez de mantener contadores desnormalizados — sencillo y suficientemente rápido para el tamaño real de una biblioteca personal.
- **Verificación de email sin bloquear el arranque si el correo falla**: `management.health.mail.enabled=false` — por defecto, Spring Boot Actuator añade un chequeo de salud que abre una conexión SMTP real en cada `/actuator/health` en cuanto detecta `spring-boot-starter-mail` en el classpath; sin desactivarlo, un problema puntual de Gmail (o no tener credenciales en local) tumbaba el health check de *todo* el servicio, no solo el envío de correos.
- **Cuentas existentes no se rompen al añadir la verificación**: `email_verified` se añade con `@ColumnDefault("true")`, así que Hibernate migra las cuentas que ya existían en la base de datos como verificadas; solo las cuentas nuevas nacen sin verificar.

## 🛠️ Cómo está hecho

| | |
|---|---|
| **Stack** | Spring Boot 3.5 · Java 21 · Spring Data JPA · Spring Security · Bean Validation · Lombok |
| **Base de datos** | PostgreSQL (Neon en producción; H2 en memoria o Docker en local) |
| **Despliegue** | Render (Web Service, Docker) vía Blueprint ([`render.yaml`](./render.yaml)) |

```text
com.shelfy
├── auth/          registro y login (controller, service, dto)
├── user/          perfil y preferencias de tema/idioma
├── book/          entidad, filtros, CRUD
├── category/      categorías propias del usuario
├── review/        reseñas anidadas en libros
├── security/      JwtService, filtro JWT, UserPrincipal
├── config/        seguridad, CORS y datos de ejemplo
└── common/        errores de API y respuesta paginada
```

Un paquete por *feature* (no por capa técnica), con las clases de cada uno separadas por
responsabilidad: `Controller` → `Service` → `Repository`, DTOs de entrada/salida propios y un
`Mapper` entre entidad y DTO.

## 🚀 Arrancar en local

Sin instalar PostgreSQL, con datos de ejemplo:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Levanta en `http://localhost:8080` con una cuenta lista para probar (`demo@shelfy.app` /
`shelfy123`) sobre una base H2 en memoria — se pierde al parar el servidor.

Con PostgreSQL de verdad, vía Docker:

```bash
docker compose up -d
mvn spring-boot:run
```

<details>
<summary><strong>Más detalles: variables de entorno, endpoints, formato de errores, despliegue</strong></summary>

### Variables de entorno

| Variable | Por defecto | Para qué sirve |
|---|---|---|
| `DB_URL` | *(compuesta, ver abajo)* | URL JDBC completa de PostgreSQL. Si se define, tiene prioridad sobre `DB_HOST`/`DB_PORT`/`DB_NAME` |
| `DB_HOST` | `localhost` | Host de PostgreSQL (alternativa a `DB_URL`, usada por `render.yaml`) |
| `DB_PORT` | `5432` | Puerto de PostgreSQL |
| `DB_NAME` | `shelfy` | Nombre de la base de datos |
| `DB_USER` | `shelfy` | Usuario de base de datos |
| `DB_PASSWORD` | `shelfy` | Contraseña de base de datos |
| `DB_SSLMODE` | `disable` | Modo TLS de la conexión (`require` en Neon y proveedores similares) |
| `JWT_SECRET` | *(valor de desarrollo)* | Secreto de firma. **Obligatorio en producción**, mínimo 32 caracteres |
| `JWT_EXPIRATION_MS` | `86400000` (24 h) | Caducidad del token |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Orígenes permitidos, separados por comas |
| `FRONTEND_URL` | `http://localhost:4200` | Base de los enlaces de verificación/recuperación en los emails |
| `MAIL_ENABLED` | `false` | Si es `false`, no se envía ningún email de verdad: el enlace se deja en el log (así se puede probar el flujo completo en local sin credenciales) |
| `MAIL_HOST` | `smtp.gmail.com` | Servidor SMTP |
| `MAIL_PORT` | `587` | Puerto SMTP |
| `MAIL_USERNAME` | — | Cuenta de Gmail que envía los correos |
| `MAIL_PASSWORD` | — | [Contraseña de aplicación](https://myaccount.google.com/apppasswords) de esa cuenta (no la contraseña normal; requiere verificación en dos pasos activada) |
| `MAIL_FROM` | el valor de `MAIL_USERNAME` | Remitente de los emails |
| `REQUIRE_EMAIL_VERIFICATION` | `true` | Interruptor de emergencia: en `false`, las cuentas nacen ya verificadas y el login no depende del email (`forgot-password` sigue funcionando igual, no depende de esta variable) |
| `DDL_AUTO` | `update` | Estrategia de esquema de Hibernate |
| `PORT` | `8080` | Puerto HTTP (Render lo inyecta automáticamente) |

### Autenticación

Todos los endpoints excepto `/api/auth/**` requieren la cabecera:

```text
Authorization: Bearer <token>
```

El token se obtiene en `register` o `login` y caduca a las 24 h.

### Endpoints

**Auth**

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| `POST` | `/api/auth/register` | `{ email, password, name }` | `201` + `{ message }` |
| `POST` | `/api/auth/login` | `{ email, password }` | `200` + `{ token, tokenType, expiresIn, user }`, o `403` si el email no está verificado |
| `GET` | `/api/auth/verify-email?token=` | — | `200` + `{ token, tokenType, expiresIn, user }` (verifica y deja logueado de una vez) |
| `POST` | `/api/auth/resend-verification` | `{ email }` | `200` + `{ message }`, siempre el mismo mensaje exista o no la cuenta |
| `POST` | `/api/auth/forgot-password` | `{ email }` | `200` + `{ message }`, siempre el mismo mensaje exista o no la cuenta |
| `POST` | `/api/auth/reset-password` | `{ token, newPassword }` | `200` + `{ message }` |

`password`: entre 8 y 72 caracteres. El registro ya no deja logueado de inmediato: hay que
verificar el email primero (enlace válido 24 h). El token de `forgot-password` caduca en 1 h y
solo sirve una vez.

**Usuario**

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| `GET` | `/api/users/me` | — | `{ id, email, name, alias, themePreference, languagePreference }` |
| `PATCH` | `/api/users/me/preferences` | `{ themePreference?, languagePreference? }` | Usuario actualizado |
| `PATCH` | `/api/users/me/alias` | `{ alias }` | Usuario actualizado, o `409` si el alias ya lo tiene otra cuenta |

- `themePreference`: `LIGHT` · `DARK` · `SYSTEM`
- `languagePreference`: `en` · `ca` · `es`
- `alias`: 3-24 caracteres, solo letras/números/`_`, único entre cuentas (sin distinguir
  mayúsculas) y sin palabras malsonantes (ES/CA/EN). Opcional — `null` hasta que el usuario elige
  uno.

**Libros**

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| `GET` | `/api/books` | — | Página de libros |
| `GET` | `/api/books/{id}` | — | Libro |
| `POST` | `/api/books` | `BookRequest` | `201` + libro |
| `PUT` | `/api/books/{id}` | `BookRequest` | Libro actualizado |
| `DELETE` | `/api/books/{id}` | — | `204` |

Filtros de `GET /api/books` (opcionales y combinables): `status`, `categoryId`, `q` (texto libre
en título/autor), `page`/`size` (paginación, 12 por defecto), `sort` (por defecto
`createdAt,desc`).

```json
// BookRequest — solo title y status son obligatorios
{
  "title": "Dune",
  "author": "Frank Herbert",
  "coverUrl": "https://...",
  "isbn": "9788497596909",
  "synopsis": "...",
  "pageCount": 688,
  "status": "WANT_TO_READ",
  "startedAt": "2026-01-01",
  "finishedAt": "2026-01-10",
  "categoryIds": [1, 3]
}
```

`status`: `WANT_TO_READ` · `READING` · `READ` · `WANT_TO_BUY`. `startedAt`/`finishedAt`: fechas
`ISO-8601` (`AAAA-MM-DD`), opcionales; si se envían ambas, `finishedAt` no puede ser anterior a
`startedAt` (`400` si lo es).

**Estadísticas de lectura**

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| `GET` | `/api/stats` | — | `{ totalBooksRead, totalBooks, readingDurations, booksByMonth }` |

`readingDurations`: un elemento por libro con `startedAt` y `finishedAt` rellenos —
`{ bookId, title, startedAt, finishedAt, daysReading }`, orden de más reciente a más antiguo.
`daysReading` cuenta el día de inicio y el de fin (empezar y acabar el mismo día cuenta como 1).

`booksByMonth`: cuántos libros se terminaron cada mes — `{ year, month, count }`, orden de más
reciente a más antiguo. Solo cuenta libros `READ` con `finishedAt`; uno sin esa fecha no se puede
atribuir a ningún mes.

**Categorías**

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| `GET` | `/api/categories` | — | Lista ordenada por nombre |
| `POST` | `/api/categories` | `{ name }` | `201` + categoría |
| `PUT` | `/api/categories/{id}` | `{ name }` | Categoría actualizada |
| `DELETE` | `/api/categories/{id}` | — | `204` |

Nombres duplicados dentro del mismo usuario devuelven `409`.

**Reseñas** (anidadas en libros)

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| `GET` | `/api/books/{bookId}/reviews` | — | Lista, más recientes primero |
| `POST` | `/api/books/{bookId}/reviews` | `{ rating, text }` | `201` + reseña |
| `PUT` | `/api/books/{bookId}/reviews/{id}` | `{ rating, text }` | Reseña actualizada |
| `DELETE` | `/api/books/{bookId}/reviews/{id}` | — | `204` |

`rating`: número de 0.5 a 5, en pasos de 0.5 (p. ej. `3.5`).

### Formato de errores

```json
{
  "timestamp": "2026-09-15T10:09:57.588Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Datos de entrada no válidos",
  "fieldErrors": { "password": "La contraseña debe tener entre 8 y 72 caracteres" }
}
```

`fieldErrors` solo aparece en errores de validación.

| Código | Cuándo |
|---|---|
| `400` | Validación fallida |
| `401` | Sin token, token caducado o credenciales incorrectas |
| `404` | El recurso no existe o pertenece a otro usuario |
| `409` | Email o nombre de categoría ya en uso |

### Desplegar en Render (backend) + Neon (base de datos)

El plan Free de Render solo permite **una** base de datos PostgreSQL gestionada por cuenta, así
que la base de datos de Shelfy vive en [Neon](https://neon.tech) (gratis, sin ese límite) y el
backend en Render como Web Service.

**Opción rápida — Blueprint**: el repo incluye [`render.yaml`](./render.yaml). En el dashboard:
**New → Blueprint** → conecta `shelfy-backend`. Te pedirá `DB_HOST`, `DB_NAME`, `DB_USER`,
`DB_PASSWORD` (los datos de conexión de Neon) y `MAIL_USERNAME`/`MAIL_PASSWORD` (ver abajo);
`DB_PORT`, `DB_SSLMODE=require`, `JWT_SECRET`, `MAIL_ENABLED` y `FRONTEND_URL` ya vienen resueltos,
y `CORS_ALLOWED_ORIGINS` apunta al frontend en Vercel.

**Opción manual**: **New → Web Service** → conecta este repositorio → runtime **Docker**. En
*Environment*, define las mismas variables a mano. Health check path: `/actuator/health`.

> Usa el endpoint **directo** de Neon, no el `-pooler`: con PgBouncer en modo *transaction* y
> Hibernate pueden darse errores intermitentes de *prepared statement*.

El plan Free de Render duerme el servicio tras ~15 min sin uso (la primera petición después puede
tardar cerca de un minuto); Neon hiberna la base de datos de forma parecida y se despierta sola en
la siguiente conexión.

**Enviar emails de verdad (Gmail):**

1. Activa la verificación en dos pasos en la cuenta de Gmail que vaya a enviar los correos.
2. Genera una [contraseña de aplicación](https://myaccount.google.com/apppasswords) (16
   caracteres, distinta de tu contraseña normal).
3. En Render (*Environment* del servicio), define `MAIL_USERNAME` con esa cuenta de Gmail y
   `MAIL_PASSWORD` con la contraseña de aplicación (**nunca la contraseña normal de la cuenta**),
   y cambia `MAIL_ENABLED` a `true` — por defecto viene en `false` (registro y recuperación
   funcionan igual, pero sin mandar el correo de verdad) para no dejar el registro roto en
   producción hasta que las credenciales estén puestas.
4. En local, deja `MAIL_ENABLED` sin definir (por defecto `false`): los enlaces de verificación y
   de recuperación se escriben en el log del servidor en vez de enviarse, para poder probar el
   flujo entero sin credenciales reales.

</details>

## 📄 Licencia

[MIT](./LICENSE)
