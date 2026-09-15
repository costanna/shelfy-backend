# Shelfy — Backend

API REST para organizar lecturas: libros leídos, pendientes, por comprar, categorías propias y reseñas.

**Stack:** Spring Boot 3.5 · Java 21 · Spring Data JPA · Spring Security (JWT) · PostgreSQL

---

## Arrancar en local

### Opción A — sin instalar PostgreSQL (H2 en memoria)

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Levanta en `http://localhost:8080` con datos de ejemplo y un usuario listo para probar:

| Email | Contraseña |
|---|---|
| `demo@shelfy.app` | `shelfy123` |

Los datos se pierden al parar el servidor (es una base en memoria).

### Opción B — con PostgreSQL vía Docker

```bash
docker compose up -d
mvn spring-boot:run
```

---

## Variables de entorno

| Variable | Por defecto | Para qué sirve |
|---|---|---|
| `DB_URL` | *(compuesta, ver abajo)* | URL JDBC completa de PostgreSQL. Si se define, tiene prioridad sobre `DB_HOST`/`DB_PORT`/`DB_NAME` |
| `DB_HOST` | `localhost` | Host de PostgreSQL (alternativa a `DB_URL`, usada por `render.yaml`) |
| `DB_PORT` | `5432` | Puerto de PostgreSQL |
| `DB_NAME` | `shelfy` | Nombre de la base de datos |
| `DB_USER` | `shelfy` | Usuario de base de datos |
| `DB_PASSWORD` | `shelfy` | Contraseña de base de datos |
| `JWT_SECRET` | *(valor de desarrollo)* | Secreto de firma. **Obligatorio en producción**, mínimo 32 caracteres |
| `JWT_EXPIRATION_MS` | `86400000` (24 h) | Caducidad del token |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Orígenes permitidos, separados por comas |
| `DDL_AUTO` | `update` | Estrategia de esquema de Hibernate |
| `PORT` | `8080` | Puerto HTTP (Render lo inyecta automáticamente) |

---

## Autenticación

Todos los endpoints excepto `/api/auth/**` requieren la cabecera:

```
Authorization: Bearer <token>
```

El token se obtiene en `register` o `login` y caduca a las 24 h.

Cada usuario solo ve y modifica sus propios libros, categorías y reseñas: pedir
un recurso ajeno devuelve `404`, no `403`, para no revelar que existe.

---

## Endpoints

### Auth

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| `POST` | `/api/auth/register` | `{ email, password, name }` | `201` + `{ token, tokenType, expiresIn, user }` |
| `POST` | `/api/auth/login` | `{ email, password }` | `200` + `{ token, tokenType, expiresIn, user }` |

`password`: entre 8 y 72 caracteres.

### Usuario

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| `GET` | `/api/users/me` | — | `{ id, email, name, themePreference, languagePreference }` |
| `PATCH` | `/api/users/me/preferences` | `{ themePreference?, languagePreference? }` | Usuario actualizado |

- `themePreference`: `LIGHT` · `DARK` · `SYSTEM`
- `languagePreference`: `en` · `ca` · `es`

### Libros

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| `GET` | `/api/books` | — | Página de libros |
| `GET` | `/api/books/{id}` | — | Libro |
| `POST` | `/api/books` | `BookRequest` | `201` + libro |
| `PUT` | `/api/books/{id}` | `BookRequest` | Libro actualizado |
| `DELETE` | `/api/books/{id}` | — | `204` |

**Filtros de `GET /api/books`** (todos opcionales y combinables):

| Parámetro | Ejemplo | Efecto |
|---|---|---|
| `status` | `?status=READING` | Filtra por estado |
| `categoryId` | `?categoryId=3` | Solo libros de esa categoría |
| `q` | `?q=dune` | Busca en título y autor (sin distinguir mayúsculas) |
| `page` / `size` | `?page=0&size=12` | Paginación (12 por defecto) |
| `sort` | `?sort=title,asc` | Orden (por defecto `createdAt,desc`) |

**`BookRequest`**

```json
{
  "title": "Dune",
  "author": "Frank Herbert",
  "coverUrl": "https://...",
  "isbn": "9788497596909",
  "synopsis": "...",
  "pageCount": 688,
  "status": "WANT_TO_READ",
  "categoryIds": [1, 3]
}
```

Solo `title` y `status` son obligatorios. `status`: `WANT_TO_READ` · `READING` · `READ` · `WANT_TO_BUY`.

**Respuesta paginada**

```json
{
  "content": [ /* libros */ ],
  "page": 0,
  "size": 12,
  "totalElements": 42,
  "totalPages": 4,
  "last": false
}
```

### Categorías

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| `GET` | `/api/categories` | — | Lista ordenada por nombre |
| `POST` | `/api/categories` | `{ name }` | `201` + categoría |
| `PUT` | `/api/categories/{id}` | `{ name }` | Categoría actualizada |
| `DELETE` | `/api/categories/{id}` | — | `204` |

Nombres duplicados dentro del mismo usuario devuelven `409`.

### Reseñas (anidadas en libros)

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| `GET` | `/api/books/{bookId}/reviews` | — | Lista, más recientes primero |
| `POST` | `/api/books/{bookId}/reviews` | `{ rating, text }` | `201` + reseña |
| `PUT` | `/api/books/{bookId}/reviews/{id}` | `{ rating, text }` | Reseña actualizada |
| `DELETE` | `/api/books/{bookId}/reviews/{id}` | — | `204` |

`rating`: entero de 1 a 5.

---

## Formato de errores

```json
{
  "timestamp": "2026-09-15T10:09:57.588Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Datos de entrada no válidos",
  "fieldErrors": {
    "password": "La contraseña debe tener entre 8 y 72 caracteres"
  }
}
```

`fieldErrors` solo aparece en errores de validación.

| Código | Cuándo |
|---|---|
| `400` | Validación fallida |
| `401` | Sin token, token caducado o credenciales incorrectas |
| `404` | El recurso no existe o pertenece a otro usuario |
| `409` | Email o nombre de categoría ya en uso |

---

## Estructura del proyecto

Un paquete por *feature*, con las clases separadas por responsabilidad:

```
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

---

## Desplegar en Render

### Opción rápida — Blueprint

Este repo incluye [`render.yaml`](./render.yaml). En el dashboard: **New → Blueprint** → conecta
`shelfy-backend`. Crea la base de datos y el Web Service ya enlazados (host, usuario y contraseña
se inyectan solos vía `DB_HOST`/`DB_USER`/`DB_PASSWORD`) y genera `JWT_SECRET` automáticamente.
Solo falta revisar `CORS_ALLOWED_ORIGINS` con la URL real del Static Site del frontend.

### Opción manual

1. **New → PostgreSQL** (plan Free) y copia la *Internal Database URL*.
2. **New → Web Service** → conecta este repositorio → runtime **Docker** (usa el `Dockerfile` incluido).
3. En *Environment*, define: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` y `CORS_ALLOWED_ORIGINS` con la URL del frontend.
4. Health check path: `/actuator/health`.

> La `Internal Database URL` de Render viene en formato `postgres://usuario:clave@host/base`.
> `DB_URL` necesita formato JDBC: `jdbc:postgresql://host/base`, con usuario y clave en sus propias variables.
> Si prefieres no montar esa URL a mano, deja `DB_URL` sin definir y usa `DB_HOST`/`DB_PORT`/`DB_NAME` sueltos
> (es justo lo que hace el Blueprint de arriba).

El plan Free duerme el servicio tras ~15 min sin uso: la primera petición después puede tardar cerca de un minuto.
