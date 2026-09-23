# Guía de B4 — Infraestructura y eventos

**Para:** Cristian · **Tarea:** B4 — eventos en RabbitMQ, Docker, Eureka, Kong y Swagger · **Rama:** `b4-infra` · **Entrega:** viernes 2 de octubre

Al terminar, Catálogo será un microservicio de verdad y no una aplicación suelta: **publicará sus cambios en RabbitMQ** para que Búsqueda y Carro se enteren, **correrá en Docker** con sus dependencias, **se registrará en Eureka** y **se alcanzará a través de Kong**. Es la tarea que hace que el profesor vea arquitectura de microservicios y no un CRUD con tres capas.

**Todo el código de esta guía ya se probó de punta a punta.** Levanté RabbitMQ con dos colas simulando a Búsqueda y a Carro, construí la imagen, levanté el compose completo, monté un servidor Eureka y comprobé el registro, y enruté peticiones reales a través de Kong. Lo que aquí dice que funciona, funciona.

**Divide esto en varios pull requests.** Son cinco piezas independientes y meterlas todas en uno hace la revisión imposible. El orden que recomiendo:

1. Eventos de RabbitMQ (lo más importante: A y C dependen de esto)
2. Dockerfile y compose
3. Swagger
4. Eureka
5. Kong

**Los eventos esperan a B2 y B3.** Las llamadas van dentro de `ProductoService`, y hasta que esos métodos no estén en `main` no hay dónde ponerlas. Lo demás puedes hacerlo desde ya.

Los comandos son para **PowerShell**, en la terminal de VS Code.

## 1. Antes de empezar

1. Necesitas **JDK 21, Docker Desktop y VS Code** ([guía de inicio](../../GUIA-INICIO.md) §1), y haber aceptado la invitación de GitHub.

2. Trae lo último y crea tu rama:

   ```powershell
   cd C:\dev\teambsoft-backend
   git switch main
   git pull
   git switch -c b4-infra
   git push -u origin b4-infra
   ```

3. Lee la **sección 5 del [contrato](../CONTRATO-CATALOGO.md)**: los cuatro eventos, sus routing keys y qué lleva cada payload. Eso es un acuerdo con los equipos A y C, y es lo que tu código tiene que cumplir al pie de la letra.

---

## 2. Los eventos de RabbitMQ

### 2.1 Cómo funciona esto, en un minuto

Catálogo **no le avisa a nadie directamente**. Publica un mensaje en RabbitMQ y se olvida. Quien quiera enterarse se suscribe. Eso es lo que permite que Búsqueda mantenga su índice sin preguntarnos nada cada vez.

Las piezas:

- **Exchange** — el buzón donde se publica. El nuestro se llama `catalogo.eventos` y es de tipo **topic**.
- **Routing key** — la etiqueta de cada mensaje: `producto.creado`, `producto.actualizado`, `producto.desactivado`, `producto.reactivado`.
- **Cola** — donde se acumulan los mensajes para un consumidor. **Nosotros no declaramos ninguna**: cada equipo crea la suya.
- **Binding** — el enlace entre una cola y el exchange, con un patrón. Búsqueda se enlaza con `producto.*` y recibe los cuatro; Carro solo con `producto.actualizado` y `producto.desactivado`.

Que sea **topic** es justo lo que permite eso: el mismo mensaje llega a todas las colas cuyo patrón encaje, sin que un consumidor le quite el mensaje al otro.

### 2.2 La dependencia

En `pom.xml`, junto a las demás:

```xml
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-amqp</artifactId>
		</dependency>
```

Sin versión: la hereda del padre de Spring Boot. Con Boot 4.1.1 resuelve Spring AMQP 4.1.1.

### 2.3 `config/ConfiguracionEventos.java`

