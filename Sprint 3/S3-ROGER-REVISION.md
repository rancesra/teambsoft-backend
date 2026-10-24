# Guía de S3-V — Revisión manual y accesibilidad (tarea de peso bajo)

**Para:** Roger Sergio · **Tareas:** S3-V1 (probar las vistas de lectura), S3-V2 (revisar los textos en español), S3-V3 (accesibilidad básica), S3-V4 (navegadores y pantallas pequeñas) · **Repositorio:** [teambsoft-frontend](https://github.com/rancesra/teambsoft-frontend) · **Rama:** `s3-revision` (solo si cambias archivos) · **Entrega:** jueves 29 de octubre

> **Peso bajo, y no bloquea nada.** Ninguna otra tarea depende de esta, y no tienes que modificar código para cumplirla. Si algo no queda terminado, el sprint se considera cumplido igual. Haz lo que alcances, en el orden de abajo. S3-V1 y S3-V2 son las dos que quedaron pendientes del Sprint 2: con esas basta para cerrar bien.

Las vistas de lectura (F2) ya están en `main`, pero solo las ha probado su autora. Tú las pruebas **como lo haría una persona que no las construyó**, y además revisas dos cosas que nadie ha mirado todavía: si se pueden usar con el teclado y con una pantalla pequeña.

Al terminar, el equipo tendrá una lista de fallos, de textos mejorables y de problemas de accesibilidad, con evidencia, antes de la entrega.

**Dependes de:**

1. **Nada**: el listado, el detalle y la administración ya están en `main`.
2. Para ver productos de verdad necesitas el backend con datos (sección 1). Si el catálogo está vacío, créalos con la administración o con el archivo `docs/ejemplos-api.http` si Cristian ya lo entregó.

Los comandos son para **PowerShell**, en la terminal de VS Code.

## 1. Antes de empezar

**Terminal 1** — el backend:

```powershell
cd C:\dev\teambsoft-backend
git switch main
git pull
docker compose up -d
```

**Importante:** Jhon está cambiando las credenciales de RabbitMQ en este sprint. Si al levantar el compose te pide un archivo `.env` (mensaje «Define RABBITMQ_USER...»), copia `.env.example` a `.env` y vuelve a ejecutar el comando. Ese archivo **no se sube** a git.

Después, el servicio:

```powershell
.\mvnw.cmd spring-boot:run
```

**Terminal 2** — el frontend:

```powershell
cd C:\dev\teambsoft-frontend
git switch main
git pull
npm install
npm run dev
```

Abre http://localhost:5173/catalogo. Si quieres ver el módulo dentro del cascarón, en una **terminal 3**:

```powershell
cd C:\dev\teambsoft-hostapp
git switch main
git pull
npm install
npm run dev
```

y abre http://localhost:5050.

**No hagas commits en `main`.** Si más adelante decides cambiar textos, crea tu propia rama con `git switch -c s3-revision` desde `main` (y solo si Rances te lo pide).

Lee del `CONTRATO-CATALOGO.md` las secciones 2 y 3 (`GET /productos`, `GET /productos/{id}` y el modelo de Producto).

## 2. Qué archivos vas a tocar

Ninguno, salvo que decidas proponer cambios de texto. Tus resultados van en **Jira** (una tarjeta por fallo, con captura) y en un comentario de la tarjeta de la tarea. Si quieres dejarlos por escrito en el repositorio, puedes subir un archivo opcional `docs/REVISION-S3.md`; no es obligatorio.

## 3. S3-V1 — Probar las vistas de lectura

Abre http://localhost:5173/catalogo y recorre esta tabla, anotando ✅ / ❌ en cada fila:

| Qué haces | Qué debe pasar | Resultado |
|---|---|---|
| Entras al catálogo | Rejilla de productos, conteo y chips de categoría | |
| Haces clic en «hogar» | Solo los de hogar, el chip queda activo y la URL dice `?categoria=cat-hogar` | |
| Recargas la página | Sigue filtrado por hogar | |
| Pulsas «2» en el paginador | Siguientes productos y la URL dice `?pagina=2` | |
| Pulsas «atrás» del navegador | Vuelve a la página 1 | |
| Cambias de categoría estando en la página 2 | Queda en la página 1 | |
| Filtras por una categoría sin productos | «No hay productos en esta categoría» y un botón para ver todo, **no un error** | |
| Haces clic en una tarjeta | El detalle en `/catalogo/<id>` | |
| Abres un producto con varias imágenes | Aparecen miniaturas y se pueden cambiar | |
| Entras a `/catalogo/noexiste123` | «Este producto no existe o fue retirado del catálogo» | |
| Apagas el backend y recargas | «No se pudo conectar con el servidor» y botón Reintentar | |
| Reintentas con el backend ya encendido | Carga sin recargar la página | |
| Abres un producto retirado | Aviso amarillo, título y precio en gris, imagen en blanco y negro, **sin** recuadro del Carro | |
| Buscas ese producto retirado en el listado | **No aparece** | |
| Entras a la administración (`/catalogo/admin`) y pulsas «Retirados» | El producto retirado sí aparece, con botón para reactivarlo | |

Además, prueba dos cosas que la tabla no cubre:

1. **Nombres largos:** un producto con nombre de tres líneas **no debe desalinear** los precios de la rejilla.
2. **Dentro del Host App** (http://localhost:5050/catalogo): las mismas tres o cuatro acciones principales funcionan igual dentro del cascarón, y se ve **un solo** encabezado.

**Cómo reportar un fallo**, para que se pueda arreglar sin preguntarte nada: una tarjeta en Jira con (a) qué hiciste, (b) qué esperabas, (c) qué pasó y (d) una captura de pantalla.

## 4. S3-V2 — Revisar los textos en español

Un producto en español se nota en los detalles. Revisa estos textos visibles que ya existen en las vistas (compruébalos en pantalla; si alguno cambió, anota el actual):

| Dónde | Texto actual |
|---|---|
| Listado, sin resultados | «No hay productos en esta categoría.» |
| Listado, botón del vacío | «Ver todo el catálogo» |
| Listado, encabezado | «N productos · página N» |
| Tarjeta, stock | «Sin stock» / «Quedan N» / «N disponibles» |
| Tarjeta, sin foto | «sin imagen» |
| Detalle, producto retirado | «Producto no disponible. Este producto fue retirado del catálogo.» |
| Error, id inexistente | «Este producto no existe o fue retirado del catálogo» |
| Error, sin conexión | «No se pudo conectar con el servidor» |
| Administración | Los textos del formulario, los avisos de éxito («Producto creado…», «Cambios guardados…») y el diálogo de desactivar |

Revisa cuatro cosas:

1. **Ortografía y tildes.**
2. **Singular y plural:** ¿qué dice el encabezado con un solo producto («1 productos»)? ¿y «Quedan 1»?
3. **Tono:** que sea el mismo en todas las pantallas (tú o usted, con o sin punto final).
4. **Mensajes de error de validación:** cuando el backend rechaza un precio de 0, ¿el mensaje se entiende? ¿está en español? (Deben estarlo.)

Entrégalo como una lista en un comentario de la tarjeta de Jira: **texto actual → texto propuesto → por qué**. **No cambies textos directamente en el código de otra persona**: propón, y que Rances (o quien sea dueño de esa vista) decida. Si te piden que los cambies tú, entonces sí haz tu rama `s3-revision` y un PR chico.

Ojo: el texto de `MensajeError` para cada código de error no se cambia a la ligera, porque ese componente traduce por **código** (`SIN_CONEXION`, `PRODUCTO_NO_ENCONTRADO`, `VALIDACION_FALLIDA`, `STOCK_INSUFICIENTE`) y lo comparten otras vistas. Si propones cambiar uno, di en qué pantallas aparece.

## 5. S3-V3 — Accesibilidad básica (tarea nueva)

Nadie ha revisado todavía si las vistas se pueden usar sin ratón. No hace falta saber de accesibilidad: es una lista de comprobaciones que cualquiera puede hacer. **Solo reporta; no arregles nada.**

### 5.1 Con el teclado

Deja el ratón quieto y usa solo estas teclas: `Tab` (avanzar), `Shift + Tab` (retroceder), `Enter` (activar).

| Qué haces | Qué debe pasar | Resultado |
|---|---|---|
| Pulsas `Tab` varias veces en el listado | El foco pasa por los chips, las tarjetas y el paginador, **en un orden lógico** | |
| Miras dónde está el foco | **Se ve** un borde o resalte alrededor del elemento enfocado | |
| Enfocas una tarjeta y pulsas `Enter` | Abre el detalle | |
| Enfocas un chip de categoría y pulsas `Enter` | Filtra | |
| Enfocas «Reintentar» (con el backend apagado) y pulsas `Enter` | Reintenta | |
| En el formulario de administración, `Tab` recorre campo por campo | Cada campo tiene su etiqueta y el orden es el de la pantalla | |
| Abres el diálogo de «Desactivar» y pulsas `Escape` | Se cierra sin desactivar | |
| Dentro del diálogo, pulsas `Tab` | El foco **no se escapa** a lo que hay detrás | |

### 5.2 Lo que se ve

| Qué compruebas | Cómo | Resultado |
|---|---|---|
| **Contraste del texto «sin imagen»** | Usa https://webaim.org/resources/contrastchecker/ con `#a8a29e` sobre `#f0efed`. Se espera algo cercano a 2:1; el mínimo recomendado para texto es 4.5:1 | |
| Contraste del texto de «Quedan N» y «Sin stock» | Misma herramienta, con los colores de la etiqueta | |
| El estado no depende solo del color | «Sin stock», «Quedan N» y «Retirado» tienen **texto**, no solo un color distinto | |
| Texto de error | El mensaje de validación del formulario se lee sin depender del rojo | |
| Zoom al 200 % (`Ctrl` + `+`) | Nada se corta ni se superpone, y el contenido sigue siendo usable | |

### 5.3 Imágenes y estructura

| Qué compruebas | Cómo | Resultado |
|---|---|---|
| Las fotos tienen texto alternativo | Clic derecho → Inspeccionar en una imagen del producto: debe tener `alt` con algo útil (por ejemplo, el nombre del producto), no vacío ni «imagen» | |
| El `<html>` declara el idioma | En Inspeccionar, mira la etiqueta `<html>`: debe decir `lang="es"` | |
| Los chips y los botones son botones | En Inspeccionar, un chip debe ser un `<button>` (o tener `role="button"` y ser enfocable); si es un `<div>` sin más, el teclado no puede activarlo | |

### 5.4 Una auditoría automática (5 minutos)

En Chrome: `F12` → pestaña **Lighthouse** → marca solo **Accessibility** → **Analyze page load**. Hazlo en el listado, en el detalle y en el formulario de administración. Anota la **puntuación** de cada una y copia los tres primeros problemas que mencione. Esa herramienta no detecta todo, pero encuentra rápido los errores comunes.

**Cómo reportar:** una tarjeta en Jira por problema (no por cada fila de la tabla), con captura, y prioridad **baja** salvo que algo impida usar la pantalla con el teclado, que es **media**. El contraste de «sin imagen» ya es un hallazgo conocido; anótalo igual, con los números, porque le sirve a Hector para decidir qué variable de color usar.

## 6. S3-V4 — Navegadores y pantallas pequeñas (tarea nueva)

Repite **tres acciones** (entrar al listado, abrir un detalle y filtrar por categoría) en cada combinación:

| Navegador / pantalla | Listado | Detalle | Filtro | Notas |
|---|---|---|---|---|
| Chrome, ventana normal | | | | |
| Edge o Firefox, ventana normal | | | | |
| Chrome, ancho de **375 px** (teléfono) | | | | |
| Chrome, ancho de **768 px** (tableta) | | | | |

Para simular un teléfono sin tenerlo: en Chrome, `F12` → ícono de dispositivos (o `Ctrl + Shift + M`) → elige un teléfono de la lista o escribe el ancho a mano.

En pantalla pequeña mira especialmente:

1. La rejilla pasa a menos columnas y **no aparece una barra de desplazamiento horizontal** en la página.
2. El detalle pasa a **una sola columna**.
3. Los botones y los chips son lo bastante grandes para tocarlos con el dedo (unos 40 px de alto o más).
4. El formulario de administración se puede completar sin que los campos se salgan de la pantalla.

Anota ✅ / ❌ y, en caso de ❌, qué se veía mal y en qué navegador.

## 7. Entregar tu trabajo

Si solo probaste y revisaste (lo normal), **no hay nada que subir a git**:

- Deja los fallos como tarjetas en **Jira** y las listas de textos y de accesibilidad como comentario en la tarjeta de la tarea.
- Pasa tus tarjetas a *Listo* al terminar.
- Cuéntales a Hector y a Rances (en el grupo, en dos líneas) cuántos fallos encontraste y cuáles son los más importantes.

Si cambiaste textos con permiso de Rances:

```powershell
git status
git add .
git commit -m "Corrige textos de las vistas de lectura"
git pull origin main
npm run build
npm test
git push
```

- Pull request con base `main`, título `S3: revision de textos`, revisión de **Carlos**.
- En `git status` **no debe aparecer `node_modules/`, `dist/` ni `.env`**.

## 8. Si algo falla

| Síntoma | Causa | Qué hacer |
|---|---|---|
| `docker compose up` dice «Define RABBITMQ_USER...» | Jhon pasó las credenciales a un archivo `.env` | `Copy-Item .env.example .env` y repite |
| La rejilla sale vacía | No hay productos en la base, o el backend no está corriendo | Crea productos desde la administración |
| El paginador no aparece | Hay 20 productos o menos: con una sola página se oculta a propósito | Crea más productos |
| Pantalla en blanco | El backend o el frontend no están corriendo | Revisa que las terminales sigan abiertas |
| No puedes volver a ver un producto retirado | Hay que filtrar por «Retirados» en la administración | Ver la tabla de la sección 3 |
| No sabes si algo es un error o está bien | El contrato es la referencia | Pregunta en el grupo antes de abrir la tarjeta |
| Lighthouse no aparece en `F12` | Navegador distinto de Chrome o Edge | Usa Chrome para esta parte |
| `git switch` dice que una rama no existe | No has traído las ramas remotas | `git fetch origin` y repite |

---

_Última actualización: 2026-10-24_
