# Guía de F3 — Vistas de administración

**Para:** Carlos · **Tareas:** F3 — Historias 1 (crear), 4 (editar), 5 (desactivar), 7 (reactivar) y 8 (ver los retirados) · **Repositorio:** [teambsoft-frontend](https://github.com/rancesra/teambsoft-frontend) · **Rama:** `f3-admin` · **Entrega:** viernes 2 de octubre

Al terminar, el catálogo se podrá **mantener**: una tabla interna con todos los productos, el formulario para crear y editar, y las acciones de retirar y reactivar con su confirmación. Son cinco historias, más que ninguna otra tarea del frontend.

**Todo el código de esta guía ya se probó** contra el backend real: creé un producto desde el formulario, retiré uno, lo reactivé, edité uno retirado y comprobé que las validaciones marcan el campo que falló. Si copias cada archivo tal cual, funciona.

**Dependes de:**

1. **F1 en `main`** — la base: enrutador, cliente y los cinco estados.
2. **F2 en `main`** — usas su `src/utilidades/formato.js`. Si F2 todavía no entró, créalo tú con el contenido de la sección 3 de [su guía](F2-LECTURA.md) y bórralo cuando se una.
3. **B2 y B3 en `main`** para probar de verdad, porque tu tarea escribe: crea, edita, retira y reactiva.

Los comandos son para **PowerShell**, en la terminal de VS Code.

## 1. Antes de empezar

1. Ten instalado lo de la [guía de inicio del frontend](https://github.com/rancesra/teambsoft-frontend/blob/main/GUIA-INICIO.md) y acepta la invitación de GitHub.

2. Trae lo último y crea tu rama:

   ```powershell
   cd C:\dev\teambsoft-frontend
   git switch main
   git pull
   npm install
   git switch -c f3-admin
   git push -u origin f3-admin
   ```

3. **Abre el mockup** `docs/mockup-catalogo-hostapp.html` en el navegador: tus pantallas son toda la sección 3 (listado de administración, confirmación, formulario y mensajes de éxito).

4. Del [contrato](../CONTRATO-CATALOGO.md) lee la sección 2 completa y la 3 (las reglas de validación). Tu tarea es la que más las toca.

**Las cuatro reglas del contrato que tienes que cumplir**, y por qué existen:

- **`PUT` reemplaza el producto completo**, no campo por campo. El formulario carga todos los datos y los envía todos. Si omites la descripción, **se borra**.
- **Editar no cambia el estado.** Guardar un producto retirado lo deja retirado (Historia 4). Para volver a publicarlo está `POST /{id}/activar`.
- **Las validaciones del navegador son solo para avisar rápido. La que manda es la del backend.** Por eso hay que mostrar el mensaje que él devuelve.
- **`DELETE` y `activar` son idempotentes**: repetirlos responde bien y no cambia nada.

## 2. Qué archivos vas a tocar

```
src/
├── componentes/
│   ├── DialogoConfirmar.vue   NUEVO  la confirmación antes de retirar o reactivar
│   └── AvisoExito.vue         NUEVO  el mensaje de "salió bien", que se va solo
└── vistas/
    ├── VistaAdmin.vue         F1 la dejó marcada: la reemplazas entera
    └── VistaFormulario.vue    lo mismo
```

## 3. `src/componentes/DialogoConfirmar.vue`

Retirar un producto lo saca del catálogo público. Eso no puede pasar con un clic distraído.

```vue
<script setup>
// Diálogo de confirmación para las acciones que cambian el catálogo público. Usa <dialog>, que es
// del navegador: atrapa el foco y cierra con Escape sin que haya que programarlo.
import { onMounted, ref } from 'vue'

defineProps({
  titulo: { type: String, required: true },
  mensaje: { type: String, default: '' },
  textoConfirmar: { type: String, default: 'Confirmar' },
  peligroso: { type: Boolean, default: false },
})
const emit = defineEmits(['confirmar', 'cancelar'])

const dialogo = ref(null)
onMounted(() => dialogo.value.showModal())
</script>

<template>
  <dialog ref="dialogo" class="dialogo" @cancel.prevent="emit('cancelar')">
    <h2>{{ titulo }}</h2>
    <p v-if="mensaje">{{ mensaje }}</p>
    <div class="acciones">
      <button class="boton secundario" @click="emit('cancelar')">Cancelar</button>
      <button class="boton" :class="{ peligro: peligroso }" @click="emit('confirmar')">
        {{ textoConfirmar }}
      </button>
    </div>
  </dialog>
</template>

<style scoped>
.dialogo {
  border: 1px solid var(--borde);
  border-radius: var(--radio);
  box-shadow: 0 14px 36px rgba(28, 25, 23, 0.16);
  padding: 22px;
  max-width: 400px;
  color: var(--texto);
}

.dialogo::backdrop {
  background: rgba(28, 25, 23, 0.35);
}

h2 {
  font-size: 16px;
  margin: 0 0 8px;
}

p {
  font-size: 14px;
  color: var(--texto-suave);
  margin: 0;
}

.acciones {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
```

**Usa `<dialog>`, la etiqueta del navegador, no un `<div>` con posición fija.** Con `showModal()` se gana gratis: el fondo oscuro (`::backdrop`), que se cierre con `Escape`, y que el foco del teclado quede atrapado adentro. Hacer todo eso a mano son cincuenta líneas y casi siempre queda mal para quien navega con teclado.

El `@cancel.prevent` es para que `Escape` avise a la vista en vez de cerrar el diálogo por su cuenta y dejar a Vue creyendo que sigue abierto.

## 4. `src/componentes/AvisoExito.vue`

```vue
<script setup>
// Avisa que una operación salió bien y se va sola. Los errores se quedan; los éxitos no, porque
// el usuario ya ve el resultado en la pantalla.
import { onMounted, onUnmounted, ref } from 'vue'

defineProps({ mensaje: { type: String, required: true } })
const emit = defineEmits(['cerrar'])

const visible = ref(true)
let reloj

onMounted(() => {
  reloj = setTimeout(() => {
    visible.value = false
    emit('cerrar')
  }, 4000)
})

onUnmounted(() => clearTimeout(reloj))
</script>

<template>
  <div v-if="visible" class="exito" role="status">
    <strong>✓</strong>
    <span>{{ mensaje }}</span>
  </div>
</template>

<style scoped>
.exito {
  display: flex;
  gap: 10px;
  align-items: center;
  background: var(--acento-suave);
  border-left: 3px solid var(--ok);
  border-radius: 0 var(--radio-chico) var(--radio-chico) 0;
  padding: 12px 15px;
  color: #166534;
  font-size: 14px;
  margin-bottom: 18px;
}
</style>
```

**El `onUnmounted` con `clearTimeout` no es opcional.** Si el usuario se va de la pantalla antes de los 4 segundos, ese temporizador seguiría corriendo y trataría de cambiar algo de un componente que ya no existe. Es una fuga de memoria pequeña pero real, y en Vue se limpia así.

`role="status"` hace que un lector de pantalla anuncie el mensaje sin interrumpir lo que el usuario esté haciendo.

## 5. `src/vistas/VistaAdmin.vue` — Historias 5, 7 y 8

Reemplaza entera la que dejó F1.

```vue
<script setup>
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { api } from '@/api/cliente'
import AvisoExito from '@/componentes/AvisoExito.vue'
import DialogoConfirmar from '@/componentes/DialogoConfirmar.vue'
import EstadoVacio from '@/componentes/EstadoVacio.vue'
import MensajeError from '@/componentes/MensajeError.vue'
import PaginadorProductos from '@/componentes/PaginadorProductos.vue'
import { enPesos, nombreDeCategoria } from '@/utilidades/formato'

const TAMANO_PAGINA = 20

const route = useRoute()
const router = useRouter()

const productos = ref([])
const categorias = ref([])
const total = ref(0)
const cargando = ref(true)
const error = ref(null)
const exito = ref(null)
const porConfirmar = ref(null)

// activo=true (activos), false (retirados) o todos. Va en la URL, igual que en el listado público.
const filtroActivo = computed(() => route.query.activo ?? 'true')
const pagina = computed(() => Number(route.query.pagina ?? 1))

function irA(cambios) {
  router.push({ query: { ...route.query, ...cambios } })
}

async function cargar() {
  cargando.value = true
  error.value = null
  try {
    if (categorias.value.length === 0) {
      categorias.value = await api.get('/categorias')
    }
    const parametros = new URLSearchParams({
      pagina: pagina.value,
      tamanoPagina: TAMANO_PAGINA,
      activo: filtroActivo.value,
    })
    const respuesta = await api.get(`/productos?${parametros}`)
    productos.value = respuesta.productos
    total.value = respuesta.total
  } catch (e) {
    error.value = e
  } finally {
    cargando.value = false
  }
}

function pedirConfirmacion(producto) {
  porConfirmar.value = producto
}

async function ejecutarAccion() {
  const producto = porConfirmar.value
  porConfirmar.value = null
  try {
    if (producto.activo) {
      await api.borrar(`/productos/${producto.id}`)
      exito.value = `"${producto.nombre}" fue retirado del catálogo.`
    } else {
      await api.post(`/productos/${producto.id}/activar`)
      exito.value = `"${producto.nombre}" volvió al catálogo.`
    }
    await cargar()
  } catch (e) {
    error.value = e
  }
}

watch([filtroActivo, pagina], cargar, { immediate: true })
</script>

<template>
  <header class="titulo">
    <h1>Productos</h1>
    <span v-if="!cargando && !error">{{ total }} en total</span>
    <RouterLink class="boton" to="/catalogo/admin/nuevo">+ Nuevo producto</RouterLink>
  </header>

  <AvisoExito v-if="exito" :mensaje="exito" @cerrar="exito = null" />

  <div class="filtros">
    <button class="chip" :class="{ activo: filtroActivo === 'true' }" @click="irA({ activo: undefined, pagina: undefined })">
      Activos
    </button>
    <button class="chip" :class="{ activo: filtroActivo === 'false' }" @click="irA({ activo: 'false', pagina: undefined })">
      Retirados
    </button>
    <button class="chip" :class="{ activo: filtroActivo === 'todos' }" @click="irA({ activo: 'todos', pagina: undefined })">
      Todos
    </button>
  </div>

  <MensajeError v-if="error" :error="error" @reintentar="cargar" />

  <p v-else-if="cargando" class="cargando">Cargando…</p>

  <EstadoVacio v-else-if="productos.length === 0" mensaje="No hay productos con este filtro." />

  <template v-else>
    <table class="tabla tarjeta">
      <thead>
        <tr>
          <th>Producto</th>
          <th>Precio</th>
          <th>Stock</th>
          <th>Estado</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="p in productos" :key="p.id">
          <td>
            <strong>{{ p.nombre }}</strong>
            <span class="categoria">{{ nombreDeCategoria(p.categoria, categorias) }}</span>
          </td>
          <td class="numero">{{ enPesos(p.precio) }}</td>
          <td class="numero">{{ p.stock }}</td>
          <td>
            <span class="estado" :class="p.activo ? 'act' : 'ina'">
              {{ p.activo ? 'Activo' : 'Retirado' }}
            </span>
          </td>
          <td class="acciones">
            <RouterLink class="accion" :to="`/catalogo/admin/${p.id}`">Editar</RouterLink>
            <button class="accion" :class="{ peligro: p.activo }" @click="pedirConfirmacion(p)">
              {{ p.activo ? 'Desactivar' : 'Reactivar' }}
            </button>
          </td>
        </tr>
      </tbody>
    </table>

    <PaginadorProductos
      :pagina="pagina"
      :total="total"
      :tamano-pagina="TAMANO_PAGINA"
      @cambiar="(n) => irA({ pagina: n })"
    />
  </template>

  <DialogoConfirmar
    v-if="porConfirmar"
    :titulo="porConfirmar.activo ? `¿Retirar &quot;${porConfirmar.nombre}&quot;?` : `¿Reactivar &quot;${porConfirmar.nombre}&quot;?`"
    :mensaje="porConfirmar.activo
      ? 'Dejará de aparecer en el catálogo público. Podrás reactivarlo después desde el filtro Retirados.'
      : 'Volverá a aparecer en el catálogo público con los mismos datos.'"
    :texto-confirmar="porConfirmar.activo ? 'Desactivar' : 'Reactivar'"
    :peligroso="porConfirmar.activo"
    @confirmar="ejecutarAccion"
    @cancelar="porConfirmar = null"
  />
</template>

<style scoped>
.titulo {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 18px;
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

.titulo .boton {
  margin-left: auto;
  text-decoration: none;
}

.filtros {
  display: flex;
  gap: 8px;
  margin-bottom: 18px;
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

.tabla {
  width: 100%;
  border-collapse: collapse;
  overflow: hidden;
  font-size: 14px;
}

th {
  background: #f5f5f4;
  text-align: left;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--texto-suave);
  padding: 11px 14px;
}

td {
  padding: 13px 14px;
  border-top: 1px solid var(--borde);
  vertical-align: middle;
}

.categoria {
  display: block;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--texto-suave);
}

.numero {
  font-variant-numeric: tabular-nums;
}

.estado {
  font-size: 12px;
  font-weight: 600;
  padding: 3px 10px;
  border-radius: 999px;
}

.estado.act {
  background: var(--acento-suave);
  color: var(--ok);
}

.estado.ina {
  background: #f5f5f4;
  color: var(--texto-suave);
}

.acciones {
  display: flex;
  gap: 14px;
  justify-content: flex-end;
}

.accion {
  background: none;
  border: none;
  padding: 0;
  font: 600 13px var(--fuente);
  color: var(--acento);
  cursor: pointer;
  text-decoration: none;
}

.accion.peligro {
  color: var(--error);
}

.cargando {
  color: var(--texto-suave);
}
</style>
```

Fíjate en tres decisiones:

- **El mismo botón hace las dos cosas.** Si el producto está activo dice *Desactivar* y llama a `DELETE`; si está retirado dice *Reactivar* y llama a `POST /{id}/activar`. Una sola función, `ejecutarAccion`, con un `if`. Dos botones separados obligarían a duplicar la confirmación, el éxito y el manejo de errores.
- **El filtro de estado usa `?activo=`**, el parámetro que agregó el contrato v2.3 para esta pantalla. Ese es todo el sentido de la Historia 8: sin él, un producto retirado desaparece y no hay forma de volver a encontrarlo.
- **Después de la acción se vuelve a cargar la lista.** Podrías cambiar el producto en memoria y ahorrarte la llamada, pero entonces el conteo del filtro quedaría desactualizado y el producto seguiría en una tabla donde ya no corresponde.

## 6. `src/vistas/VistaFormulario.vue` — Historias 1 y 4

El mismo componente sirve para crear y para editar: cambia si llega o no un `id` por la ruta.

```vue
<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { api } from '@/api/cliente'
import MensajeError from '@/componentes/MensajeError.vue'

const props = defineProps({ id: { type: String, default: null } })

const router = useRouter()
const editando = computed(() => props.id !== null)

const categorias = ref([])
const cargando = ref(true)
const guardando = ref(false)
const error = ref(null)
const activo = ref(true)

// Un solo objeto con el formulario: así el envío es una línea y no hay que acordarse de cada campo.
const formulario = ref({
  nombre: '',
  descripcion: '',
  precio: '',
  categoria: '',
  stock: '',
  imagenes: '',
})

// Errores por campo. El backend manda "precio: debe ser mayor que 0"; aquí se parte en dos para
// poder pintarlo debajo del campo que falló en vez de en una alerta genérica arriba.
const errores = ref({})

function validar() {
  const e = {}
  const f = formulario.value

  if (!f.nombre.trim()) e.nombre = 'es obligatorio'
  else if (f.nombre.length > 120) e.nombre = 'no puede pasar de 120 caracteres'

  if (f.precio === '' || Number(f.precio) <= 0) e.precio = 'debe ser mayor que 0'
  if (f.stock === '' || Number(f.stock) < 0) e.stock = 'no puede ser negativo'
  if (!f.categoria) e.categoria = 'es obligatoria'

  errores.value = e
  return Object.keys(e).length === 0
}

async function cargar() {
  cargando.value = true
  error.value = null
  try {
    categorias.value = await api.get('/categorias')

    if (editando.value) {
      const p = await api.get(`/productos/${props.id}`)
      activo.value = p.activo
      formulario.value = {
        nombre: p.nombre,
        descripcion: p.descripcion ?? '',
        precio: String(p.precio),
        categoria: p.categoria,
        stock: String(p.stock),
        imagenes: (p.imagenes ?? []).join('\n'),
      }
    }
  } catch (e) {
    error.value = e
  } finally {
    cargando.value = false
  }
}

async function guardar() {
  if (!validar()) return

  guardando.value = true
  error.value = null
  errores.value = {}

  // PUT reemplaza el producto completo (contrato §2): se envían todos los campos, no solo los que
  // cambiaron. Omitir la descripción la borraría.
  const cuerpo = {
    nombre: formulario.value.nombre.trim(),
    descripcion: formulario.value.descripcion.trim() || null,
    precio: Number(formulario.value.precio),
    categoria: formulario.value.categoria,
    stock: Number(formulario.value.stock),
    imagenes: formulario.value.imagenes
      .split('\n')
      .map((u) => u.trim())
      .filter(Boolean),
  }

  try {
    if (editando.value) {
      await api.put(`/productos/${props.id}`, cuerpo)
    } else {
      await api.post('/productos', cuerpo)
    }
    router.push('/catalogo/admin')
  } catch (e) {
    // La validación del navegador solo avisa rápido; la que manda es la del backend.
    if (e.codigo === 'VALIDACION_FALLIDA') {
      const [campo, ...resto] = e.mensaje.split(': ')
      if (resto.length > 0 && campo in formulario.value) {
        errores.value = { [campo]: resto.join(': ') }
      } else {
        error.value = e
      }
    } else {
      error.value = e
    }
  } finally {
    guardando.value = false
  }
}

watch(() => props.id, cargar, { immediate: true })
</script>

<template>
  <h1>{{ editando ? 'Editar producto' : 'Nuevo producto' }}</h1>

  <p v-if="cargando" class="cargando">Cargando…</p>

  <template v-else>
    <MensajeError v-if="error" :error="error" @reintentar="cargar" />

    <!--
      Editar un producto retirado es válido: PUT no cambia el estado (contrato §2, Historia 4).
      Hay que avisarlo para que nadie crea que al guardar lo está volviendo a publicar.
    -->
    <div v-if="editando && !activo" class="retirado">
      <strong>Este producto está retirado.</strong>
      Guardar los cambios no lo vuelve a publicar: para eso está el botón Reactivar del listado.
    </div>

    <form class="formulario tarjeta" @submit.prevent="guardar">
      <label>
        Nombre
        <input v-model="formulario.nombre" class="campo" :class="{ malo: errores.nombre }" maxlength="120" />
        <small v-if="errores.nombre" class="mal">nombre: {{ errores.nombre }}</small>
        <small v-else>{{ formulario.nombre.length }} / 120 caracteres</small>
      </label>

      <label>
        Descripción
        <textarea v-model="formulario.descripcion" class="campo" rows="3"></textarea>
        <small>Opcional</small>
      </label>

      <div class="dos">
        <label>
          Precio
          <input v-model="formulario.precio" class="campo" :class="{ malo: errores.precio }" type="number" min="1" />
          <small v-if="errores.precio" class="mal">precio: {{ errores.precio }}</small>
          <small v-else>Mayor que 0</small>
        </label>

        <label>
          Stock
          <input v-model="formulario.stock" class="campo" :class="{ malo: errores.stock }" type="number" min="0" />
          <small v-if="errores.stock" class="mal">stock: {{ errores.stock }}</small>
          <small v-else>0 o más</small>
        </label>
      </div>

      <label>
        Categoría
        <select v-model="formulario.categoria" class="campo" :class="{ malo: errores.categoria }">
          <option value="">Elige una categoría</option>
          <option v-for="c in categorias" :key="c.id" :value="c.id">{{ c.nombre }}</option>
        </select>
        <small v-if="errores.categoria" class="mal">categoría: {{ errores.categoria }}</small>
        <small v-else>Las opciones vienen de GET /categorias</small>
      </label>

      <label>
        Imágenes
        <textarea v-model="formulario.imagenes" class="campo" rows="3" placeholder="https://..."></textarea>
        <small>Opcional · una URL por línea</small>
      </label>

      <div class="acciones">
        <button class="boton" type="submit" :disabled="guardando">
          {{ guardando ? 'Guardando…' : 'Guardar producto' }}
        </button>
        <button class="boton secundario" type="button" @click="router.push('/catalogo/admin')">
          Cancelar
        </button>
      </div>
    </form>
  </template>
</template>

<style scoped>
h1 {
  font-size: 24px;
  letter-spacing: -0.02em;
}

.formulario {
  padding: 26px;
  max-width: 640px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

label {
  display: block;
  font-size: 13px;
  font-weight: 600;
}

label .campo {
  margin-top: 6px;
}

small {
  display: block;
  font-size: 12px;
  color: var(--texto-suave);
  margin-top: 5px;
  font-weight: 400;
}

small.mal {
  color: var(--error);
  font-weight: 500;
}

.dos {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.acciones {
  display: flex;
  gap: 10px;
  border-top: 1px solid var(--borde);
  padding-top: 20px;
}

.retirado {
  background: #fdf4e6;
  border: 1px solid #ebd3a6;
  color: var(--aviso);
  border-radius: var(--radio-chico);
  padding: 12px 15px;
  font-size: 14px;
  margin-bottom: 18px;
  max-width: 640px;
}

.cargando {
  color: var(--texto-suave);
}

@media (max-width: 600px) {
  .dos {
    grid-template-columns: 1fr;
  }
}
</style>
```

Aquí hay tres cosas que valen una explicación, porque son el corazón de tu tarea:

**1. El formulario envía todos los campos, siempre.** Mira `cuerpo`: se arma completo aunque el usuario solo haya cambiado el precio. Eso es porque `PUT` **reemplaza** el producto, no lo modifica campo por campo. Si mandaras solo el precio, el backend recibiría el resto vacío y borraría el nombre, la descripción y las imágenes.

**2. Los errores del backend se pintan bajo el campo que falló.** El backend responde `{"codigo":"VALIDACION_FALLIDA","mensaje":"precio: debe ser mayor que 0"}`. Ese mensaje trae **el nombre del campo antes de los dos puntos**, y por eso se puede partir en dos y ponerlo donde corresponde, en vez de una alerta genérica arriba que obliga al usuario a adivinar cuál de los cinco campos está mal.

   Esa es la razón de esta línea:

   ```js
   const [campo, ...resto] = e.mensaje.split(': ')
   ```

   Y del `if (campo in formulario.value)`: si el mensaje no empieza con un nombre de campo que exista (por ejemplo *"La categoría 'cat-x' no existe"*), no se inventa nada y se muestra el error completo arriba.

**3. La validación del navegador no sustituye a la del backend.** `validar()` corre antes de enviar y evita un viaje al servidor por algo obvio. Pero el backend valida igual, y con razón: el navegador se puede saltar. Por eso el `catch` también sabe pintar errores. Las dos capas dicen lo mismo, y el contrato es la fuente de las dos.

## 7. Probarlo de verdad

Necesitas **B2 y B3 en `main`**, porque tu tarea escribe.

**Terminal 1** — el backend:

```powershell
cd C:\dev\teambsoft-backend
docker compose up -d
.\mvnw.cmd spring-boot:run
```

**Terminal 2** — el frontend:

```powershell
cd C:\dev\teambsoft-frontend
npm run dev
```

Abre http://localhost:5173/catalogo/admin y recorre esto en orden. Es el guion de tu sustentación:

| # | Qué haces | Qué debe pasar |
|---|---|---|
| 1 | Entras a administración | La tabla con los productos activos y el conteo |
| 2 | Pulsas **+ Nuevo producto** y le das a Guardar en blanco | Cuatro errores en rojo: `nombre: es obligatorio`, `precio: debe ser mayor que 0`, `stock: no puede ser negativo`, `categoría: es obligatoria` |
| 3 | Llenas todo bien y guardas | Vuelve al listado y el producto aparece, con el conteo subido en uno |
| 4 | Pulsas **Editar** en ese producto | El formulario con **todos** los datos cargados, incluida la categoría seleccionada |
| 5 | Cambias el precio y guardas | Vuelve al listado con el precio nuevo, y **el nombre y la descripción siguen ahí** |
| 6 | Pulsas **Desactivar** | Sale el diálogo; si pulsas `Escape` o Cancelar, no pasa nada |
| 7 | Confirmas | Mensaje verde *"… fue retirado del catálogo"*, y desaparece de la lista |
| 8 | Pulsas el filtro **Retirados** | Ahí está, con estado *Retirado* y el botón **Reactivar** |
| 9 | Pulsas **Editar** sobre el retirado | El aviso amarillo de que guardar **no** lo vuelve a publicar |
| 10 | Guardas desde ahí | Vuelve al listado y **sigue retirado** — este es el criterio de la Historia 4 |
| 11 | Pulsas **Reactivar** y confirmas | Mensaje verde *"… volvió al catálogo"* y vuelve a la lista de activos |
| 12 | Apagas el backend y pulsas Desactivar | *"No se pudo conectar con el servidor"*, y el producto no cambia |

El paso **10** es el que más se falla. Si al guardar un producto retirado vuelve a aparecer en el catálogo público, algo está mandando `activo` en el cuerpo — y el contrato dice que `PUT` lo ignora.

Antes de subir:

```powershell
npm run build
```

## 8. Subir tu trabajo

```powershell
git status
git add .
git commit -m "Agrega las vistas de administracion: tabla, formulario, retirar y reactivar"
git pull origin main
npm run build
git push
```

- En `git status` **no debe aparecer `node_modules/` ni `dist/`**.
- Pull request con base `main`, título `F3: vistas de administracion`, y pide la revisión de Juan Diego o Roger.
- En **Jira**, pasa a *En revisión* tus cinco historias (1, 4, 5, 7 y 8) y a *Listo* cuando se una el PR.
- Marca las casillas de F3 en el README del repositorio de frontend.

## 9. Si algo falla

| Síntoma | Causa | Qué hacer |
|---|---|---|
| Al editar, el producto pierde la descripción | Se está enviando solo el campo que cambió | `PUT` reemplaza todo: sección 6, el objeto `cuerpo` |
| Guardar un producto retirado lo reactiva | Se está mandando `activo` en el cuerpo | Quítalo: el contrato dice que `PUT` lo ignora |
| El diálogo no aparece | Falta el `showModal()` del `onMounted`, o el componente no está dentro de un `v-if` | Sección 3 |
| El diálogo no cierra con Escape | Falta `@cancel.prevent` | Sección 3 |
| El error sale arriba y no bajo el campo | El mensaje del backend no empezaba con `campo: ` | Es el comportamiento correcto para errores que no son de un campo |
| La categoría no queda seleccionada al editar | Se está comparando el nombre en vez del `id` | El `:value` de cada `<option>` debe ser `c.id` |
| Después de retirar, el conteo no cambia | No se volvió a llamar a `cargar()` | Sección 5, al final de `ejecutarAccion` |
| El precio llega como texto y el backend responde 400 | Falta el `Number(...)` al armar el cuerpo | Sección 6 |
| `Failed to resolve import "@/utilidades/formato"` | F2 todavía no está en `main` | Créalo tú con la sección 3 de [la guía de F2](F2-LECTURA.md) |

---

_Última actualización: 2026-09-23_