```java
package co.edu.uis.catalogo.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración del bus de eventos (contrato, sección 5).
 *
 * <p>Catálogo solo <b>publica</b>: no escucha nada. Quien quiera enterarse —Búsqueda y Carro— crea
 * su propia cola y la enlaza a este exchange con las routing keys que le interesen. Por eso aquí no
 * se declara ninguna cola: declararlas sería decidir por ellos.
 */
@Configuration
public class ConfiguracionEventos {

	/** Nombre del exchange acordado en el contrato. */
	public static final String EXCHANGE = "catalogo.eventos";

	/**
	 * Un exchange <b>topic</b> reparte cada mensaje a todas las colas cuyo patrón encaje con la routing
	 * key. Así, Búsqueda puede enlazarse con {@code producto.*} para recibirlos todos y Carro solo con
	 * {@code producto.actualizado} y {@code producto.desactivado}, sin que ninguno estorbe al otro.
	 *
	 * <p>{@code durable = true} hace que el exchange sobreviva a un reinicio de RabbitMQ.
	 */
	@Bean
	TopicExchange catalogoEventos() {
		return new TopicExchange(EXCHANGE, true, false);
	}

	/**
	 * Sin esto, Spring enviaría los objetos serializados en formato binario de Java, que solo otro
	 * programa Java podría leer. El contrato dice que el cuerpo es JSON.
	 */
	@Bean
	MessageConverter convertidorJson() {
		return new JacksonJsonMessageConverter();
	}

}
```

**Ojo con el nombre del convertidor.** En Spring AMQP 4 conviven dos: `Jackson2JsonMessageConverter` (el viejo, para Jackson 2) y `JacksonJsonMessageConverter` (el nuevo, para Jackson 3, que es el que trae Boot 4). Usa el segundo. Si usas el viejo compila igual, pero arrastras una biblioteca que ya no hace falta.

Y **el convertidor no es opcional**: sin él, Spring manda los objetos serializados en binario de Java. El equipo A, que lee con Elasticsearch, no podría interpretar nada.

### 2.4 Los cuerpos de los eventos

`evento/ProductoEvento.java`:

```java
package co.edu.uis.catalogo.evento;

import java.math.BigDecimal;

import co.edu.uis.catalogo.model.Producto;

/**
 * Cuerpo de los eventos {@code producto.creado}, {@code producto.actualizado} y
 * {@code producto.reactivado} (contrato v2.3, sección 5).
 *
 * <p>Lleva {@code descripcion} y {@code stock} porque el Equipo A los necesita: sin la descripción no
 * puede indexar búsqueda de texto completo, y lo marcó como bloqueante en su contrato.
 *
 * <p>No incluye {@code activo}: quien quiera saber que un producto volvió al catálogo se entera por
 * la routing key {@code producto.reactivado}, no por un campo dentro del mensaje.
 */
public record ProductoEvento(String id, String nombre, String descripcion, BigDecimal precio,
		String categoria, int stock) {

	public static ProductoEvento desde(Producto producto) {
		return new ProductoEvento(producto.getId(), producto.getNombre(), producto.getDescripcion(),
				producto.getPrecio(), producto.getCategoria(), producto.getStock());
	}

}
```

`evento/ProductoDesactivadoEvento.java`:

```java
package co.edu.uis.catalogo.evento;

/**
 * Cuerpo del evento {@code producto.desactivado} (contrato, sección 5). Solo lleva el id: quien lo
 * recibe ya tiene el producto indexado y lo único que necesita saber es cuál sacar.
 *
 * <p>Es un record aparte y no el mismo {@link ProductoEvento} con los campos vacíos, para que el
 * mensaje que sale sea exactamente el que promete el contrato, sin campos en null.
 */
public record ProductoDesactivadoEvento(String id) {

}
```

**Son dos records y no uno con campos opcionales** porque el contrato dice que `producto.desactivado` lleva solo el id. Con un record compartido saldría un JSON lleno de `null`, y el equipo A tendría que adivinar si eso significa "no cambió" o "quedó vacío".

### 2.5 `evento/PublicadorEventos.java`

