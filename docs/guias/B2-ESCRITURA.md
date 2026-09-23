# Guía de B2 — Escritura de productos

**Para:** Hector · **Tareas:** B2 — Historias 1 (registrar), 4 (actualizar) y 7 (reactivar), más el descuento de stock que pidió el equipo C · **Rama:** `b2-escritura` · **Entrega:** viernes 2 de octubre

Al terminar, el catálogo podrá **crear, editar, reactivar productos y descontar stock**, con las validaciones del contrato y los errores en el formato acordado con los equipos A y C. Son cuatro endpoints y once pruebas automáticas.

**El código de esta guía ya se probó.** Lo compilé y corrí las pruebas de la capa web antes de escribirla: 10 de 10 pasan. Si copias cada archivo tal cual, funciona.

**No tienes que esperar a nadie.** B1 ya está en `main` con los modelos, los repositorios y el manejo de errores. Tu tarea no depende de B3 ni de B4; lo único que comparten es que los dos van a tocar `ProductoService` y `ProductoController`, así que quien una su pull request de segundo tendrá un conflicto sencillo ([guía de git](GUIA-GIT.md) §7).

Los comandos son para **PowerShell**, en la terminal de VS Code.

## 1. Antes de empezar

1. Necesitas **JDK 21, Docker Desktop y VS Code** con el *Extension Pack for Java* ([guía de inicio](GUIA-INICIO.md) §1), y haber aceptado la invitación de GitHub.
2. Trae lo último de `main` y crea tu rama:

   ```powershell
   cd C:\dev\teambsoft-backend
   git switch main
   git pull
   git switch -c b2-escritura
   git push -u origin b2-escritura
   ```

3. Levanta MongoDB y comprueba que el proyecto arranca como está:

   ```powershell
   docker compose up -d
   .\mvnw.cmd test
   ```

   Debe terminar en `BUILD SUCCESS`. Si falla aquí, no sigas: el problema es del entorno, no tuyo.

4. Lee la **sección 2 del [contrato](../CONTRATO-CATALOGO.md)** (tus cuatro endpoints) y la **sección 3** (las reglas de validación). Lo que diga el contrato manda sobre lo que diga esta guía.

## 2. Cómo crear cada archivo

- **Archivo nuevo:** en el explorador de VS Code, clic derecho sobre la carpeta `src/main/java/co/edu/uis/catalogo` → **New File…** y escribe la ruta con la carpeta, por ejemplo `dto/ProductoRequest.java`.
- **Archivo que ya existe** (`Producto.java`, `ProductoService.java`, `ProductoController.java`, `ManejadorGlobalErrores.java`): no lo borres entero, agrégale lo que indica cada paso.
- **Respeta mayúsculas y minúsculas**: los `import` dependen de eso.

Al terminar, tu tarea habrá tocado estos archivos:

```
src/main/java/co/edu/uis/catalogo/
├── dto/
│   ├── ProductoRequest.java          NUEVO  lo que llega en POST y PUT
│   ├── DescuentoStockRequest.java    NUEVO  lo que envía Carro al confirmar
│   └── DescuentoStockResponse.java   NUEVO  lo que se le responde
├── error/
│   ├── StockInsuficienteException.java  NUEVO
│   └── ManejadorGlobalErrores.java      se le agrega el 409
├── model/Producto.java               se le agrega activar()
├── repository/
│   └── ProductoStockRepository.java  NUEVO  el descuento atómico
├── service/ProductoService.java      se le agregan 4 métodos
└── controller/ProductoController.java se le agregan 4 endpoints
```

## 3. Lo que entra: `dto/ProductoRequest.java`

Un **DTO** (*Data Transfer Object*) es el formato en que la API recibe o entrega datos, separado de la entidad que se guarda en la base. Se usa un record porque solo transporta datos.

Es **el mismo record para POST y para PUT**, porque el contrato dice que PUT reemplaza el producto completo, no campo por campo. Y **no tiene `id` ni `activo`**: el contrato manda ignorarlos si el cliente los envía, y la forma más simple de ignorar algo es no recibirlo.

