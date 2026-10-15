# Guía de S2-K — Documentación del backend (tarea de peso bajo)

**Para:** Cristian Rivera · **Tareas:** S2-K1 (README del backend), S2-K2 (ejemplos de la API), S2-K3 (opcional: probar B4 desde cero) · **Repositorio:** [teambsoft-backend](https://github.com/rancesra/teambsoft-backend) · **Rama:** `s2-docs-backend` · **Entrega:** viernes 22 de octubre

> **Peso bajo, y no bloquea nada.** Ninguna otra tarea depende de esta. Si algo no queda terminado, el sprint se considera cumplido igual. Haz lo que alcances, en el orden de abajo.

Al terminar, cualquier persona nueva del equipo podrá levantar el backend y probar la API leyendo el README y el archivo de ejemplos, sin preguntarle a nadie.

**Dependes de:**

1. **S2-K1 y S2-K2** no dependen de nadie: empiézalas ya.
2. **S2-K3** solo se puede hacer cuando Jhon tenga sus PR de B4 (Docker, Kong, eventos) en `main`.

Los comandos son para **PowerShell**, en la terminal de VS Code.

## 1. Antes de empezar

```powershell
cd C:\dev\teambsoft-backend
git switch main
git pull
git switch -c s2-docs-backend
git push -u origin s2-docs-backend
```

Ten a mano `CONTRATO-CATALOGO.md` (secciones 2 a 6) y `GUIA-INICIO.md`. Los datos del README salen de ahí, **no de memoria**.

## 2. Qué archivos vas a tocar

```
teambsoft-backend/
├── README.md                      actualizar
└── docs/
    └── ejemplos-api.http          NUEVO
```

No tocas código Java. El archivo `docs/ejemplos-api.http` es distinto de tu `pruebas.http` personal: **este sí se sube**, y por eso no debe llevar claves ni datos tuyos.

## 3. S2-K1 — README del backend

Que el README responda estas cuatro preguntas, cada una en su sección:

1. **¿Cómo lo levanto?** Los comandos mínimos, tal cual se usan en el equipo:

   ```powershell
   docker compose up -d
   .\mvnw.cmd test
   .\mvnw.cmd spring-boot:run
   ```

2. **¿Qué endpoints tiene?** Una tabla, con lo que hay en el contrato:

   | Método y ruta | Para qué sirve | Respuestas principales |
   |---|---|---|
   | `GET /categorias` | Lista de categorías | 200 |
   | `GET /productos` | Listado paginado (`pagina`, `tamanoPagina`, `categoria`, `activo`) | 200; 400 si un parámetro es inválido |
   | `GET /productos/{id}` | Detalle, incluso de productos retirados | 200 (con `activo: false` si está retirado); 404 si el id no existe |
   | `POST /productos` | Crear | 201; 400 por validación |
   | `PUT /productos/{id}` | Reemplazar el producto completo (no cambia `activo`) | 200; 400; 404 |
   | `DELETE /productos/{id}` | Retirar (no borra el registro) | 204; 404 |
   | `POST /productos/{id}/activar` | Reactivar | 200; 404 |
   | `POST /productos/descontar-stock` | Descuento de stock del Carro («todo o nada») | 200; 409 `STOCK_INSUFICIENTE`; 400 |

   Antes de dar la tabla por buena, **compárala contra `CONTRATO-CATALOGO.md`**; si algo no coincide, el contrato manda y avisa a Hector.

3. **¿Qué errores devuelve?** Los códigos que Catálogo responde: `PRODUCTO_NO_ENCONTRADO` (404), `VALIDACION_FALLIDA` (400) y `STOCK_INSUFICIENTE` (409). Ojo: `SIN_CONEXION` **no** es del backend; es un código que inventa el cliente del frontend cuando el servidor está apagado, no lo pongas aquí.

4. **¿Dónde veo cada cosa?** Una lista de direcciones útiles:

   | Qué | Dirección |
   |---|---|
   | Swagger | `http://localhost:8080/swagger-ui/index.html` |
   | Estado de salud | `http://localhost:8080/actuator/health` |
   | RabbitMQ (interfaz) | `http://localhost:15672` |
   | Eureka | `http://localhost:8761` |
   | Kong (proxy / admin) | `http://localhost:8000/api/catalogo/...` y `http://localhost:8001/status` |

   Las de Swagger, Eureka y Kong **existen solo cuando los PR de B4 estén en `main`**: si aún no se unen, escríbelas igual con la nota «disponible tras B4».

**Cómo saber que está listo:** una persona que no participó en el proyecto puede levantar el backend siguiendo solo el README.

## 4. S2-K2 — Ejemplos de la API

Un archivo `docs/ejemplos-api.http` para la extensión **REST Client** de VS Code, con un caso que funciona y uno que falla por cada endpoint.

Empieza con una variable para la dirección base, de modo que sirva igual contra el backend directo (8080, sin prefijo) o contra Kong (8000, con `/api/catalogo`):

```http
@base = http://localhost:8080

### Listar la primera página
GET {{base}}/productos?pagina=1&tamanoPagina=20

### Error esperado: la primera página es la 1, no la 0 (responde 400)
GET {{base}}/productos?pagina=0

### Crear un producto
POST {{base}}/productos
Content-Type: application/json

{"nombre":"Camiseta basica algodon","descripcion":"Unisex 100% algodon.",
 "precio":49900,"categoria":"cat-ropa","stock":120,"imagenes":[]}

### Error esperado: falta el nombre (responde 400)
POST {{base}}/productos
Content-Type: application/json

{"descripcion":"Sin nombre","precio":49900,"categoria":"cat-ropa","stock":120,"imagenes":[]}
```

Sigue el mismo patrón para el resto. Para el caso de éxito de cada uno, anota en un comentario **qué código debe responder** (es lo que hace útil al archivo).

Tres cosas a tener en cuenta:

- El cuerpo de `POST /productos/descontar-stock` **no lo escribas de memoria**: ábrelo en `DescuentoStockRequest.java` y copia la forma exacta.
- `{{id}}` en las rutas con id: deja un comentario que diga de dónde sacarlo (de la respuesta del `POST`).
- Para mostrar el 409, el ejemplo debe pedir **más stock del que tiene** el producto de la prueba.

## 5. S2-K3 — Probar B4 desde cero (opcional)

Esta es la tarea de «ojos nuevos»: comprobar que lo que Jhon construyó funciona en una máquina limpia, no solo en la suya.

1. Cuando los PR de B4 estén en `main`, clona el repositorio en una carpeta nueva:

   ```powershell
   cd C:\dev
   git clone https://github.com/rancesra/teambsoft-backend.git teambsoft-backend-limpio
   cd teambsoft-backend-limpio
   docker compose up -d --build
   ```

2. Espera a que los servicios estén sanos:

   ```powershell
   docker compose ps
   curl.exe http://localhost:8080/actuator/health
   ```

   Debe responder `"status":"UP"`.
3. Prueba las direcciones de la tabla de la sección 3 y anota cuáles responden.
4. Reporta el resultado en un comentario en el PR de Jhon: qué funcionó y qué no, con el mensaje de error completo si algo falló.

Si no hay tiempo, déjala: el equipo la cubre en la prueba de integración de cierre.

## 6. Subir tu trabajo

```powershell
git status
git add README.md docs/ejemplos-api.http
git commit -m "Actualiza README y agrega ejemplos de la API"
git pull origin main
git push
```

- En `git status` **no debe aparecer `pruebas.http`, `target/` ni claves de ningún tipo**.
- Pull request con base `main`, título `S2: README y ejemplos de la API`, revisión de Hector.
- En **Jira**, pasa tus tarjetas a *En revisión*, y a *Listo* cuando se una el PR.

## 7. Si algo falla

| Síntoma | Causa | Qué hacer |
|---|---|---|
| REST Client no muestra «Send Request» | Falta la extensión o el archivo no termina en `.http` | Instala *REST Client* y revisa el nombre |
| El `GET /productos?pagina=0` responde 200 | Estás probando una versión sin B3 | Haz `git pull` de `main` y reinicia el backend |
| `curl` en PowerShell pide parámetros raros | `curl` es un alias de PowerShell | Usa `curl.exe` |
| `docker compose up` falla por RabbitMQ | Error `.erlang.cookie: eacces` | Falta `user: rabbitmq` en el compose; avísale a Jhon |
| No sabes qué cuerpo lleva un endpoint | El contrato y el código son la fuente | Mira `CONTRATO-CATALOGO.md` o el record de `dto/` |

---

_Última actualización: 2026-10-15_
