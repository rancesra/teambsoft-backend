# Guía de S3-H — Coordinación, protección de `main`, decisiones y cierre del Sprint 3

**Para:** Hector Julian  · **Tareas:** S3-H1 (gestión del sprint), S3-H2 (proteger `main` y clave de Jira), S3-H3 (decisiones abiertas y variables de color del Host App), S3-H4 (prueba de integración de cierre) · **Repositorios:** [teambsoft-backend](https://github.com/rancesra/teambsoft-backend), [teambsoft-hostapp](https://github.com/rancesra/teambsoft-hostapp) y [teambsoft-frontend](https://github.com/rancesra/teambsoft-frontend) · **Rama:** `s3-coordinacion` (una en cada repositorio que toques) · **Entrega:** jueves 29 de octubre

Al terminar, los tres repositorios tendrán `main` protegido, cada rama y commit llevará la clave de Jira, las decisiones que quedaron abiertas tendrán respuesta escrita, el contrato del Host App tendrá las variables de color nuevas (v1.2) y habrá una prueba de punta a punta con su resultado documentado.

**Dependes de:**

1. **S3-H1, S3-H2 y S3-H3** no dependen de nadie: empiézalas ya.
2. **Juan Diego (S3-J2) te espera a ti.** No puede reemplazar los colores hasta que decidas qué variables entran al contrato. Tu fecha límite para esa parte es el **lunes 26 de octubre**.
3. **S3-H4** depende de que estén en `main` los PR de Jhon, Rances, Juan Diego y Carlos. Si alguno no llega, haz la prueba con lo que haya y anota qué faltó.

Los comandos son para **PowerShell**, en la terminal de VS Code. Usa `curl.exe` y no `curl`: en PowerShell `curl` es un alias de otro comando.

## 1. Antes de empezar

Backend:

```powershell
cd C:\dev\teambsoft-backend
git switch main
git pull
git switch -c s3-coordinacion
git push -u origin s3-coordinacion
```

Las ramas del Host App y del frontend las creas cuando llegues a esa parte (secciones 4 y 5).

## 2. Qué archivos vas a tocar

```
teambsoft-backend/
├── GUIA-GIT.md                      sección nueva: clave de Jira (S3-H2)
└── docs/
    ├── DECISIONES-SPRINT3.md        NUEVO  (S3-H3)
    └── PRUEBA-INTEGRACION-S3.md     NUEVO  (S3-H4)

teambsoft-frontend/
└── GUIA-GIT.md                      la misma sección nueva (S3-H2)

teambsoft-hostapp/
├── CONTRATO-HOSTAPP.md              v1.2: variables de color (S3-H3)
└── src/
    ├── estilos.css                  variables nuevas (S3-H3)
    └── componentes/MarcoModulo.vue  usa una variable en vez de un color a mano (S3-H3)
```

## 3. S3-H1 — Gestión del sprint

**Qué hacer**

1. En Jira, crea el tablero del Sprint 3 (del 23 al 29 de octubre) con una tarjeta por tarea:

   | Integrante | Tarea | Peso | Bloquea a otros |
   |---|---|---|---|
   | Rances | S3-R — Prueba automática de eventos y evaluación del Outbox | Alto | — |
   | Jhon | S3-B4 — Healthcheck, credenciales, Swagger restringido y base de pruebas | Alto | — |
   | Juan Diego | S3-J — Módulos de prueba en el Host App y variables de color | Medio | — |
   | Carlos | S3-C — Pruebas de las vistas de listado y detalle | Medio | — |
   | Hector | S3-H — Coordinación, `main` protegido, decisiones e integración | Medio | Juan Diego (variables de color) |
   | Cristian | S3-K — Documentación del backend | **Bajo** | **Nada** |
   | Roger | S3-R-REV — Revisión manual y accesibilidad | **Bajo** | **Nada** |

2. Marca en Jira que las tarjetas de **Cristian y Roger son opcionales para el cierre**: si no se terminan, el sprint se considera cumplido.
3. Acuerda la rutina: una *daily* corta por escrito en el grupo y estas fechas:

   | Fecha | Hito |
   |---|---|
   | Sáb 24 – dom 25 oct | Planeación: cada quien lee su guía y confirma que la entiende |
   | Lun 26 oct | Tú publicas las decisiones de variables de color (S3-H3) y `main` queda protegido (S3-H2) |
   | Mar 27 oct | Revisión intermedia: todos los PR de las tareas de peso alto y medio deben estar abiertos |
   | Mié 28 oct | Prueba de integración de cierre (S3-H4) |
   | Jue 29 oct | Entrega, sprint review y retrospectiva |

4. **Orden de unión recomendado**, para evitar conflictos conocidos:
   - Backend: primero Jhon (cambia `docker-compose.yml`, `application.yml` y los perfiles de prueba), después Rances (su prueba de eventos usa esa configuración).
   - Host App: primero tú (`estilos.css`), después Juan Diego (su módulo de prueba).
   - Frontend: Carlos y Juan Diego tocan los mismos `.vue` (Carlos solo agrega pruebas y, a lo sumo, atributos `data-testid`; Juan Diego cambia colores). Es un conflicto fácil, pero avísales.

**Cómo saber que está listo:** el tablero existe, cada tarjeta tiene dueño y fecha, y todos saben cuáles son opcionales.

## 4. S3-H2 — Proteger `main` y la clave de Jira

### 4.1 Proteger `main` en los tres repositorios

Repite esto en **teambsoft-backend**, **teambsoft-frontend** y **teambsoft-hostapp**. Necesitas permisos de administrador en cada uno (si no los tienes, pídeselos a Rances antes de empezar).

1. En GitHub, entra al repositorio → **Settings** → **Rules** → **Rulesets** → **New ruleset** → **New branch ruleset**.
2. **Ruleset name:** `proteger-main`. **Enforcement status:** *Active*.
3. En **Target branches** → **Add target** → **Include default branch**.
4. Activa estas reglas (y deja el resto apagado):
   - **Restrict deletions**
   - **Block force pushes**
   - **Require a pull request before merging**, con **Required approvals: 1** y *Require conversation resolution before merging*.
5. **No** actives *Require status checks*: no tenemos integración continua todavía.
6. Deja la lista de *Bypass* vacía. Si un PR se queda trabado porque nadie puede revisarlo, se lo pides a Rances; no se desactiva la regla.
7. **Create**.

> **Si la opción no aparece** o dice que necesita un plan de pago: los repositorios privados en una cuenta gratuita no admiten reglas de rama. Opciones: activar GitHub Education/Pro con el paquete de estudiantes, o volver públicos los repositorios. Si ninguna es posible, déjalo como **acuerdo escrito** en `DECISIONES-SPRINT3.md` y avisa en el grupo: «nadie sube a `main` directo» (ya está en la guía de git, pero ahora sin ayuda de GitHub).

**Cómo comprobar que funciona**, en cada repositorio, con el árbol de trabajo limpio (`git status` debe decir `working tree clean`):

```powershell
git switch main
git pull
git commit --allow-empty -m "Prueba de proteccion de main"
git push
```

Debe **rechazarlo** (algo como `Repository rule violations found` o `protected branch hook declined`). Deshaz el commit de prueba:

```powershell
git reset --hard origin/main
```

Si el `push` fue aceptado, la regla no está activa: revisa que el *Enforcement* sea *Active* y que apunte a la rama por defecto.

### 4.2 Clave de Jira en ramas, commits y PR

1. Abre cualquier tarjeta de Jira del proyecto: su identificador (por ejemplo `PROY-12`) tiene la **clave** antes del guion. Anótala y publícala en el grupo.
2. Agrega esta sección a `GUIA-GIT.md` del **backend** y del **frontend**, antes de «Conflictos». Cambia `CLAVE` por la clave real:

   ```markdown
   ## Clave de Jira en ramas, commits y pull requests

   Cada tarea tiene una tarjeta en Jira con un identificador (por ejemplo `CLAVE-12`). Ese identificador va en tres sitios, para que Jira enlace solo el trabajo con la tarjeta:

   | Dónde | Formato | Ejemplo |
   |---|---|---|
   | Rama | `CLAVE-N-nombre-corto` | `CLAVE-12-s3-healthcheck` |
   | Commit | `CLAVE-N: lo que hiciste` | `CLAVE-12: Agrega healthcheck al servicio de catalogo` |
   | Título del PR | `CLAVE-N: tarea y qué entrega` | `CLAVE-12: S3 healthcheck y credenciales` |

   Si una tarea tiene varias tarjetas, usa la de la subtarea que estás tocando.
   ```

3. Cuando alguien ya tenga una rama sin la clave, **no hace falta renombrarla**: basta con que el título del PR y los commits nuevos la lleven.

**Cómo saber que está listo:** el `push` directo a `main` falla en los tres repositorios y la clave está publicada en el grupo.

## 5. S3-H3 — Decisiones abiertas y variables de color

### 5.1 Las decisiones

El Sprint 2 dejó decisiones sin respuesta. Tu tarea es **conseguir la respuesta y dejarla escrita**; las que afectan a otros equipos no las decides tú solo.

| # | Decisión | Opciones | A quién preguntar | Plan provisional si no hay respuesta |
|---|---|---|---|---|
| 1 | ¿Quién opera el servidor de **Eureka**? | Un equipo lo opera para los tres, o cada uno usa uno local | Equipos A y C | Se sigue con el cliente deshabilitado en el compose; no bloquea nada |
| 2 | ¿Quién opera **Kong**? | Igual que arriba | Equipos A y C | El Kong del compose queda solo para desarrollo de Catálogo |
| 3 | El botón **«Agregar al carro»** del detalle | Catálogo lo pinta y emite algo, o el Host App monta ahí un componente de Carrito | Equipo C y Host App (tú) | El detalle conserva el recuadro «Espacio reservado · Carro (Equipo C)» |
| 4 | Qué se muestra en `/` | Seguir redirigiendo a `/catalogo`, o hacer una portada | Los tres equipos | Sigue redirigiendo a `/catalogo` |
| 5 | Cuándo pasar a la etapa 2 (Module Federation) | Este sprint, el próximo o nunca | Los tres equipos | No se pasa en este sprint |
| 6 | Variables de color nuevas | Las decides tú (sección 5.2) | Nadie | — |

**Qué hacer**

1. Escribe a los equipos A y C. Puedes usar este mensaje:

   > Hola. Estamos cerrando decisiones de integración del Host App. Necesitamos su respuesta **antes del lunes 26 de octubre a las 8 p. m.**: (1) ¿quién levanta el servidor de Eureka y quién el de Kong para los tres módulos, o cada equipo usa el suyo local? (2) El botón «Agregar al carro» en el detalle de Catálogo: ¿lo pinta Catálogo y les avisa, o ustedes montan un componente en un espacio que dejamos? (3) ¿Hacemos una portada en `/` o seguimos redirigiendo a `/catalogo`? Si no hay respuesta, seguimos con el plan provisional de cada punto. Además: para el miércoles 28 les pedimos que su módulo esté corriendo en una dirección para probarlo dentro del Host App (Juan Diego les pasa la lista de requisitos).

2. Registra cada respuesta en `docs/DECISIONES-SPRINT3.md` con este formato:

   ```markdown
   # Decisiones del Sprint 3

   ## 1. Servidor de Eureka
   - **Fecha:** 2026-10-__
   - **Decisión:** ...
   - **Quién decidió:** ...
   - **Qué cambia para Catálogo:** ...
   - **Quién tiene que hacer algo:** ...
   ```

3. Si pasada la fecha límite no hay respuesta, regístrala como **«sin decidir — se sigue con el plan provisional»**. Una decisión pendiente documentada vale más que una olvidada.
4. Cuando haya respuesta, avisa a quien le afecta (Jhon para 1 y 2, Rances y Juan Diego para 3).

### 5.2 Variables de color nuevas (tarea técnica pequeña)

La auditoría de S2-J dejó colores escritos a mano que no tienen variable. Tú decides cuáles entran al contrato. Esto desbloquea a Juan Diego, así que hazlo primero.

**Paso 1 — Consigue la lista.** Pídele a Juan Diego la lista de «necesita decisión» de S2-J (está en la descripción de su PR `S2: estilos y prueba en Host App`). Los colores conocidos son estos:

| Dónde | Color | Para qué se usa |
|---|---|---|
| `TarjetaProducto.vue`, `VistaDetalle.vue` | `#f0efed` | Fondo de la foto y de los esqueletos |
| `TarjetaProducto.vue`, `VistaDetalle.vue` | `#a8a29e` | Texto «sin imagen» |
| `TarjetaProducto.vue` | `#fffbeb` | Fondo del stock «Quedan N» |
| `TarjetaProducto.vue`, `VistaDetalle.vue` | `#f5f5f4` | Fondo del stock «Sin stock» |
| `VistaDetalle.vue` | `#fdf4e6` y `#ebd3a6` | Fondo y borde del aviso de producto retirado |
| `VistaListado.vue` | `#fff` | Texto del chip activo |

**Paso 2 — Aplica este criterio a cada color.** Una variable nueva entra al contrato solo si:

1. Se usa en **dos o más sitios**, o representa un estado reutilizable (aviso, deshabilitado, fondo de relleno).
2. **No existe ya** una variable con un valor equivalente.
3. Si es un color de **texto**, tiene contraste suficiente sobre su fondo (mínimo 4.5:1). Puedes comprobarlo en https://webaim.org/resources/contrastchecker/.

**Paso 3 — Una propuesta para que decidas rápido.** Cambia lo que quieras:

| Color | Propuesta | Razón |
|---|---|---|
| `#f0efed` y `#f5f5f4` | Variable nueva `--fondo-suave: #f0efed` (unifica los dos) | Se usa en foto, esqueleto y «Sin stock»; la diferencia entre los dos grises es imperceptible |
| `#fffbeb` y `#fdf4e6` | Variable nueva `--aviso-suave: #fffbeb` (unifica los dos) | Mismo caso para «Quedan N» y el aviso de retirado |
| `#ebd3a6` | Variable nueva `--aviso-borde: #ebd3a6` | Solo se usa en un sitio, pero va ligado a `--aviso-suave` |
| `#fff` en el chip activo y en `.boton` | Variable nueva `--texto-sobre-acento: #ffffff` | Texto encima del color de acento |
| `#a8a29e` («sin imagen») | **No crear variable.** Usar `--texto-suave` (`#57534e`) | Sobre `#f0efed` el contraste es de aproximadamente 2:1, por debajo del 4.5:1. Es un problema de accesibilidad, no de variables |

**Paso 4 — Corrige una incoherencia que ya existe.** El `estilos.css` del Host App define `--acento-suave`, `--error-suave`, `--radio-chico` y `--fuente`, y la tabla de la sección 6 del contrato no los lista (y dice que `--radio` vale «8px / 12px», cuando en el código `--radio-chico` es 8px y `--radio` es 12px). Documéntalos en la misma actualización.

**Paso 5 — Aplica el cambio en el Host App.**

```powershell
cd C:\dev\teambsoft-hostapp
git switch main
git pull
git switch -c s3-coordinacion
git push -u origin s3-coordinacion
```

1. En `src/estilos.css`, agrega las variables aceptadas dentro de `:root`, con los mismos valores de la tabla.
2. En `src/componentes/MarcoModulo.vue`, cambia el `background: #f0efed;` de `code` por `var(--fondo-suave)` (solo si la aceptaste).
3. En `src/estilos.css`, cambia el `color: #fff;` de `.boton` por `var(--texto-sobre-acento)` (solo si la aceptaste).
4. En `CONTRATO-HOSTAPP.md`:
   - Sube la versión a **1.2** y la fecha a la de hoy.
   - En la tabla de la sección 6, agrega las variables nuevas y las que faltaban (paso 4), y corrige la fila de `--radio`.
   - Agrega una nota en la sección 6: *«Con iframes (etapa 1), cada módulo tiene su propio `:root`: las variables no se heredan del Host App. Son una convención de valores idénticos, y cada módulo debe definirlas en su propio `estilos.css`.»*
   - Agrega la fila de v1.2 al historial de cambios.
5. Comprueba:

   ```powershell
   npm install
   npm run build
   npm run dev
   ```

   Abre http://localhost:5050 y revisa que el encabezado y los avisos del cascarón se vean igual que antes.
6. Publica en el grupo la tabla final de variables y avisa a Juan Diego: **esa es su señal para empezar S3-J2.**

**Cómo saber que está listo:** el contrato está en v1.2, Juan Diego tiene la tabla final y `DECISIONES-SPRINT3.md` tiene las seis decisiones registradas (aunque sea como «sin decidir»).

## 6. S3-H4 — Prueba de integración de cierre

Hazla el **miércoles 28 de octubre**, cuando los PR de las otras tareas estén en `main`.

**Preparación**

```powershell
cd C:\dev\teambsoft-backend
git switch main
git pull
docker compose up -d --build
docker compose ps
```

Si Jhon cambió las credenciales, antes necesitas el archivo `.env` (copia `.env.example` a `.env`); el detalle está en su guía.

**Prueba del backend a través de Kong** (puerto 8000, con el prefijo `/api/catalogo`):

| Qué haces | Qué debe pasar |
|---|---|
| `docker compose ps` | Mongo, RabbitMQ, catálogo y Kong aparecen `(healthy)` (el de catálogo es nuevo en este sprint) |
| `curl.exe http://localhost:8080/actuator/health` | `"status":"UP"` y **sin detalles internos** si Jhon los ocultó |
| `GET /api/catalogo/categorias` | 200 con las tres categorías |
| `POST /api/catalogo/productos` con un producto válido | 201 |
| `GET /api/catalogo/productos` | 200, el producto nuevo aparece |
| `DELETE /api/catalogo/productos/<id>` y luego `GET` del mismo id | 204 y luego 200 con `activo: false` |
| `POST /api/catalogo/productos/descontar-stock` pidiendo más de lo que hay | 409 `STOCK_INSUFICIENTE` |
| `GET /api/catalogo/actuator/health` | 404 de Kong (no debe quedar expuesto) |
| `GET /api/catalogo/swagger-ui/index.html` | 404 de Kong (si Jhon lo restringió; si no, anótalo) |
| `GET /api/inventado` | 404 de Kong |

Para el cuerpo de las peticiones usa `docs/ejemplos-api.http` si Cristian ya lo entregó; si no, el cuerpo del descuento de stock está en `DescuentoStockRequest.java`.

**Pruebas automáticas:**

```powershell
cd backend   # si tu repositorio lo tiene en esa carpeta; si no, queda en la raíz
.\mvnw.cmd test
```

Deben pasar todas, incluida la nueva prueba de eventos de Rances.

**Prueba de eventos:** repite la secuencia del Sprint 2 en la consola de RabbitMQ (http://localhost:15672, con las credenciales nuevas si cambiaron): crear, actualizar, descontar, desactivar ×2, reactivar ×2. Deben quedar **5 mensajes** en `busqueda.productos` y **3** en `carro.productos`.

**Prueba del Host App con los tres módulos:**

```powershell
cd C:\dev\teambsoft-hostapp
git switch main
git pull
npm install
npm run dev
```

Abre http://localhost:5050 y comprueba: Catálogo carga (con `npm run dev` del frontend corriendo en otra terminal), y los módulos de prueba o los reales de A y C cargan en `/busqueda` y `/carrito` (Juan Diego te dice cómo levantarlos). Un módulo apagado debe mostrar «no está disponible» con botón de reintentar.

**Resultado:** copia las tablas en `docs/PRUEBA-INTEGRACION-S3.md` con una columna extra «Resultado» (✅ / ❌ y una nota). Cada ❌ es una tarjeta nueva en Jira, asignada a quien corresponda.

## 7. Subir tu trabajo

En cada repositorio donde hayas hecho cambios:

```powershell
git status
git add <solo los archivos de ese repositorio>
git commit -m "CLAVE-N: Describe lo que hiciste"
git pull origin main
git push
```

- En `git status` **no debe aparecer `.env`, `pruebas.http`, `target/` ni `node_modules/`**.
- Un pull request por repositorio, con base `main`. Revisores sugeridos: Juan Diego (backend y frontend), Carlos (Host App).
- Los PR de las tareas de otros los revisas tú **solo si te los piden**: tu prioridad es la coordinación.
- En **Jira**, pasa S3-H1 a S3-H4 a *En revisión*, y a *Listo* cuando se unan los PR.

## 8. Si algo falla

| Síntoma | Causa | Qué hacer |
|---|---|---|
| No aparece la opción de rulesets | Repositorio privado en cuenta gratuita | Ver el aviso de la sección 4.1 |
| El `push` de prueba a `main` fue aceptado | La regla no está activa o apunta a otra rama | Revisa *Enforcement: Active* y que el objetivo sea la rama por defecto |
| `git reset --hard origin/main` borró algo | Tenías cambios sin guardar | Antes del comando, `git status` debe estar limpio |
| `curl` en PowerShell pide parámetros raros | `curl` es un alias de PowerShell | Usa `curl.exe` |
| `docker compose up` falla con `.env` ausente | Jhon exige credenciales por variables | Copia `.env.example` a `.env` |
| Kong responde 200 en `/api/catalogo/actuator/health` | La ruta no está restringida | Devuélvelo a Jhon (sección de Swagger/actuator de su guía) |
| Un módulo de A o C no carga en el iframe | Su servidor envía `X-Frame-Options` o no está corriendo | Revisa con `curl.exe -I <dirección>` y pásale la lista de requisitos de Juan Diego |
| Nadie responde las decisiones | Falta fecha límite | Regístralas como «sin decidir» con el plan provisional |

---

_Última actualización: 2026-10-24_