```java
package co.edu.uis.catalogo.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Datos que llegan en el cuerpo de POST y PUT. Es el mismo record para los dos porque PUT reemplaza
 * el producto completo (contrato, sección 2).
 *
 * <p>No tiene {@code id} ni {@code activo}: el contrato dice que se ignoran si el cliente los envía,
 * y la forma más simple de ignorarlos es no recibirlos. El {@code id} sale de la URL y {@code activo}
 * solo lo cambian DELETE y activar.
 *
 * <p>Los mensajes van fijos en español: si se dejan los de Jakarta, el mismo error llega traducido
 * según el idioma que pida el cliente, y el contrato exige un texto estable.
 */
public record ProductoRequest(

		@NotBlank(message = "es obligatorio")
		@Size(max = 120, message = "no puede pasar de 120 caracteres")
		String nombre,

		String descripcion,

		@NotNull(message = "es obligatorio")
		@Positive(message = "debe ser mayor que 0")
		BigDecimal precio,

		@NotBlank(message = "es obligatoria")
		String categoria,

		// Integer y no int: con int, un cuerpo sin stock llegaría como 0 y @NotNull nunca saltaría.
		@NotNull(message = "es obligatorio")
		@PositiveOrZero(message = "no puede ser negativo")
		Integer stock,

		List<String> imagenes) {

}
```

Dos detalles que parecen menores y no lo son:

- **`Integer stock` y no `int`.** Si fuera `int` y el cliente no manda el campo, Java lo rellena con 0 y `@NotNull` nunca se dispara: un producto sin stock declarado entraría como stock 0 en silencio. Con `Integer`, si no viene es `null` y la validación salta.
- **Los mensajes en español, escritos a mano.** Jakarta trae mensajes propios y los traduce según la cabecera `Accept-Language` del cliente. Sin escribirlos, el mismo error llegaría en español al navegador y en inglés al servicio del equipo C.

## 4. Crear y actualizar — Historias 1 y 4

### 4.1 Los métodos en `service/ProductoService.java`

El service es donde viven las reglas de negocio. El controller solo recibe y responde; el repositorio solo guarda y lee. Agrega estos dos métodos **antes** de `buscarExistente`:

```java
	/** POST: crea un producto. Nace activo y MongoDB le asigna el id al guardarlo (Historia 1). */
	public ProductoResponse crear(ProductoRequest datos) {
		validarCategoriaExiste(datos.categoria());

		Producto producto = new Producto(datos.nombre(), datos.descripcion(), datos.precio(),
				datos.categoria(), datos.stock(), datos.imagenes());

		return ProductoResponse.desde(productoRepository.save(producto));
	}

	/**
	 * PUT: reemplaza el producto completo (Historia 4). No cambia {@code id} ni {@code activo}: sobre un
	 * producto inactivo responde 200 y el producto sigue inactivo.
	 */
	public ProductoResponse actualizar(String id, ProductoRequest datos) {
		Producto producto = buscarExistente(id);
		validarCategoriaExiste(datos.categoria());

		producto.actualizar(datos.nombre(), datos.descripcion(), datos.precio(), datos.categoria(),
				datos.stock(), datos.imagenes());

		return ProductoResponse.desde(productoRepository.save(producto));
	}

	/**
	 * La categoría debe ser el id de una categoría existente (contrato, sección 3). Es 400 y no 404
	 * porque lo que falla es un dato del producto que se está enviando, no la dirección de la petición.
	 */
	private void validarCategoriaExiste(String categoria) {
		if (!categoriaRepository.existsById(categoria)) {
			throw new ValidacionFallidaException("La categoría '" + categoria + "' no existe");
		}
	}
```

Y agrega los `import` que faltan, arriba del archivo:

```java
import co.edu.uis.catalogo.dto.ProductoRequest;
import co.edu.uis.catalogo.dto.ProductoResponse;
import co.edu.uis.catalogo.error.ValidacionFallidaException;
```

