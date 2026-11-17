# Prueba web en navegador - SyncPass

Fecha: 2026-11-17 (hora local -03)
Web: http://localhost:5173
API: http://localhost:8080/api
Perfil API: local

## Rutas probadas

- `/` login
- `/proyectos`
- `/eventos`

Las tres rutas respondieron HTTP 200 en la sesión de navegador.

## Creacion de proyecto

Se creo desde el navegador el proyecto **Proyecto Prueba UI**.

Comprobacion en la API antes de limpiar:

```http
GET /api/proyectos
Authorization: Bearer <token Coordinador>
HTTP 200
```

Resultado: proyecto encontrado.

- proyectoId: 3
- titulo: Proyecto Prueba UI
- estado: PLANIFICADO

Respuesta completa guardada en `prueba-web-proyectos.json`.

## Rol Monitor

En la sesion con `monitor@syncpass.cl` se comprobo que la interfaz no muestra
botones de escritura (crear, editar o eliminar) en Proyectos ni en Eventos.

La web aplica el mismo criterio en codigo:

- `puedeEditarCatalogo(roles) = roles.includes('Coordinador')`.
- En Eventos, para roles sin permiso se muestra:
  "Solo el rol Coordinador puede crear, editar o eliminar eventos."

## Consola

0 errores de consola durante la prueba de navegador.

## Limpieza

El proyecto de prueba se elimino por API:

```http
DELETE /api/proyectos/3
Authorization: Bearer <token Coordinador>
HTTP 204
```

Verificacion posterior con `GET /api/proyectos`: el proyecto ya no aparece.
Quedan 2 proyectos en el catalogo (los originales).

## Cierre

- API 8080: detenida.
- Web 5173: detenida.
- Puertos 8080 y 5173: libres.
