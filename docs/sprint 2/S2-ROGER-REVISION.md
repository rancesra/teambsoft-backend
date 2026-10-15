# Guía de S2-R — Revisión y pruebas manuales (tarea de peso bajo)

**Para:** Roger Sergio · **Tareas:** S2-R1 (probar las vistas de lectura), S2-R2 (revisar los textos en español), S2-R3 (opcional: segunda mirada al PR de F2) · **Repositorio:** [teambsoft-frontend](https://github.com/rancesra/teambsoft-frontend) · **Rama:** `s2-revision` (solo si cambias archivos) · **Entrega:** viernes 30 de octubre

> **Peso bajo, y no bloquea nada.** Ninguna otra tarea depende de esta, y no tienes que modificar código para cumplirla. Si algo no queda terminado, el sprint se considera cumplido igual. Haz lo que alcances, en el orden de abajo.

F2 (vistas de lectura) pasó a Rances este sprint, así que no la construyes. Pero **conoces esas pantallas**, y eso te hace muy buen probador: sabes qué debería pasar.

Al terminar, el equipo tendrá una lista de fallos y de textos mejorables de las vistas de lectura, con evidencia, antes de la entrega.

**Dependes de:**

1. Que Rances tenga F2 en su rama `f2-lectura` o ya en `main`. Mientras no exista, no hay nada que probar: espera o pásate a S2-R2 con lo que haya.
2. Para ver productos de verdad, que **B3** esté en `main`. Si no, llena el catálogo con productos de prueba (sección 3).

Los comandos son para **PowerShell**, en la terminal de VS Code.

## 1. Antes de empezar

```powershell
cd C:\dev\teambsoft-frontend
git fetch origin
git switch f2-lectura
git pull
npm install
```

Si F2 ya está en `main`, usa `git switch main` y `git pull` en vez de lo de arriba.

**No hagas commits en `f2-lectura`**: es la rama de Rances. Si luego decides cambiar textos, crea tu propia rama con `git switch -c s2-revision` desde `main`.

Lee del `CONTRATO-CATALOGO.md` las secciones 2 y 3 (`GET /productos`, `GET /productos/{id}` y el modelo de Producto).

## 2. Qué archivos vas a tocar

Ninguno, salvo que en S2-R2 decidas proponer cambios de texto. En ese caso:

```
src/
├── componentes/*.vue      solo textos visibles
└── vistas/*.vue           solo textos visibles
```

## 3. S2-R1 — Probar las vistas de lectura

Necesitas el backend corriendo y productos en la base.

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

Si el catálogo está vacío, la guía de F2 explica cómo llenarlo (con la extensión **REST Client** y un archivo `pruebas.http` que **no se sube**, o con un comando directo a Mongo). Pídele a Rances el comando si no lo tienes a mano.

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

Además, prueba dos cosas que la tabla no cubre:

1. **Pantalla angosta** (estrecha la ventana o usa las herramientas de desarrollo del navegador): el detalle debe pasar a una sola columna.
2. **Nombres largos:** un producto con nombre de tres líneas **no debe desalinear** los precios de la rejilla.

**Cómo reportar un fallo**, para que Rances pueda arreglarlo sin preguntarte nada: una tarjeta en Jira con (a) qué hiciste, (b) qué esperabas, (c) qué pasó y (d) una captura de pantalla.

## 4. S2-R2 — Revisar los textos en español

Un producto en español se nota en los detalles. Revisa estos textos visibles que ya existen en las vistas:

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

Revisa tres cosas: **ortografía y tildes**, **que el singular y el plural funcionen** (¿qué dice el encabezado con un solo producto?, ¿y «Quedan 1»?) y **que el tono sea el mismo** en todas las pantallas.

Entrégalo como una lista en un comentario de la tarjeta de Jira: texto actual → texto propuesto → por qué. **No cambies textos directamente en el código de otra persona**: propón y que Rances decida. Si Rances te pide que los cambies tú, entonces sí haz tu rama `s2-revision` y un PR chico.

Ojo: el texto de `MensajeError` para cada código de error no se cambia a la ligera, porque ese componente traduce por **código** (`SIN_CONEXION`, `PRODUCTO_NO_ENCONTRADO`, `VALIDACION_FALLIDA`, `STOCK_INSUFICIENTE`) y lo comparten otras vistas. Si propones cambiar uno, di en qué pantallas aparece.

## 5. S2-R3 — Segunda mirada al PR de F2 (opcional)

Cuando Rances abra el PR `F2: listado y detalle de productos`, Juan Diego y Carlos son los revisores oficiales. Si te sobra tiempo, déjale comentarios tú también. Fíjate en las tres reglas del contrato que más se olvidan:

1. **La primera página es la 1**, no la 0.
2. **El producto trae el id de su categoría** (`cat-ropa`); para mostrar el nombre hay que cruzarlo con `GET /categorias`.
3. **El detalle de un producto retirado responde 200 con `activo: false`**, no 404.

No necesitas aprobar ni rechazar: tus comentarios son una ayuda extra.

## 6. Entregar tu trabajo

Si solo probaste y revisaste (lo normal), no hay nada que subir a Git:

- Deja los fallos como tarjetas en **Jira** y la lista de textos como comentario.
- Pasa tus tarjetas a *Listo* al terminar.

Si cambiaste textos con permiso de Rances:

```powershell
git status
git add .
git commit -m "Corrige textos de las vistas de lectura"
git pull origin main
npm run build
git push
```

- Pull request con base `main`, título `S2: revision de textos`, revisión de Juan Diego o Carlos.

## 7. Si algo falla

| Síntoma | Causa | Qué hacer |
|---|---|---|
| `git switch f2-lectura` dice que no existe | No has traído las ramas remotas | `git fetch origin` y repite |
| La rejilla sale vacía | No hay productos en la base, o B3 no está corriendo | Llena el catálogo (ver guía de F2, sección 7) |
| El paginador no aparece | Hay 20 productos o menos: con una sola página se oculta a propósito | Crea más productos |
| Pantalla en blanco | El backend o el frontend no están corriendo | Revisa que las dos terminales sigan abiertas |
| No sabes si algo es un error o está bien | El contrato es la referencia | Pregunta en el grupo antes de abrir la tarjeta |

---

_Última actualización: 2026-10-15_