Fíjate en tres cosas:

- **`actualizar` no construye un `Producto` nuevo**: busca el que existe y le pide que se actualice. Como la entidad no tiene setters, la única forma de cambiarla es por sus métodos con nombre, y ahí es donde está escrita la regla de que PUT no toca `id` ni `activo`. La regla vive en el código, no solo en el documento.
- **El 404 lo lanza `buscarExistente`**, que ya existía. No lo repitas.
- **La categoría inexistente es 400, no 404.** El 404 es para la dirección de la petición; aquí la dirección está bien y lo que viene mal es un dato del cuerpo.

### 4.2 Los endpoints en `controller/ProductoController.java`

```java
	/**
	 * {@code @Valid} es lo que dispara las validaciones del record. Sin esa anotación, las reglas
	 * {@code @NotBlank} y compañía quedan escritas pero nadie las ejecuta.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProductoResponse crear(@Valid @RequestBody ProductoRequest datos) {
		return productoService.crear(datos);
	}

	@PutMapping("/{id}")
	public ProductoResponse actualizar(@PathVariable String id, @Valid @RequestBody ProductoRequest datos) {
		return productoService.actualizar(id, datos);
	}
```

Con sus `import`:

```java
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import co.edu.uis.catalogo.dto.ProductoRequest;
import co.edu.uis.catalogo.dto.ProductoResponse;

import jakarta.validation.Valid;
```

**El `@Valid` es obligatorio y es el error más común.** Sin él, todas las validaciones del record quedan escritas y ninguna se ejecuta: un producto con precio negativo entraría sin chistar. Si tus pruebas de validación fallan, revisa esto primero.

`@ResponseStatus(HttpStatus.CREATED)` es lo que hace que POST responda **201** y no 200, como exige el contrato.

## 5. Reactivar — Historia 7

Esta historia es nueva en el contrato v2.3. Antes, un producto desactivado no tenía forma de volver: `DELETE` lo apaga y `PUT` ignora `activo` a propósito.

Primero, el método en la entidad. En `model/Producto.java`, justo debajo de `desactivar()`:

```java
	/**
	 * Vuelve a poner el producto en el catálogo (contrato v2.3, sección 2). Es la operación inversa de
	 * {@link #desactivar}: no toca ningún otro dato. Llamarla sobre un producto ya activo no cambia nada.
	 */
	public void activar() {
		this.activo = true;
	}
```

Después, en el service:

```java
	/**
	 * Reactiva un producto desactivado (Historia 7). Es idempotente: si ya estaba activo, devuelve el
	 * producto sin guardar nada, igual que hace DELETE con uno que ya estaba inactivo.
	 */
	public ProductoResponse activar(String id) {
		Producto producto = buscarExistente(id);

		if (producto.isActivo()) {
			return ProductoResponse.desde(producto);
		}

		producto.activar();
		return ProductoResponse.desde(productoRepository.save(producto));
	}
```

Y en el controller:

```java
	/** POST y no PUT: no reemplaza un recurso, dispara una acción sobre él (contrato v2.3). */
	@PostMapping("/{id}/activar")
	public ProductoResponse activar(@PathVariable String id) {
		return productoService.activar(id);
	}
```

**El `if` del principio es la idempotencia**, y no es adorno: cuando B4 conecte los eventos, ese `return` temprano es lo que evita publicar un `producto.reactivado` por algo que no cambió. El equipo A reindexaría de gratis.

## 6. El descuento de stock

Esta es la parte delicada de tu tarea. Lo pidió el equipo C: su checkout no puede responder "no hay stock" de forma confiable si nosotros no descontamos.

**Súbela en un pull request aparte** del resto. Si algo se discute, que no bloquee los tres endpoints anteriores.

### 6.1 El error que hay que evitar

La forma intuitiva sería esta, y **está mal**:

