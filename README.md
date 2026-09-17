# 📚 Shelfy — Backend

**🇪🇸 Español** (este documento) · **[🇬🇧 English](#english)**

> API REST para Shelfy, una biblioteca personal: libros por estado de lectura con progreso por páginas, sagas y formato, categorías propias, reseñas y notas privadas, estadísticas, relecturas, y una capa social con feed de actividad y notificaciones — con autenticación JWT y aislamiento estricto por usuario.

[![API en vivo](https://img.shields.io/badge/API-en%20vivo-brightgreen)](https://shelfy-backend-prw2.onrender.com/actuator/health)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Neon-4169E1?logo=postgresql&logoColor=white)
![JWT](https://img.shields.io/badge/Auth-JWT-000000?logo=jsonwebtokens&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-blue)

Este repositorio es el **backend**. El frontend (Angular) que lo consume vive en
[**shelfy-frontend**](https://github.com/costanna/shelfy-frontend) —
**[🔗 pruébalo en vivo](https://shelfy-reads.vercel.app)** (cuenta de prueba ya cargada con
datos: `demo@shelfy.app` / `shelfy123`).

> Desplegado en el plan gratuito de Render: si lleva un rato dormido, la primera petición puede
> tardar hasta un minuto en responder. Es normal, no un error.

---

## 📖 Qué expone

Registro con verificación por email, login, recuperación de contraseña, CRUD de libros con
filtros, ordenación y paginación, progreso de lectura por páginas, sagas y formato (físico/ebook/
audiolibro), categorías propias por usuario, reseñas y notas anidadas en cada libro, importar/
exportar la biblioteca en CSV, estadísticas de lectura (libros terminados por mes, días que ha
costado cada uno, cuántos llevas leyendo ahora mismo), la posibilidad de volver a leer un libro
ya terminado sin perder el rastro de la lectura anterior, y una capa social opcional: buscar a
otros usuarios por alias, seguirlos para ver su estantería y sus reseñas, recibir una
notificación cuando alguien te sigue, y un feed con lo último que ha hecho la gente que sigues —
con la garantía de que nadie puede *modificar* los datos de otro usuario bajo ningún concepto, y
de que la estantería, las reseñas y la actividad de alguien solo se pueden *ver* si esa persona
te tiene entre sus seguidores.

## ✨ Puntos a destacar

- **Autenticación JWT** de principio a fin (Spring Security), con contraseñas con hash y expiración de token configurable.
- **Aislamiento por usuario a nivel de datos**, no solo de UI: pedir un libro, categoría o reseña ajena devuelve `404`, no `403`, para no revelar siquiera que existe. Se aplica de forma consistente en el `Service`, no confiando en el filtrado del cliente.
- **Filtros y paginación reales** en `GET /api/books` (estado, categoría, texto libre, orden), con `Specification` de Spring Data JPA en vez de *queries* ad hoc por cada combinación de filtros.
- **Manejo de errores centralizado**: un único `@ControllerAdvice` traduce validaciones, duplicados y recursos no encontrados a un formato de error consistente en toda la API.
- **Validación de negocio propia con Bean Validation**: `@HalfStep` (reseñas, solo admite medias estrellas), `@ValidDateRange` (libros, `finishedAt` no puede ser anterior a `startedAt`) y `@NoProfanity` (alias de usuario) son anotaciones a medida con su propio `ConstraintValidator`, igual que `@Min`/`@Max` para cualquier otra regla del dominio — `@ValidDateRange` incluso redirige el error a un campo concreto (`finishedAt`) desde una validación a nivel de clase.
- **Alias de usuario único sin bloquearse a sí mismo**: la comprobación de unicidad vive en el `Service`, no en la anotación de validación — así un usuario puede volver a guardar el alias que ya tenía sin que se rechace como "ya en uso" por chocar contra su propia fila.
- **Visibilidad social sin duplicar datos**: no existe una "versión pública" separada de `Book`/`Review` en base de datos — `UserProfileService` reutiliza las mismas entidades y solo decide, en el momento de la petición, si rellenar `books` en la respuesta o devolverlo vacío según `own`/`followedByMe`. Además, sin alias un usuario simplemente no aparece en `/api/users/search`: no ser buscable es el valor por defecto, hay que ponerse alias para ser encontrable.
- **Listo para producción sin cambiar código**: toda la configuración (BD, JWT, CORS) sale de variables de entorno, con valores por defecto sensatos para desarrollo local.
- **Estadísticas calculadas al vuelo**: `/api/stats` agrupa los libros del usuario en memoria con la Stream API (por mes de `finishedAt`, por estado, etc.) en vez de mantener contadores desnormalizados — sencillo y suficientemente rápido para el tamaño real de una biblioteca personal. El feed de actividad (`/api/feed`) sigue el mismo criterio: combina en memoria libros y reseñas recientes de la gente que sigues en vez de mantener una tabla de eventos aparte.
- **Relecturas sin perder el historial**: `Book.startedAt`/`finishedAt` siguen siendo la lectura "actual", pero volver a leer un libro (`POST .../reread`, o reabrirlo desde Estadísticas) archiva la lectura terminada en `ReadEvent` antes de reiniciarla — así `/api/stats` puede seguir contando el mes en que lo acabaste la primera vez, y el mes en que lo vuelvas a acabar, en vez de que la segunda lectura pise a la primera.
- **Verificación de email sin bloquear el arranque si el correo falla**: `management.health.mail.enabled=false` — por defecto, Spring Boot Actuator añade un chequeo de salud que abre una conexión SMTP real en cada `/actuator/health` en cuanto detecta `spring-boot-starter-mail` en el classpath; sin desactivarlo, un problema puntual de Gmail (o no tener credenciales en local) tumbaba el health check de *todo* el servicio, no solo el envío de correos.
- **Cuentas existentes no se rompen al añadir la verificación**: `email_verified` se añade con `@ColumnDefault("true")`, así que Hibernate migra las cuentas que ya existían en la base de datos como verificadas; solo las cuentas nuevas nacen sin verificar.
- **Índices explícitos en las columnas de propietario y claves foráneas** (`books.owner_id`, `categories.owner_id`, `reviews.book_id`/`user_id`, `follows.followed_id`, `book_categories.book_id`/`category_id`): PostgreSQL no las indexa solas por defecto, solo la clave primaria y las `UNIQUE` — y toda consulta de "mis libros/categorías/reseñas" filtra precisamente por una de estas columnas.

## 🛠️ Cómo está hecho

| | |
|---|---|
| **Stack** | Spring Boot 3.5 · Java 21 · Spring Data JPA · Spring Security · Bean Validation · Lombok |
| **Base de datos** | PostgreSQL (Neon en producción; H2 en memoria o Docker en local) |
| **Despliegue** | Render (Web Service, Docker) vía Blueprint ([`render.yaml`](./render.yaml)) |

```text
com.shelfy
├── auth/          registro, login, verificación de email, recuperar contraseña
├── user/          perfil, alias, preferencias, búsqueda de usuarios, perfil público
├── follow/        seguir/dejar de seguir, contadores
├── notification/  notificaciones (p. ej. nuevo seguidor)
├── feed/          feed de actividad de la gente que sigues
├── book/          entidad, filtros, CRUD, progreso de lectura, relecturas, CSV
├── category/      categorías propias del usuario
├── review/        reseñas anidadas en libros
├── note/          notas privadas anidadas en libros
├── goal/          objetivo de lectura anual
├── readinglog/    calendario de lectura (días marcados, rachas)
├── stats/         estadísticas de lectura
├── mail/          envío de emails (verificación, recuperación, recordatorios)
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
| `REQUIRE_EMAIL_VERIFICATION` | `false` | Interruptor de emergencia: en `false`, las cuentas nacen ya verificadas y el login no depende del email (`forgot-password` sigue funcionando igual, no depende de esta variable) |
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

Cada cuenta nueva se crea con 8 categorías por defecto (Ficción, No ficción, Fantasía, Ciencia
ficción, Misterio y thriller, Romance, Biografía, Poesía) — `CategoryService.seedDefaults()`, para
no empezar con la sección de Categorías completamente vacía. Son categorías normales: se pueden
renombrar o borrar como cualquier otra, no están protegidas.

**Usuario**

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| `GET` | `/api/users/me` | — | `{ id, email, name, alias, themePreference, languagePreference, avatarUpdatedAt }` |
| `PATCH` | `/api/users/me/preferences` | `{ themePreference?, languagePreference? }` | Usuario actualizado |
| `PATCH` | `/api/users/me/alias` | `{ alias }` | Usuario actualizado, o `409` si el alias ya lo tiene otra cuenta |
| `POST` | `/api/users/me/avatar` | `multipart/form-data`, campo `file` | Usuario actualizado, o `400` si no es una imagen válida (PNG/JPEG/WEBP, máx. 5 MB) |
| `DELETE` | `/api/users/me/avatar` | — | Usuario actualizado (idempotente: no falla si no tenías avatar) |
| `GET` | `/api/users/{id}/avatar` | — | Imagen JPEG (público, sin autenticar), o `404` si ese usuario no tiene avatar |

- `themePreference`: `LIGHT` · `DARK` · `SYSTEM`
- `languagePreference`: `en` · `ca` · `es`
- `alias`: 3-24 caracteres, solo letras/números/`_`, único entre cuentas (sin distinguir
  mayúsculas) y sin palabras malsonantes (ES/CA/EN). Opcional — `null` hasta que el usuario elige
  uno.
- `avatarUpdatedAt`: `null` si no tiene avatar; si no, la fecha en que se subió/cambió — pensada
  para que el frontend la use como parámetro de caché (`?v=...`) en la URL de la imagen, no para
  mostrarla. Cualquier imagen subida se recorta a cuadrado (centrado) y se redimensiona a
  320×320 px en JPEG antes de guardarse, así que el tamaño en base de datos no depende de lo que
  suba cada usuario. `GET /api/users/{id}/avatar` es la única ruta bajo `/api/users` que no exige
  sesión: una etiqueta `<img>` no manda el `Authorization` que sí añade el interceptor HTTP del
  frontend, así que tiene que ser pública — como el resto de la app, no hay nada sensible en la
  imagen de perfil de alguien.

**Libros**

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| `GET` | `/api/books` | — | Página de libros |
| `GET` | `/api/books/{id}` | — | Libro |
| `POST` | `/api/books` | `BookRequest` | `201` + libro |
| `PUT` | `/api/books/{id}` | `BookRequest` | Libro actualizado |
| `DELETE` | `/api/books/{id}` | — | `204` |
| `PATCH` | `/api/books/{id}/reading-dates` | `{ startedAt?, finishedAt? }` | Libro actualizado |
| `PATCH` | `/api/books/{id}/progress` | `{ currentPage }` | Libro actualizado |
| `POST` | `/api/books/{id}/reread` | — | Libro actualizado |
| `GET` | `/api/books/export` | — | CSV de toda la biblioteca |
| `POST` | `/api/books/import` | `multipart/form-data`, campo `file` | `BookImportResult` |

Filtros de `GET /api/books` (opcionales y combinables): `status`, `categoryId`, `q` (texto libre
en título/autor), `page`/`size` (paginación, 12 por defecto), `sort` (por defecto
`createdAt,desc`; acepta cualquier campo propio del libro, p. ej. `title,asc` o
`pageCount,desc`).

```json
// BookRequest — solo title y status son obligatorios
{
  "title": "Dune",
  "author": "Frank Herbert",
  "coverUrl": "https://...",
  "isbn": "9788497596909",
  "synopsis": "...",
  "pageCount": 688,
  "currentPage": 120,
  "series": "Dune",
  "seriesPosition": 1,
  "format": "PHYSICAL",
  "status": "WANT_TO_READ",
  "startedAt": "2026-01-01",
  "finishedAt": "2026-01-10",
  "categoryIds": [1, 3]
}
```

`status`: `WANT_TO_READ` · `READING` · `READ` · `WANT_TO_BUY`. `format` (opcional): `PHYSICAL` ·
`EBOOK` · `AUDIOBOOK`. `startedAt`/`finishedAt`: fechas `ISO-8601` (`AAAA-MM-DD`), opcionales; si
se envían ambas, `finishedAt` no puede ser anterior a `startedAt` (`400` si lo es).

`PATCH /api/books/{id}/reading-dates` existe aparte de `PUT` para poder cambiar (o borrar, mandando
ambas a `null`) solo el rango de lectura desde Estadísticas sin tener que reenviar el resto del
libro — mismo criterio de fechas que `BookRequest`. Como esta ruta no pide el estado (a diferencia
del formulario completo), lo infiere de las fechas: mandar `finishedAt` pone el libro en `READ`, y
mandar solo `startedAt` (sin `finishedAt`) lo pone en `READING` si estaba en `WANT_TO_READ`,
`WANT_TO_BUY` o `READ` — en este último caso, igual que `POST .../reread` (ver abajo), archiva la
lectura que tenía terminada antes de sobrescribirla, para no perderla de "Libros terminados por
mes". Nunca degrada un libro `READ`: borrar `finishedAt` sin mandar `startedAt` lo deja en
`WANT_TO_READ`, no en `READING`. Sin este comportamiento, poner solo la fecha sin tocar el estado
dejaba el libro fuera de "Libros terminados por mes" en Estadísticas aunque sí tuviera
`finishedAt`.

`PATCH /api/books/{id}/progress` — `{ currentPage }` (número, `≥ 0`) actualiza solo la página por
la que vas, sin reenviar el resto del libro. Si el libro estaba en `WANT_TO_READ` o
`WANT_TO_BUY` pasa a `READING`, y si no tenía `startedAt` se le pone la fecha de hoy.
`currentPage` nunca supera `pageCount` (se recorta al máximo si lo intentas).

`POST /api/books/{id}/reread` — solo para libros en `READ`. Archiva la lectura actual (si tenía
`finishedAt`) y pone el libro en `READING` con `startedAt` hoy, `finishedAt` y `currentPage` a
`null`. `400` si el libro no está en `READ`. El historial de lecturas archivadas se puede
consultar en `readHistory` dentro de la respuesta del libro (`{ startedAt, finishedAt }[]`, de
más reciente a más antigua).

`GET /api/books/export` descarga un CSV (`title,author,isbn,status,pageCount,series,
seriesPosition,format,startedAt,finishedAt,categories,synopsis`) con toda tu biblioteca.
`POST /api/books/import` lee ese mismo formato: cada fila necesita al menos `title`, las demás
columnas son opcionales y las filas sin título se omiten (recogido en `BookImportResult` —
`{ imported, skipped, messages }`). No exporta ni importa `currentPage` ni el historial de
relecturas, solo el estado "actual" del libro.

**Estadísticas de lectura**

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| `GET` | `/api/stats` | — | `{ totalBooksRead, totalBooks, currentlyReading, readingDurations, booksByMonth }` |

`totalBooksRead`: libros en `READ` ahora mismo (uno por libro, una relectura no lo cuenta dos
veces). `totalBooks`: tamaño total de la biblioteca. `currentlyReading`: libros en `READING`
ahora mismo.

`readingDurations`: un elemento por cada lectura completada con `startedAt` y `finishedAt`
rellenos — `{ bookId, title, startedAt, finishedAt, daysReading, current }`, orden de más
reciente a más antigua. `daysReading` cuenta el día de inicio y el de fin (empezar y acabar el
mismo día cuenta como 1). `current` distingue la lectura activa del libro (editable vía
`PATCH .../reading-dates`) de una archivada por una relectura (`false`; sus fechas ya no se
pueden tocar desde aquí, solo consultar).

`booksByMonth`: cuántas lecturas se terminaron cada mes — `{ year, month, count }`, orden de más
reciente a más antiguo. Cuenta tanto el libro `READ` con `finishedAt` como cualquier lectura
archivada de una relectura anterior, así que volver a leer un libro no borra el mes en que lo
terminaste la primera vez — y si vuelves a terminarlo, suma un segundo acierto ese mes nuevo. Un
libro sin `finishedAt` no se puede atribuir a ningún mes.

**Calendario de lectura**

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| `GET` | `/api/reading-log?year=&month=` | — | `{ year, month, days }` — solo los días con algo marcado |
| `GET` | `/api/reading-log/streak` | — | `{ currentStreak, longestStreak }` |
| `GET` | `/api/reading-log/summary` | — | Todo el historial agrupado por libro: `[{ bookId, title, dates }]` |
| `POST` | `/api/reading-log` | `{ bookId, date }` | `204`. Idempotente: si ese libro ya estaba marcado ese día, no hace nada |
| `DELETE` | `/api/reading-log?bookId=&date=` | — | `204` (idempotente) |

Independiente de `startedAt`/`finishedAt` del libro (que siguen siendo el rango "oficial" del
detalle): esto es un registro día a día, pensado para el calendario interactivo de Estadísticas —
un mismo día puede tener varios libros marcados (leer más de uno en paralelo), y un libro puede
tener marcados días sueltos sin relación con su rango de lectura declarado.

`days` trae, para cada día con al menos un libro marcado, `{ date, books: [{ id, title, coverUrl }] }`.
`date` de `POST`/`DELETE` no puede ser futuro (`400` si lo es). Marcar un libro que no es tuyo
devuelve `404`, no `403` (mismo criterio que el resto de la API).

`currentStreak`/`longestStreak` se calculan sobre todo el historial, no solo el mes que se esté
viendo — la racha actual cuenta días consecutivos terminando hoy, pero sigue "viva" si el último
día marcado fue ayer (para no penalizar a quien aún no ha marcado el día de hoy); dos días sin
marcar nada la rompe.

**Seguir a otros usuarios**

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| `GET` | `/api/users/search?q=` | — | Hasta 20 usuarios cuyo alias contiene `q` (sin distinguir mayúsculas), sin incluirte a ti mismo |
| `GET` | `/api/users/{id}/profile` | — | Perfil público de ese usuario |
| `POST` | `/api/users/{id}/follow` | — | `204`. `409` si ya le sigues, `400` si es tu propio id |
| `DELETE` | `/api/users/{id}/follow` | — | `204` (idempotente: no falla si no le seguías) |

`GET /api/users/search` solo encuentra usuarios que tienen alias puesto — sin alias, no eres
localizable. La respuesta de cada resultado: `{ id, alias, name, followersCount, followedByMe }`.

El perfil (`GET /api/users/{id}/profile`) siempre devuelve `{ id, alias, name, followersCount,
followingCount, followedByMe, own, visible, books }` — pero `books` solo viene relleno
(con sus reseñas y categorías anidadas) si `visible` es `true`: o es tu propio perfil (`own`), o
sigues a esa persona (`followedByMe`). Si no, `books` llega vacío aunque el usuario tenga libros de
verdad — la estantería y las reseñas de alguien son privadas hasta que le sigues.

**Notificaciones**

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| `GET` | `/api/notifications` | — | Página de notificaciones, más recientes primero |
| `GET` | `/api/notifications/unread-count` | — | `{ count }` |
| `POST` | `/api/notifications/read-all` | — | `204` |

Cada notificación: `{ id, type, actorId, actorAlias, actorName, read, createdAt }`. Por ahora el
único `type` es `NEW_FOLLOWER`, creada automáticamente al seguir a alguien
(`FollowService.follow()`). `POST .../read-all` marca como leídas todas las que tuvieras
pendientes de este usuario.

**Feed de actividad**

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| `GET` | `/api/feed?limit=` | — | Lista de `FeedItemResponse`, más reciente primero |

`limit` (opcional, por defecto 20, máximo 50). Cada elemento:
`{ type, actorId, actorAlias, actorName, bookId, bookTitle, bookCoverUrl, rating, occurredAt }`,
con `type` uno de `STARTED_READING`, `FINISHED_READING` o `REVIEWED` (`rating` solo viene relleno
en este último). Solo incluye actividad de la gente que sigues — mismo criterio de visibilidad
que el resto de la capa social.

`FeedService` no mantiene una tabla de eventos aparte: combina en memoria, para la gente que
sigues, sus libros que han pasado a `READING`/`READ` (usando `updatedAt` como marca de tiempo) y
sus reseñas recientes — mismo criterio pragmático que `StatsService`. Como consecuencia, editar
un libro que ya estaba en ese estado (corregir un dato suelto, por ejemplo) puede hacer que
reaparezca en el feed como si acabara de cambiar de estado.

**Categorías**

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| `GET` | `/api/categories` | — | Lista ordenada por nombre |
| `POST` | `/api/categories` | `{ name }` | `201` + categoría |
| `PUT` | `/api/categories/{id}` | `{ name }` | Categoría actualizada |
| `DELETE` | `/api/categories/{id}` | — | `204` |
| `POST` | `/api/categories/seed-defaults` | — | Categorías creadas (solo las que faltaban) |

Nombres duplicados dentro del mismo usuario devuelven `409`. `POST .../seed-defaults` es la misma
función que ya crea las 8 categorías por defecto al registrarse (ver más arriba), pero disponible
bajo demanda para una cuenta que ya existía antes de esa función o que las borró — es idempotente,
solo añade las que falten por nombre.

**Objetivo de lectura anual**

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| `GET` | `/api/reading-goals/current` | — | `{ year, targetBooks, booksRead }` |
| `PUT` | `/api/reading-goals/current` | `{ targetBooks }` | Objetivo actualizado |

Siempre sobre el año en curso. `targetBooks`: entero de 1 a 1000. `booksRead` se calcula a partir
de los libros `READ` con `finishedAt` en ese año (no hace falta guardarlo aparte).

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

---

## English

**[🇪🇸 Español](#-shelfy--backend)** · **🇬🇧 English** (this document)

> REST API for Shelfy, a personal library: books by reading status with page progress, series and
> format, your own categories, private reviews and notes, stats, re-reads, and a social layer with
> an activity feed and notifications — with JWT authentication and strict per-user isolation.

[![API live](https://img.shields.io/badge/API-live-brightgreen)](https://shelfy-backend-prw2.onrender.com/actuator/health)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Neon-4169E1?logo=postgresql&logoColor=white)
![JWT](https://img.shields.io/badge/Auth-JWT-000000?logo=jsonwebtokens&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-blue)

This repository is the **backend**. The frontend (Angular) that consumes it lives in
[**shelfy-frontend**](https://github.com/costanna/shelfy-frontend) —
**[🔗 try it live](https://shelfy-reads.vercel.app)** (a demo account is already loaded with data:
`demo@shelfy.app` / `shelfy123`).

> Deployed on Render's free tier: if it's been asleep for a while, the first request can take up
> to a minute to respond. That's expected, not an error.

---

### What it exposes

Registration with email verification, login, password recovery, book CRUD with filters, sorting
and pagination, reading progress by page, series and format (physical/ebook/audiobook), a user's
own categories, reviews and notes nested in each book, importing/exporting the library as CSV,
reading stats (books finished per month, how many days each one took, how many you're currently
reading), the ability to re-read a book you've already finished without losing track of the
previous read, and an optional social layer: search other users by alias, follow them to see their
shelf and reviews, get a notification when someone follows you, and a feed with what the people you
follow have been up to — guaranteeing that no one can ever *modify* another user's data, and that
someone's shelf, reviews and activity can only be *seen* if that person has you among their
followers.

### Highlights

- **JWT authentication** end to end (Spring Security), with hashed passwords and configurable token expiration.
- **Data-level, per-user isolation**, not just UI-level: requesting someone else's book, category or review returns `404`, not `403`, so as not to even reveal that it exists. Enforced consistently in the `Service`, never trusting client-side filtering.
- **Real filtering and pagination** on `GET /api/books` (status, category, free text, sort order), using Spring Data JPA's `Specification` instead of ad hoc queries for every filter combination.
- **Centralized error handling**: a single `@ControllerAdvice` translates validation errors, duplicates and not-found resources into a consistent error format across the whole API.
- **Custom business validation with Bean Validation**: `@HalfStep` (reviews, half-star steps only), `@ValidDateRange` (books, `finishedAt` can't be before `startedAt`) and `@NoProfanity` (user alias) are purpose-built annotations with their own `ConstraintValidator`, just like `@Min`/`@Max` for any other domain rule — `@ValidDateRange` even redirects the error to a specific field (`finishedAt`) from a class-level validation.
- **Unique user alias without locking yourself out**: the uniqueness check lives in the `Service`, not in the validation annotation — so a user can save the alias they already had without it being rejected as "already in use" for colliding with their own row.
- **Social visibility without duplicating data**: there's no separate "public version" of `Book`/`Review` in the database — `UserProfileService` reuses the same entities and only decides, at request time, whether to fill in `books` in the response or return it empty based on `own`/`followedByMe`. Also, without an alias a user simply doesn't show up in `/api/users/search`: not being findable is the default, you have to set an alias to become discoverable.
- **Production-ready with no code changes**: all configuration (DB, JWT, CORS) comes from environment variables, with sensible defaults for local development.
- **Stats computed on the fly**: `/api/stats` groups the user's books in memory with the Stream API (by `finishedAt` month, by status, etc.) instead of keeping denormalized counters — simple and fast enough for the real size of a personal library. The activity feed (`/api/feed`) follows the same approach: it combines recent books and reviews from the people you follow in memory instead of keeping a separate event table.
- **Re-reads without losing history**: `Book.startedAt`/`finishedAt` remain the "current" read, but re-reading a book (`POST .../reread`, or reopening it from Stats) archives the finished read into `ReadEvent` before resetting it — so `/api/stats` can keep counting the month you first finished it, and the month you finish it again, instead of the second read overwriting the first.
- **Email verification without blocking startup if mail fails**: `management.health.mail.enabled=false` — by default, Spring Boot Actuator adds a health check that opens a real SMTP connection on every `/actuator/health` as soon as it detects `spring-boot-starter-mail` on the classpath; without disabling it, a one-off Gmail hiccup (or missing credentials locally) took down the health check for the *whole* service, not just email sending.
- **Existing accounts don't break when verification is added**: `email_verified` is added with `@ColumnDefault("true")`, so Hibernate migrates accounts that already existed in the database as verified; only new accounts start out unverified.
- **Explicit indexes on owner and foreign-key columns** (`books.owner_id`, `categories.owner_id`, `reviews.book_id`/`user_id`, `follows.followed_id`, `book_categories.book_id`/`category_id`): PostgreSQL doesn't index these on its own, only the primary key and `UNIQUE` ones — and every "my books/categories/reviews" query filters by exactly one of these columns.

### How it's built

| | |
|---|---|
| **Stack** | Spring Boot 3.5 · Java 21 · Spring Data JPA · Spring Security · Bean Validation · Lombok |
| **Database** | PostgreSQL (Neon in production; in-memory H2 or Docker locally) |
| **Deployment** | Render (Web Service, Docker) via Blueprint ([`render.yaml`](./render.yaml)) |

```text
com.shelfy
├── auth/          registration, login, email verification, password recovery
├── user/          profile, alias, preferences, user search, public profile
├── follow/        follow/unfollow, counters
├── notification/  notifications (e.g. new follower)
├── feed/          activity feed of the people you follow
├── book/          entity, filters, CRUD, reading progress, re-reads, CSV
├── category/      a user's own categories
├── review/        reviews nested in books
├── note/          private notes nested in books
├── goal/          annual reading goal
├── readinglog/    reading calendar (marked days, streaks)
├── stats/         reading stats
├── mail/          sending emails (verification, recovery, reminders)
├── security/      JwtService, JWT filter, UserPrincipal
├── config/        security, CORS and sample data
└── common/        API errors and paginated response
```

One package per *feature* (not per technical layer), with each one's classes split by
responsibility: `Controller` → `Service` → `Repository`, their own input/output DTOs and a
`Mapper` between entity and DTO.

### Running locally

Without installing PostgreSQL, with sample data:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Starts at `http://localhost:8080` with a ready-to-use account (`demo@shelfy.app` /
`shelfy123`) on an in-memory H2 database — lost when the server stops.

With real PostgreSQL, via Docker:

```bash
docker compose up -d
mvn spring-boot:run
```

<details>
<summary><strong>More details: environment variables, endpoints, error format, deployment</strong></summary>

#### Environment variables

| Variable | Default | What it's for |
|---|---|---|
| `DB_URL` | *(composed, see below)* | Full PostgreSQL JDBC URL. If set, it takes priority over `DB_HOST`/`DB_PORT`/`DB_NAME` |
| `DB_HOST` | `localhost` | PostgreSQL host (alternative to `DB_URL`, used by `render.yaml`) |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `shelfy` | Database name |
| `DB_USER` | `shelfy` | Database user |
| `DB_PASSWORD` | `shelfy` | Database password |
| `DB_SSLMODE` | `disable` | Connection TLS mode (`require` on Neon and similar providers) |
| `JWT_SECRET` | *(development value)* | Signing secret. **Required in production**, at least 32 characters |
| `JWT_EXPIRATION_MS` | `86400000` (24 h) | Token expiration |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Allowed origins, comma-separated |
| `FRONTEND_URL` | `http://localhost:4200` | Base for the verification/recovery links in emails |
| `MAIL_ENABLED` | `false` | If `false`, no real email is sent: the link is left in the log instead (so the whole flow can be tested locally without credentials) |
| `MAIL_HOST` | `smtp.gmail.com` | SMTP server |
| `MAIL_PORT` | `587` | SMTP port |
| `MAIL_USERNAME` | — | Gmail account that sends the emails |
| `MAIL_PASSWORD` | — | [App password](https://myaccount.google.com/apppasswords) for that account (not the regular password; requires 2-step verification enabled) |
| `MAIL_FROM` | value of `MAIL_USERNAME` | Sender of the emails |
| `REQUIRE_EMAIL_VERIFICATION` | `false` | Emergency switch: when `false`, accounts are born already verified and login doesn't depend on email (`forgot-password` still works the same, unaffected by this variable) |
| `DDL_AUTO` | `update` | Hibernate schema strategy |
| `PORT` | `8080` | HTTP port (Render injects this automatically) |

#### Authentication

Every endpoint except `/api/auth/**` requires the header:

```text
Authorization: Bearer <token>
```

The token is obtained from `register` or `login` and expires after 24 h.

#### Endpoints

**Auth**

| Method | Route | Body | Response |
|---|---|---|---|
| `POST` | `/api/auth/register` | `{ email, password, name }` | `201` + `{ message }` |
| `POST` | `/api/auth/login` | `{ email, password }` | `200` + `{ token, tokenType, expiresIn, user }`, or `403` if the email isn't verified |
| `GET` | `/api/auth/verify-email?token=` | — | `200` + `{ token, tokenType, expiresIn, user }` (verifies and logs in at once) |
| `POST` | `/api/auth/resend-verification` | `{ email }` | `200` + `{ message }`, always the same message whether the account exists or not |
| `POST` | `/api/auth/forgot-password` | `{ email }` | `200` + `{ message }`, always the same message whether the account exists or not |
| `POST` | `/api/auth/reset-password` | `{ token, newPassword }` | `200` + `{ message }` |

`password`: 8 to 72 characters. Registration no longer logs you in right away: you need to verify
your email first (link valid for 24 h). The `forgot-password` token expires in 1 h and only works
once.

Every new account is created with 8 default categories (Fiction, Non-fiction, Fantasy, Science
Fiction, Mystery & Thriller, Romance, Biography, Poetry) — `CategoryService.seedDefaults()`, so the
Categories section doesn't start out completely empty. They're regular categories: they can be
renamed or deleted like any other, they aren't protected.

**User**

| Method | Route | Body | Response |
|---|---|---|---|
| `GET` | `/api/users/me` | — | `{ id, email, name, alias, themePreference, languagePreference, avatarUpdatedAt }` |
| `PATCH` | `/api/users/me/preferences` | `{ themePreference?, languagePreference? }` | Updated user |
| `PATCH` | `/api/users/me/alias` | `{ alias }` | Updated user, or `409` if the alias is already taken by another account |
| `POST` | `/api/users/me/avatar` | `multipart/form-data`, field `file` | Updated user, or `400` if it's not a valid image (PNG/JPEG/WEBP, max. 5 MB) |
| `DELETE` | `/api/users/me/avatar` | — | Updated user (idempotent: doesn't fail if you had no avatar) |
| `GET` | `/api/users/{id}/avatar` | — | JPEG image (public, unauthenticated), or `404` if that user has no avatar |

- `themePreference`: `LIGHT` · `DARK` · `SYSTEM`
- `languagePreference`: `en` · `ca` · `es`
- `alias`: 3–24 characters, letters/numbers/`_` only, unique across accounts (case-insensitive)
  and free of profanity (ES/CA/EN). Optional — `null` until the user picks one.
- `avatarUpdatedAt`: `null` if there's no avatar; otherwise, the date it was uploaded/changed —
  meant for the frontend to use as a cache-busting parameter (`?v=...`) in the image URL, not to
  display. Any uploaded image is cropped to a centered square and resized to 320×320 px as JPEG
  before saving, so the size stored in the database doesn't depend on what each user uploads.
  `GET /api/users/{id}/avatar` is the only route under `/api/users` that doesn't require a
  session: an `<img>` tag doesn't send the `Authorization` header the frontend's HTTP interceptor
  adds, so it has to be public — like the rest of the app, there's nothing sensitive in someone's
  profile picture.

**Books**

| Method | Route | Body | Response |
|---|---|---|---|
| `GET` | `/api/books` | — | Page of books |
| `GET` | `/api/books/{id}` | — | Book |
| `POST` | `/api/books` | `BookRequest` | `201` + book |
| `PUT` | `/api/books/{id}` | `BookRequest` | Updated book |
| `DELETE` | `/api/books/{id}` | — | `204` |
| `PATCH` | `/api/books/{id}/reading-dates` | `{ startedAt?, finishedAt? }` | Updated book |
| `PATCH` | `/api/books/{id}/progress` | `{ currentPage }` | Updated book |
| `POST` | `/api/books/{id}/reread` | — | Updated book |
| `GET` | `/api/books/export` | — | CSV of the whole library |
| `POST` | `/api/books/import` | `multipart/form-data`, field `file` | `BookImportResult` |

`GET /api/books` filters (optional and combinable): `status`, `categoryId`, `q` (free text on
title/author), `page`/`size` (pagination, 12 by default), `sort` (defaults to `createdAt,desc`;
accepts any of the book's own fields, e.g. `title,asc` or `pageCount,desc`).

```json
// BookRequest — only title and status are required
{
  "title": "Dune",
  "author": "Frank Herbert",
  "coverUrl": "https://...",
  "isbn": "9788497596909",
  "synopsis": "...",
  "pageCount": 688,
  "currentPage": 120,
  "series": "Dune",
  "seriesPosition": 1,
  "format": "PHYSICAL",
  "status": "WANT_TO_READ",
  "startedAt": "2026-01-01",
  "finishedAt": "2026-01-10",
  "categoryIds": [1, 3]
}
```

`status`: `WANT_TO_READ` · `READING` · `READ` · `WANT_TO_BUY`. `format` (optional): `PHYSICAL` ·
`EBOOK` · `AUDIOBOOK`. `startedAt`/`finishedAt`: `ISO-8601` dates (`YYYY-MM-DD`), optional; if both
are sent, `finishedAt` can't be before `startedAt` (`400` if it is).

`PATCH /api/books/{id}/reading-dates` exists separately from `PUT` so you can change (or clear, by
sending both as `null`) just the reading range from Stats without resending the rest of the book —
same date rules as `BookRequest`. Since this route doesn't take a status (unlike the full form), it
infers one from the dates: sending `finishedAt` puts the book in `READ`, and sending only
`startedAt` (without `finishedAt`) puts it in `READING` if it was in `WANT_TO_READ`, `WANT_TO_BUY`
or `READ` — in this last case, just like `POST .../reread` (see below), it archives the read it had
finished before overwriting it, so it isn't lost from "books finished per month". It never
downgrades a `READ` book: clearing `finishedAt` without sending `startedAt` leaves it at
`WANT_TO_READ`, not `READING`. Without this, setting just the date without touching the status left
the book out of "books finished per month" in Stats even though it had a `finishedAt`.

`PATCH /api/books/{id}/progress` — `{ currentPage }` (number, `≥ 0`) updates just the page you're
on, without resending the rest of the book. If the book was in `WANT_TO_READ` or `WANT_TO_BUY` it
moves to `READING`, and if it had no `startedAt` it gets today's date. `currentPage` never exceeds
`pageCount` (it's clamped to the max if you try).

`POST /api/books/{id}/reread` — only for books in `READ`. Archives the current read (if it had a
`finishedAt`) and puts the book in `READING` with `startedAt` set to today, and `finishedAt` and
`currentPage` set to `null`. `400` if the book isn't in `READ`. The history of archived reads can
be checked in `readHistory` inside the book's response (`{ startedAt, finishedAt }[]`, most recent
first).

`GET /api/books/export` downloads a CSV (`title,author,isbn,status,pageCount,series,
seriesPosition,format,startedAt,finishedAt,categories,synopsis`) with your whole library.
`POST /api/books/import` reads that same format: each row needs at least a `title`, the other
columns are optional and rows with no title are skipped (reported in `BookImportResult` —
`{ imported, skipped, messages }`). It doesn't export or import `currentPage` or the re-read
history, only the book's "current" state.

**Reading stats**

| Method | Route | Body | Response |
|---|---|---|---|
| `GET` | `/api/stats` | — | `{ totalBooksRead, totalBooks, currentlyReading, readingDurations, booksByMonth }` |

`totalBooksRead`: books in `READ` right now (one per book, a re-read doesn't count it twice).
`totalBooks`: total library size. `currentlyReading`: books in `READING` right now.

`readingDurations`: one entry per completed read with both `startedAt` and `finishedAt` set —
`{ bookId, title, startedAt, finishedAt, daysReading, current }`, most recent first.
`daysReading` counts both the start and end day (starting and finishing the same day counts as 1).
`current` distinguishes the book's active read (editable via `PATCH .../reading-dates`) from one
archived by a re-read (`false`; its dates can no longer be edited here, only viewed).

`booksByMonth`: how many reads were finished each month — `{ year, month, count }`, most recent
first. Counts both the `READ` book with a `finishedAt` and any read archived by an earlier
re-read, so re-reading a book doesn't erase the month you first finished it — and if you finish it
again, it adds a second hit to that new month. A book with no `finishedAt` can't be attributed to
any month.

**Reading calendar**

| Method | Route | Body | Response |
|---|---|---|---|
| `GET` | `/api/reading-log?year=&month=` | — | `{ year, month, days }` — only days with something marked |
| `GET` | `/api/reading-log/streak` | — | `{ currentStreak, longestStreak }` |
| `GET` | `/api/reading-log/summary` | — | The whole history grouped by book: `[{ bookId, title, dates }]` |
| `POST` | `/api/reading-log` | `{ bookId, date }` | `204`. Idempotent: if that book was already marked that day, it's a no-op |
| `DELETE` | `/api/reading-log?bookId=&date=` | — | `204` (idempotent) |

Independent of the book's `startedAt`/`finishedAt` (which remain the "official" range on the
detail page): this is a day-by-day log meant for the interactive calendar in Stats — the same day
can have several books marked (reading more than one in parallel), and a book can have loose
marked days unrelated to its declared reading range.

`days` returns, for each day with at least one book marked, `{ date, books: [{ id, title, coverUrl }] }`.
`date` for `POST`/`DELETE` can't be in the future (`400` if it is). Marking a book that isn't yours
returns `404`, not `403` (same rule as the rest of the API).

`currentStreak`/`longestStreak` are computed over the whole history, not just the month being
viewed — the current streak counts consecutive days ending today, but stays "alive" if the last
marked day was yesterday (so as not to penalize someone who hasn't marked today yet); two days
with nothing marked breaks it.

**Following other users**

| Method | Route | Body | Response |
|---|---|---|---|
| `GET` | `/api/users/search?q=` | — | Up to 20 users whose alias contains `q` (case-insensitive), never including yourself |
| `GET` | `/api/users/{id}/profile` | — | That user's public profile |
| `POST` | `/api/users/{id}/follow` | — | `204`. `409` if you already follow them, `400` if it's your own id |
| `DELETE` | `/api/users/{id}/follow` | — | `204` (idempotent: doesn't fail if you weren't following them) |

`GET /api/users/search` only finds users who have an alias set — without one, you're not
findable. Each result: `{ id, alias, name, followersCount, followedByMe }`.

The profile (`GET /api/users/{id}/profile`) always returns `{ id, alias, name, followersCount,
followingCount, followedByMe, own, visible, books }` — but `books` is only filled in (with its
nested reviews and categories) if `visible` is `true`: either it's your own profile (`own`), or
you follow that person (`followedByMe`). Otherwise, `books` comes back empty even if the user has
real books — someone's shelf and reviews are private until you follow them.

**Notifications**

| Method | Route | Body | Response |
|---|---|---|---|
| `GET` | `/api/notifications` | — | Page of notifications, most recent first |
| `GET` | `/api/notifications/unread-count` | — | `{ count }` |
| `POST` | `/api/notifications/read-all` | — | `204` |

Each notification: `{ id, type, actorId, actorAlias, actorName, read, createdAt }`. For now the
only `type` is `NEW_FOLLOWER`, created automatically when someone follows you
(`FollowService.follow()`). `POST .../read-all` marks all of this user's pending notifications as
read.

**Activity feed**

| Method | Route | Body | Response |
|---|---|---|---|
| `GET` | `/api/feed?limit=` | — | List of `FeedItemResponse`, most recent first |

`limit` (optional, defaults to 20, max 50). Each item:
`{ type, actorId, actorAlias, actorName, bookId, bookTitle, bookCoverUrl, rating, occurredAt }`,
with `type` one of `STARTED_READING`, `FINISHED_READING` or `REVIEWED` (`rating` is only filled in
for the latter). Only includes activity from the people you follow — same visibility rule as the
rest of the social layer.

`FeedService` doesn't keep a separate event table: for the people you follow, it combines in
memory their books that moved to `READING`/`READ` (using `updatedAt` as the timestamp) and their
recent reviews — same pragmatic approach as `StatsService`. As a consequence, editing a book
that's already in that status (fixing a stray typo, say) can make it resurface in the feed as if
it had just changed status.

**Categories**

| Method | Route | Body | Response |
|---|---|---|---|
| `GET` | `/api/categories` | — | List sorted by name |
| `POST` | `/api/categories` | `{ name }` | `201` + category |
| `PUT` | `/api/categories/{id}` | `{ name }` | Updated category |
| `DELETE` | `/api/categories/{id}` | — | `204` |
| `POST` | `/api/categories/seed-defaults` | — | Categories created (only the missing ones) |

Duplicate names within the same user return `409`. `POST .../seed-defaults` is the same function
that already creates the 8 default categories at registration (see above), but available on
demand for an account that existed before that feature or that deleted them — it's idempotent,
only adding the ones missing by name.

**Annual reading goal**

| Method | Route | Body | Response |
|---|---|---|---|
| `GET` | `/api/reading-goals/current` | — | `{ year, targetBooks, booksRead }` |
| `PUT` | `/api/reading-goals/current` | `{ targetBooks }` | Updated goal |

Always for the current year. `targetBooks`: integer from 1 to 1000. `booksRead` is computed from
the `READ` books with a `finishedAt` in that year (no need to store it separately).

**Reviews** (nested in books)

| Method | Route | Body | Response |
|---|---|---|---|
| `GET` | `/api/books/{bookId}/reviews` | — | List, most recent first |
| `POST` | `/api/books/{bookId}/reviews` | `{ rating, text }` | `201` + review |
| `PUT` | `/api/books/{bookId}/reviews/{id}` | `{ rating, text }` | Updated review |
| `DELETE` | `/api/books/{bookId}/reviews/{id}` | — | `204` |

`rating`: a number from 0.5 to 5, in steps of 0.5 (e.g. `3.5`).

#### Error format

```json
{
  "timestamp": "2026-09-15T10:09:57.588Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid input data",
  "fieldErrors": { "password": "Password must be between 8 and 72 characters" }
}
```

`fieldErrors` only appears for validation errors.

| Code | When |
|---|---|
| `400` | Validation failed |
| `401` | No token, expired token, or wrong credentials |
| `404` | The resource doesn't exist or belongs to another user |
| `409` | Email or category name already in use |

#### Deploying to Render (backend) + Neon (database)

Render's Free plan only allows **one** managed PostgreSQL database per account, so Shelfy's
database lives on [Neon](https://neon.tech) (free, no such limit) and the backend runs on Render
as a Web Service.

**Quick option — Blueprint**: the repo includes [`render.yaml`](./render.yaml). In the dashboard:
**New → Blueprint** → connect `shelfy-backend`. It will ask for `DB_HOST`, `DB_NAME`, `DB_USER`,
`DB_PASSWORD` (Neon's connection details) and `MAIL_USERNAME`/`MAIL_PASSWORD` (see below);
`DB_PORT`, `DB_SSLMODE=require`, `JWT_SECRET`, `MAIL_ENABLED` and `FRONTEND_URL` are already
resolved, and `CORS_ALLOWED_ORIGINS` points at the frontend on Vercel.

**Manual option**: **New → Web Service** → connect this repository → **Docker** runtime. In
*Environment*, define the same variables by hand. Health check path: `/actuator/health`.

> Use Neon's **direct** endpoint, not the `-pooler` one: with PgBouncer in *transaction* mode,
> Hibernate can hit intermittent *prepared statement* errors.

Render's Free plan puts the service to sleep after ~15 min of inactivity (the first request
afterward can take close to a minute); Neon hibernates the database similarly and wakes up on its
own on the next connection.

**Sending real emails (Gmail):**

1. Enable 2-step verification on the Gmail account that will send the emails.
2. Generate an [app password](https://myaccount.google.com/apppasswords) (16 characters,
   different from your regular password).
3. On Render (the service's *Environment*), set `MAIL_USERNAME` to that Gmail account and
   `MAIL_PASSWORD` to the app password (**never the account's regular password**), and switch
   `MAIL_ENABLED` to `true` — it defaults to `false` (registration and recovery still work, just
   without actually sending the email) so as not to leave registration broken in production until
   the credentials are in place.
4. Locally, leave `MAIL_ENABLED` unset (defaults to `false`): verification and recovery links are
   written to the server log instead of being sent, so the whole flow can be tested without real
   credentials.

</details>

## License

[MIT](./LICENSE)
