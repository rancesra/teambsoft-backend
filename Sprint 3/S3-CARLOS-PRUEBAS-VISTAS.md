# Guía de S3-C — Pruebas automáticas de las vistas de listado y detalle

**Para:** Carlos Andrés · **Tareas:** S3-C1 (pruebas de `VistaListado`), S3-C2 (pruebas de `VistaDetalle`), S3-C3 (pruebas de `MensajeError`), S3-C4 (documentar cómo probar) · **Repositorio:** [teambsoft-frontend](https://github.com/rancesra/teambsoft-frontend) · **Rama:** `s3-pruebas-vistas` · **Entrega:** jueves 29 de octubre

Al terminar, las dos vistas que hoy no tienen pruebas (el listado con su filtro y paginación, y el detalle con el producto retirado) quedarán cubiertas, y el equipo tendrá un patrón claro para probar vistas que llaman al backend. Es el riesgo «medio» que dejó el informe del Sprint 2: *«el listado y el detalle no tienen pruebas automatizadas; solo la tarjeta y `formato.js`»*.

**Dependes de:**

1. **F2 y tu S2-C en `main`** (ya están): tienes `vitest.config.js`, `@vue/test-utils` y 12 pruebas funcionando.
2. **Juan Diego (S3-J2) cambia los colores** en los mismos `.vue`. Tus pruebas comprueban texto y clases, no colores, así que el conflicto, si aparece, es fácil. Avísale cuando empieces.

Los comandos son para **PowerShell**, en la terminal de VS Code.

## 1. Antes de empezar

```powershell
cd C:\dev\teambsoft-frontend
git switch main
git pull
npm install
npm test
git switch -c s3-pruebas-vistas
git push -u origin s3-pruebas-vistas
```

`npm test` debe seguir dando **12 pruebas en verde** antes de que toques nada. Si Hector ya publicó la clave de Jira, antepónla al nombre de la rama.

## 2. Qué archivos vas a tocar

```
src/
├── vistas/__tests__/
│   ├── VistaListado.spec.js       NUEVO  (S3-C1)
│   └── VistaDetalle.spec.js       NUEVO  (S3-C2)
├── componentes/__tests__/
│   └── MensajeError.spec.js       NUEVO  (S3-C3)
README.md                          actualizar la sección de pruebas  (S3-C4)
```

**No cambias el comportamiento de los componentes.** Si necesitas un selector estable, la única modificación permitida es agregar un atributo `data-testid="..."` a un elemento. Avisa a Rances y a Juan Diego si lo haces, porque son quienes más tocan esos archivos.

## 3. Lo primero: entender cómo llaman al backend las vistas

Las pruebas **no deben llamar al backend de verdad**: tienen que ser rápidas y funcionar sin Docker. Para eso se **simula** la respuesta. Cómo se simula depende de cómo esté escrito el código, así que primero lee:

1. Abre `src/vistas/VistaListado.vue` y `src/vistas/VistaDetalle.vue` y mira **de dónde sale `fetch`**:
   - ¿Importan un archivo de servicios (por ejemplo `@/servicios/api.js` o `@/api/...`)? Es el caso más probable.
   - ¿Llaman a `fetch` directo? ¿O usan Axios? Revisa `package.json`.
2. Abre ese archivo de servicios y mira: ¿cómo construye la URL (`VITE_API_URL`)?, ¿qué hace con una respuesta con error (400, 404)?, ¿qué hace cuando `fetch` falla del todo (servidor apagado)? El contrato Frontend dice que el cliente fabrica un error con código `SIN_CONEXION`.
3. Mira cómo la vista lee la URL: `useRoute()` y `route.query.categoria` / `route.query.pagina`, y cómo la cambia (`router.push`).
4. Anota los **textos exactos** que muestra en cada estado. Los que se conocen:

   | Estado | Texto |
   |---|---|
   | Lista vacía | «No hay productos en esta categoría.» y un botón «Ver todo el catálogo» |
   | Sin conexión | «No se pudo conectar con el servidor» y botón Reintentar |
   | Id inexistente | «Este producto no existe o fue retirado del catálogo» |
   | Producto retirado | «Producto no disponible» / «Este producto fue retirado del catálogo» |
   | Stock | «Sin stock» / «Quedan N» / «N disponibles» |

   Confirma cada uno en el código; si el texto real es distinto, manda el del código.

## 4. Una técnica para las dos vistas

La forma más independiente del código interno es **simular `fetch`** a nivel global, así funciona aunque cambien el cliente de servicios. Crea una ayuda reutilizable al principio de cada archivo de prueba (o, si prefieres, en `src/utilidades/__tests__/ayudas.js` y la importas):

```js
import { vi } from 'vitest'

/** Respuesta falsa con la forma mínima que usa un cliente de fetch. */
export function respuesta(cuerpo, estado = 200) {
  return {
    ok: estado >= 200 && estado < 300,
    status: estado,
    json: async () => cuerpo,
  }
}

/**
 * Simula el backend: recibe un objeto { fragmentoDeUrl: respuesta | () => respuesta } y contesta
 * según la URL pedida. Si la URL no está en el mapa, falla la prueba con un mensaje claro.
 */
export function simularBackend(rutas) {
  const falso = vi.fn(async (url) => {
    const direccion = String(url)
    for (const [fragmento, respuestaFalsa] of Object.entries(rutas)) {
      if (direccion.includes(fragmento)) {
        return typeof respuestaFalsa === 'function' ? respuestaFalsa(direccion) : respuestaFalsa
      }
    }
    throw new Error('La prueba no esperaba esta petición: ' + direccion)
  })
  vi.stubGlobal('fetch', falso)
  return falso
}
```

Y la limpieza, que no puede faltar: sin ella, un `fetch` falso de una prueba se filtra a la siguiente.

```js
import { afterEach } from 'vitest'

afterEach(() => {
  vi.unstubAllGlobals()
  vi.clearAllMocks()
})
```

> **Si el código usa Axios** (`package.json` lo dice), esta técnica no sirve: simula el módulo con `vi.mock('axios')` o `vi.mock('@/<ruta del cliente>')`. La idea es la misma: la prueba decide qué «contesta el backend».

Para montar una vista que usa el enrutador, usa uno **real, en memoria**, con las mismas rutas de la aplicación:

```js
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'

import VistaListado from '../VistaListado.vue'
import VistaDetalle from '../VistaDetalle.vue'

function crearRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/catalogo', component: VistaListado },
      { path: '/catalogo/:id', component: VistaDetalle },
    ],
  })
}

async function montarEn(ruta, vista) {
  const router = crearRouter()
  await router.push(ruta)
  await router.isReady()
  const envoltura = mount(vista, { global: { plugins: [router] } })
  await flushPromises() // espera a que terminen las peticiones y se vuelva a pintar
  return { envoltura, router }
}
```

Mira cómo están definidas las rutas reales en `src/router/index.js` y copia el mismo formato (por ejemplo, si la vista del módulo cuelga de un prefijo distinto).

**Datos de prueba**, siguiendo el contrato (`GET /productos` y `GET /categorias`):

```js
const categorias = [
  { id: 'cat-ropa', nombre: 'ropa' },
  { id: 'cat-hogar', nombre: 'hogar' },
]

function producto(cambios = {}) {
  return {
    id: 'p1', nombre: 'Camiseta basica', descripcion: 'Algodon', precio: 49900,
    categoria: 'cat-ropa', stock: 50, imagenes: [], activo: true, ...cambios,
  }
}

function pagina(productos, extra = {}) {
  return { productos, total: productos.length, pagina: 1, tamanoPagina: 20, ...extra }
}
```

## 5. S3-C1 — Pruebas de `VistaListado`

Crea `src/vistas/__tests__/VistaListado.spec.js` con estos casos. Cada uno corresponde a una regla del contrato, que es lo que realmente se está protegiendo:

| # | Qué prueba | Cómo | Esperado |
|---|---|---|---|
| 1 | Muestra los productos | Backend devuelve 2 productos | Aparecen los dos nombres y el conteo de productos |
| 2 | **La primera página es la 1** | Entra a `/catalogo` sin parámetros | La petición a `/productos` lleva `pagina=1` (no `0`) |
| 3 | Lee filtro y página de la URL | Entra a `/catalogo?categoria=cat-hogar&pagina=2` | La petición lleva `categoria=cat-hogar` y `pagina=2` |
| 4 | Muestra la categoría con nombre | Producto con `categoria: 'cat-ropa'` | Se ve «ropa» y no `cat-ropa` |
| 5 | **Cambiar de categoría vuelve a la página 1** | Empieza en `?pagina=3`, pulsa otro chip de categoría | La ruta queda en la página 1 (o sin `pagina`), según lo que haga el código |
| 6 | Lista vacía no es un error | Backend devuelve `productos: []`, `total: 0` | «No hay productos en esta categoría.» y botón para ver todo; **no** el mensaje de error |
| 7 | Sin conexión | `fetch` rechaza (`Promise.reject`) | «No se pudo conectar con el servidor» y botón Reintentar |
| 8 | Reintentar vuelve a pedir | En el caso 7, pulsa Reintentar | Se hace una nueva petición |
| 9 | Con una sola página no hay paginador | `total` ≤ `tamanoPagina` | No aparece el paginador (es el comportamiento previsto, no un fallo) |

Un esqueleto para empezar, con los casos 2 y 6 completos:

```js
import { describe, expect, it } from 'vitest'

// ... las ayudas y datos de la sección 4 ...

describe('VistaListado', () => {
  it('pide la página 1 por defecto (el contrato arranca en 1, no en 0)', async () => {
    const falso = simularBackend({
      '/categorias': respuesta(categorias),
      '/productos': respuesta(pagina([producto()])),
    })

    await montarEn('/catalogo', VistaListado)

    const peticionDeProductos = falso.mock.calls
      .map(([url]) => String(url))
      .find((url) => url.includes('/productos'))
    expect(peticionDeProductos).toContain('pagina=1')
    expect(peticionDeProductos).not.toContain('pagina=0')
  })

  it('una lista vacía muestra el estado vacío y no un error', async () => {
    simularBackend({
      '/categorias': respuesta(categorias),
      '/productos': respuesta(pagina([], { total: 0 })),
    })

    const { envoltura } = await montarEn('/catalogo?categoria=cat-hogar', VistaListado)

    expect(envoltura.text()).toContain('No hay productos en esta categoría.')
    expect(envoltura.text()).not.toContain('No se pudo conectar')
  })

  // Los casos 1, 3, 4, 5, 7, 8 y 9, con el mismo patrón.
})
```

Para pulsar un chip o un botón: `await envoltura.find('<selector>').trigger('click')` seguido de `await flushPromises()`. Mira en el `<template>` qué selector tiene (una clase, un `data-testid` o buscarlo por su texto con `envoltura.findAll('button').find((b) => b.text() === 'hogar')`).

Para simular el servidor apagado (caso 7): `simularBackend` no lo cubre; usa directamente `vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))`.

## 6. S3-C2 — Pruebas de `VistaDetalle`

Crea `src/vistas/__tests__/VistaDetalle.spec.js`:

| # | Qué prueba | Cómo | Esperado |
|---|---|---|---|
| 1 | Producto activo | `GET /productos/p1` devuelve 200 con `activo: true` | Nombre, precio en pesos (con punto de miles), categoría **por nombre** y el recuadro reservado del Carro |
| 2 | **Producto retirado: 200 con `activo: false`** | Backend devuelve 200 con `activo: false` | Aviso «Producto no disponible», y **no** el recuadro del Carro. **No** se muestra el mensaje de 404 |
| 3 | **Id inexistente** | Backend devuelve 404 `{ codigo: 'PRODUCTO_NO_ENCONTRADO', mensaje: '...' }` | «Este producto no existe o fue retirado del catálogo» |
| 4 | Sin conexión | `fetch` rechaza | «No se pudo conectar con el servidor» y botón Reintentar |
| 5 | Reintentar | En el caso 4, pulsa Reintentar | Nueva petición |
| 6 | Varias imágenes | `imagenes` con tres URLs | Aparecen tres miniaturas |
| 7 | Sin imágenes | `imagenes: []` | Se ve el relleno «sin imagen» |

Notas para los casos 2 y 3, que son los más importantes:

- La diferencia entre el 2 y el 3 es **el código HTTP**: un producto retirado responde **200**, un id inventado responde **404**. Las dos pruebas existen para que nadie «simplifique» esa regla nunca. La razón es del Equipo C: Carro necesita consultar un producto retirado para marcarlo como no disponible, y con un 404 no podría distinguir «lo retiraron» de «nunca existió».
- Para el 404, el cuerpo de la respuesta falsa es `{ codigo: 'PRODUCTO_NO_ENCONTRADO', mensaje: 'No existe un producto con id zzz' }`, con estado `404`: `respuesta({...}, 404)`.
- El precio en pesos: no lo compares con un texto exacto. `Intl` pone un espacio no separable entre el símbolo y el número (por eso en `formato.spec.js` usas una expresión regular): `expect(texto).toMatch(/49\.900/)`.
- Para el id, entra con `montarEn('/catalogo/p1', VistaDetalle)`; los nombres del parámetro de ruta (`id`) tienen que coincidir con los del `router`.

## 7. S3-C3 — Pruebas de `MensajeError`

`MensajeError` es el componente que **traduce un código de error en un mensaje**, y lo comparten varias vistas. Es la pieza más barata de probar y la que más daño haría si alguien la rompe. Léelo primero: ¿qué props recibe (un error, un código)?, ¿qué mensaje da para cada código?

Crea `src/componentes/__tests__/MensajeError.spec.js` con un caso por código:

```js
it.each([
  ['SIN_CONEXION', 'No se pudo conectar con el servidor'],
  ['PRODUCTO_NO_ENCONTRADO', 'Este producto no existe o fue retirado del catálogo'],
  // ['VALIDACION_FALLIDA', '...'], ['STOCK_INSUFICIENTE', '...']: completa con lo que haga el componente
])('para el código %s muestra su mensaje', (codigo, texto) => {
  const envoltura = mount(MensajeError, { props: { /* la forma real de las props */ } })
  expect(envoltura.text()).toContain(texto)
})
```

Cubre, además:

1. **Un código desconocido** no rompe nada: muestra un mensaje genérico (el contrato dice que cualquier otra cosa se trata como error inesperado).
2. La interfaz reacciona al **código**, no al texto que venga en `mensaje`: prueba que con el mismo código y un `mensaje` distinto, el texto mostrado es el mismo.

## 8. S3-C4 — Documentar cómo probar

En el `README.md` del frontend:

1. En «Cómo ejecutar», agrega `npm test   # corre las pruebas automáticas (Vitest)`.
2. Agrega una sección corta **«Cómo se prueban las vistas»** con el patrón de la sección 4 (simular `fetch`, enrutador en memoria, `flushPromises`) y la regla *«una prueba que nunca falla no prueba nada: rómpela a propósito una vez»*.
3. En «Estado», actualiza el número de pruebas.

## 9. Cómo comprobar

1. `npm test` pasa **todas**: las 12 de antes y las nuevas.
2. **Haz fallar cada prueba importante a propósito**, una vez. Por ejemplo: en `VistaListado.vue` cambia la página inicial a `0`, o en `VistaDetalle.vue` haz que el producto retirado muestre el mensaje de 404. `npm test` debe fallar justo en la prueba que protege esa regla. Después `git restore` del componente. Si no falla, la prueba no sirve.
3. `npm run build` pasa.
4. `git status` solo muestra tus pruebas, el README y, si agregaste alguno, atributos `data-testid`: **ningún** cambio de comportamiento.
5. Anota en la descripción del PR: cuántas pruebas hay (antes → ahora) y qué rompiste a propósito para comprobarlas.

## 10. Subir tu trabajo

```powershell
git status
git add src/ README.md
git commit -m "Agrega pruebas de VistaListado, VistaDetalle y MensajeError"
git pull origin main
npm run build
npm test
git push
```

- En `git status` **no debe aparecer `node_modules/` ni `dist/`**.
- Pull request con base `main`, título `S3: pruebas de las vistas de listado y detalle`, revisión de **Juan Diego**.
- En **Jira**, pasa S3-C1 a S3-C4 a *En revisión*, y a *Listo* cuando se una el PR.

## 11. Si algo falla

| Síntoma | Causa | Qué hacer |
|---|---|---|
| La prueba espera una petición que nunca ocurre | La vista llama a otra URL o usa otro cliente | Imprime `falso.mock.calls` para ver qué se pidió realmente |
| `La prueba no esperaba esta petición: ...` | Falta una ruta en `simularBackend` | Agrega el fragmento de URL que muestra el mensaje |
| La prueba pasa pero el texto aún no se ve | Falta esperar | `await flushPromises()` después de montar y después de cada clic |
| `Failed to resolve component: RouterLink` | Montaste sin enrutador | Usa `global: { plugins: [router] }` |
| `No match for ...` al montar la vista | Las rutas de prueba no coinciden con las reales | Copia el formato de `src/router/index.js` |
| Una prueba de `fetch` de una vista afecta a otra | Falta la limpieza | `vi.unstubAllGlobals()` en `afterEach` |
| El precio no coincide con el texto | `Intl` usa un espacio no separable | Usa la expresión regular, no el texto literal |
| `Cannot read properties of undefined` en la prueba | Datos de prueba incompletos | Revisa que `producto()` tenga todos los campos del contrato |
| Conflicto con un PR de Juan Diego en un `.vue` | Los dos tocaron el mismo archivo | Conserva ambos cambios (**Accept Both Changes**) |

---

_Última actualización: 2026-10-24_