```java
// ¡NO HAGAS ESTO!
Producto producto = productoRepository.findById(id).orElseThrow();
if (producto.getStock() >= cantidad) {
    producto.setStock(producto.getStock() - cantidad);
    productoRepository.save(producto);
}
```

Entre el `findById` y el `save` pasan milisegundos, y en esos milisegundos cabe otro checkout haciendo exactamente lo mismo. Los dos leen 10 unidades, los dos creen que alcanza, los dos guardan 4. Se vendieron 12 unidades de 10. Eso es **sobreventa**, y es un problema clásico: se llama *condición de carrera*.

La solución es no leer y escribir por separado, sino **mandarle a MongoDB una sola operación que lleve la condición adentro**. MongoDB garantiza que una operación sobre un documento es atómica: nadie lo toca mientras se ejecuta.

### 6.2 `repository/ProductoStockRepository.java`

`ProductoRepository` hereda de `MongoRepository`, que solo sabe leer y guardar documentos completos. Para esto hace falta `MongoTemplate`, que permite mandar operaciones a la medida.

```java
package co.edu.uis.catalogo.repository;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import co.edu.uis.catalogo.model.Producto;

/**
 * Operaciones de stock que no se pueden hacer con {@link ProductoRepository}.
 *
 * <p>{@code ProductoRepository} hereda de {@code MongoRepository}, que solo sabe leer y guardar
 * documentos completos. Para descontar stock eso no sirve: entre leer el producto, restar en Java y
 * guardarlo cabe otra petición haciendo lo mismo, y las dos pasarían. Eso es sobreventa.
 *
 * <p>Aquí se usa {@code MongoTemplate}, que permite mandarle a MongoDB una operación condicionada.
 * MongoDB garantiza que esa operación es atómica <b>sobre un documento</b>: nadie más lo toca
 * mientras se ejecuta.
 */
@Repository
public class ProductoStockRepository {

	private final MongoTemplate mongoTemplate;

	public ProductoStockRepository(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	/**
	 * Descuenta {@code cantidad} unidades solo si el producto está activo y le quedan al menos esas
	 * unidades. Devuelve el stock que quedó, o {@code null} si no alcanzó.
	 *
	 * <p>La condición viaja dentro de la misma operación que la resta, y ahí está todo el truco: si dos
	 * checkouts piden a la vez las últimas 3 unidades, MongoDB ejecuta uno y para cuando llega el otro
	 * el documento ya no cumple {@code stock >= cantidad}, así que no modifica nada y devuelve null.
	 */
	public Integer descontar(String id, int cantidad) {
		Query consulta = Query.query(Criteria.where("_id").is(id)
				.and("activo").is(true)
				.and("stock").gte(cantidad));
		Update resta = new Update().inc("stock", -cantidad);

		// returnNew(true) devuelve el documento ya actualizado, así no hay que volver a leerlo.
		Producto actualizado = mongoTemplate.findAndModify(consulta, resta,
				FindAndModifyOptions.options().returnNew(true), Producto.class);

		return (actualizado == null) ? null : actualizado.getStock();
	}

	/** Devuelve unidades al stock. Solo se usa para deshacer descuentos de una petición que falló. */
	public void devolver(String id, int cantidad) {
		mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(id)),
				new Update().inc("stock", cantidad), Producto.class);
	}

}
```

Léelo con calma, porque en cuatro líneas está toda la protección: la consulta dice *"el documento con este id, activo, y con stock mayor o igual a la cantidad"*, y la actualización dice *"réstale esa cantidad"*. Van juntas en la misma operación. Si el documento ya no cumple la condición cuando MongoDB llega a ejecutarla, no modifica nada y devuelve `null`.

### 6.3 Los DTOs

Carro manda todos los ítems en una sola petición, no uno por uno. Es a propósito: la regla es *o pasa todo, o no pasa nada*, y eso solo se puede garantizar si el servicio ve la lista completa.

`dto/DescuentoStockRequest.java`:

