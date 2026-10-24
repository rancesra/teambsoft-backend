# Guía de S3-B4 — Healthcheck, credenciales, Swagger restringido y base de pruebas

**Para:** Jhon Jairo · **Tareas:** S3-B4a (healthcheck del contenedor), S3-B4b (credenciales por variables de entorno), S3-B4c (Swagger y actuator restringidos), S3-B4d (base de pruebas separada) · **Repositorio:** [teambsoft-backend](https://github.com/rancesra/teambsoft-backend) · **Ramas:** una por tarea (ver sección 1) · **Entrega:** jueves 29 de octubre

Al terminar, el servicio de catálogo reportará su salud real a Docker, ninguna clave quedará escrita en el repositorio, Swagger y los detalles internos de `/actuator` no se podrán alcanzar a través de Kong, y las pruebas dejarán de escribir en la misma base que el desarrollo.

**Dependes de:**

1. Nadie: B4 ya está en `main`. Las cuatro tareas se pueden empezar ya.
2. **Rances te espera en S3-B4d.** Su prueba automática de eventos usa el perfil `test` que vas a crear. Si puedes, **haz S3-B4d primero**: es la más corta.

> **Esta vez, un PR por tarea.** En el Sprint 2, B4 se publicó como un solo commit de 16 archivos y eso hizo la revisión casi imposible. Aquí son cuatro cambios independientes: cuatro ramas, cuatro PR pequeños. Si el tiempo aprieta, es aceptable unir b y c en uno solo, pero **no los cuatro juntos**.

Los comandos son para **PowerShell**, en la terminal de VS Code. Usa `curl.exe` y no `curl`.

## 1. Antes de empezar

```powershell
cd C:\dev\teambsoft-backend
git switch main
git pull
docker compose up -d --build
docker compose ps
```

Todo debe levantar como terminó el Sprint 2. Después, para cada tarea, crea su rama desde un `main` actualizado:

| Tarea | Rama |
|---|---|
| S3-B4d | `s3-base-pruebas` |
| S3-B4a | `s3-healthcheck` |
| S3-B4b | `s3-credenciales` |
| S3-B4c | `s3-swagger-actuator` |

Si Hector ya publicó la clave de Jira, antepónla: `CLAVE-N-s3-healthcheck`. Ejemplo para la primera:

```powershell
git switch main
git pull
git switch -c s3-base-pruebas
git push -u origin s3-base-pruebas
```

Para las siguientes, vuelve a `main`, haz `git pull` y crea la nueva rama desde ahí.

## 2. Qué archivos vas a tocar

```
teambsoft-backend/
├── .env.example                          NUEVO  (S3-B4b)
├── .gitignore                            agregar `.env`  (S3-B4b)
├── Dockerfile                            solo si falta curl  (S3-B4a)
├── docker-compose.yml                    healthcheck, variables, puertos  (a, b, c)
├── kong/kong.yml                         ruta de bloqueo  (S3-B4c)
└── src/
    ├── main/resources/application.yml    variables con valor por defecto  (b)
    └── test/
        ├── resources/application-test.yml     base de pruebas  (d)
        └── java/.../*Test.java                @ActiveProfiles("test")  (d)
```

> El `docker-compose.yml` y `Dockerfile` pueden estar en la raíz del repositorio o dentro de `backend/`. Usa la ubicación real que tengan hoy.

## 3. S3-B4d — Base de pruebas separada

**Por qué.** Hoy las pruebas de integración escriben en la base `catalogo`, la misma que usas para desarrollar. Una prueba que haga `deleteAll()` te borra tus datos.

**Qué hacer**

1. **Revisa primero un detalle que viene del Sprint 2:** el informe dice que el compose publica el puerto 27017 del contenedor en el **27018** del host (porque el 27017 estaba ocupado en tu computador). Compara con lo que hay en `application.yml`:

   ```powershell
   Select-String -Path docker-compose.yml,src\main\resources\application.yml -Pattern "2701"
   ```

   Si el compose publica `27018` pero `application.yml` apunta a `localhost:27017`, todos los demás (que no tienen ese conflicto) y quien corra `spring-boot:run` fuera de Docker se van a conectar a un puerto equivocado. Decide con Hector si el puerto vuelve a 27017 (lo más simple, porque la guía de inicio y el README lo dicen) y deja el cambio en este PR o en uno aparte.

2. Abre `src/test/resources/application-test.yml` (ya existe desde el Sprint 2) y deja ahí la base de pruebas:

   ```yaml
   spring:
     mongodb:
       # Misma instancia de Mongo, otra base: las pruebas no tocan los datos de desarrollo.
       uri: mongodb://localhost:27017/catalogo_pruebas
   ```

   Usa el puerto que hayas dejado en el paso 1.

3. Agrega `@ActiveProfiles("test")` a **cada clase de prueba que arranque Spring con Mongo**: `CatalogoApplicationTests`, `ProductoServiceListadoTest`, `ProductoServiceDescuentoTest`, y cualquier otra que use el repositorio. Con el import:

   ```java
   import org.springframework.test.context.ActiveProfiles;
   ```

   Las pruebas con `@WebMvcTest` (como `ManejadorGlobalErroresTest`) no usan Mongo y no lo necesitan.

4. Si una prueba necesita partir de una base vacía, ahora es seguro limpiar en `@BeforeEach` (`productoRepository.deleteAll()`), porque ya no es tu base de desarrollo.

**Cómo comprobar**

1. Cuenta tus productos de desarrollo:

   ```powershell
   docker exec catalogo-mongo mongosh --quiet catalogo --eval "db.productos.countDocuments()"
   ```

2. Corre las pruebas: `.\mvnw.cmd test`. Deben pasar las 51.
3. Vuelve a contar los de `catalogo`: el número **no cambió**.
4. Comprueba que las pruebas sí escribieron en la otra:

   ```powershell
   docker exec catalogo-mongo mongosh --quiet catalogo_pruebas --eval "db.categorias.countDocuments()"
   ```

   Debe dar `3` (la carga de categorías corre al arrancar cada contexto de prueba).

Avisa en el grupo cuando se una: **Rances** ya puede escribir su prueba de eventos con el perfil `test`.

## 4. S3-B4a — Healthcheck del contenedor de catálogo

**Por qué.** `docker compose ps` muestra `(healthy)` en Mongo, RabbitMQ y Kong, pero el servicio de catálogo solo dice «running»: Docker sabe que el proceso existe, no que responda.

**Qué hacer**

1. **Revisa qué herramienta de red tiene la imagen.** El healthcheck necesita `curl` (o `wget`) dentro del contenedor:

   ```powershell
   docker compose exec catalogo sh -c "which curl wget"
   ```

   - Si imprime una ruta, ya la tienes. Salta al paso 3.
   - Si no imprime nada, ve al paso 2.

2. **Instálala en la etapa final del `Dockerfile`**, antes de la línea que cambia a un usuario sin privilegios (`USER ...`), porque instalar paquetes requiere ser root. Según la imagen base:

   ```dockerfile
   # Imagen basada en Debian/Ubuntu (por ejemplo eclipse-temurin:21-jre)
   RUN apt-get update \
       && apt-get install -y --no-install-recommends curl \
       && rm -rf /var/lib/apt/lists/*

   # Imagen basada en Alpine (por ejemplo eclipse-temurin:21-jre-alpine): wget ya viene en busybox,
   # y si prefieres curl:
   # RUN apk add --no-cache curl
   ```

3. **Agrega el healthcheck** al servicio de catálogo en `docker-compose.yml`:

   ```yaml
   catalogo:
     # ... lo que ya tiene ...
     healthcheck:
       test: ["CMD", "curl", "-fsS", "http://localhost:8080/actuator/health"]
       interval: 15s
       timeout: 5s
       retries: 5
       start_period: 40s
   ```

   - `-f` hace que `curl` falle si la respuesta es 503 (`"status":"DOWN"`), que es justo lo que se quiere detectar.
   - `start_period` da tiempo a que Spring arranque sin contar los fallos iniciales.
   - Dentro del contenedor, `localhost` es el propio contenedor: aquí está bien.

4. Revisa si Mongo, RabbitMQ y Kong ya tienen su healthcheck (el informe dice que aparecen `(healthy)`, así que probablemente sí). **No dupliques los que existen.**

5. Haz que Kong espere a catálogo, y catálogo a sus dependencias:

   ```yaml
   catalogo:
     depends_on:
       mongo:
         condition: service_healthy
       rabbitmq:
         condition: service_healthy

   kong:
     depends_on:
       catalogo:
         condition: service_healthy
   ```

   Adapta los nombres a los que tenga tu compose.

**Cómo comprobar**

```powershell
docker compose up -d --build
docker compose ps
```

Tras un minuto, catálogo debe decir `(healthy)`. Después prueba que el healthcheck de verdad detecta fallos:

```powershell
docker compose stop mongo
Start-Sleep -Seconds 90
docker compose ps
docker compose start mongo
```

Con Mongo apagado, catálogo debe pasar a `(unhealthy)` (su `/actuator/health` responde 503). Al volver a encender Mongo, vuelve a `(healthy)`.

## 5. S3-B4b — Credenciales por variables de entorno

**Por qué.** Hoy RabbitMQ usa `guest` / `guest` escrito en el compose y, probablemente, en `application.yml`. Son credenciales públicas conocidas; una vez que alguien levante el compose fuera de su computador, son una puerta abierta.

**Qué hacer**

1. **Busca dónde están hoy:**

   ```powershell
   Select-String -Path docker-compose.yml,src\main\resources\application.yml -Pattern "guest"
   ```

2. **Crea `.env.example`** (este sí se sube; no lleva claves reales):

   ```
   # Copia este archivo a ".env" y cambia los valores. El archivo ".env" NO se sube a git.
   RABBITMQ_USER=catalogo
   RABBITMQ_PASSWORD=cambia-esta-clave
   ```

3. **Agrega `.env` al `.gitignore`** (una línea con `.env`). Después comprueba con `git status` que `.env` no aparece.

4. **Usa las variables en `docker-compose.yml`.** Docker Compose lee el archivo `.env` de la misma carpeta automáticamente:

   ```yaml
   rabbitmq:
     environment:
       RABBITMQ_DEFAULT_USER: ${RABBITMQ_USER:?Define RABBITMQ_USER en el archivo .env}
       RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD:?Define RABBITMQ_PASSWORD en el archivo .env}

   catalogo:
     environment:
       SPRING_RABBITMQ_USERNAME: ${RABBITMQ_USER}
       SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
   ```

   El `:?` hace que Compose se detenga con ese mensaje si falta la variable, en vez de arrancar con una clave vacía.

5. **En `application.yml`**, para quien corre `spring-boot:run` fuera de Docker, deja valores por defecto que lean la variable si existe:

   ```yaml
   spring:
     rabbitmq:
       username: ${RABBITMQ_USER:guest}
       password: ${RABBITMQ_PASSWORD:guest}
   ```

   Así el desarrollo sin Docker no cambia, y dentro de Docker mandan las variables.

6. **Cierra puertos innecesarios.** Revisa en el compose que los puertos de RabbitMQ (5672 y 15672) y el de administración de Kong (8001) solo se publiquen hacia tu propio computador, como ya hace Mongo:

   ```yaml
   ports:
     - "127.0.0.1:15672:15672"
   ```

   El puerto público de Kong (8000) sí puede quedar abierto.

7. **Un detalle que trae sorpresa.** RabbitMQ crea el usuario inicial solo la **primera vez** que arranca con un volumen vacío. Si ya existe un volumen con `guest`, cambiar las variables no cambia nada. Para empezar de cero **solo con RabbitMQ**:

   ```powershell
   docker compose rm -sf rabbitmq
   docker volume ls
   ```

   Si aparece un volumen de RabbitMQ, bórralo por su nombre (`docker volume rm <nombre>`). Si el compose no define un volumen para RabbitMQ, basta con recrear el contenedor. **No uses `docker compose down -v`**: borra también el volumen de Mongo, con todos los datos.

8. Avisa en el grupo: **todos necesitan copiar `.env.example` a `.env`** antes de volver a levantar el compose. Díselo a Hector (lo necesita en la prueba de integración), a Rances (su prueba de eventos) y a Cristian (para el README).

**Cómo comprobar**

1. `Copy-Item .env.example .env`, y luego `docker compose up -d --build`.
2. En http://localhost:15672, `guest` / `guest` **ya no entra**; el usuario nuevo sí.
3. `docker compose logs catalogo` no muestra errores de autenticación y el servicio queda `(healthy)`.
4. `.\mvnw.cmd test` pasa.
5. `git status` no muestra `.env`, y `git grep guest` no encuentra la clave en archivos de configuración (solo en el valor por defecto de `application.yml`).
6. Borra temporalmente tu `.env` y corre `docker compose config`: debe fallar con el mensaje de `:?`.

## 6. S3-B4c — Swagger y actuator restringidos

**Por qué.** Hay dos puertas por las que se ve información interna:

- `/actuator/health` tiene `show-details: always`: muestra el estado de Mongo y RabbitMQ y datos de la infraestructura a cualquiera.
- Swagger documenta toda la API, incluidos los endpoints de administración.

**Comprueba primero si Kong los deja pasar.** Como la ruta de Kong usa `strip_path: true`, una petición a `/api/catalogo/actuator/health` llega al servicio como `/actuator/health`, y puede estar expuesta aunque nadie lo haya querido:

```powershell
curl.exe -i http://localhost:8000/api/catalogo/actuator/health
curl.exe -i http://localhost:8000/api/catalogo/swagger-ui/index.html
curl.exe -i http://localhost:8000/api/catalogo/v3/api-docs
```

Anota qué responde cada una. Si dan 200, están expuestas.

**Qué hacer**

1. **Oculta los detalles de salud en el compose**, sin tocar el valor de desarrollo de `application.yml`. Spring convierte las variables de entorno a propiedades:

   ```yaml
   catalogo:
     environment:
       MANAGEMENT_ENDPOINT_HEALTH_SHOWDETAILS: never
   ```

   `/actuator/health` seguirá respondiendo `{"status":"UP"}` (lo que necesita el healthcheck), sin los componentes.

2. **Bloquea en Kong las rutas internas.** En `kong/kong.yml`, agrega una segunda ruta al mismo servicio, con prefijos **más largos** que la ruta principal (Kong elige la ruta cuyo prefijo coincide más) y un plugin que responde 404:

   ```yaml
   routes:
     - name: catalogo-ruta               # la que ya existe
       paths: [/api/catalogo]
       strip_path: true
     - name: catalogo-interno-bloqueado   # NUEVA
       paths:
         - /api/catalogo/actuator
         - /api/catalogo/swagger-ui
         - /api/catalogo/v3
       strip_path: true
       plugins:
         - name: request-termination
           config:
             status_code: 404
             message: "No encontrado"
   ```

   Adapta los nombres al `kong.yml` real. No uses rutas con expresión regular (las que empiezan con `~`): con `strip_path` quitan el texto coincidente completo y rompen la ruta principal.

3. **Swagger sigue disponible para desarrollo**, directo al puerto 8080 de tu computador: `http://localhost:8080/swagger-ui/index.html`. Lo único que cambia es que **no se alcanza a través de Kong**. Si además quieres poder apagarlo del todo, agrega en el compose `SPRINGDOC_SWAGGER_UI_ENABLED: ${SWAGGER_ENABLED:-true}` y `SPRINGDOC_API_DOCS_ENABLED: ${SWAGGER_ENABLED:-true}`, y documenta en `.env.example` la línea `# SWAGGER_ENABLED=false  (para un despliegue real)`. Es opcional.

4. Reinicia Kong para que lea el archivo: `docker compose up -d --force-recreate kong`.

**Cómo comprobar**

| Petición | Antes | Ahora |
|---|---|---|
| `curl.exe -i http://localhost:8000/api/catalogo/actuator/health` | 200 | **404** (de Kong) |
| `curl.exe -i http://localhost:8000/api/catalogo/swagger-ui/index.html` | 200 | **404** |
| `curl.exe -i http://localhost:8000/api/catalogo/v3/api-docs` | 200 | **404** |
| `curl.exe -i http://localhost:8000/api/catalogo/productos` | 200 | **200** (sigue funcionando) |
| `curl.exe -i http://localhost:8000/api/catalogo/categorias` | 200 | **200** |
| `curl.exe http://localhost:8080/actuator/health` | con detalles | `{"status":"UP"}` sin componentes |
| `curl.exe -I http://localhost:8080/swagger-ui/index.html` | 200/302 | sigue igual (directo, solo desarrollo) |

Y `docker compose ps` debe seguir mostrando catálogo `(healthy)`.

## 7. Subir tu trabajo

Para cada rama:

```powershell
git status
git add <los archivos de esa tarea>
git commit -m "Agrega healthcheck al servicio de catalogo"
git pull origin main
.\mvnw.cmd test
git push
```

- En `git status` **no debe aparecer `.env`**, `target/` ni `pruebas.http`.
- Un pull request por rama, con base `main`, título como `S3: healthcheck del servicio de catalogo` (con la clave de Jira si ya existe), **revisión de Rances**.
- En la descripción del PR, pega la tabla de comprobación de esa tarea con lo que viste.
- En **Jira**, pasa tus tarjetas a *En revisión*, y a *Listo* cuando se una cada PR.

## 8. Si algo falla

| Síntoma | Causa | Qué hacer |
|---|---|---|
| El healthcheck dice `exec: "curl": executable file not found` | La imagen no trae curl | Sección 4, paso 2 |
| Catálogo queda `(unhealthy)` aunque funciona | Falta `start_period` o el puerto del healthcheck no es el interno | Usa `localhost:8080` (puerto del contenedor) y sube `start_period` |
| RabbitMQ sigue aceptando `guest` | El volumen ya existía con el usuario viejo | Sección 5, paso 7 (sin `down -v`) |
| `docker compose` dice `Define RABBITMQ_USER...` | Falta el archivo `.env` | `Copy-Item .env.example .env` |
| Catálogo: `ACCESS_REFUSED - Login was refused` | Las credenciales del servicio no coinciden con las de RabbitMQ | Revisa que `SPRING_RABBITMQ_*` use las mismas variables |
| Kong sigue respondiendo 200 en `/actuator` | No recargó el archivo, o la ruta nueva no tiene prefijos más largos | `docker compose up -d --force-recreate kong` y revisa los `paths` |
| Kong ya no deja pasar `/api/catalogo/productos` | La ruta bloqueada usa regex o un prefijo demasiado corto | Quítale la regex y revisa que los prefijos terminen en `actuator`, `swagger-ui` y `v3` |
| `Connection refused` a Mongo desde `spring-boot:run` | El puerto del host (27018) no coincide con `application.yml` | Sección 3, paso 1 |
| Las pruebas siguen escribiendo en `catalogo` | Falta `@ActiveProfiles("test")` en esa clase | Agrégalo y revisa el nombre del perfil |

---

_Última actualización: 2026-10-24_
