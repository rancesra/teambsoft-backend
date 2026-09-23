# Guía de F1 — Base del módulo de frontend

**Para:** Juan Diego · **Tarea:** F1 — el proyecto de Vue, el enrutador, el cliente de la API y los cinco estados compartidos · **Repositorio:** [teambsoft-frontend](https://github.com/rancesra/teambsoft-frontend) · **Rama:** `f1-base` · **Entrega:** viernes 2 de octubre

Al terminar tendrás el módulo de Catálogo corriendo: las cinco rutas del mockup, el cliente que habla con el backend, y los cinco estados (cargando, vacío, no encontrado, error de validación y sin conexión) listos para que Roger y Carlos los usen sin volver a escribirlos.

**Tu tarea bloquea a F2 y F3.** Es lo primero que hay que tener en `main` del lado del frontend.

**Todo el código de esta guía ya se probó**: compila, arranca, trae las categorías reales del backend y muestra bien el estado de "sin conexión" cuando el servicio está apagado. Si copias cada archivo tal cual, funciona.

**No tienes que esperar al backend completo.** `GET /categorias` ya está en `main`, así que puedes probar de verdad hoy mismo. Las demás pantallas las llenan F2 y F3 cuando B2 y B3 terminen.

Los comandos son para **PowerShell**, en la terminal de VS Code.

## 1. Antes de empezar

1. Necesitas **Git, Node.js LTS y VS Code** con la extensión **Vue - Official** ([guía de inicio del frontend](https://github.com/rancesra/teambsoft-frontend/blob/main/GUIA-INICIO.md)), y haber aceptado la invitación de GitHub.

2. Comprueba Node:

   ```powershell
   node --version
   npm --version
   ```

   Si `npm` responde que *la ejecución de scripts está deshabilitada en este sistema*, ejecuta esto una vez y confirma con `S`:

   ```powershell
   Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned
   ```

3. Clona el repositorio del **frontend** (no el del backend) fuera de OneDrive, y crea tu rama:

   ```powershell
   cd C:\dev
   git clone https://github.com/rancesra/teambsoft-frontend.git
   cd teambsoft-frontend
   git switch -c f1-base
   git push -u origin f1-base
   code .
   ```

4. **Abre los dos mockups antes de escribir nada.** Están en `docs/` de ese mismo repositorio: haz doble clic en `mockup-catalogo-hostapp.html` y en `propuesta-visual-catalogo.html`. En GitHub se ven como código; hay que abrirlos desde tu copia.

5. Lee la **sección 2 del [contrato](../CONTRATO-CATALOGO.md)** (qué endpoints hay) y la **sección 4** (el formato de los errores). Y del [contrato del Host App](https://github.com/rancesra/teambsoft-hostapp/blob/main/CONTRATO-HOSTAPP.md), las secciones 2, 3 y 6: rutas, la regla de oro y las variables de estilo.

## 2. Crear el proyecto de Vue

El proyecto va en la **raíz del repositorio**, igual que en el backend: `package.json` arriba del todo.

> ⚠️ **No ejecutes `npm create vue@latest .` con el punto.** Como la carpeta ya tiene el README, las guías y los mockups, la herramienta pregunta *"Current directory is not empty. Remove all existing files and continue?"*. Si respondes que sí, **borra todo eso**. Usa la secuencia de abajo, que ya está probada.

```powershell
npm create vue@latest temporal -- --router --eslint
```

Si pregunta `Ok to proceed? (y)`, responde `y`. Crea la carpeta `temporal` con Vue 3, Vue Router y los revisores de código.

Ahora pasa el contenido a la raíz sin pisar lo que ya existe:

```powershell
Remove-Item temporal\README.md, temporal\.gitignore, temporal\.gitattributes
Get-ChildItem -Path temporal -Force | Move-Item -Destination .
Remove-Item temporal
```

La primera línea borra los tres archivos que chocarían con los del repositorio. La segunda mueve todo lo demás, incluidos los que empiezan por punto. Fíjate que `Move-Item` va **sin** `-Force`: si quedara algún choque, falla en vez de sobrescribir en silencio.

Comprueba que quedó bien: debes ver `package.json`, `index.html`, `src/` y `vite.config.js` junto al `README.md` y a `docs/` que ya estaban.

```powershell
Get-ChildItem -Force
```

Borra lo que trae la plantilla y no vamos a usar:

```powershell
Remove-Item -Recurse src\components, src\views, src\assets
```

E instala:

```powershell
npm install
```

Todavía no arranques nada: `main.js` y `App.vue` importan archivos que acabas de borrar. En el paso 4 los reemplazas.

## 3. Cómo crear cada archivo

- **Archivo nuevo:** clic derecho en el explorador de VS Code → **New File…** y escribe la ruta completa, por ejemplo `src/api/cliente.js`. VS Code crea las carpetas que falten.
- **Archivo que ya existe** (`index.html`, `vite.config.js`, `src/main.js`, `src/App.vue`, `src/router/index.js`): borra todo su contenido y pega el nuevo.
- **Respeta mayúsculas y minúsculas**: los `import` se escriben exactamente así.

Así queda el repositorio al terminar:

```
teambsoft-frontend/
├── index.html
├── vite.config.js           el proxy hacia el backend
├── package.json             lo crea Vue; npm lo actualiza solo
├── docs/                    los mockups (ya estaban)
└── src/
    ├── main.js              punto de entrada
    ├── App.vue              el encabezado de cuando corre solo
    ├── estilos.css          las variables acordadas con los 3 equipos
    ├── api/
    │   └── cliente.js       única puerta hacia el backend
    ├── router/
    │   └── index.js         la tabla de rutas del módulo
    ├── componentes/         las piezas que usan todas las vistas
    │   ├── CargandoTarjetas.vue
    │   ├── EstadoVacio.vue
    │   ├── MensajeError.vue
    │   └── PaginadorProductos.vue
    └── vistas/              una por pantalla del mockup
        ├── VistaListado.vue       F2 la llena
        ├── VistaDetalle.vue       F2 la llena
        ├── VistaAdmin.vue         F3 la llena
        ├── VistaFormulario.vue    F3 la llena
        └── VistaNoEncontrada.vue
```

## 4. La configuración

### 4.1 `vite.config.js`

```js
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    // El navegador bloquea las llamadas entre orígenes distintos (esto es CORS): la página corre en
    // el 5173 y el backend en el 8080. El proxy evita el problema sin tocar el backend: el navegador
    // cree que todo sale del 5173 y Vite reenvía por detrás.
    proxy: {
      '/api/catalogo': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // Kong quita el prefijo /api/catalogo antes de llegar al servicio (strip_path).
        // Aquí se hace lo mismo, para que el código sea idéntico en desarrollo y en integración.
        rewrite: (ruta) => ruta.replace(/^\/api\/catalogo/, ''),
      },
    },
  },
})
```

Dos cosas que conviene entender, porque las van a preguntar:

- **CORS.** El navegador, por seguridad, no deja que una página servida en `localhost:5173` llame a `localhost:8080`: son orígenes distintos. La salida fácil sería abrir CORS en el backend, pero entonces habría que configurarlo también en Kong y las cabeceras se duplican, lo que el navegador rechaza. El **proxy de Vite** resuelve el problema del lado del frontend: la página pide a `/api/catalogo/...`, que es su mismo origen, y Vite reenvía la petición al backend por detrás.
- **El `rewrite` imita a Kong.** El backend expone `/productos`, sin prefijo, porque Kong le quita `/api/catalogo` antes de pasárselo (eso se llama *strip path*). Aquí se hace lo mismo, y así tu código llama siempre a `/api/catalogo/productos` — en tu computador y en integración.

El alias `@` deja escribir `@/componentes/...` en vez de contar carpetas con `../../`.

### 4.2 `index.html`

```html
<!DOCTYPE html>
<html lang="es">
  <head>
    <meta charset="UTF-8" />
    <link rel="icon" href="/favicon.ico" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Catálogo — Tienda Virtual</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.js"></script>
  </body>
</html>
```

### 4.3 `src/main.js`

```js
import { createApp } from 'vue'

import App from './App.vue'
import router from './router'
import './estilos.css'

createApp(App).use(router).mount('#app')
```

### 4.4 `src/estilos.css`

Estas son **las variables que acordaron los tres equipos** (contrato del Host App, sección 6). La regla es simple: ningún componente escribe un color a mano, todos usan `var(--...)`. Así, cuando el Host App monte los tres módulos, los tres se ven como la misma tienda.

```css
/* Las variables acordadas con los 3 equipos (contrato del Host App, sección 6). Ningún componente
   escribe un color a mano: todos usan var(--...). Así, cuando el Host App monte los tres módulos,
   los tres se ven como la misma tienda. */
:root {
  --acento: #0f766e;
  --acento-suave: #ecfdf5;
  --texto: #1c1917;
  --texto-suave: #57534e;
  --borde: #e7e5e4;
  --fondo: #fafaf9;
  --superficie: #ffffff;
  --ok: #15803d;
  --aviso: #b45309;
  --error: #b91c1c;
  --error-suave: #fef2f2;
  --radio-chico: 8px;
  --radio: 12px;
  --sombra: 0 4px 14px rgba(28, 25, 23, 0.08);
  --fuente: ui-sans-serif, system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
}

* {
  box-sizing: border-box;
}

body {
  margin: 0;
  background: var(--fondo);
  color: var(--texto);
  font-family: var(--fuente);
  line-height: 1.55;
}

.contenedor {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px 16px 64px;
}

.boton {
  border: 1px solid var(--acento);
  background: var(--acento);
  color: #fff;
  border-radius: var(--radio-chico);
  padding: 10px 18px;
  font: 600 14px var(--fuente);
  cursor: pointer;
}

.boton.secundario {
  background: var(--superficie);
  color: var(--texto-suave);
  border-color: var(--borde);
}

.boton.peligro {
  background: var(--superficie);
  color: var(--error);
  border-color: #fecaca;
}

.boton:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.campo {
  width: 100%;
  border: 1px solid var(--borde);
  background: var(--superficie);
  border-radius: var(--radio-chico);
  padding: 10px 12px;
  font: 14px var(--fuente);
  color: var(--texto);
}

.campo.malo {
  border-color: var(--error);
}

.tarjeta {
  background: var(--superficie);
  border: 1px solid var(--borde);
  border-radius: var(--radio);
  box-shadow: var(--sombra);
}
```

## 5. El cliente de la API: `src/api/cliente.js`

**Es el único archivo que usa `fetch`.** Las vistas llaman a `api.get('/productos')` y no saben nada de cabeceras ni de códigos HTTP. Traduce todo al formato de error del contrato, y agrega un código propio, `SIN_CONEXION`, para cuando no hubo respuesta.

```js
// Única puerta hacia el backend. Ninguna vista usa fetch directamente: así, cuando cambie algo del
// contrato, se cambia en un solo archivo. Traduce los errores al formato {codigo, mensaje} del
// contrato y agrega SIN_CONEXION, que no viene del backend sino de que no hubo respuesta.

const BASE = import.meta.env.VITE_API_URL ?? '/api/catalogo'

export class ErrorApi extends Error {
  constructor({ estado, codigo, mensaje }) {
    super(mensaje)
    this.estado = estado
    this.codigo = codigo
  }
}

const sinConexion = () =>
  new ErrorApi({
    estado: 0,
    codigo: 'SIN_CONEXION',
    mensaje: 'No se pudo conectar con el servidor',
  })

async function pedir(metodo, ruta, cuerpo) {
  let respuesta
  try {
    respuesta = await fetch(`${BASE}${ruta}`, {
      method: metodo,
      headers: cuerpo === undefined ? {} : { 'Content-Type': 'application/json' },
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    })
  } catch {
    // fetch solo falla así cuando no hubo respuesta: servidor apagado o sin red.
    throw sinConexion()
  }

  // 204 (DELETE) no trae cuerpo: intentar leerlo como JSON reventaría.
  if (respuesta.status === 204) return null

  const datos = await respuesta.json().catch(() => null)
  if (respuesta.ok) return datos

  // Si el backend está apagado, el proxy de Vite responde 500 sin JSON.
  if (respuesta.status >= 500 && datos === null) throw sinConexion()

  throw new ErrorApi({
    estado: respuesta.status,
    codigo: datos?.codigo ?? 'ERROR_INESPERADO',
    mensaje: datos?.mensaje ?? 'Algo salió mal. Intenta de nuevo',
  })
}

export const api = {
  get: (ruta) => pedir('GET', ruta),
  post: (ruta, cuerpo) => pedir('POST', ruta, cuerpo),
  put: (ruta, cuerpo) => pedir('PUT', ruta, cuerpo),
  borrar: (ruta) => pedir('DELETE', ruta),
}
```

Los tres detalles que hacen que esto funcione bien y que son fáciles de olvidar:

- **`fetch` no lanza error con un 404 ni con un 500.** Solo falla cuando no hubo respuesta: el servidor apagado o sin red. Por eso el `try/catch` de arriba devuelve `SIN_CONEXION` y los errores del contrato se revisan después, con `respuesta.ok`.
- **El 204 del `DELETE` no trae cuerpo.** Intentar leerlo como JSON revienta, por eso se devuelve `null` antes.
- **`VITE_API_URL`.** Vite solo expone al navegador las variables que empiezan por `VITE_`. Si no está definida, se usa `/api/catalogo`, que es lo que el proxy entiende. Cuando entre Kong de verdad, se cambia esa variable y no el código.

## 6. El enrutador: `src/router/index.js`

```js
import { createRouter, createWebHistory } from 'vue-router'

// Las rutas se declaran relativas a /catalogo porque este módulo se monta dentro del Host App bajo
// ese prefijo (contrato del Host App, sección 2). Si el prefijo cambiara, solo cambia esta constante.
const rutas = [
  { path: '/', redirect: '/catalogo' },
  {
    path: '/catalogo',
    name: 'catalogo-listado',
    component: () => import('@/vistas/VistaListado.vue'),
  },
  {
    path: '/catalogo/admin',
    name: 'catalogo-admin',
    component: () => import('@/vistas/VistaAdmin.vue'),
  },
  {
    path: '/catalogo/admin/nuevo',
    name: 'catalogo-nuevo',
    component: () => import('@/vistas/VistaFormulario.vue'),
  },
  {
    path: '/catalogo/admin/:id',
    name: 'catalogo-editar',
    component: () => import('@/vistas/VistaFormulario.vue'),
    props: true,
  },
  {
    // Va de última: /catalogo/admin es más específica y debe ganar sobre /catalogo/:id.
    path: '/catalogo/:id',
    name: 'catalogo-detalle',
    component: () => import('@/vistas/VistaDetalle.vue'),
    props: true,
  },
  { path: '/:rutaInvalida(.*)', component: () => import('@/vistas/VistaNoEncontrada.vue') },
]

export default createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: rutas,
})
```

Tres cosas:

- **El orden importa.** `/catalogo/:id` va de última porque `:id` acepta cualquier cosa, incluida la palabra `admin`. Si estuviera antes, entrar a `/catalogo/admin` abriría el detalle de un producto con id `admin`.
- **`component: () => import(...)`** carga cada vista solo cuando alguien entra en ella. Es lo que en el `npm run build` genera un archivo por vista en vez de uno gigante.
- **`props: true`** le pasa el `:id` de la URL al componente como una propiedad normal, sin que tenga que leer el enrutador.

## 7. Los cinco estados

Son cuatro componentes en `src/componentes/`. **No son adorno:** cada uno corresponde a una respuesta que el backend puede dar hoy, y F2 y F3 los van a reusar tal cual.

### 7.1 `src/componentes/CargandoTarjetas.vue`

```vue
<script setup>
// Estado "cargando" del contrato: mientras la llamada va en camino nunca se deja la pantalla vacía.
// Se dibujan cajas grises del tamaño del contenido real para que la página no salte al llegar.
defineProps({ cuantas: { type: Number, default: 8 } })
</script>

<template>
  <div class="esqueletos">
    <div v-for="n in cuantas" :key="n" class="esqueleto tarjeta"></div>
  </div>
</template>

<style scoped>
.esqueletos {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(198px, 1fr));
  gap: 18px;
}

.esqueleto {
  height: 240px;
  background: linear-gradient(90deg, #f5f5f4, #edebe9, #f5f5f4);
  background-size: 200% 100%;
  animation: brillo 1.4s infinite;
  box-shadow: none;
}

@keyframes brillo {
  from {
    background-position: 200% 0;
  }
  to {
    background-position: -200% 0;
  }
}
</style>
```

### 7.2 `src/componentes/EstadoVacio.vue`

```vue
<script setup>
// Estado "lista vacía": una categoría sin productos responde 200 con total 0, no es un error.
defineProps({
  icono: { type: String, default: '🗂️' },
  mensaje: { type: String, required: true },
})
</script>

<template>
  <div class="vacio tarjeta">
    <span class="icono">{{ icono }}</span>
    <p>{{ mensaje }}</p>
    <slot />
  </div>
</template>

<style scoped>
.vacio {
  text-align: center;
  padding: 48px 24px;
  color: var(--texto-suave);
  box-shadow: none;
}

.icono {
  font-size: 32px;
  display: block;
  margin-bottom: 10px;
  opacity: 0.55;
}

p {
  margin: 0 0 12px;
}
</style>
```

### 7.3 `src/componentes/MensajeError.vue`

```vue
<script setup>
// Muestra un ErrorApi. Decide qué decir según el CÓDIGO, nunca según el texto del mensaje: el texto
// puede cambiar sin avisar, el código es parte del contrato (sección 4).
const props = defineProps({ error: { type: Object, required: true } })
defineEmits(['reintentar'])

const TEXTOS = {
  SIN_CONEXION: 'No se pudo conectar con el servidor.',
  PRODUCTO_NO_ENCONTRADO: 'Este producto no existe o fue retirado del catálogo.',
  VALIDACION_FALLIDA: 'Hay datos incorrectos en el formulario.',
  STOCK_INSUFICIENTE: 'No hay unidades suficientes de algún producto.',
}

const texto = () => TEXTOS[props.error.codigo] ?? props.error.mensaje
</script>

<template>
  <div class="error" role="alert">
    <strong>{{ texto() }}</strong>
    <p v-if="error.codigo === 'VALIDACION_FALLIDA'">{{ error.mensaje }}</p>
    <button v-if="error.codigo === 'SIN_CONEXION'" class="boton secundario" @click="$emit('reintentar')">
      Reintentar
    </button>
  </div>
</template>

<style scoped>
.error {
  background: var(--error-suave);
  border-left: 3px solid var(--error);
  border-radius: 0 var(--radio-chico) var(--radio-chico) 0;
  padding: 14px 16px;
  color: #991b1b;
}

p {
  margin: 6px 0 0;
  font-size: 14px;
}

button {
  margin-top: 10px;
}
</style>
```

**Fíjate en `TEXTOS`:** la interfaz decide qué decir según el **código**, no según el texto que manda el backend. El código es parte del contrato y no cambia; el texto puede cambiar en cualquier momento y no debería romper la pantalla.

### 7.4 `src/componentes/PaginadorProductos.vue`

```vue
<script setup>
import { computed } from 'vue'

// La primera página es la 1, no la 0 (contrato, sección 2). Este componente es el único que hace
// esa cuenta, para que ninguna vista se equivoque.
const props = defineProps({
  pagina: { type: Number, required: true },
  total: { type: Number, required: true },
  tamanoPagina: { type: Number, required: true },
})
defineEmits(['cambiar'])

const ultimaPagina = computed(() => Math.max(1, Math.ceil(props.total / props.tamanoPagina)))
const paginas = computed(() => Array.from({ length: ultimaPagina.value }, (_, i) => i + 1))
</script>

<template>
  <nav v-if="ultimaPagina > 1" class="paginas" aria-label="Paginación">
    <button class="pagina" :disabled="pagina === 1" @click="$emit('cambiar', pagina - 1)">‹</button>
    <button
      v-for="n in paginas"
      :key="n"
      class="pagina"
      :class="{ actual: n === pagina }"
      @click="$emit('cambiar', n)"
    >
      {{ n }}
    </button>
    <button class="pagina" :disabled="pagina === ultimaPagina" @click="$emit('cambiar', pagina + 1)">
      ›
    </button>
  </nav>
</template>

<style scoped>
.paginas {
  display: flex;
  gap: 6px;
  justify-content: center;
  margin-top: 28px;
}

.pagina {
  min-width: 34px;
  height: 34px;
  border: 1px solid var(--borde);
  background: var(--superficie);
  border-radius: var(--radio-chico);
  color: var(--texto-suave);
  font: 500 13px var(--fuente);
  cursor: pointer;
}

.pagina.actual {
  background: var(--acento);
  border-color: var(--acento);
  color: #fff;
  font-weight: 700;
}

.pagina:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
```

**El total que recibe es el del filtro completo, no el de la página.** Por eso `Math.ceil(total / tamanoPagina)` da el número de páginas. Si le pasaran el tamaño de la lista que se está mostrando, siempre saldría una sola página.

## 8. `App.vue` y las vistas

### 8.1 `src/App.vue`

```vue
<script setup>
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <!--
    Este encabezado solo se ve cuando el módulo corre solo (npm run dev). Dentro del Host App, el
    encabezado y el menú los pone el cascarón y este bloque no se monta: el Host App importa las
    rutas del módulo, no este componente (contrato del Host App, sección 4).
  -->
  <header class="barra">
    <div class="contenedor barra-dentro">
      <span class="marca">Catálogo</span>
      <nav>
        <RouterLink to="/catalogo">Productos</RouterLink>
        <RouterLink to="/catalogo/admin">Administración</RouterLink>
      </nav>
      <span class="aviso">modo independiente</span>
    </div>
  </header>

  <main class="contenedor">
    <RouterView />
  </main>
</template>

<style scoped>
.barra {
  background: var(--superficie);
  border-bottom: 1px solid var(--borde);
}

.barra-dentro {
  display: flex;
  align-items: center;
  gap: 24px;
  padding-top: 14px;
  padding-bottom: 14px;
}

.marca {
  font-weight: 700;
  font-size: 16px;
}

nav {
  display: flex;
  gap: 18px;
}

nav a {
  color: var(--texto-suave);
  text-decoration: none;
  font-size: 14px;
}

nav a.router-link-active {
  color: var(--acento);
  font-weight: 600;
}

.aviso {
  margin-left: auto;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--aviso);
  border: 1px dashed var(--aviso);
  border-radius: 999px;
  padding: 2px 10px;
}
</style>
```

**La etiqueta "modo independiente" no es decoración.** El contrato del Host App exige que el módulo funcione solo; ese distintivo recuerda que lo que estás viendo es el módulo por su cuenta y que el encabezado de verdad lo pondrá el cascarón.

### 8.2 `src/vistas/VistaListado.vue`

Esta es la única vista que dejas funcionando: prueba el cliente, el enrutador y tres de los cinco estados contra el backend de verdad. **Roger la completa en F2** con la rejilla de productos.

```vue
<script setup>
import { onMounted, ref } from 'vue'

import { api } from '@/api/cliente'
import CargandoTarjetas from '@/componentes/CargandoTarjetas.vue'
import EstadoVacio from '@/componentes/EstadoVacio.vue'
import MensajeError from '@/componentes/MensajeError.vue'

// F1 deja aquí el esqueleto: la llamada, los tres estados y el filtro de categorías, que es lo
// único que se puede probar hoy porque GET /categorias ya está en main. F2 reemplaza el bloque
// marcado con la rejilla de productos (Historia 2).

const categorias = ref([])
const cargando = ref(true)
const error = ref(null)

async function cargar() {
  cargando.value = true
  error.value = null
  try {
    categorias.value = await api.get('/categorias')
  } catch (e) {
    error.value = e
  } finally {
    cargando.value = false
  }
}

onMounted(cargar)
</script>

<template>
  <h1>Catálogo</h1>

  <MensajeError v-if="error" :error="error" @reintentar="cargar" />

  <template v-else>
    <div v-if="!cargando" class="filtros">
      <button class="chip activo">Todas</button>
      <button v-for="c in categorias" :key="c.id" class="chip">{{ c.nombre }}</button>
    </div>

    <!-- F2: reemplaza este bloque por la rejilla de productos -->
    <CargandoTarjetas v-if="cargando" />
    <EstadoVacio v-else mensaje="Aquí va la rejilla de productos (tarea F2)." />
  </template>
</template>

<style scoped>
h1 {
  font-size: 24px;
  letter-spacing: -0.02em;
}

.filtros {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin: 16px 0 22px;
}

.chip {
  border: 1px solid var(--borde);
  background: var(--superficie);
  color: var(--texto-suave);
  border-radius: 999px;
  padding: 6px 15px;
  font: 500 13px var(--fuente);
  cursor: pointer;
}

.chip.activo {
  background: var(--acento);
  border-color: var(--acento);
  color: #fff;
}
</style>
```

**Ese patrón de tres variables —`cargando`, `error` y los datos— es el que van a repetir todas las vistas.** El `finally` es importante: apaga el "cargando" tanto si la llamada salió bien como si falló. Sin él, un error deja la pantalla cargando para siempre.

### 8.3 Las tres vistas que llenan F2 y F3

Son marcadores de posición, para que el enrutador no falle. Crea las tres cambiando solo el título y la nota:

`src/vistas/VistaDetalle.vue`:

```vue
<script setup>
import EstadoVacio from '@/componentes/EstadoVacio.vue'
</script>

<template>
  <h1>Detalle del producto</h1>
  <EstadoVacio icono="🚧" mensaje="Esta vista la construye la tarea F2 — Historia 3." />
</template>

<style scoped>
h1 {
  font-size: 24px;
  letter-spacing: -0.02em;
}
</style>
```

`src/vistas/VistaAdmin.vue`: igual, con el título `Administración` y el mensaje `Esta vista la construye la tarea F3 — listado interno.`

`src/vistas/VistaFormulario.vue`: igual, con el título `Crear o editar producto` y el mensaje `Esta vista la construye la tarea F3 — Historias 1, 4 y 7.`

### 8.4 `src/vistas/VistaNoEncontrada.vue`

```vue
<script setup>
import { RouterLink } from 'vue-router'

import EstadoVacio from '@/componentes/EstadoVacio.vue'
</script>

<template>
  <EstadoVacio icono="🧭" mensaje="Esta página no existe.">
    <RouterLink class="boton" to="/catalogo">Volver al catálogo</RouterLink>
  </EstadoVacio>
</template>

<style scoped>
.boton {
  text-decoration: none;
  display: inline-block;
}
</style>
```

## 9. Probarlo de verdad

Primero comprueba que compila:

```powershell
npm run build
```

Debe terminar con `✓ built in ...` y sin errores.

Ahora, **con el backend corriendo**. En otra terminal, en la carpeta del repositorio del backend:

```powershell
cd C:\dev\teambsoft-backend
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Y en la del frontend:

```powershell
npm run dev
```

Abre http://localhost:5173/catalogo. Esto es lo que debe pasar:

| Qué haces | Qué debe verse |
|---|---|
| Entras a `/catalogo` | El título, y **tres chips: ropa, hogar y electrónica** — vienen del backend, no están escritos en el código |
| Recargas con la red lenta (F12 → Network → *Slow 3G*) | Las cajas grises del estado "cargando" antes de los chips |
| **Apagas el backend** (`Ctrl + C`) y recargas | *"No se pudo conectar con el servidor"* y un botón **Reintentar** |
| Vuelves a encender el backend y pulsas Reintentar | Aparecen los chips, sin recargar la página |
| Entras a `/catalogo/admin` | La vista marcadora de F3 |
| Entras a `/catalogo/cualquier-cosa` | El detalle marcador de F2 |
| Entras a `/una-ruta-que-no-existe` | La pantalla de "esta página no existe" |

**Si los chips salen pero el título no, o al revés, mira la consola del navegador** (F12 → Console). Casi siempre es un `import` con el nombre del archivo mal escrito.

## 10. Subir tu trabajo

```powershell
git status
git add .
git commit -m "Agrega la base del modulo: enrutador, cliente de la API y los cinco estados"
git pull origin main
npm run build
git push
```

- En `git status` **no debe aparecer `node_modules/` ni `dist/`**. Si aparecen, no sigas y avisa: el `.gitignore` del repositorio debería excluirlos.
- Abre el pull request en GitHub con base `main`, título `F1: base del modulo de frontend`, y pide la revisión de Roger o Carlos — son los que van a construir encima.
- En **Jira**, pasa tu tarjeta a *En revisión*, y a *Listo* cuando se una el PR.
- Marca la casilla de F1 en el README del repositorio de frontend, en el mismo PR.
- **Avisa en el grupo apenas esté en `main`**: Roger y Carlos están esperando esto para empezar.

## 11. Si algo falla

| Síntoma | Causa | Qué hacer |
|---|---|---|
| `npm` dice que *la ejecución de scripts está deshabilitada* | Política de PowerShell | El comando `Set-ExecutionPolicy` del paso 1 |
| La herramienta pregunta si borra los archivos existentes | Se ejecutó `npm create vue@latest .` con el punto | Responde **No** y usa la secuencia del paso 2 |
| La página sale en blanco | Un error de JavaScript | F12 → **Console**. Casi siempre es un `import` mal escrito |
| `Failed to resolve import "@/..."` | Falta el alias `@` en `vite.config.js` | Sección 4.1 |
| Los chips no aparecen y en la consola sale un error de CORS | Se está llamando a `http://localhost:8080` directo en vez de `/api/catalogo` | El cliente debe usar la ruta relativa; sección 5 |
| Siempre sale "No se pudo conectar" | El backend está apagado, o Mongo no arrancó | `docker compose up -d` y `.\mvnw.cmd spring-boot:run` |
| `/catalogo/admin` abre el detalle de un producto | `/catalogo/:id` quedó antes que `/catalogo/admin` | Sección 6: el orden de las rutas |
| Vite dice `Port 5173 is in use` | Ya hay otro Vite corriendo | Usa la dirección que muestre, o cierra la otra terminal |
| `npm run build` falla por el revisor de código | Hay una regla de estilo sin cumplir | El mensaje dice archivo y línea; corrígelo |

---

_Última actualización: 2026-09-23_