```java
package co.edu.uis.catalogo.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Lo que envía Carro al confirmar el checkout: todos los ítems en una sola petición (contrato,
 * sección 2). Viene todo junto y no ítem por ítem porque la regla es "o pasa todo, o no pasa nada",
 * y eso solo se puede garantizar si el servicio ve la lista completa.
 */
public record DescuentoStockRequest(

		@NotEmpty(message = "no puede venir vacía")
		@Valid
		List<Item> items) {

	/**
	 * {@code @Valid} en la lista de arriba es lo que hace que Jakarta entre a validar cada Item. Sin
	 * esa anotación, solo se validaría que la lista no esté vacía y las cantidades pasarían sin revisar.
	 */
	public record Item(

			@NotBlank(message = "es obligatorio")
			String id,

			@NotNull(message = "es obligatoria")
			@Min(value = 1, message = "debe ser 1 o más")
			Integer cantidad) {

	}

}
```

`dto/DescuentoStockResponse.java`:

```java
package co.edu.uis.catalogo.dto;

import java.util.List;

/**
 * Lo que responde el descuento de stock cuando todo salió bien: cuánto quedó de cada producto.
 * Carro lo usa para mostrar el resultado sin tener que volver a consultar el catálogo.
 */
public record DescuentoStockResponse(List<Item> items) {

	public record Item(String id, int stockRestante) {

	}

}
```

### 6.4 El error 409

`error/StockInsuficienteException.java`:

```java
package co.edu.uis.catalogo.error;

/**
 * No hay unidades suficientes de un producto para completar el descuento. El manejador global la
 * convierte en 409 con el código {@code STOCK_INSUFICIENTE} (contrato, sección 4).
 *
 * <p>Es 409 y no 400 porque no es un error de formato de la petición: la misma petición puede fallar
 * ahora y funcionar en un minuto, cuando el stock cambie. Es un conflicto con el estado del recurso.
 */
public class StockInsuficienteException extends RuntimeException {

	public StockInsuficienteException(String mensaje) {
		super(mensaje);
	}

}
```

Y en `error/ManejadorGlobalErrores.java`, agrega la constante junto a las otras dos:

```java
	private static final String STOCK_INSUFICIENTE = "STOCK_INSUFICIENTE";
```

y este método, encima del que maneja `MethodArgumentNotValidException`:

```java
	/**
	 * 409 y no 400: no es un error de formato de la petición, es un conflicto con el estado actual del
	 * producto. La misma petición puede fallar ahora y funcionar en un minuto (contrato v2.3, sección 4).
	 */
	@ExceptionHandler(StockInsuficienteException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ErrorResponse stockInsuficiente(StockInsuficienteException e) {
		return new ErrorResponse(STOCK_INSUFICIENTE, e.getMessage());
	}
```

### 6.5 El método en el service

```java
	/**
	 * Descuenta stock de varios productos al confirmar un checkout (contrato v2.3, sección 2).
	 *
	 * <p>La regla es <b>o pasa todo, o no pasa nada</b>. Como esta entrega corre un MongoDB de un solo
	 * nodo, no hay transacciones entre documentos: si un ítem no alcanza, hay que devolver a mano lo que
	 * ya se descontó. Por eso se va guardando en {@code descontados} lo que va saliendo bien.
	 */
	public DescuentoStockResponse descontarStock(DescuentoStockRequest peticion) {
		validarSinRepetidos(peticion.items());

		List<DescuentoStockRequest.Item> descontados = new ArrayList<>();
		List<DescuentoStockResponse.Item> resultado = new ArrayList<>();

		for (DescuentoStockRequest.Item item : peticion.items()) {
			Producto producto = buscarExistente(item.id());

			if (!producto.isActivo()) {
				devolver(descontados);
				throw new ValidacionFallidaException(
						"El producto " + item.id() + " está desactivado y no se puede vender");
			}

			Integer restante = productoStockRepository.descontar(item.id(), item.cantidad());

			if (restante == null) {
				devolver(descontados);
				throw new StockInsuficienteException("No hay stock suficiente del producto " + item.id()
						+ ": se pidieron " + item.cantidad() + " unidades");
			}

			descontados.add(item);
			resultado.add(new DescuentoStockResponse.Item(item.id(), restante));
		}

		return new DescuentoStockResponse(resultado);
	}

	/** Deshace los descuentos de una petición que terminó fallando. */
	private void devolver(List<DescuentoStockRequest.Item> descontados) {
		for (DescuentoStockRequest.Item item : descontados) {
			productoStockRepository.devolver(item.id(), item.cantidad());
		}
	}

	/**
	 * Dos líneas con el mismo producto harían dos descuentos separados y la reversión quedaría a medias.
	 * Es más simple rechazarlo: que Carro sume las cantidades antes de enviar.
	 */
	private void validarSinRepetidos(List<DescuentoStockRequest.Item> items) {
		Set<String> vistos = new HashSet<>();
		for (DescuentoStockRequest.Item item : items) {
			if (!vistos.add(item.id())) {
				throw new ValidacionFallidaException("El producto " + item.id() + " viene repetido en la lista");
			}
		}
	}
```

