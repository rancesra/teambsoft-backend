# Guía de S3-J — Módulos de prueba en el Host App y variables de color

**Para:** Juan Diego · **Tareas:** S3-J1 (probar la integración de Búsqueda y Carro con módulos de prueba y dejar la lista de requisitos), S3-J2 (aplicar las variables de color nuevas en el módulo de Catálogo), S3-J3 (revisión del PR de Carlos) · **Repositorios:** [teambsoft-hostapp](https://github.com/rancesra/teambsoft-hostapp) (J1) y [teambsoft-frontend](https://github.com/rancesra/teambsoft-frontend) (J2, J3) · **Ramas:** `s3-integracion` (en cada repositorio) · **Entrega:** jueves 29 de octubre

Al terminar, el Host App habrá demostrado que carga **tres módulos** (aunque dos sean de mentira), los equipos A y C tendrán una lista corta de lo que su módulo debe cumplir para encajar, y el módulo de Catálogo usará las variables de color nuevas del contrato en lugar de colores escritos a mano.

**Dependes de:**

1. **S3-J1** no depende de nadie: empiézala ya.
2. **S3-J2 espera a Hector.** Él decide qué variables de color entran al contrato (v1.2 del Host App) y te avisa en el grupo con la tabla final, a más tardar el **lunes 26**. Mientras tanto haz J1 y, si quieres, deja lista la lista de búsqueda de colores (sección 5, paso 1).
3. **S3-J3** empieza cuando Carlos abra su PR (meta: martes 27).

Los comandos son para **PowerShell**, en la terminal de VS Code.

## 1. Antes de empezar

Host App (J1):

```powershell
cd C:\dev
git clone https://github.com/rancesra/teambsoft-hostapp.git   # solo si no lo tienes
cd teambsoft-hostapp
git switch main
git pull
npm install
git switch -c s3-integracion
git push -u origin s3-integracion
```

Frontend (J2), cuando llegue su momento:

```powershell
cd C:\dev\teambsoft-frontend
git switch main
git pull
npm install
git switch -c s3-integracion
git push -u origin s3-integracion
```

Si Hector ya publicó la clave de Jira, antepónla al nombre de la rama (`CLAVE-N-s3-integracion`).

## 2. Qué archivos vas a tocar

```
teambsoft-hostapp/
├── .env.development.local               NUEVO, solo en tu computador (git lo ignora)
└── docs/
    ├── modulo-de-prueba/index.html      NUEVO  módulo de mentira  (S3-J1)
    └── LISTA-INTEGRACION-MODULOS.md     NUEVO  requisitos para A y C  (S3-J1)

teambsoft-frontend/
└── src/
    ├── estilos.css                      variables nuevas  (S3-J2)
    ├── componentes/*.vue                colores a mano → variables  (S3-J2)
    └── vistas/*.vue                     igual  (S3-J2)
```

## 3. Cómo funciona hoy la integración (léelo antes de tocar nada)

El Host App no ejecuta los módulos: **los carga dentro de un iframe**. Mira `src/componentes/MarcoModulo.vue`:

1. Lee la dirección del módulo de `src/modulos.js`, que sale de las variables `VITE_MODULO_*` de `.env.development`.
2. Antes de mostrarlo, hace un `fetch` a esa dirección (con `mode: 'no-cors'`). Si el servidor está apagado, el `fetch` falla y se muestra «no está disponible» con botón de reintentar; si no hay dirección, «todavía no está conectado».
3. Si responde, pone un `<iframe>` con la dirección del módulo **más la ruta completa que se pidió** (`url + route.fullPath`). Por eso `/busqueda/algo?q=zapatos` en el cascarón abre `/busqueda/algo?q=zapatos` dentro del módulo.

Consecuencia que conviene tener clara: **cada iframe es un documento aparte**, con su propio `:root`. Las variables CSS del Host App **no llegan** a los módulos. Las «variables compartidas» del contrato son una convención de valores idénticos, que cada módulo repite en su propio `estilos.css`. Lo verás en J2.

## 4. S3-J1 — Probar la integración con módulos de prueba

El Equipo A y el Equipo C todavía no tienen su módulo corriendo. Para no depender de ellos, vas a **simular dos módulos** y comprobar que el cascarón los monta.

### 4.1 El módulo de mentira

Crea `docs/modulo-de-prueba/index.html`:

```html
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Módulo de prueba</title>
  <style>
    body { font-family: system-ui, sans-serif; margin: 0; background: #fafaf9; color: #1c1917; }
    .barra { background: #b45309; color: #fff; padding: 10px 16px; }
    main { padding: 24px 16px; }
    code { background: #f0efed; padding: 1px 6px; border-radius: 4px; }
  </style>
</head>
<body>
  <!-- Contrato del Host App, sección 4: el módulo NO pinta su encabezado cuando está embebido. -->
  <div class="barra" id="barra">Encabezado propio del módulo (solo se ve si corre solo)</div>
  <main>
    <h1 id="titulo">Módulo de prueba</h1>
    <p>Puerto: <code id="puerto"></code></p>
    <p>Ruta recibida: <code id="ruta"></code></p>
    <p>Búsqueda recibida: <code id="busqueda"></code></p>
    <p>¿Está embebido en un iframe? <code id="embebido"></code></p>
  </main>
  <script>
    const embebido = window.self !== window.top
    document.getElementById('puerto').textContent = location.port
    document.getElementById('ruta').textContent = location.pathname
    document.getElementById('busqueda').textContent = location.search || '(ninguna)'
    document.getElementById('embebido').textContent = embebido ? 'sí' : 'no'
    document.getElementById('titulo').textContent = 'Módulo de prueba en el puerto ' + location.port
    if (embebido) document.getElementById('barra').style.display = 'none'
  </script>
</body>
</html>
```

### 4.2 Levantarlo dos veces

En **dos terminales aparte**, desde la carpeta del Host App:

```powershell
npx serve docs/modulo-de-prueba -s -l 5174
```

```powershell
npx serve docs/modulo-de-prueba -s -l 5175
```

`-s` hace que cualquier ruta (`/busqueda/lo-que-sea`) devuelva el mismo `index.html`, como lo haría una aplicación de Vue con su enrutador. La primera vez `npx` pregunta si instala `serve`: responde que sí.

### 4.3 Conectarlos al Host App

Crea el archivo `.env.development.local` en la raíz del Host App (no se sube: el `.gitignore` ignora `.env.*.local`):

```
VITE_MODULO_BUSQUEDA=http://localhost:5174
VITE_MODULO_CARRO=http://localhost:5175
```

Reinicia el Host App (`Ctrl + C` y `npm run dev`) porque Vite lee las variables al arrancar. Para ver Catálogo también, el módulo de Catálogo tiene que estar corriendo en otra terminal (`npm run dev` en `teambsoft-frontend`).

### 4.4 Qué comprobar

Abre http://localhost:5050 y recorre esta tabla anotando ✅ / ❌:

| Qué haces | Qué debe pasar | Resultado |
|---|---|---|
| Entras a `/` | Redirige a `/catalogo` | |
| Miras el menú | Aparecen Catálogo, Búsqueda y Carro, con el activo resaltado | |
| Pulsas «Búsqueda» | La URL es `/busqueda` y el iframe muestra «Módulo de prueba en el puerto 5174», con ruta `/busqueda` | |
| Escribes `http://localhost:5050/busqueda/algo/profundo?q=zapatos` | El módulo recibe la ruta completa y la búsqueda `?q=zapatos` | |
| Miras el iframe | **No** se ve la barra naranja del módulo (está embebido) | |
| Abres http://localhost:5174 directo, fuera del Host App | **Sí** se ve la barra naranja (la regla de oro: funciona solo) | |
| Pulsas «Carro» | Muestra el módulo del puerto 5175 | |
| Apagas el servidor 5174 (`Ctrl + C`) y recargas `/busqueda` | «El módulo de Búsqueda no está disponible», con la dirección y un botón Reintentar | |
| Vuelves a encender 5174 y pulsas Reintentar | El módulo carga sin recargar toda la página | |
| Quitas `VITE_MODULO_CARRO` del `.env.development.local` y reinicias | «Carro todavía no está conectado» | |
| Entras a `/loquesea` | «Esta página no existe» con botón al catálogo | |
| Vuelves a Catálogo | Sigue funcionando igual que antes | |

**Un límite que ya está en el contrato:** mientras el usuario navega *dentro* del módulo, la dirección del cascarón no cambia. Entrar directo a una ruta funciona; lo que no se actualiza es la barra de direcciones al navegar adentro. No lo reportes como fallo.

Cada ❌ es una tarjeta en Jira con captura. Si el fallo es del Host App, corrígelo en tu rama; si es de una decisión de diseño, avisa a Hector.

### 4.5 La lista de requisitos para A y C

Crea `docs/LISTA-INTEGRACION-MODULOS.md` en el Host App. Es una página que Hector enviará a los otros equipos:

```markdown
# Lista de integración de un módulo en el Host App

Antes de pedir que tu módulo se conecte, comprueba esto. Las reglas completas están en la sección 4 del [contrato del Host App](../CONTRATO-HOSTAPP.md).

## Los cuatro requisitos

- [ ] **Tiene una dirección fija.** `npm run dev` deja tu módulo corriendo en un puerto (Catálogo usa el 5173, el Host App el 5050). Elige uno que no choque con los demás y que no cambie solo (en Vite: `server.strictPort: true`).
- [ ] **Conserva su prefijo.** Búsqueda responde en `/busqueda/*`; Carrito, en `/carrito/*`. El Host App le pasa la ruta tal cual.
- [ ] **No pinta su encabezado cuando está dentro del Host App.** Usa `const embebido = window.self !== window.top` y `v-if="!embebido"` en tu barra. Cuando corre solo, la sigue mostrando.
- [ ] **Se deja cargar en un iframe.** No envía `X-Frame-Options: DENY` ni `frame-ancestors 'none'`. Compruébalo así (con tu módulo corriendo):

  ```powershell
  curl.exe -I http://localhost:PUERTO
  ```

  En la respuesta **no** debe aparecer `x-frame-options` ni un `content-security-policy` con `frame-ancestors`.

## Cómo conectarlo

1. Haz un pull request al repositorio del Host App que ponga tu dirección en `.env.development`:

   ```
   VITE_MODULO_BUSQUEDA=http://localhost:PUERTO
   ```

2. Para probarlo solo en tu computador, sin pull request, ponla en un archivo `.env.development.local` (git lo ignora) y reinicia el Host App.
3. Abre http://localhost:5050/busqueda (o `/carrito`). Si ves «todavía no está conectado», falta la dirección; si ves «no está disponible», tu servidor está apagado o en otro puerto.

## Estilos

Usa las variables de la sección 6 del contrato con `var(--...)`; no escribas colores a mano. **Define esas variables en tu propio archivo de estilos**: con iframes, el Host App no las comparte con tu módulo. Usa `<style scoped>` o el prefijo `busqueda-` / `carrito-` en tus clases globales.

## Si algo no funciona

| Síntoma | Causa probable |
|---|---|
| Se ven dos encabezados | Tu módulo no detecta que está embebido |
| «No está disponible» aunque tu módulo corre | Puerto distinto al de `.env.development`, o el servidor escucha solo en otra interfaz |
| El iframe sale en blanco con un error de «rechazó la conexión» | `X-Frame-Options` o `frame-ancestors` en tu servidor |
| Recargar una subruta da 404 | Tu servidor no devuelve el `index.html` para rutas desconocidas |
```

### 4.6 Entregar y compartir

1. Pásale a Hector la lista de requisitos: él la envía a los equipos A y C.
2. **Si el miércoles 28 el Equipo A o el C ya tiene su módulo corriendo**, cambia en tu `.env.development.local` el puerto de mentira por el suyo, repite la tabla de 4.4 y anota el resultado en la tarjeta de Jira. Con eso Hector puede incluirlo en la prueba de integración. Si no lo tienen, el resultado con módulos de prueba es el que vale.

**Cómo saber que está listo:** la tabla de 4.4 está completa con ✅ / ❌ en la descripción del PR, y `docs/LISTA-INTEGRACION-MODULOS.md` está escrita.

## 5. S3-J2 — Variables de color en el módulo de Catálogo

**Antes de empezar, espera la tabla de Hector** (contrato v1.2). No cambies nada por tu cuenta: agregar variables al contrato es una decisión suya, y lo que él rechace tiene otro camino.

1. **Vuelve a buscar los colores a mano** (puedes hacer este paso ya, para tener la lista fresca):

   ```powershell
   cd C:\dev\teambsoft-frontend
   Get-ChildItem src -Recurse -Include *.vue | Select-String -Pattern '#[0-9a-fA-F]{3,8}\b'
   ```

   (`src\estilos.css` no se revisa: ahí se definen las variables.)

2. **Define las variables nuevas en `src/estilos.css` del módulo**, con **exactamente** los mismos valores que Hector publicó. Es obligatorio por la regla de oro: el módulo tiene que verse bien también cuando corre solo, y como vimos en la sección 3, el Host App no se las comparte.

3. **Reemplaza cada color** según la decisión de Hector. Si aceptó las propuestas de su guía, queda así:

   | Dónde | Antes | Después |
   |---|---|---|
   | `TarjetaProducto.vue`, `VistaDetalle.vue` (foto, esqueletos, «Sin stock») | `#f0efed`, `#f5f5f4` | `var(--fondo-suave)` |
   | `TarjetaProducto.vue` («Quedan N»), `VistaDetalle.vue` (aviso de retirado) | `#fffbeb`, `#fdf4e6` | `var(--aviso-suave)` |
   | `VistaDetalle.vue` (borde del aviso) | `#ebd3a6` | `var(--aviso-borde)` |
   | `VistaListado.vue` (chip activo) | `#fff` | `var(--texto-sobre-acento)` |
   | `TarjetaProducto.vue`, `VistaDetalle.vue` («sin imagen») | `#a8a29e` | `var(--texto-suave)` |

   Si Hector decidió otra cosa, **manda su tabla, no esta**. Antes de reemplazar, abre `estilos.css` y mira el **valor real** de cada variable; no las elijas por el nombre.

   **No uses valor de respaldo** (`var(--x, #f0efed)`): vuelve a esconder el color escrito a mano que la regla quiere eliminar. Basta con que la variable esté definida en `estilos.css`.

4. **Comprueba** que quedó limpio: el comando del paso 1 no debe devolver nada (o solo colores que Hector dejó fuera a propósito, que anotas en la descripción del PR).

5. **Comprobación visual.** Con el backend y los productos de prueba, abre el módulo en modo independiente (http://localhost:5173/catalogo) y mira estas pantallas antes y después de tu cambio:

   | Pantalla | Qué mirar |
   |---|---|
   | Listado | Tarjetas, chip activo (texto blanco sobre verde), foto de relleno |
   | Producto con poco stock | Etiqueta «Quedan N» |
   | Producto sin stock | Etiqueta «Sin stock» |
   | Detalle retirado | Aviso amarillo, título y precio en gris |
   | Estado de carga | Esqueletos grises |

   Nada debe verse distinto, salvo el texto «sin imagen», que ahora se lee mejor (más oscuro). Toma una captura de cada una; van en el PR.

6. **Dentro del Host App**, comprueba lo mismo en http://localhost:5050/catalogo.

7. Corre `npm run build` y `npm test`: ambos deben pasar. Las pruebas de Carlos comprueban textos y clases, no colores, así que no deberían romperse. Si alguna falla, léela antes de arreglarla: puede que el cambio rompiera una clase.

> **Corrección de lo que se anotó en S2-J2.** Esa tabla decía que, si el Host App cambia el valor de `--acento`, los chips y botones del módulo cambian. Con iframes eso **no puede pasar**: son documentos separados. Cambia el valor en el Host App y en el módulo a la vez, o no cambia en el módulo. Anótalo en la descripción del PR; Hector lo recoge en la v1.2 del contrato.

**Cómo saber que está listo:** el comando de búsqueda devuelve solo lo que Hector dejó fuera a propósito, hay capturas antes/después y `npm run build` y `npm test` pasan.

## 6. S3-J3 — Revisión del PR de Carlos

Cuando Carlos abra el PR `S3: pruebas de las vistas de listado y detalle`, revísalo con esta lista. Lo que importa es que las pruebas **prueben las reglas del contrato** y que **puedan fallar**.

| Qué revisas | Cómo lo compruebas |
|---|---|
| Las pruebas leen el comportamiento, no los detalles internos | Comprueban texto visible y llamadas al servidor, no nombres de variables internas |
| La primera página es la 1 | Hay una prueba de que sin parámetros se pide `pagina=1` |
| Producto retirado: 200 con `activo: false`, sin 404 | La prueba lo cubre y verifica que **no** se muestra el recuadro del Carro |
| Id inexistente | La prueba usa la respuesta `PRODUCTO_NO_ENCONTRADO` y espera el mensaje «Este producto no existe o fue retirado del catálogo» |
| Backend apagado | La prueba simula `fetch` que falla y espera «No se pudo conectar con el servidor» |
| Las pruebas **pueden fallar** | Rompe a propósito algo del componente (por ejemplo, cambia un texto), corre `npm test` y confirma que **falla**; después restaura con `git restore` |
| Sin cambios de comportamiento en los componentes | Solo se agregaron pruebas (y, a lo sumo, atributos `data-testid`) |
| `npm test` y `npm run build` | Pasan |
| `git status` | Sin `node_modules/` ni `dist/` en el PR |

Deja los comentarios en el PR de GitHub. Aprueba solo cuando todo pase; si algo falla, di **qué regla del contrato no queda cubierta** además de la línea.

## 7. Subir tu trabajo

En cada repositorio, desde su carpeta:

```powershell
git status
git add <los archivos de esa tarea>
git commit -m "Agrega modulo de prueba y lista de integracion"
git pull origin main
npm run build
git push
```

- En `git status` **no debe aparecer `.env.development.local`**, `node_modules/` ni `dist/`.
- Pull request en cada repositorio con base `main`:
  - Host App: `S3: modulos de prueba y lista de integracion` → revisión de **Carlos**.
  - Frontend: `S3: variables de color del contrato` → revisión de **Carlos** (o de Rances).
- En **Jira**, pasa S3-J1, S3-J2 y S3-J3 a *En revisión*, y a *Listo* cuando se unan los PR.

## 8. Si algo falla

| Síntoma | Causa | Qué hacer |
|---|---|---|
| `npx serve` dice que el puerto está ocupado | Otro programa usa el 5174 o 5175 | Cambia el número y ajusta `.env.development.local` |
| El Host App no ve las direcciones nuevas | Vite lee las variables solo al arrancar | Reinicia `npm run dev` |
| «Todavía no está conectado» aunque pusiste la dirección | Nombre de variable mal escrito | Deben ser `VITE_MODULO_BUSQUEDA` y `VITE_MODULO_CARRO`, sin espacios ni comillas |
| El iframe se queda en «Cargando…» | El `fetch` previo no recibe respuesta | Abre la dirección directo en el navegador y comprueba que carga |
| Se ven dos encabezados en el módulo de Catálogo | `App.vue` del módulo no detecta el iframe | Debe usar `window.self !== window.top` |
| Reemplazaste un color y se ve distinto | La variable tiene otro valor del esperado | Mira el valor real en `estilos.css` antes de elegir |
| El comando de búsqueda no devuelve nada | PowerShell no encontró la carpeta | Ejecútalo desde la raíz del repositorio |
| Conflicto con el PR de Carlos en un `.vue` | Los dos tocaron el mismo archivo | Conserva ambos cambios (casi siempre **Accept Both Changes**) |
| La tabla de Hector todavía no llega | Depende de él | Haz J1 y J3 primero, y avísale el martes por la mañana |

---

_Última actualización: 2026-10-24_