```java
package co.edu.uis.catalogo.evento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import co.edu.uis.catalogo.config.ConfiguracionEventos;
import co.edu.uis.catalogo.model.Producto;

/**
 * Publica en RabbitMQ los cambios del catálogo (contrato, sección 5).
 *
 * <p>Está aparte del service a propósito: así {@code ProductoService} sigue ocupándose solo de las
 * reglas de negocio y no sabe nada de RabbitMQ. Si mañana cambia el bus de eventos, se cambia aquí.
 */
@Component
public class PublicadorEventos {

	private static final Logger log = LoggerFactory.getLogger(PublicadorEventos.class);

	private final RabbitTemplate rabbitTemplate;

	public PublicadorEventos(RabbitTemplate rabbitTemplate) {
		this.rabbitTemplate = rabbitTemplate;
	}

	public void productoCreado(Producto producto) {
		publicar("producto.creado", ProductoEvento.desde(producto));
	}

	public void productoActualizado(Producto producto) {
		publicar("producto.actualizado", ProductoEvento.desde(producto));
	}

	public void productoReactivado(Producto producto) {
		publicar("producto.reactivado", ProductoEvento.desde(producto));
	}

	public void productoDesactivado(String id) {
		publicar("producto.desactivado", new ProductoDesactivadoEvento(id));
	}

	private void publicar(String routingKey, Object cuerpo) {
		rabbitTemplate.convertAndSend(ConfiguracionEventos.EXCHANGE, routingKey, cuerpo);
		log.info("Evento publicado: {}", routingKey);
	}

}
```

### 2.6 Conectarlo al service

Esto es lo que espera a B2 y B3. En `ProductoService`, agrega el campo y el parámetro del constructor:

```java
	/** Avisa a Búsqueda y a Carro de cada cambio del catálogo (contrato, sección 5). */
	private final PublicadorEventos publicadorEventos;
```

Y en cada método, **después de guardar**:

| Método | Qué agregar |
|---|---|
| `crear` | `publicadorEventos.productoCreado(guardado);` |
| `actualizar` | `publicadorEventos.productoActualizado(guardado);` |
| `activar` | `publicadorEventos.productoReactivado(guardado);` **solo si de verdad cambió** |
| `desactivar` | `publicadorEventos.productoDesactivado(id);` **solo si de verdad cambió** |
| `descontarStock` | `productoRepository.findById(item.id()).ifPresent(publicadorEventos::productoActualizado);` por cada ítem |

Por ejemplo, `crear` queda así:

```java
	public ProductoResponse crear(ProductoRequest datos) {
		validarCategoriaExiste(datos.categoria());

		Producto producto = new Producto(datos.nombre(), datos.descripcion(), datos.precio(),
				datos.categoria(), datos.stock(), datos.imagenes());

		// El evento sale DESPUÉS de guardar: si el guardado falla, nadie debe enterarse de un producto
		// que no existe.
		Producto guardado = productoRepository.save(producto);
		publicadorEventos.productoCreado(guardado);

		return ProductoResponse.desde(guardado);
	}
```

**Dos reglas que no se pueden saltar:**

1. **El evento sale después de guardar, nunca antes.** Si publicas primero y el guardado falla, el equipo A indexa un producto que no existe y nadie se lo va a desdecir.
2. **Las operaciones idempotentes no publican.** Desactivar algo ya desactivado responde 204 igual, pero **sin evento**. Si publicas de todos modos, el equipo A reindexa de gratis cada vez. En el código eso es el `return` temprano que ya dejaron B2 y B3: publica solo después de ese `if`.

Y en `application.yml`, dentro de `spring:`:

```yaml
  rabbitmq:
    # Bus de eventos. Dentro de Docker el host es el nombre del servicio (rabbitmq), no localhost:
    # se cambia con la variable de entorno SPRING_RABBITMQ_HOST.
    host: localhost
    port: 5672
    username: guest
    password: guest
```

### 2.7 Probar los eventos de verdad

Esta es la parte que más vale la pena, porque comprueba el acuerdo con los otros dos equipos.

Levanta RabbitMQ:

```powershell
docker run -d --name catalogo-rabbit --hostname catalogo-rabbit --user rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:4-management
```

> **El `--user rabbitmq` importa.** Sin él, en Docker Desktop reciente el contenedor arranca como root, Erlang escribe su cookie de autenticación con permisos de root, y el proceso —que corre como `rabbitmq`— no la puede leer. Falla con `Error when reading /var/lib/rabbitmq/.erlang.cookie: eacces` y un volcado de Erlang de cien líneas que no dice nada útil. Me pasó al probar esta guía.

Arranca el servicio y **crea un producto**: Spring declara el exchange cuando abre la primera conexión, no al arrancar.