El service necesita el repositorio nuevo. Agrega el campo y ponlo en el constructor:

```java
	/** Para el descuento atómico de stock, que MongoRepository no sabe hacer. */
	private final ProductoStockRepository productoStockRepository;

	public ProductoService(ProductoRepository productoRepository, CategoriaRepository categoriaRepository,
			ProductoStockRepository productoStockRepository) {
		this.productoRepository = productoRepository;
		this.categoriaRepository = categoriaRepository;
		this.productoStockRepository = productoStockRepository;
	}
```

Y los `import` que faltan:

```java
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import co.edu.uis.catalogo.dto.DescuentoStockRequest;
import co.edu.uis.catalogo.dto.DescuentoStockResponse;
import co.edu.uis.catalogo.error.StockInsuficienteException;
import co.edu.uis.catalogo.repository.ProductoStockRepository;
```

**Por qué se lleva la lista `descontados`:** si el tercer ítem de un carrito no alcanza, los dos primeros ya se descontaron. Sin devolverlos, el cliente vería un error y aun así habría perdido unidades del inventario. Esa reversión es de **mejor esfuerzo**: si el servicio se cae justo en medio, puede quedar diferencia. Está documentado como riesgo aceptado en el contrato §8; con un MongoDB de un solo nodo no hay transacciones entre documentos y esta es la mejor garantía posible.

### 6.6 El endpoint

```java
	/**
	 * Lo llama Carro al confirmar el checkout, no el navegador. Va antes que {@code /{id}/activar} en
	 * importancia, pero no choca con él: Spring prefiere siempre la ruta literal sobre la que tiene
	 * variable, así que {@code descontar-stock} nunca se confunde con un id.
	 */
	@PostMapping("/descontar-stock")
	public DescuentoStockResponse descontarStock(@Valid @RequestBody DescuentoStockRequest peticion) {
		return productoService.descontarStock(peticion);
	}
```

## 7. Las pruebas

Crea `src/test/java/co/edu/uis/catalogo/controller/ProductoControllerTest.java`. Estas **no necesitan MongoDB**: `@WebMvcTest` carga solo la capa web y `@MockitoBean` reemplaza el service por un doble que responde lo que uno le diga. Corren en segundos.

