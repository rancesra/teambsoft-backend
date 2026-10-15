# Guía de S2-C — Pruebas automáticas y verificación de F3

**Para:** Carlos Andrés · **Tareas:** S2-C1 (pruebas con Vitest), S2-C2 (F3 sobre F2 integrado), S2-C3 (guion de F3 contra datos reales) · **Repositorio:** [teambsoft-frontend](https://github.com/rancesra/teambsoft-frontend) · **Rama:** `s2-pruebas` · **Entrega:** viernes 22 de octubre

Al terminar, el frontend tendrá sus primeras pruebas automáticas (formato de precios, nombre de categoría y la tarjeta de producto), y tu F3 habrá sido verificada sobre el código real de F2 y contra el backend completo.

**Dependes de:**

1. **F1 en `main`** (ya está).
2. **S2-C1** no depende de nadie: `formato.js` y `TarjetaProducto.vue` son de F2, pero puedes probarlos desde la rama `f2-lectura` de Rances.
3. **S2-C2 y S2-C3** esperan a que **F2 esté en `main`**, y S2-C3 además a **B2 y B3**.

Los comandos son para **PowerShell**, en la terminal de VS Code.

## 1. Antes de empezar

```powershell
cd C:\dev\teambsoft-frontend
git switch main
git pull
npm install
git switch -c s2-pruebas
git push -u origin s2-pruebas
```

Si F2 todavía no está en `main`, trae los archivos que necesitas desde la rama de Rances sin cambiarte de rama:

```powershell
git fetch origin
git checkout origin/f2-lectura -- src/utilidades/formato.js src/componentes/TarjetaProducto.vue
```

Estos dos archivos **no los subas en tu PR** si F2 aún no se ha unido: bórralos del área de preparación con `git restore --staged` antes del commit, o espera a que F2 entre.

## 2. Qué archivos vas a tocar

```
├── package.json                                 + dependencias y script "test"
├── vitest.config.js                             NUEVO
└── src/
    ├── utilidades/__tests__/formato.spec.js     NUEVO
    └── componentes/__tests__/TarjetaProducto.spec.js   NUEVO
```

## 3. S2-C1 — Primeras pruebas con Vitest

### 3.1 Instalación

```powershell
npm install -D vitest jsdom @vue/test-utils
```

Agrega el script en `package.json`, dentro de `"scripts"`:

```json
"test": "vitest run"
```

### 3.2 `vitest.config.js`

```js
import { mergeConfig, defineConfig } from 'vitest/config'

import viteConfig from './vite.config'

// Se mezcla con la configuración de Vite para heredar el alias "@": los componentes importan
// "@/utilidades/formato" y sin esto las pruebas no encontrarían el archivo.
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: { environment: 'jsdom' },
  }),
)
```

### 3.3 `src/utilidades/__tests__/formato.spec.js`

```js
import { describe, expect, it } from 'vitest'

import { enPesos, nombreDeCategoria } from '../formato'

const categorias = [
  { id: 'cat-ropa', nombre: 'ropa' },
  { id: 'cat-hogar', nombre: 'hogar' },
]

describe('enPesos', () => {
  it('usa punto de miles', () => {
    // Se usa una expresión regular y no una comparación exacta: Intl pone un espacio especial
    // (no separable) entre el símbolo y el número, y no se ve igual al escribirlo a mano.
    expect(enPesos(49900)).toMatch(/49\.900/)
  })

  it('no muestra centavos', () => {
    expect(enPesos(49900)).not.toMatch(/,/)
  })
})

describe('nombreDeCategoria', () => {
  it('devuelve el nombre cuando encuentra el id', () => {
    expect(nombreDeCategoria('cat-ropa', categorias)).toBe('ropa')
  })

  it('devuelve el propio id si no lo encuentra', () => {
    expect(nombreDeCategoria('cat-inexistente', categorias)).toBe('cat-inexistente')
  })

  it('devuelve el id si la lista de categorías está vacía', () => {
    expect(nombreDeCategoria('cat-ropa', [])).toBe('cat-ropa')
  })
})
```

### 3.4 `src/componentes/__tests__/TarjetaProducto.spec.js`

```js
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import TarjetaProducto from '../TarjetaProducto.vue'

const categorias = [{ id: 'cat-ropa', nombre: 'ropa' }]

const base = {
  id: 'p1',
  nombre: 'Camiseta basica',
  precio: 49900,
  categoria: 'cat-ropa',
  stock: 50,
  imagenes: [],
  activo: true,
}

function montar(cambios = {}) {
  return mount(TarjetaProducto, {
    props: { producto: { ...base, ...cambios }, categorias },
    // La tarjeta es un RouterLink; aquí solo importa a dónde apunta, no navegar de verdad.
    global: {
      stubs: { RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' } },
    },
  })
}

describe('TarjetaProducto', () => {
  it('muestra el nombre de la categoría y no su id', () => {
    const texto = montar().text()
    expect(texto).toContain('ropa')
    expect(texto).not.toContain('cat-ropa')
  })

  it('enlaza al detalle del producto', () => {
    expect(montar().find('a').attributes('href')).toBe('/catalogo/p1')
  })

  it('muestra «sin imagen» cuando no hay imágenes', () => {
    expect(montar({ imagenes: [] }).text()).toContain('sin imagen')
  })

  it.each([
    [0, 'sin', 'Sin stock'],
    [5, 'poco', 'Quedan 5'],
    [10, 'poco', 'Quedan 10'],
    [11, 'hay', '11 disponibles'],
  ])('con stock %i usa la variante «%s»', (stock, clase, texto) => {
    const etiqueta = montar({ stock }).find('.stock')
    expect(etiqueta.classes()).toContain(clase)
    expect(etiqueta.text()).toBe(texto)
  })
})
```

Ejecuta:

```powershell
npm test
```

**Importante:** estas pruebas son un punto de partida y **no se han ejecutado**. Si alguna falla, léela con calma antes de «arreglar» la prueba: puede que la prueba esté bien y el componente tenga un error. Si el stub de `RouterLink` no se aplica, la alternativa es montar con un enrutador real (`createRouter` + `createMemoryHistory`) en `global.plugins`.

## 4. S2-C2 — F3 sobre el F2 real

F3 necesita `src/utilidades/formato.js`, que crea F2. Si en el Sprint 1 creaste una copia temporal, tiene que desaparecer ahora.

1. Cuando F2 esté en `main`:

   ```powershell
   git switch main
   git pull
   git switch s2-pruebas
   git merge main
   ```

2. Comprueba que **hay un solo `formato.js`** (el de `src/utilidades/`) y que tus vistas lo importan de ahí:

   ```powershell
   Get-ChildItem src -Recurse -Filter formato*.js
   Select-String -Path src\vistas\*.vue -Pattern "formato"
   ```

3. Corre `npm run build` y `npm test`. Si F2 cambió algo que F3 usaba, arréglalo aquí.

## 5. S2-C3 — Guion de F3 con todo integrado

Repite el guion de sustentación de F3 (los 12 pasos), pero ahora con **F2 real en `main`** y con **B2 y B3 reales** en el backend. Con el backend corriendo (`docker compose up -d` y `.\mvnw.cmd spring-boot:run`) y el frontend con `npm run dev`:

| # | Acción | Resultado |
|---|---|---|
| 1 | Entrar a administración | Tabla + conteo de activos |
| 2 | Guardar en blanco | 4 errores: nombre, precio, stock, categoría |
| 3 | Llenar bien y guardar | Vuelve al listado, el conteo sube |
| 4 | Editar ese producto | Todos los datos cargados, categoría seleccionada |
| 5 | Cambiar precio y guardar | Precio nuevo; nombre y descripción intactos |
| 6 | Desactivar y cancelar (o Escape) | No pasa nada |
| 7 | Confirmar desactivar | Verde «retirado del catálogo» y desaparece |
| 8 | Filtro Retirados | Aparece con botón Reactivar |
| 9 | Editar el retirado | Aviso amarillo |
| 10 | Guardar desde ahí | **Sigue retirado** |
| 11 | Reactivar y confirmar | Verde «volvió al catálogo» |
| 12 | Apagar el backend y desactivar | «No se pudo conectar» y el producto sin cambios |

Agrega dos comprobaciones nuevas, que solo se pueden hacer con F2 integrado:

| # | Acción | Resultado |
|---|---|---|
| 13 | Con un producto retirado en el paso 7, abrir su detalle público | Se ve con aviso amarillo y **no aparece** en el listado público |
| 14 | Crear un producto y buscarlo en el listado público | Aparece con su categoría **por nombre** y el precio en pesos |

Anota ✅ / ❌ por paso en la descripción del PR. Cada ❌ es una tarjeta en Jira.

## 6. Subir tu trabajo

```powershell
git status
git add package.json package-lock.json vitest.config.js src/
git commit -m "Agrega pruebas con Vitest para formato y TarjetaProducto"
git pull origin main
npm run build
npm test
git push
```

- En `git status` **no debe aparecer `node_modules/` ni `dist/`**.
- Pull request con base `main`, título `S2: pruebas automaticas y verificacion de F3`, revisión de Juan Diego o Roger.
- En **Jira**, pasa S2-C1, S2-C2 y S2-C3 a *En revisión*, y a *Listo* cuando se una el PR.

## 7. Si algo falla

| Síntoma | Causa | Qué hacer |
|---|---|---|
| `Failed to resolve import "@/utilidades/formato"` en una prueba | `vitest.config.js` no hereda el alias de Vite | Usa `mergeConfig` con `vite.config`, como en la sección 3.2 |
| `document is not defined` | Falta el entorno del navegador | `test: { environment: 'jsdom' }` en `vitest.config.js` |
| La prueba de `enPesos` falla comparando texto exacto | El símbolo y el número van separados por un espacio no separable | Usa la expresión regular, no el texto literal |
| `Failed to resolve component: RouterLink` | El stub no se registró | Revisa `global.stubs` o monta con un enrutador real |
| Dos archivos `formato.js` | Quedó la copia temporal de F3 | Borra la copia y deja la de `src/utilidades/` |
| `git merge main` da conflicto en `VistaAdmin.vue` | Se tocó el mismo archivo en dos ramas | Resuelve conservando tu versión de F3 y vuelve a correr el guion |

---

_Última actualización: 2026-10-15_