```powershell
.\mvnw.cmd spring-boot:run
```

Abre http://localhost:15672 (usuario `guest`, clave `guest`). En **Exchanges** debe aparecer `catalogo.eventos` de tipo `topic`.

Ahora **simula a los dos equipos**. En la pestaña **Queues and Streams → Add a new queue**, crea `busqueda.productos` y `carro.productos`. Después, en **Exchanges → catalogo.eventos → Bindings**, agrega:

| Cola | Routing key | Quién sería |
|---|---|---|
| `busqueda.productos` | `producto.*` | Equipo A: quiere los cuatro eventos |
| `carro.productos` | `producto.actualizado` | Equipo C |
| `carro.productos` | `producto.desactivado` | Equipo C |

Y ejecuta todas las operaciones, con REST Client o con los endpoints de B2 y B3: crear, actualizar, descontar stock, desactivar, **desactivar otra vez**, reactivar, **reactivar otra vez**.

Esto es lo que debe quedar:

| Cola | Mensajes | Cuáles |
|---|---|---|
| `busqueda.productos` | **5** | creado, actualizado (del PUT), actualizado (del descuento de stock), desactivado, reactivado |
| `carro.productos` | **3** | actualizado ×2 y desactivado |

**Si te salen 7 en lugar de 5, las operaciones repetidas están publicando.** Vuelve a la sección 2.6.

Entra a un mensaje de `busqueda.productos` con **Get Message** y comprueba el cuerpo:

- `producto.creado` debe traer `id`, `nombre`, **`descripcion`**, `precio`, `categoria` y **`stock`**.
- `producto.desactivado` debe traer **solo** `{"id":"..."}`.

Esos dos detalles son exactamente lo que el equipo A pidió como bloqueante y lo que el contrato v2.3 prometió.

---

## 3. Docker

### 3.1 `Dockerfile`

En la raíz del repositorio:

```dockerfile
# Construcción en dos etapas: la primera compila con el JDK completo, la segunda solo se queda con
# el jar y un JRE. La imagen final pesa la mitad y no lleva ni el código fuente ni Maven.

FROM eclipse-temurin:21-jdk AS construccion
WORKDIR /app

# El wrapper y el pom van primero y solos: mientras no cambien, Docker reutiliza la capa de
# dependencias y no las vuelve a descargar en cada build.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

# Un usuario sin privilegios: si alguien se cuela por una vulnerabilidad, no entra como root.
RUN useradd --system --no-create-home catalogo
USER catalogo

COPY --from=construccion /app/target/*.jar catalogo.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "catalogo.jar"]
```

Tres decisiones que conviene que sepas explicar:

- **Dos etapas.** La primera necesita el JDK, Maven y el código; la segunda solo el jar. La imagen final no lleva ni el código fuente ni el compilador, así que pesa menos de la mitad y expone menos.
- **El `pom.xml` se copia antes que `src/`.** Docker cachea por capas: si solo cambió el código, la capa de dependencias sigue valiendo y el build tarda segundos en vez de minutos.
- **`USER catalogo`.** Por defecto un contenedor corre como root. Si alguien encuentra un fallo en la aplicación, con root tiene mucho más margen.

Constrúyela:

```powershell
docker build -t catalogo:local .
```

La primera vez tarda varios minutos porque descarga todas las dependencias dentro del contenedor.

### 3.2 `docker-compose.yml`

Reemplaza el que existe (que hoy solo tiene Mongo):