```java
package co.edu.uis.catalogo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.uis.catalogo.dto.DescuentoStockResponse;
import co.edu.uis.catalogo.dto.ProductoResponse;
import co.edu.uis.catalogo.error.ProductoNoEncontradoException;
import co.edu.uis.catalogo.error.StockInsuficienteException;
import co.edu.uis.catalogo.service.ProductoService;

/**
 * Comprueba los endpoints de B2: los códigos de estado, las validaciones del contrato y el formato de
 * los errores.
 */
@WebMvcTest(ProductoController.class)
class ProductoControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProductoService productoService;

	private static final String CUERPO_VALIDO = """
			{"nombre":"Camiseta","descripcion":"Algodón","precio":49900,
			 "categoria":"cat-ropa","stock":120,"imagenes":[]}""";

	private static ProductoResponse ejemplo(boolean activo) {
		return new ProductoResponse("abc123", "Camiseta", "Algodón", new BigDecimal("49900"),
				"cat-ropa", 120, List.of(), activo);
	}

	@Test
	void postValidoResponde201() throws Exception {
		given(productoService.crear(any())).willReturn(ejemplo(true));

		mockMvc.perform(post("/productos").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value("abc123"))
				.andExpect(jsonPath("$.activo").value(true));
	}

	@Test
	void postSinNombreResponde400() throws Exception {
		mockMvc.perform(post("/productos").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nombre":"","precio":49900,"categoria":"cat-ropa","stock":10}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
				.andExpect(jsonPath("$.mensaje").value("nombre: es obligatorio"));
	}

	@Test
	void postConPrecioCeroResponde400() throws Exception {
		mockMvc.perform(post("/productos").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nombre":"Camiseta","precio":0,"categoria":"cat-ropa","stock":10}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("precio: debe ser mayor que 0"));
	}

	@Test
	void postConStockNegativoResponde400() throws Exception {
		mockMvc.perform(post("/productos").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"nombre":"Camiseta","precio":100,"categoria":"cat-ropa","stock":-1}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("stock: no puede ser negativo"));
	}

	@Test
	void putValidoResponde200() throws Exception {
		given(productoService.actualizar(eq("abc123"), any())).willReturn(ejemplo(true));

		mockMvc.perform(put("/productos/abc123").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Camiseta"));
	}

	@Test
	void putAIdInexistenteResponde404() throws Exception {
		willThrow(new ProductoNoEncontradoException("nada")).given(productoService).actualizar(eq("nada"), any());

		mockMvc.perform(put("/productos/nada").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("PRODUCTO_NO_ENCONTRADO"));
	}

	@Test
	void activarResponde200ConElProductoActivo() throws Exception {
		given(productoService.activar("abc123")).willReturn(ejemplo(true));

		mockMvc.perform(post("/productos/abc123/activar"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(true));
	}

	@Test
	void descontarStockResponde200ConElRestante() throws Exception {
		given(productoService.descontarStock(any()))
				.willReturn(new DescuentoStockResponse(List.of(new DescuentoStockResponse.Item("abc123", 118))));

		mockMvc.perform(post("/productos/descontar-stock").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"items":[{"id":"abc123","cantidad":2}]}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].stockRestante").value(118));
	}

	@Test
	void descontarStockSinSuficienteResponde409() throws Exception {
		willThrow(new StockInsuficienteException("No hay stock suficiente del producto abc123"))
				.given(productoService).descontarStock(any());

		mockMvc.perform(post("/productos/descontar-stock").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"items":[{"id":"abc123","cantidad":999}]}"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("STOCK_INSUFICIENTE"));
	}

	@Test
	void descontarStockConCantidadCeroResponde400() throws Exception {
		mockMvc.perform(post("/productos/descontar-stock").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"items":[{"id":"abc123","cantidad":0}]}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
	}

}
```

Córrelas:

```powershell
.\mvnw.cmd test
```

Debe decir `Tests run: 10, Failures: 0, Errors: 0` para esta clase, más las que ya existían.

## 8. Probarlo a mano

Con Mongo encendido, arranca el servicio:

```powershell
.\mvnw.cmd spring-boot:run
```

Crea un archivo `pruebas.http` en la raíz (no lo subas, es para ti) e instala la extensión **REST Client** de VS Code. Cada bloque tiene un botón *Send Request* encima:

