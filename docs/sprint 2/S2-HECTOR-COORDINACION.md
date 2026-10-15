# Guía de S2-H — Coordinación y cierre del Sprint 2

**Para:** Hector Julian · **Rol:** Scrum Master · **Tareas:** S2-H1 (gestión del sprint), S2-H2 (decisiones abiertas), S2-H3 (prueba de integración de cierre) · **Repositorio:** [teambsoft-backend](https://github.com/rancesra/teambsoft-backend) · **Rama:** `s2-coordinacion` · **Entrega:** viernes 30 de octubre

Al terminar, el equipo tendrá el sprint ordenado en Jira, las tres decisiones que quedaron abiertas en el Sprint 1 resueltas por escrito, y una prueba de punta a punta (frontend → Kong → backend → RabbitMQ) con su resultado documentado.

**Dependes de:**

1. **S2-H1 y S2-H2 no dependen de nadie**: empiézalas ya.
2. **S2-H3 depende de que estén en `main`:** B2, B3, B4 (eventos, Docker y Kong) y F2. El resumen del Sprint 1 no dice si B2 y B3 ya se unieron: **verifícalo tú primero** (sección 1).

Los comandos son para **PowerShell**, en la terminal de VS Code. Usa `curl.exe` y no `curl`: en PowerShell `curl` es un alias de otro comando y no acepta las mismas opciones.

## 1. Antes de empezar

1. Trae la última versión del backend y crea tu rama:

   ```powershell
   cd C:\dev\teambsoft-backend
   git switch main
   git pull
   git switch -c s2-coordinacion
   git push -u origin s2-coordinacion
   ```

2. Revisa qué hay realmente en `main` (esto decide cuándo puedes hacer S2-H3):

   ```powershell
   git log --oneline -20
   ```

   Busca los PR de B2 (`POST y PUT de productos`), B3 (`listado, detalle y borrado`) y el **PR aparte del descuento de stock** de B2. Si falta alguno, abre una tarjeta en Jira y avisa a su dueño.

3. Ten a mano el resumen del Sprint 1 y los contratos (`CONTRATO-CATALOGO.md`, `CONTRATO-HOSTAPP.md`).

## 2. Qué archivos vas a tocar

```
teambsoft-backend/
└── docs/
    ├── DECISIONES-SPRINT2.md        NUEVO  registro de las 3 decisiones (S2-H2)
    └── PRUEBA-INTEGRACION-S2.md     NUEVO  resultado de la prueba de cierre (S2-H3)
```

No tocas código. Tu trabajo en este sprint es que el código de los demás **encaje**.

## 3. S2-H1 — Gestión del sprint

**Qué hacer**

1. En Jira, crea el tablero del Sprint 2 con una tarjeta por tarea:

   | Integrante | Tarea | Peso | Bloquea a otros |
   |---|---|---|---|
   | Rances | F2 — Vistas de lectura (pendiente del Sprint 1) | Alto | F3 (usa `formato.js`) |
   | Jhon | B4 — Infraestructura y eventos | Alto | — |
   | Juan Diego | S2-J — Estilos, Host App y revisión de F2 | Medio | — |
   | Carlos | S2-C — Pruebas automáticas y verificación de F3 | Medio | — |
   | Hector | S2-H — Coordinación, decisiones e integración | Medio | — |
   | Cristian | S2-K — Documentación del backend | **Bajo** | **Nada** |
   | Roger | S2-R — Revisión y pruebas manuales | **Bajo** | **Nada** |

2. Marca explícitamente en Jira que las tarjetas de Cristian y Roger son **opcionales para el cierre**: si no se terminan, el sprint se considera cumplido.
3. Acuerda la rutina: una *daily* corta (puede ser por escrito en el grupo) y fechas fijas:

   | Fecha | Hito |
   |---|---|
   | Vie 16 oct | Planeación: cada quien confirma que entiende su guía |
   | Vie 23 oct | Revisión intermedia: F2 y los PR de B4 ya deben estar abiertos |
   | Mié 28 – jue 29 oct | Prueba de integración (S2-H3) |
   | Vie 30 oct | Entrega, sprint review y retrospectiva |

4. **Orden de unión recomendado**, para evitar conflictos conocidos: F2 primero (F3 usa su `formato.js`); en el backend, los PR de B4 se unen después de B2 y B3, porque los eventos van dentro de `ProductoService`.

**Cómo saber que está listo:** el tablero está creado, cada tarjeta tiene dueño y fecha, y todos saben cuáles son opcionales.

## 4. S2-H2 — Cerrar las decisiones abiertas

El Sprint 1 dejó tres decisiones sin tomar. Ninguna se resuelve sola, y dos de ellas afectan a otros equipos, así que tu tarea es **conseguir la respuesta y dejarla escrita**, no decidirla tú solo.

| # | Decisión | Opciones | A quién preguntar | Qué pasa si no se decide |
|---|---|---|---|---|
| 1 | ¿Quién opera el servidor de **Eureka**? Es infraestructura compartida por los 3 equipos y ningún contrato lo dice | Un equipo lo opera para todos, o cada equipo usa uno local mientras tanto | Los otros dos equipos | Jhon sigue con un servidor de prueba local; no bloquea B4 |
| 2 | ¿Quién opera **Kong**? Mismo caso: un solo Kong para los tres módulos | Igual que arriba | Los otros dos equipos | El Kong de B4 queda solo para desarrollo de Catálogo |
| 3 | El botón del **Carro** en el detalle: ¿lo pinta Catálogo o lo monta el Host App en el espacio reservado? | Catálogo pinta el botón / Host App lo monta | Equipo C y Host App | `VistaDetalle.vue` conserva el recuadro «Espacio reservado · Carro (Equipo C)» |

**Qué hacer**

1. Escribe a los responsables de cada decisión con la pregunta concreta y una **fecha límite** (sugerido: miércoles 21 de octubre).
2. Registra cada respuesta en `docs/DECISIONES-SPRINT2.md` con este formato:

   ```markdown
   # Decisiones del Sprint 2

   ## 1. Servidor de Eureka
   - **Fecha:** 2026-10-__
   - **Decisión:** ...
   - **Quién decidió:** ...
   - **Qué cambia para Catálogo:** ...
   - **Quién tiene que hacer algo:** ...
   ```

3. Cuando haya respuesta, avisa a quien le afecta: Jhon (1 y 2) y Rances (3).
4. Si pasada la fecha límite no hay respuesta, regístrala igual como **«sin decidir — se sigue con el plan provisional»**. Una decisión pendiente documentada vale más que una olvidada.

## 5. S2-H3 — Prueba de integración de cierre

Hazla cuando F2 y los PR de B4 estén en `main` (idealmente el 28 o 29 de octubre).

**Preparación**

```powershell
cd C:\dev\teambsoft-backend
git switch main
git pull
docker compose up -d
```

Comprueba que los servicios están arriba y sanos:

```powershell
docker compose ps
curl.exe http://localhost:8080/actuator/health
```

Debe responder `"status":"UP"` con `mongo` y `rabbit` en UP.

**Prueba del backend a través de Kong** (puerto 8000, con el prefijo `/api/catalogo`):

| Qué haces | Qué debe pasar |
|---|---|
| `GET /api/catalogo/categorias` | 200 con las tres categorías |
| `POST /api/catalogo/productos` con un producto válido | 201 |
| `GET /api/catalogo/productos` | 200, el producto nuevo aparece |
| `GET /api/catalogo/productos/<id>` | 200 con el producto |
| `DELETE /api/catalogo/productos/<id>` | 204 |
| `GET /api/catalogo/productos/<id>` (ya retirado) | **200** con `activo: false`, no 404 |
| `DELETE` otra vez sobre el mismo id | 204, **sin** evento nuevo en RabbitMQ |
| `POST /api/catalogo/productos/<id>/activar` | 200; repetirlo no cambia nada |
| `POST /api/catalogo/productos/descontar-stock` pidiendo más de lo que hay | **409** `STOCK_INSUFICIENTE` |
| `GET /api/inventado` | 404 de Kong |

El cuerpo exacto del descuento de stock está en `DescuentoStockRequest.java`; no lo copies de memoria.

**Prueba de eventos** (interfaz en http://localhost:15672, usuario `guest` / clave `guest`): con las colas `busqueda.productos` (`producto.*`) y `carro.productos` (`producto.actualizado`, `producto.desactivado`) creadas, repite la secuencia de B4: crear, actualizar, descontar, desactivar ×2, reactivar ×2. Deben quedar **5 mensajes** en la primera y **3** en la segunda. Si salen 7, hay publicaciones duplicadas en operaciones idempotentes: devuelve el caso a Jhon.

**Prueba del frontend contra Kong**, en otra terminal:

```powershell
cd C:\dev\teambsoft-frontend
git switch main
git pull
npm install
$env:VITE_API_URL = "http://localhost:8000/api/catalogo"
npm run dev
```

Abre http://localhost:5173/catalogo y recorre listado, filtro, página 2, detalle, un producto retirado y la administración. Si el navegador se queja de CORS, el problema está en el plugin `cors` de Kong (el CORS se configura **solo** ahí, nunca en Spring).

**Resultado:** copia las dos tablas en `docs/PRUEBA-INTEGRACION-S2.md` con una columna extra «Resultado» (✅ / ❌ y una nota). Cada ❌ es una tarjeta nueva en Jira, asignada a quien corresponda.

## 6. Subir tu trabajo

```powershell
git status
git add docs/
git commit -m "Agrega decisiones y prueba de integracion del sprint 2"
git pull origin main
git push
```

- En `git status` **no debe aparecer `pruebas.http`, `target/` ni `node_modules/`**.
- Pull request con base `main`, título `S2: decisiones y prueba de integracion`, revisión de Jhon.
- En **Jira**, pasa S2-H1, S2-H2 y S2-H3 a *En revisión*, y a *Listo* cuando se una el PR.

## 7. Si algo falla

| Síntoma | Causa | Qué hacer |
|---|---|---|
| `curl` en PowerShell pide parámetros raros o abre una vista web | `curl` es un alias de PowerShell | Usa `curl.exe` |
| RabbitMQ no arranca con `.erlang.cookie: eacces` | Falta `user: rabbitmq` en el compose | Revisa el `docker-compose.yml` de B4 |
| Salen 7 mensajes en vez de 5 y 3 | Se publica en operaciones idempotentes | Devolver el caso a Jhon: el `return` temprano va antes de publicar |
| CORS bloquea el frontend | El plugin `cors` de Kong no tiene el origen `http://localhost:5173` | Revisar `kong/kong.yml`; no agregar CORS en Spring |
| `/api/catalogo/...` da 404 de Kong | Ruta o `strip_path` mal configurados | Revisar `catalogo-ruta` en `kong/kong.yml` |
| El frontend sigue llamando a `/api/catalogo` por el proxy de Vite | La variable de entorno no se definió en esa terminal | Define `$env:VITE_API_URL` en la misma terminal donde corres `npm run dev` |
| Nadie responde las decisiones | Falta fecha límite | Regístralas como «sin decidir» con el plan provisional |

---

_Última actualización: 2026-10-15_