```yaml
# Levanta el microservicio con sus dos dependencias. Para desarrollo del día a día basta con
# "docker compose up -d mongo rabbitmq" y correr la aplicación desde VS Code.

services:
  mongo:
    image: mongo:7.0
    container_name: catalogo-mongo
    ports:
      # Solo a 127.0.0.1: la base no queda expuesta a la red de la universidad.
      - "127.0.0.1:27017:27017"
    volumes:
      - mongo-datos:/data/db
    healthcheck:
      test: ["CMD", "mongosh", "--quiet", "--eval", "db.adminCommand('ping')"]
      interval: 10s
      timeout: 5s
      retries: 5

  rabbitmq:
    image: rabbitmq:4-management
    container_name: catalogo-rabbit
    hostname: catalogo-rabbit
    # Sin esto, en algunas versiones de Docker el contenedor arranca como root, Erlang escribe su
    # cookie con permisos de root y el proceso, que corre como rabbitmq, no la puede leer.
    user: rabbitmq
    ports:
      - "127.0.0.1:5672:5672"
      - "127.0.0.1:15672:15672"
    volumes:
      - rabbit-datos:/var/lib/rabbitmq
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  catalogo:
    build: .
    container_name: catalogo-servicio
    ports:
      - "8080:8080"
    environment:
      # Dentro de la red de Docker, los servicios se llaman por su nombre, no por localhost:
      # "localhost" dentro de un contenedor es ese mismo contenedor.
      SPRING_MONGODB_URI: mongodb://mongo:27017/catalogo
      SPRING_RABBITMQ_HOST: rabbitmq
    depends_on:
      mongo:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy

volumes:
  mongo-datos:
  rabbit-datos:
```

**Lo más importante de este archivo son dos líneas:**

```yaml
      SPRING_MONGODB_URI: mongodb://mongo:27017/catalogo
      SPRING_RABBITMQ_HOST: rabbitmq
```

Dentro de un contenedor, **`localhost` es ese mismo contenedor**, no tu computador. Cada servicio del compose se alcanza por su nombre, que Docker resuelve en su red interna. Es el error número uno al meter una aplicación en Docker: funciona fuera y dentro dice "connection refused".

Esas variables **sobrescriben** lo que dice `application.yml` sin tocar el archivo: Spring lee las variables de entorno con más prioridad, convirtiendo `SPRING_MONGODB_URI` en `spring.mongodb.uri`. Así el mismo jar sirve en tu máquina y en Docker.

Y el `depends_on` con `condition: service_healthy` evita el problema clásico: Docker arranca los tres a la vez, el servicio llega antes que Mongo y muere. Con el healthcheck, Docker espera a que Mongo **responda**, no solo a que el contenedor exista.

Pruébalo:

```powershell
docker compose up -d
docker compose ps
```

Los tres deben salir `Up`, y mongo y rabbitmq además `(healthy)`. Luego:

```powershell
curl http://localhost:8080/actuator/health
```

Debe decir `"status":"UP"` y, dentro de `components`, **`mongo` y `rabbit` los dos en UP**. Eso prueba que el servicio, dentro de Docker, alcanza a los dos por nombre de red.

---

## 4. Swagger

Documenta la API sola, leyendo los controllers. En `pom.xml`:

```xml
		<dependency>
			<groupId>org.springdoc</groupId>
			<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
			<version>3.1.1</version>
		</dependency>
```

**La versión 3.1.1 es la que funciona con Spring Boot 4.1** — lo verifiqué. Las 2.x son para Boot 3 y fallan al arrancar.

No hay que configurar nada más. Arranca el servicio y abre:

- http://localhost:8080/swagger-ui/index.html — la página con los endpoints y el botón de probar
- http://localhost:8080/v3/api-docs — el JSON de OpenAPI

Deben aparecer los ocho endpoints: `/categorias`, `/productos` (GET y POST), `/productos/{id}` (GET, PUT, DELETE), `/productos/{id}/activar` y `/productos/descontar-stock`.

> **Antes de la entrega, revisa si quieres dejarlo público.** Swagger expone toda la API, incluidos los endpoints de administración. Para esta entrega está bien; en un sistema real se restringe.

---

## 5. Eureka

### 5.1 Qué es y por qué hace falta

Eureka es un **directorio de servicios**: cada microservicio se anuncia al arrancar diciendo "me llamo `catalogo` y estoy en tal dirección", y los demás preguntan por nombre en vez de tener direcciones escritas a mano. Cuando un servicio se cae o cambia de dirección, el directorio se entera.

**Son dos cosas distintas y conviene no confundirlas:**

- El **servidor** de Eureka: una aplicación aparte que guarda el directorio. **No existe todavía.**
- El **cliente**: la dependencia que agregas a Catálogo para que se anuncie. Eso sí es tu tarea.

