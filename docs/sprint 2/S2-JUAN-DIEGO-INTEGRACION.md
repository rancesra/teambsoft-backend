# Guía de S2-J — Estilos, Host App y revisión de F2

**Para:** Juan Diego · **Tareas:** S2-J1 (auditoría de colores), S2-J2 (módulo dentro del Host App), S2-J3 (revisión del PR de F2) · **Repositorio:** [teambsoft-frontend](https://github.com/rancesra/teambsoft-frontend) · **Rama:** `s2-integracion` · **Entrega:** viernes 22 de octubre

Al terminar, el módulo de Catálogo respetará de verdad las variables de estilo del contrato con el Host App, se habrá probado corriendo dentro del cascarón, y el PR de F2 de Rances habrá pasado una revisión con criterios claros.

**Dependes de:**

1. **F1 en `main`** (ya lo hiciste tú).
2. **S2-J1** puedes empezarla ya sobre F1; la parte que toca F2 espera a que el PR de Rances esté en `main`.
3. **S2-J3** empieza cuando Rances abra el PR de F2.

Los comandos son para **PowerShell**, en la terminal de VS Code.

## 1. Antes de empezar

```powershell
cd C:\dev\teambsoft-frontend
git switch main
git pull
npm install
git switch -c s2-integracion
git push -u origin s2-integracion
```

Lee del `CONTRATO-HOSTAPP.md` las secciones 2, 3, 4 y 6. La 6 es la lista de variables de estilo; las otras tres describen cómo se integra un módulo con el cascarón. **Esta guía no las repite**, porque son la fuente oficial.

## 2. Qué archivos vas a tocar

```
src/
├── estilos.css                  posible: solo si se acuerda una variable nueva
├── componentes/*.vue            reemplazar colores a mano por variables
└── vistas/*.vue                 igual
```

Lo que más vas a producir es una **lista de hallazgos** (en Jira o en la descripción del PR); el código que cambies debe ser poco.

## 3. S2-J1 — Auditoría de colores a mano

**Por qué.** El contrato dice que **ningún componente escribe colores a mano**: todos usan las variables `--acento`, `--acento-suave`, `--texto`, `--texto-suave`, `--borde`, `--fondo`, `--superficie`, `--ok`, `--aviso`, `--error`, `--error-suave`. Si un color queda escrito como `#f0efed`, cuando el Host App cambie su tema ese componente se quedará con el color viejo.

**Qué hacer**

1. Busca todos los colores escritos a mano en los componentes y las vistas (el archivo `estilos.css` se excluye: ahí es donde se definen las variables):

   ```powershell
   Get-ChildItem src -Recurse -Include *.vue | Select-String -Pattern '#[0-9a-fA-F]{3,8}\b'
   ```

2. Haz una tabla con cada hallazgo: archivo, línea, color y para qué se usa.
3. Para cada uno decide:
   - **Hay una variable equivalente** → reemplázalo por `var(--...)`. Antes de elegir, abre `estilos.css` y mira el **valor real** de cada variable; no las elijas por el nombre.
   - **No hay variable equivalente** → **no inventes una**. Las variables son parte del contrato con el Host App; agrégalo a la lista de «necesita decisión» y llévasela a Hector.

**Hallazgos que ya se conocen en el código de la guía de F2** (compruébalos cuando F2 esté en `main`):

| Dónde | Color | Para qué se usa |
|---|---|---|
| `TarjetaProducto.vue`, `VistaDetalle.vue` | `#f0efed` | Fondo de la foto y de los esqueletos |
| `TarjetaProducto.vue`, `VistaDetalle.vue` | `#a8a29e` | Texto «sin imagen» |
| `TarjetaProducto.vue` | `#fffbeb` | Fondo del stock «Quedan N» |
| `TarjetaProducto.vue`, `VistaDetalle.vue` | `#f5f5f4` | Fondo del stock «Sin stock» |
| `VistaDetalle.vue` | `#fdf4e6` y `#ebd3a6` | Fondo y borde del aviso de producto retirado |
| `VistaListado.vue` | `#fff` | Texto del chip activo |

Probablemente varios de estos no tengan variable equivalente, y esa es justo la conversación que hay que tener con Hector antes de tocar el contrato.

**Cómo saber que está listo:** el comando de búsqueda solo devuelve colores que están en la lista de «necesita decisión», y esa lista está entregada a Hector.

## 4. S2-J2 — El módulo dentro del Host App

Hasta ahora el módulo se probó en modo independiente (`npm run dev`). Falta verlo dentro del cascarón.

**Qué hacer**

1. Trae y levanta el Host App según las instrucciones de su repositorio ([teambsoft-hostapp](https://github.com/rancesra/teambsoft-hostapp)) y el `CONTRATO-HOSTAPP.md`.
2. Con el backend corriendo (`docker compose up -d` y `.\mvnw.cmd spring-boot:run` en `teambsoft-backend`), monta el módulo de Catálogo en el Host App.
3. Comprueba esto, punto por punto:

   | Qué compruebas | Qué debe pasar |
   |---|---|
   | Encabezado | **Solo uno**: el del cascarón. `App.vue` del módulo no debe pintar el suyo |
   | Rutas | El listado vive en `/catalogo`, el detalle en `/catalogo/<id>`, la administración en `/catalogo/admin` |
   | Estilos | Si el Host App cambia el valor de `--acento`, los chips y botones del módulo cambian de color |
   | Llamadas al backend | Salen por `/api/catalogo` (Kong), no directo al puerto 8080 |
   | Recargar en `/catalogo?categoria=cat-hogar&pagina=2` | Se conserva el filtro y la página |
   | Botón «atrás» | Vuelve a la página anterior, no sale del módulo |

4. Cada fila que falle es una tarjeta en Jira con captura. Si el fallo está del lado del Host App y no del módulo, avísale a Hector para que lo lleve a ese equipo.

**Cómo saber que está listo:** la tabla está completa con ✅ / ❌ en la descripción del PR.

## 5. S2-J3 — Revisión del PR de F2

Cuando Rances abra el PR `F2: listado y detalle de productos`, revísalo con esta lista. Son las reglas del contrato que más se olvidan:

| Qué revisas | Cómo lo compruebas |
|---|---|
| La primera página es la 1, no la 0 | El paginador y `pagina` en la URL arrancan en 1 |
| La categoría se muestra con nombre, no con id | Las tarjetas dicen «ropa», no `cat-ropa` |
| El filtro y la página viven en la URL | Filtra, recarga: sigue filtrado; «atrás» funciona |
| Cambiar de categoría vuelve a la página 1 | Ve a la 2, cambia de categoría: queda en la 1 |
| Producto retirado: 200 con `activo: false`, **sin redirigir ni mostrar 404** | Se ve con aviso amarillo, gris y sin el recuadro del Carro |
| Id inexistente | «Este producto no existe o fue retirado del catálogo» |
| Backend apagado | «No se pudo conectar con el servidor» y botón Reintentar |
| `npm run build` | Sin errores |
| `git status` | Sin `node_modules/` ni `dist/` en el PR |

Deja los comentarios en el PR de GitHub. Aprueba solo cuando todo lo anterior pase; si algo falla, di **qué regla del contrato se rompe** además de qué línea.

## 6. Subir tu trabajo

```powershell
git status
git add .
git commit -m "Reemplaza colores a mano por variables y documenta prueba en Host App"
git pull origin main
npm run build
git push
```

- Pull request con base `main`, título `S2: estilos y prueba en Host App`, revisión de Carlos o Roger.
- En **Jira**, pasa S2-J1, S2-J2 y S2-J3 a *En revisión*, y a *Listo* cuando se una el PR.

## 7. Si algo falla

| Síntoma | Causa | Qué hacer |
|---|---|---|
| El comando de búsqueda no devuelve nada | PowerShell no encontró la carpeta | Ejecútalo desde la raíz del repositorio |
| Reemplazaste un color y el componente se ve distinto | La variable tiene otro valor del esperado | Mira el valor real en `estilos.css` antes de elegir |
| El módulo muestra dos encabezados dentro del Host App | `App.vue` está pintando el suyo | Debe verse solo en modo independiente |
| Las llamadas van al puerto 8080 en vez de a Kong | `VITE_API_URL` no apunta a Kong | Usa `http://localhost:8000/api/catalogo` |
| El PR de F2 todavía no existe | Rances sigue trabajando | Haz primero S2-J1 sobre F1 y S2-J2; la revisión espera |

---

_Última actualización: 2026-10-15_
