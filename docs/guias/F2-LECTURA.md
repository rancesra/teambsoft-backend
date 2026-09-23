# Guía de F2 — Vistas de lectura

**Para:** Roger · **Tareas:** F2 — Historias 2 (listado) y 3 (detalle) · **Repositorio:** [teambsoft-frontend](https://github.com/rancesra/teambsoft-frontend) · **Rama:** `f2-lectura` · **Entrega:** viernes 2 de octubre

Al terminar, el catálogo se podrá **navegar de verdad**: la rejilla de productos con filtro por categoría y paginación, y la vista de detalle de cada producto — incluidos los que fueron retirados, que se ven distinto pero se ven.

**Todo el código de esta guía ya se probó** contra el backend real, con 23 productos en tres categorías: la rejilla, el filtro, la página 2, el detalle, un producto retirado y un id que no existe. Si copias cada archivo tal cual, funciona.

**Dependes de dos cosas:**

1. **F1 tiene que estar en `main`** (la base: enrutador, cliente y los cinco estados). Sin eso no arranca nada.
2. **B3 tiene que estar en `main`** para ver productos de verdad. Mientras tanto puedes construir todo igual: la sección 7 te dice cómo llenar el catálogo con productos de prueba en un minuto.

Los comandos son para **PowerShell**, en la terminal de VS Code.

## 1. Antes de empezar

1. Ten instalado lo de la [guía de inicio del frontend](https://github.com/rancesra/teambsoft-frontend/blob/main/GUIA-INICIO.md) y acepta la invitación de GitHub.
2. Trae la base de F1 y crea tu rama:

   ```powershell
   cd C:\dev\teambsoft-frontend
   git switch main
   git pull
   npm install
   git switch -c f2-lectura
   git push -u origin f2-lectura
   ```

   `npm install` después del `git pull` es necesario porque F1 agregó dependencias nuevas.

3. **Abre los dos mockups** de `docs/`: `mockup-catalogo-hostapp.html` (qué lleva cada pantalla y por qué) y `propuesta-visual-catalogo.html` (cómo se ve). Tus dos vistas son las secciones 2 y 3 de los dos documentos.

4. Lee del [contrato](../CONTRATO-CATALOGO.md) la sección 2 (`GET /productos` y `GET /productos/{id}`) y la 3 (el modelo de Producto).

**Las tres reglas del contrato que tu tarea tiene que cumplir**, y que son justo las que se olvidan:

- **La primera página es la 1**, no la 0. El componente `PaginadorProductos` que dejó F1 ya lo tiene en cuenta.
- **El producto trae el `id` de su categoría** (`cat-ropa`), no el nombre. Para mostrar "ropa" hay que cruzarlo con `GET /categorias`.
- **El detalle de un producto retirado responde 200 con `activo: false`**, no 404. Son dos pantallas distintas: "no disponible" y "no encontrado".

## 2. Qué archivos vas a tocar

```
src/
├── utilidades/
│   └── formato.js              NUEVO  precios en pesos y nombre de categoría
├── componentes/
│   └── TarjetaProducto.vue     NUEVO  una tarjeta de la rejilla
└── vistas/
    ├── VistaListado.vue        F1 la dejó marcada: la reemplazas entera
    └── VistaDetalle.vue        lo mismo
```

Los componentes de estados (`CargandoTarjetas`, `EstadoVacio`, `MensajeError`, `PaginadorProductos`) ya los dejó F1: **no los reescribas, úsalos**.

## 3. `src/utilidades/formato.js`

Dos funciones que usan tus dos vistas y también las de Carlos (F3). Van en un archivo aparte para que el precio se vea igual en todas partes.

```js
// Formatos que usan varias vistas. Están aquí y no repetidos en cada componente para que el precio
// se vea igual en el listado, en el detalle y en la administración.

const PESOS = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  maximumFractionDigits: 0,
})

/** 49900 -> "$ 49.900". El backend manda el precio como número, no como texto ya formateado. */
export function enPesos(valor) {
  return PESOS.format(valor)
}

/**
 * El producto trae el id de su categoría ("cat-ropa"), no el nombre. Esta función cruza ese id con
 * la lista de GET /categorias. Si no lo encuentra, devuelve el id: es mejor mostrar algo raro que
 * dejar el espacio vacío.
 */
export function nombreDeCategoria(id, categorias) {
  return categorias.find((c) => c.id === id)?.nombre ?? id
}
```

`Intl.NumberFormat` viene en el navegador, no hay que instalar nada. Con `es-CO` pone el punto de miles como se usa en Colombia y `maximumFractionDigits: 0` quita los centavos, que en pesos no se usan.

## 4. `src/componentes/TarjetaProducto.vue`

Cada producto de la rejilla. Es un enlace completo: toda la tarjeta lleva al detalle, no solo el nombre.

```vue
<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import { enPesos, nombreDeCategoria } from '@/utilidades/formato'

const props = defineProps({
  producto: { type: Object, required: true },
  categorias: { type: Array, default: () => [] },
})

// El stock es un número del contrato; estas tres variantes son decisión nuestra de interfaz.
const estadoStock = computed(() => {
  if (props.producto.stock === 0) return { clase: 'sin', texto: 'Sin stock' }
  if (props.producto.stock <= 10) return { clase: 'poco', texto: `Quedan ${props.producto.stock}` }
  return { clase: 'hay', texto: `${props.producto.stock} disponibles` }
})
</script>

<template>
  <RouterLink class="producto tarjeta" :to="`/catalogo/${producto.id}`">
    <div class="foto">
      <img v-if="producto.imagenes?.length" :src="producto.imagenes[0]" :alt="producto.nombre" />
      <span v-else class="sin-foto">sin imagen</span>
    </div>

    <div class="cuerpo">
      <span class="categoria">{{ nombreDeCategoria(producto.categoria, categorias) }}</span>
      <h3>{{ producto.nombre }}</h3>
      <p class="precio">{{ enPesos(producto.precio) }}</p>
      <span class="stock" :class="estadoStock.clase">{{ estadoStock.texto }}</span>
    </div>
  </RouterLink>
</template>

<style scoped>
.producto {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  text-decoration: none;
  color: inherit;
}

.foto {
  aspect-ratio: 4 / 3;
  background: #f0efed;
  display: grid;
  place-items: center;
}

.foto img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.sin-foto {
  font-size: 11px;
  color: #a8a29e;
  letter-spacing: 0.06em;
}

.cuerpo {
  padding: 13px 14px 15px;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.categoria {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--texto-suave);
  font-weight: 600;
}

h3 {
  font-size: 14px;
  font-weight: 600;
  margin: 4px 0 auto;
  line-height: 1.35;
}

.precio {
  font-size: 19px;
  font-weight: 700;
  margin: 11px 0 0;
  font-variant-numeric: tabular-nums;
}

.stock {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 9px;
  border-radius: 999px;
  margin-top: 7px;
  width: fit-content;
}

.stock.hay {
  background: var(--acento-suave);
  color: var(--ok);
}

.stock.poco {
  background: #fffbeb;
  color: var(--aviso);
}

.stock.sin {
  background: #f5f5f4;
  color: var(--texto-suave);
}
</style>
```

Tres detalles que parecen pequeños:

- **`producto.imagenes?.length`** — el `?.` evita que reviente si `imagenes` no viene. En el contrato es opcional, así que la mitad de los productos de prueba no la traen.
- **`margin: 4px 0 auto`** en el nombre — ese `auto` empuja el precio hacia abajo. Así, con nombres de una o de tres líneas, **todos los precios quedan alineados**. Sin eso la rejilla se ve desordenada.
- **Las tres variantes de stock son decisión nuestra**, no del contrato: el contrato solo manda el número. Un producto sin stock sí aparece en el listado, porque sigue activo.

## 5. `src/vistas/VistaListado.vue` — Historia 2

Reemplaza entera la que dejó F1.

```vue
<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { api } from '@/api/cliente'
import CargandoTarjetas from '@/componentes/CargandoTarjetas.vue'
import EstadoVacio from '@/componentes/EstadoVacio.vue'
import MensajeError from '@/componentes/MensajeError.vue'
import PaginadorProductos from '@/componentes/PaginadorProductos.vue'
import TarjetaProducto from '@/componentes/TarjetaProducto.vue'

const TAMANO_PAGINA = 20

const route = useRoute()
const router = useRouter()

const productos = ref([])
const categorias = ref([])
const total = ref(0)
const cargando = ref(true)
const error = ref(null)

// El filtro y la página viven en la URL, no en una variable suelta: así, recargar o compartir el
// enlace conserva lo que el usuario estaba viendo, en vez de devolverlo a la primera página.
const categoria = computed(() => route.query.categoria ?? null)
const pagina = computed(() => Number(route.query.pagina ?? 1))

function irA(cambios) {
  router.push({ query: { ...route.query, ...cambios } })
}

function filtrarPor(idCategoria) {
  // Al cambiar de categoría se vuelve a la página 1: quedarse en la 3 de un filtro nuevo casi
  // siempre muestra una lista vacía y parece un error.
  irA({ categoria: idCategoria ?? undefined, pagina: undefined })
}

async function cargar() {
  cargando.value = true
  error.value = null
  try {
    // Las categorías solo se piden una vez: son fijas y se usan para traducir el id a nombre.
    if (categorias.value.length === 0) {
      categorias.value = await api.get('/categorias')
    }

    const parametros = new URLSearchParams({ pagina: pagina.value, tamanoPagina: TAMANO_PAGINA })
    if (categoria.value) parametros.set('categoria', categoria.value)

    const respuesta = await api.get(`/productos?${parametros}`)
    productos.value = respuesta.productos
    total.value = respuesta.total
  } catch (e) {
    error.value = e
  } finally {
    cargando.value = false
  }
}

// immediate: true hace la primera carga; después se repite cada vez que cambia la URL.
watch([categoria, pagina], cargar, { immediate: true })
</script>

<template>
  <header class="titulo">
    <h1>Catálogo</h1>
    <span v-if="!cargando && !error">{{ total }} productos · página {{ pagina }}</span>
  </header>

  <div class="filtros">
    <button class="chip" :class="{ activo: !categoria }" @click="filtrarPor(null)">Todas</button>
    <button
      v-for="c in categorias"
      :key="c.id"
      class="chip"
      :class="{ activo: categoria === c.id }"
      @click="filtrarPor(c.id)"
    >
      {{ c.nombre }}
    </button>
  </div>

  <MensajeError v-if="error" :error="error" @reintentar="cargar" />

  <CargandoTarjetas v-else-if="cargando" />

  <EstadoVacio
    v-else-if="productos.length === 0"
    mensaje="No hay productos en esta categoría."
  >
    <button v-if="categoria" class="boton secundario" @click="filtrarPor(null)">
      Ver todo el catálogo
    </button>
  </EstadoVacio>

  <template v-else>
    <div class="rejilla">
      <TarjetaProducto
        v-for="p in productos"
        :key="p.id"
        :producto="p"
        :categorias="categorias"
      />
    </div>

    <PaginadorProductos
      :pagina="pagina"
      :total="total"
      :tamano-pagina="TAMANO_PAGINA"
      @cambiar="(n) => irA({ pagina: n })"
    />
  </template>
</template>

<style scoped>
.titulo {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
}

h1 {
  font-size: 24px;
  letter-spacing: -0.02em;
  margin: 0;
}

.titulo span {
  color: var(--texto-suave);
  font-size: 13px;
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

.rejilla {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(198px, 1fr));
  gap: 18px;
}
</style>
```

Hay una decisión de diseño importante aquí y conviene que la entiendas, porque es lo que van a preguntar en la sustentación:

**El filtro y la página viven en la URL, no en una variable de Vue.** Fíjate que `categoria` y `pagina` son `computed` que leen `route.query`, y que para cambiarlos se hace `router.push`. Después, un `watch` sobre esos dos valores vuelve a cargar.

Lo que se gana: si el usuario está en `/catalogo?categoria=cat-ropa&pagina=2` y recarga, sigue ahí. Si le manda ese enlace a alguien, ve lo mismo. Y el botón "atrás" del navegador funciona. Con una variable normal, cualquiera de esas tres cosas lo devuelve a la página 1 sin filtro.

Y **al cambiar de categoría se vuelve a la página 1** (por eso `pagina: undefined` en `filtrarPor`). Si alguien está en la página 3 de "ropa" y pasa a "hogar", que solo tiene 8 productos, la página 3 está vacía y parece un error.

## 6. `src/vistas/VistaDetalle.vue` — Historia 3

```vue
<script setup>
import { ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { api } from '@/api/cliente'
import MensajeError from '@/componentes/MensajeError.vue'
import { enPesos, nombreDeCategoria } from '@/utilidades/formato'

const props = defineProps({ id: { type: String, required: true } })

const producto = ref(null)
const categorias = ref([])
const cargando = ref(true)
const error = ref(null)
const imagenActual = ref(0)

async function cargar() {
  cargando.value = true
  error.value = null
  imagenActual.value = 0
  try {
    if (categorias.value.length === 0) {
      categorias.value = await api.get('/categorias')
    }
    producto.value = await api.get(`/productos/${props.id}`)
  } catch (e) {
    error.value = e
    producto.value = null
  } finally {
    cargando.value = false
  }
}

watch(() => props.id, cargar, { immediate: true })
</script>

<template>
  <p class="miga">
    <RouterLink to="/catalogo">Catálogo</RouterLink>
    <template v-if="producto"> · {{ nombreDeCategoria(producto.categoria, categorias) }}</template>
  </p>

  <MensajeError v-if="error" :error="error" @reintentar="cargar" />

  <div v-else-if="cargando" class="esqueleto-detalle">
    <div class="caja"></div>
    <div>
      <div class="linea ancha"></div>
      <div class="linea"></div>
      <div class="linea corta"></div>
    </div>
  </div>

  <article v-else-if="producto" class="detalle">
    <div>
      <div class="foto-grande" :class="{ retirado: !producto.activo }">
        <img
          v-if="producto.imagenes?.length"
          :src="producto.imagenes[imagenActual]"
          :alt="producto.nombre"
        />
        <span v-else class="sin-foto">sin imagen</span>
      </div>

      <!-- imagenes es una lista en el contrato, no una sola URL -->
      <div v-if="producto.imagenes?.length > 1" class="miniaturas">
        <button
          v-for="(url, i) in producto.imagenes"
          :key="url"
          class="miniatura"
          :class="{ actual: i === imagenActual }"
          @click="imagenActual = i"
        >
          <img :src="url" :alt="`Imagen ${i + 1}`" />
        </button>
      </div>
    </div>

    <div>
      <!--
        Un producto desactivado responde 200 con activo:false, no 404 (contrato §2). Por eso esta
        vista lo muestra en gris y sin llamada a la acción, en vez de fingir que nunca existió.
      -->
      <div v-if="!producto.activo" class="retirado-aviso">
        <strong>Producto no disponible.</strong> Este producto fue retirado del catálogo.
      </div>

      <h1 :class="{ apagado: !producto.activo }">{{ producto.nombre }}</h1>

      <span v-if="producto.activo" class="stock" :class="producto.stock === 0 ? 'sin' : 'hay'">
        {{ producto.stock === 0 ? 'Sin stock' : `${producto.stock} disponibles` }}
      </span>

      <p class="precio" :class="{ apagado: !producto.activo }">{{ enPesos(producto.precio) }}</p>

      <p v-if="producto.descripcion" class="descripcion">{{ producto.descripcion }}</p>

      <!--
        Espacio reservado para el módulo de Carro (Equipo C). No lo construimos nosotros: falta
        acordar si el botón lo pinta Catálogo o lo monta aquí el Host App.
      -->
      <div v-if="producto.activo && producto.stock > 0" class="espacio-carro">
        Espacio reservado · Carro (Equipo C)
      </div>
    </div>
  </article>
</template>

<style scoped>
.miga {
  font-size: 13px;
  color: var(--texto-suave);
  margin: 0 0 14px;
}

.miga a {
  color: var(--acento);
  text-decoration: none;
}

.detalle {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 36px;
}

.foto-grande {
  aspect-ratio: 1;
  background: #f0efed;
  border: 1px solid var(--borde);
  border-radius: var(--radio);
  display: grid;
  place-items: center;
  overflow: hidden;
}

.foto-grande.retirado {
  filter: grayscale(1);
  opacity: 0.65;
}

.foto-grande img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.sin-foto {
  font-size: 12px;
  color: #a8a29e;
}

.miniaturas {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}

.miniatura {
  width: 62px;
  height: 62px;
  border: 1px solid var(--borde);
  border-radius: var(--radio-chico);
  overflow: hidden;
  padding: 0;
  background: none;
  cursor: pointer;
}

.miniatura.actual {
  border: 2px solid var(--acento);
}

.miniatura img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.retirado-aviso {
  background: #fdf4e6;
  border: 1px solid #ebd3a6;
  color: var(--aviso);
  border-radius: var(--radio-chico);
  padding: 10px 13px;
  font-size: 14px;
  margin-bottom: 14px;
}

h1 {
  font-size: 27px;
  letter-spacing: -0.025em;
  margin: 0 0 8px;
  line-height: 1.2;
}

h1.apagado,
.precio.apagado {
  color: var(--texto-suave);
}

.stock {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: 999px;
}

.stock.hay {
  background: var(--acento-suave);
  color: var(--ok);
}

.stock.sin {
  background: #f5f5f4;
  color: var(--texto-suave);
}

.precio {
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -0.03em;
  margin: 12px 0 0;
  font-variant-numeric: tabular-nums;
}

.descripcion {
  color: var(--texto-suave);
  font-size: 14px;
  border-top: 1px solid var(--borde);
  padding-top: 16px;
  margin-top: 16px;
}

.espacio-carro {
  margin-top: 20px;
  border: 2px dashed var(--aviso);
  border-radius: var(--radio-chico);
  padding: 16px;
  color: var(--aviso);
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  text-align: center;
}

.esqueleto-detalle {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 36px;
}

.caja {
  aspect-ratio: 1;
  background: #f0efed;
  border-radius: var(--radio);
}

.linea {
  height: 16px;
  background: #f0efed;
  border-radius: 999px;
  margin-bottom: 12px;
}

.linea.ancha {
  height: 28px;
}

.linea.corta {
  width: 55%;
}

@media (max-width: 760px) {
  .detalle,
  .esqueleto-detalle {
    grid-template-columns: 1fr;
    gap: 22px;
  }
}
</style>
```

**La parte que te van a preguntar en la sustentación es el producto retirado.** Fíjate que no hay ningún `if` que redirija ni que muestre un 404: el producto se muestra, con un aviso amarillo, el título y el precio en gris, la foto en blanco y negro, y **sin el espacio del Carro**.

¿Por qué no un 404? Porque el contrato dice que el detalle responde 200 con `activo: false`, y eso existe por el equipo C: si alguien tiene un producto en el carrito y lo retiran, Carro necesita poder consultarlo para marcarlo como no disponible. Con un 404 no podría distinguir "lo retiraron" de "ese id nunca existió".

El 404 de verdad —un id inventado— lo maneja solo `MensajeError`, que traduce el código `PRODUCTO_NO_ENCONTRADO` a *"Este producto no existe o fue retirado del catálogo"*.

## 7. Probarlo de verdad

Necesitas el backend corriendo y productos en la base.

**Terminal 1** — el backend:

```powershell
cd C:\dev\teambsoft-backend
docker compose up -d
.\mvnw.cmd spring-boot:run
```

**Si B2 ya está en `main`**, llena el catálogo con la extensión **REST Client** de VS Code y un archivo `pruebas.http` (no lo subas). Repite el bloque cambiando nombre, categoría, precio y stock hasta tener unos 25:

```http
### Crear un producto
POST http://localhost:8080/productos
Content-Type: application/json

{"nombre":"Camiseta basica algodon","descripcion":"Unisex 100% algodon.",
 "precio":49900,"categoria":"cat-ropa","stock":120,"imagenes":[]}
```

**Si B2 todavía no está**, insértalos directo en Mongo:

```powershell
docker exec -it catalogo-mongo mongosh catalogo --eval "for (let i = 1; i <= 25; i++) { db.productos.insertOne({ nombre: 'Producto ' + i, descripcion: 'Descripcion de prueba', precio: NumberDecimal(10000 * i), categoria: ['cat-ropa','cat-hogar','cat-electronica'][i % 3], stock: i, imagenes: [], activo: true, _class: 'co.edu.uis.catalogo.model.Producto' }) }"
```

`NumberDecimal` es obligatorio: el precio se guarda como Decimal128 y si lo insertas como número normal, el backend no lo puede leer.

**Terminal 2** — el frontend:

```powershell
cd C:\dev\teambsoft-frontend
npm run dev
```

Abre http://localhost:5173/catalogo y comprueba esto, punto por punto:

| Qué haces | Qué debe pasar |
|---|---|
| Entras al catálogo | La rejilla con los productos, el conteo y los chips de categoría |
| Haces clic en "hogar" | Solo los de hogar, el chip se pone verde y **la URL cambia** a `?categoria=cat-hogar` |
| **Recargas la página** | Sigue filtrado por hogar — esto es lo que prueba que el filtro está en la URL |
| Pulsas "2" en el paginador | Los siguientes productos, y la URL dice `?pagina=2` |
| Pulsas "atrás" en el navegador | Vuelve a la página 1 |
| Filtras por una categoría vacía | *"No hay productos en esta categoría"* y un botón para ver todo — **no un error** |
| Haces clic en una tarjeta | El detalle, con la ruta `/catalogo/<id>` |
| Entras a `/catalogo/noexiste123` | *"Este producto no existe o fue retirado del catálogo"* |
| Apagas el backend y recargas | *"No se pudo conectar con el servidor"* y el botón Reintentar |

Y el caso importante, **el producto retirado**. Desactiva uno (pídele el `DELETE` a Jhon, o hazlo directo en Mongo) y entra a su detalle:

```powershell
docker exec -it catalogo-mongo mongosh catalogo --eval "db.productos.updateOne({}, { \$set: { activo: false } })"
```

Debe verse: el aviso amarillo de *"Producto no disponible"*, el título y el precio en gris, la imagen en blanco y negro y **sin el recuadro del Carro**. Y ese producto **no debe aparecer** en el listado.

Antes de subir, comprueba que compila:

```powershell
npm run build
```

## 8. Subir tu trabajo

```powershell
git status
git add .
git commit -m "Agrega el listado con filtros y paginacion y la vista de detalle"
git pull origin main
npm run build
git push
```

- En `git status` **no debe aparecer `node_modules/` ni `dist/`**.
- Pull request con base `main`, título `F2: listado y detalle de productos`, y pide la revisión de Juan Diego o Carlos.
- En **Jira**, pasa tus tarjetas de las Historias 2 y 3 a *En revisión*, y a *Listo* cuando se una el PR.
- Marca las casillas de F2 en el README del repositorio de frontend.

## 9. Si algo falla

| Síntoma | Causa | Qué hacer |
|---|---|---|
| Las tarjetas muestran `cat-ropa` en vez de `ropa` | No se está pasando `:categorias` a `TarjetaProducto` | Sección 5, en el `v-for` |
| El precio sale `49900` sin formato | Se usó `producto.precio` directo en vez de `enPesos(...)` | Sección 3 |
| Al recargar se pierde el filtro | El filtro quedó en un `ref` en vez de en la URL | Sección 5 |
| El paginador no aparece | Hay 20 productos o menos: con una sola página se oculta a propósito | Crea más productos |
| La página 2 muestra los mismos de la 1 | El `watch` no está observando `pagina` | Sección 5, la última línea del `script` |
| Un producto retirado da 404 | Se agregó un `if` que lo trata como error | Quítalo: el contrato dice 200 con `activo:false` |
| El detalle no cambia al pasar de un producto a otro | Falta el `watch` sobre `props.id` | Sección 6 |
| Los precios de las tarjetas quedan a distinta altura | Falta `margin: 4px 0 auto` en el `h3` | Sección 4 |
| `Failed to resolve import "@/utilidades/formato"` | El archivo quedó en otra carpeta o con otro nombre | Revisa mayúsculas y la ruta |

---

_Última actualización: 2026-09-23_