> ⚠️ **Esto está sin decidir y hay que hablarlo en el grupo.** El servidor de Eureka es infraestructura **compartida** entre los tres equipos, igual que Kong: no tiene sentido que cada equipo tenga el suyo, porque entonces no hay un directorio, hay tres. Ninguno de los tres contratos dice quién lo opera. Mientras se decide, montas uno local para probar, como explica el paso 5.3.

### 5.2 El cliente en Catálogo

En `pom.xml`, agrega la propiedad de versión:

```xml
	<properties>
		<java.version>21</java.version>
		<spring-cloud.version>2025.1.3</spring-cloud.version>
	</properties>
```

La dependencia:

```xml
		<dependency>
			<groupId>org.springframework.cloud</groupId>
			<artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
		</dependency>
```

Y el bloque de gestión de versiones, **después** de `</dependencies>`:

```xml
	<dependencyManagement>
		<dependencies>
			<dependency>
				<groupId>org.springframework.cloud</groupId>
				<artifactId>spring-cloud-dependencies</artifactId>
				<version>${spring-cloud.version}</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
		</dependencies>
	</dependencyManagement>
```

**Spring Cloud 2025.1.3 es la serie que corresponde a Spring Boot 4.1** — lo verifiqué contra Maven Central. Con una versión de otra serie el proyecto ni arranca. Ese `dependencyManagement` es lo que hace que no tengas que escribir la versión de cada artefacto de Spring Cloud.

En `application.yml`, antes de `management:`:

```yaml
eureka:
  client:
    service-url:
      # Dónde vive el registro. Dentro de Docker es el nombre del servicio; se cambia con la
      # variable de entorno EUREKA_CLIENT_SERVICEURL_DEFAULTZONE.
      defaultZone: http://localhost:8761/eureka/
  instance:
    # Se anuncia por IP y no por el nombre de la máquina: dentro de Docker el hostname es un
    # identificador que nadie más puede resolver.
    prefer-ip-address: true
```

El servicio se registra con el nombre de `spring.application.name`, que ya es `catalogo`.

### 5.3 Un servidor local para probar

Créalo **fuera del repositorio** (por ejemplo en `C:\dev\registro-prueba`), porque todavía no está decidido dónde vivirá el definitivo. Es un proyecto de Spring Boot con una sola dependencia.

`pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>

	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>4.1.1</version>
		<relativePath/>
	</parent>

	<groupId>co.edu.uis</groupId>
	<artifactId>registro</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<name>registro</name>

	<properties>
		<java.version>21</java.version>
		<spring-cloud.version>2025.1.3</spring-cloud.version>
	</properties>

	<dependencies>
		<dependency>
			<groupId>org.springframework.cloud</groupId>
			<artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
		</dependency>
	</dependencies>

	<dependencyManagement>
		<dependencies>
			<dependency>
				<groupId>org.springframework.cloud</groupId>
				<artifactId>spring-cloud-dependencies</artifactId>
				<version>${spring-cloud.version}</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
		</dependencies>
	</dependencyManagement>

	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>
		</plugins>
	</build>
</project>
```

`src/main/java/co/edu/uis/registro/RegistroApplication.java`:

```java
package co.edu.uis.registro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/** Servidor de registro del ecosistema: aquí se anuncian los microservicios y se descubren entre sí. */
@SpringBootApplication
@EnableEurekaServer
public class RegistroApplication {

	public static void main(String[] args) {
		SpringApplication.run(RegistroApplication.class, args);
	}

}
```

`src/main/resources/application.yml`:

```yaml
server:
  port: 8761

spring:
  application:
    name: registro

eureka:
  client:
    # El servidor no se registra en sí mismo ni busca otros: es el único nodo del registro.
    register-with-eureka: false
    fetch-registry: false
```

Sí: **un servidor de Eureka son tres archivos y una anotación.** Arráncalo:

```powershell
.\mvnw.cmd spring-boot:run
```

### 5.4 Comprobarlo

Con el registro corriendo, arranca Catálogo y abre http://localhost:8761. En **Instances currently registered with Eureka** debe aparecer:

| Application | Status |
|---|---|
| **CATALOGO** | `UP (1) - 192.168.x.x:catalogo:8080` |

Si prefieres la terminal:

```powershell
curl -H "Accept: application/json" http://localhost:8761/eureka/apps
```