```http
### Crear un producto
POST http://localhost:8080/productos
Content-Type: application/json

{"nombre":"Camiseta básica algodón","descripcion":"Unisex 100% algodón",
 "precio":49900,"categoria":"cat-ropa","stock":10,"imagenes":[]}

### Precio inválido -> 400
POST http://localhost:8080/productos
Content-Type: application/json

{"nombre":"Camiseta","precio":-5,"categoria":"cat-ropa","stock":1}

### Categoría que no existe -> 400
POST http://localhost:8080/productos
Content-Type: application/json

{"nombre":"Camiseta","precio":100,"categoria":"cat-inventada","stock":1}

### Descontar stock (cambia el id por el que te devolvió el POST)
POST http://localhost:8080/productos/descontar-stock
Content-Type: application/json

{"items":[{"id":"PEGA_AQUI_EL_ID","cantidad":3}]}

### Pedir más de lo que hay -> 409
POST http://localhost:8080/productos/descontar-stock
Content-Type: application/json

{"items":[{"id":"PEGA_AQUI_EL_ID","cantidad":9999}]}
```

Lo que debe pasar, punto por punto:

| Petición | Esperado |
|---|---|
| Crear con datos válidos | **201**, con `id` y `activo: true` |
| Precio negativo o cero, stock negativo, nombre vacío | **400** `VALIDACION_FALLIDA`, con el nombre del campo en el mensaje |
| Categoría inexistente | **400** `VALIDACION_FALLIDA` |
| `POST` con `activo:false` o con `id` en el cuerpo | Se ignoran: nace activo y con id nuevo |
| `PUT` sobre un producto inactivo | **200**, y sigue con `activo: false` |
| Activar uno inactivo | **200**, `activo: true` |
| Activar uno que ya estaba activo | **200**, sin cambios |
| Descontar con stock disponible | **200**, y el stock baja en Mongo |
| Descontar más de lo que hay | **409** `STOCK_INSUFICIENTE`, y **ningún** stock cambió |

Para verificar el stock en la base, en otra terminal:

```powershell
docker exec -it catalogo-mongo mongosh catalogo --eval "db.productos.find({}, {nombre:1, stock:1, activo:1})"
```

## 9. Subir tu trabajo

```powershell
git status
git add .
git commit -m "Agrega POST y PUT de productos con sus validaciones"
git pull origin main
.\mvnw.cmd test
git push
```

- En `git status` no debe aparecer `target/` ni `pruebas.http`. Si aparecen, avisa antes de seguir.
- **El `git pull origin main` antes del push no es opcional**: B3 está tocando los mismos dos archivos que tú. Si hay conflicto, aparece ahora, en tu rama, y no en el pull request.
- Abre el pull request en GitHub con base `main`, ponle de título `B2: POST y PUT de productos` y pide la revisión de Jhon.
- En **Jira**, pasa tus tarjetas a *En revisión*, y a *Listo* cuando se una el PR.
- Marca en el README las casillas de las Historias 1, 4 y 7 en el mismo PR.

Recuerda: **el descuento de stock va en un segundo pull request**, después de que este quede en `main`.

## 10. Si algo falla

| Síntoma | Causa | Qué hacer |
|---|---|---|
| Las validaciones no saltan: entra un precio negativo | Falta `@Valid` en el parámetro del controller | Sección 4.2 |
| `Parameter 0 of constructor in ProductoService required a bean` | Falta `@Repository` en `ProductoStockRepository` | Revisa la anotación |
| `ns does not exist: catalogo.productos` | Mongo está encendido pero la base está vacía | Normal la primera vez; crea un producto |
| `Connection refused` al arrancar | Docker no está corriendo | `docker compose up -d` |
| El POST responde 200 en vez de 201 | Falta `@ResponseStatus(HttpStatus.CREATED)` | Sección 4.2 |
| `descontar-stock` responde 404 con un id válido | El id se copió con comillas o con espacios | Pégalo tal cual lo devolvió el POST |
| El mensaje de error sale en inglés | Se usaron los mensajes por defecto de Jakarta | Escribe `message = "..."` en cada anotación |
| `Web server failed to start. Port 8080 was already in use` | Ya hay otra instancia corriendo | Ciérrala, o `Get-Process -Id (Get-NetTCPConnection -LocalPort 8080).OwningProcess` |

---

_Última actualización: 2026-09-23_