Debe traer `"name":"CATALOGO"` y `"status":"UP"`. Tarda hasta 30 segundos en aparecer: el cliente se anuncia poco después de arrancar, no al instante.

---

## 6. Kong

### 6.1 Qué hace

Kong es la **única puerta de entrada** al sistema. El cliente web no le pega a Catálogo, ni a Búsqueda, ni a Carro: le pega a Kong, y Kong reparte según la ruta. Eso es lo que permite que haya una sola dirección, un solo sitio donde poner CORS y, más adelante, un solo sitio donde validar los tokens de Keycloak.

Lo vamos a usar en **modo sin base de datos**: la configuración vive en un archivo YAML del repositorio, versionado como el resto del código. La alternativa —Kong con su propia base de datos y configuración por API— es más flexible y bastante más pesada de montar para lo que necesitamos.

> ⚠️ **Mismo aviso que con Eureka:** Kong es infraestructura compartida. Un solo Kong para los tres módulos es justamente la gracia. El que montas aquí es para desarrollar y probar Catálogo solo.

### 6.2 `kong/kong.yml`

Crea la carpeta `kong/` en la raíz del repositorio:

```yaml
# Configuración declarativa de Kong (modo sin base de datos). Kong lee este archivo al arrancar;
# no hay panel ni estado que mantener, y el enrutamiento queda versionado en el repositorio.
_format_version: "3.0"

services:
  - name: catalogo
    # Dentro de la red de Docker, el nombre del servicio del compose.
    url: http://catalogo:8080

    routes:
      - name: catalogo-ruta
        paths:
          - /api/catalogo
        # strip_path quita el prefijo antes de reenviar: /api/catalogo/productos llega al
        # servicio como /productos. Por eso los controllers no llevan el prefijo.
        strip_path: true

    plugins:
      - name: cors
        config:
          # En desarrollo, el frontend corre en el 5173 (Vite). Cuando el Host App tenga su
          # dirección definitiva, se agrega aquí.
          origins:
            - http://localhost:5173
          methods: [GET, POST, PUT, DELETE, OPTIONS]
          headers: [Content-Type, Authorization]
          credentials: true
```

**`strip_path: true` es la línea que explica un detalle del código que quizá te preguntaste.** Nuestros controllers dicen `@RequestMapping("/productos")`, sin `/api/catalogo`. El prefijo lo pone Kong y lo quita antes de reenviar: el cliente pide `/api/catalogo/productos` y al servicio le llega `/productos`. Así el servicio no sabe ni le importa bajo qué prefijo lo publicaron.

**Y el CORS va aquí, no en Spring.** Si lo configuras en los dos, las cabeceras salen duplicadas y el navegador rechaza la respuesta con un error que no menciona la duplicación. Un solo sitio.

### 6.3 Kong en el compose

Agrégalo **antes** del bloque `volumes:` del final:

```yaml
  # --- Infraestructura del ecosistema ---------------------------------------------------------
  # Kong es la única puerta de entrada al sistema: todo el tráfico del cliente pasa por aquí.
  # Ojo: es infraestructura COMPARTIDA con los equipos A y C. Está aquí para poder desarrollar y
  # probar Catálogo solo; cuando se decida quién opera el Kong del sistema, esto se mueve allá.
  kong:
    image: kong:3.9
    container_name: catalogo-kong
    environment:
      KONG_DATABASE: "off"
      KONG_DECLARATIVE_CONFIG: /kong/kong.yml
      KONG_PROXY_LISTEN: 0.0.0.0:8000
      KONG_ADMIN_LISTEN: 0.0.0.0:8001
    volumes:
      - ./kong:/kong:ro
    ports:
      - "8000:8000"
      - "127.0.0.1:8001:8001"
    depends_on:
      - catalogo
```

**Cuidado con dónde lo pegas.** A mí se me fue debajo de `volumes:` y Docker respondió `volumes.kong additional properties 'image', 'ports'… not allowed`, que no dice en ningún momento que el problema sea la posición. Va dentro de `services:`, al mismo nivel que `catalogo`.

El `:ro` del volumen monta la configuración como solo lectura: Kong no tiene por qué escribir ahí.

### 6.4 Comprobarlo

```powershell
docker compose up -d
```

Y pruebas, todas por el **puerto 8000**, que es el de Kong:

| Petición | Esperado |
|---|---|
| `curl http://localhost:8000/api/catalogo/categorias` | Las tres categorías |
| `curl http://localhost:8000/api/catalogo/productos` | La página de productos |
| `curl http://localhost:8000/api/inventado` | **404** de Kong: no tiene esa ruta |
| `curl http://localhost:8001/status` | El estado de Kong (puerto de administración) |

Y el CORS, que es lo que le sirve al frontend:

```powershell
curl -I -X OPTIONS http://localhost:8000/api/catalogo/productos -H "Origin: http://localhost:5173" -H "Access-Control-Request-Method: GET"
```

Debe responder `200` y traer la cabecera `Access-Control-Allow-Origin: http://localhost:5173`.

**La prueba de que `strip_path` funciona** es que `/api/catalogo/categorias` devuelva las categorías: el servicio solo conoce `/categorias`, así que si llegara con el prefijo respondería 404.

---

## 7. Antes de entregar

Una cosa más, pequeña pero importante: hoy `application.yml` tiene

```yaml
management:
  endpoint:
    health:
      show-details: always
```

Eso hace que `/actuator/health` muestre las bases de datos, las rutas del disco y las direcciones internas. Es muy cómodo mientras desarrollas y **no debería quedar así si el servicio sale de tu máquina**. Cámbialo a `when-authorized` o `never` en el último pull request, y dilo en la sustentación: el profesor valora que se note la diferencia entre desarrollo y producción.

## 8. Subir tu trabajo

Un pull request por pieza. Por ejemplo, el de los eventos:

```powershell
git status
git add .
git commit -m "Publica los eventos del catalogo en RabbitMQ"
git pull origin main
.\mvnw.cmd test
git push
```

- Título del PR: `B4: eventos en RabbitMQ`. Pide la revisión de Hector o de Jhon.
- **En la descripción del PR, pega el conteo de mensajes de las dos colas** (5 y 3). Es la evidencia de que el contrato con A y C se cumple, y le ahorra al revisor tener que montar RabbitMQ.
- En **Jira**, pasa la tarjeta a *En revisión*, y a *Listo* cuando se una.
- Marca en el README las casillas correspondientes: eventos, Eureka, Kong.

## 9. Si algo falla

| Síntoma | Causa | Qué hacer |
|---|---|---|
| RabbitMQ no arranca: `Error when reading .erlang.cookie: eacces` | El contenedor arranca como root y el proceso corre como rabbitmq | `--user rabbitmq`, o la línea `user: rabbitmq` en el compose |
| El exchange no aparece en el panel | Spring lo declara al abrir la primera conexión | Crea un producto y recarga |
| Los mensajes salen pero ninguna cola los recibe | Un exchange topic descarta lo que no encaja con ningún binding | Crea las colas y sus enlaces antes de publicar |
| El cuerpo del mensaje se ve binario y no JSON | Falta el `MessageConverter` | Sección 2.3 |
| Salen 7 mensajes en vez de 5 | Las operaciones repetidas están publicando | Sección 2.6, los `return` tempranos |
| Dentro de Docker: `Connection refused` a Mongo | Se está usando `localhost` en vez del nombre del servicio | Sección 3.2 |
| El servicio muere al arrancar el compose | Arrancó antes que Mongo | `depends_on` con `condition: service_healthy` |
| `volumes.kong additional properties … not allowed` | El bloque de Kong quedó debajo de `volumes:` | Va dentro de `services:` |
| Kong responde 404 a `/api/catalogo/...` | La ruta o el `url` del servicio están mal | `docker logs catalogo-kong` y revisa `kong/kong.yml` |
| Catálogo no aparece en Eureka | Tarda hasta 30 s; o el `defaultZone` está mal | Espera y revisa `application.yml` |
| Maven falla con `Cannot access central in offline mode` | Se está compilando sin red con dependencias nuevas | Quita el `-o` |
| El navegador se queja de CORS duplicado | Está configurado en Kong y en Spring | Déjalo solo en Kong |

---

_Última actualización: 2026-09-23_
