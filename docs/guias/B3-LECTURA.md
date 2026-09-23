# Guía de B3 — Lectura y borrado de productos

**Para:** Jhon · **Tareas:** B3 — Historias 2 (listar), 3 (ver detalle), 5 (desactivar) y 8 (ver los desactivados) · **Rama:** `b3-lectura` · **Entrega:** viernes 2 de octubre

Al terminar, el catálogo podrá **listarse con filtros y paginación, consultarse por id y desactivarse**. Es lo que necesitan el equipo C para validar antes de agregar al carrito y el frontend para pintar el listado.

**El código de esta guía ya se probó**: 20 pruebas, 10 de la capa web y 10 contra MongoDB de verdad. Si copias cada archivo tal cual, funciona.

**No tienes que esperar a nadie.** B1 ya está en `main`. Tu tarea no depende de B2; lo único que comparten es que los dos tocan `ProductoService` y `ProductoController`, así que quien una su pull request de segundo tendrá un conflicto sencillo ([guía de git](../../GUIA-GIT.md) §7).

Los comandos son para **PowerShell**, en la terminal de VS Code.

## 1. Antes de empezar

1. Necesitas **JDK 21, Docker Desktop y VS Code** con el *Extension Pack for Java* ([guía de inicio](../../GUIA-INICIO.md) §1), y haber aceptado la invitación de GitHub.
2. Trae lo último de `main` y crea tu rama:

   ```powershell
   cd C:\dev\teambsoft-backend
   git switch main
   git pull
   git switch -c b3-lectura
   git push -u origin b3-lectura
   ```

3. Levanta MongoDB y comprueba que el proyecto arranca como está:

   ```powershell
   docker compose up -d
   .\mvnw.cmd test
   ```

4. Lee la **sección 2 del [contrato](../CONTRATO-CATALOGO.md)**, sobre todo la parte de `GET /productos`: las reglas de paginación son las que más se equivocan.

## 2. Cómo crear cada archivo

- **Archivo nuevo:** clic derecho sobre `src/main/java/co/edu/uis/catalogo` → **New File…** y escribe la ruta con la carpeta.
- **Archivo que ya existe:** agrégale lo que indica cada paso, no lo reemplaces entero.

Lo que vas a tocar:

```
src/main/java/co/edu/uis/catalogo/
├── dto/PaginaProductos.java          NUEVO  la forma de la respuesta del listado
├── repository/ProductoRepository.java  se le agregan 3 consultas
├── service/ProductoService.java        se le agregan 3 métodos y 2 privados
└── controller/ProductoController.java  se le agregan 3 endpoints
```

## 3. La respuesta del listado: `dto/PaginaProductos.java`

`GET /productos` no devuelve una lista pelada: devuelve un objeto con la lista y los datos de la paginación, porque el frontend necesita saber cuántas páginas hay para dibujar los botones.

```java
package co.edu.uis.catalogo.dto;

import java.util.List;

/**
 * Una página de resultados de {@code GET /productos}, con la forma exacta del contrato (sección 2).
 *
 * <p>{@code total} es la cantidad de productos que cumplen el filtro, <b>no</b> los de esta página:
 * es lo que el frontend necesita para dibujar cuántas páginas hay.
 */
public record PaginaProductos(List<ProductoResponse> productos, long total, int pagina, int tamanoPagina) {

}
```

**`total` es el total del filtro, no el de la página.** Si hay 42 productos y pides 20, la respuesta trae 20 en `productos` y `42` en `total`. Confundirlos es el error que hace que al frontend le salga una sola página cuando hay tres.

## 4. Las consultas: `repository/ProductoRepository.java`

Hoy la interfaz está vacía. Agrégale tres métodos:

```java
package co.edu.uis.catalogo.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import co.edu.uis.catalogo.model.Producto;

/**
 * Acceso a la colección de productos. Al heredar de {@code MongoRepository} ya vienen hechos
 * {@code save}, {@code findById}, {@code findAll} y {@code deleteAll}.
 *
 * <p>Los tres métodos de abajo no tienen cuerpo a propósito: Spring Data los implementa solo, leyendo
 * el nombre. {@code findByActivoAndCategoria} se traduce a "buscar donde activo = ... y categoria = ...".
 * Si se escribe mal un nombre de campo, la aplicación falla al arrancar, no en tiempo de ejecución.
 *
 * <p>El orden de los campos en el nombre no es casual: coincide con el índice compuesto
 * {@code {activo, categoria}} que declara {@link Producto}, y por eso MongoDB puede usarlo.
 */
public interface ProductoRepository extends MongoRepository<Producto, String> {

	Page<Producto> findByActivo(boolean activo, Pageable paginado);

	Page<Producto> findByCategoria(String categoria, Pageable paginado);

	Page<Producto> findByActivoAndCategoria(boolean activo, String categoria, Pageable paginado);

}
```

Esto es **consultas derivadas del nombre**, una de las cosas más útiles de Spring Data: no escribes la consulta, la escribes en el nombre del método. `findByActivoAndCategoria` se traduce solo a "busca los documentos donde `activo` valga esto y `categoria` valga aquello".

Dos cosas que vale la pena saber:

- **Si escribes mal un campo**, por ejemplo `findByActivoAndCategorias`, la aplicación **no arranca**. Es una buena noticia: el error sale al segundo, no en producción.
- **El orden importa para el rendimiento.** `Producto` declara un índice compuesto `{activo, categoria}` (mira su `@CompoundIndex`). Un índice compuesto se puede usar empezando por la izquierda, así que `findByActivoAndCategoria` lo aprovecha; al revés, no.

## 5. Listar productos — Historias 2 y 8

### 5.1 El método en el service

Agrega esto en `ProductoService`:

```java
	/** Valor de {@code ?activo=} que trae los productos sin importar su estado. */
	private static final String TODOS = "todos";

	/**
	 * GET /productos: una página de productos, con filtro opcional por categoría y por estado
	 * (Historias 2 y 8).
	 */
	public PaginaProductos listar(String categoria, int pagina, int tamanoPagina, String activo) {
		validarFiltroActivo(activo);

		// PageRequest cuenta desde 0 y el contrato desde 1: aquí se hace la resta, una sola vez.
		Pageable paginado = PageRequest.of(pagina - 1, tamanoPagina);
		Page<Producto> encontrados = buscarSegunFiltros(categoria, activo, paginado);

		List<ProductoResponse> productos = encontrados.getContent().stream()
				.map(ProductoResponse::desde)
				.toList();

		return new PaginaProductos(productos, encontrados.getTotalElements(), pagina, tamanoPagina);
	}

	/** Elige el método del repositorio segun los filtros que hayan llegado. */
	private Page<Producto> buscarSegunFiltros(String categoria, String activo, Pageable paginado) {
		boolean todos = TODOS.equals(activo);
		boolean soloActivos = Boolean.parseBoolean(activo);

		if (categoria == null || categoria.isBlank()) {
			return todos ? productoRepository.findAll(paginado)
					: productoRepository.findByActivo(soloActivos, paginado);
		}

		return todos ? productoRepository.findByCategoria(categoria, paginado)
				: productoRepository.findByActivoAndCategoria(soloActivos, categoria, paginado);
	}

	/**
	 * El parámetro solo acepta tres valores. Se valida aquí y no con una anotación porque el mensaje
	 * de error tiene que nombrar los tres, y eso es más claro escrito que en una expresión regular.
	 */
	private void validarFiltroActivo(String activo) {
		if (!TODOS.equals(activo) && !"true".equals(activo) && !"false".equals(activo)) {
			throw new ValidacionFallidaException("activo: debe ser true, false o todos");
		}
	}
```

Con sus `import`:

```java
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import co.edu.uis.catalogo.dto.PaginaProductos;
import co.edu.uis.catalogo.dto.ProductoResponse;
import co.edu.uis.catalogo.error.ValidacionFallidaException;
```

**La línea más importante de toda tu tarea es esta:**

```java
Pageable paginado = PageRequest.of(pagina - 1, tamanoPagina);
```

Spring cuenta las páginas **desde 0** y nuestro contrato **desde 1**. Ese `- 1` es la traducción entre los dos mundos, y va en un solo sitio a propósito. Si se te olvida, `?pagina=1` devuelve los resultados de la segunda página y nadie se da cuenta hasta que el frontend muestra el catálogo con el primer producto faltando.

**Por qué `activo` es un `String` y no un `boolean`:** porque tiene **tres** valores posibles —`true`, `false` y `todos`— y un booleano solo da dos. Un valor distinto es 400 con un mensaje que los nombra los tres.

### 5.2 El endpoint

```java
	/**
	 * Los valores por defecto están aquí y no en el service porque son parte del contrato de la API:
	 * quien no envíe nada recibe la primera página, de 20, solo con productos activos.
	 */
	@GetMapping
	public PaginaProductos listar(
			@RequestParam(required = false) String categoria,
			@RequestParam(defaultValue = "1") @Min(value = 1, message = "debe ser 1 o más") int pagina,
			@RequestParam(defaultValue = "20") @Min(value = 1, message = "debe ser 1 o más")
			@Max(value = 100, message = "no puede pasar de 100") int tamanoPagina,
			@RequestParam(defaultValue = "true") String activo) {

		return productoService.listar(categoria, pagina, tamanoPagina, activo);
	}
```

Con sus `import`:

```java
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import co.edu.uis.catalogo.dto.PaginaProductos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
```

**`activo` por defecto en `"true"` es lo que mantiene el contrato compatible.** El parámetro es nuevo en v2.3: los equipos A y C escribieron su código cuando no existía, y como el valor por defecto reproduce el comportamiento viejo, no tienen que cambiar nada. Esa es la diferencia entre un cambio de versión menor y uno que rompe a todo el mundo.

Los mensajes de las anotaciones ya salen en el formato del contrato: el manejador global que dejó B1 convierte `pagina: debe ser 1 o más` en `{"codigo":"VALIDACION_FALLIDA","mensaje":"pagina: debe ser 1 o más"}`. No tienes que hacer nada más.

## 6. Ver el detalle — Historia 3

```java
	/** GET /productos/{id}: devuelve el producto aunque esté inactivo (Historia 3). */
	public ProductoResponse obtener(String id) {
		return ProductoResponse.desde(buscarExistente(id));
	}
```

Y el endpoint:

```java
	/** Devuelve el producto aunque esté inactivo: el 404 es solo para un id que no existe. */
	@GetMapping("/{id}")
	public ProductoResponse obtener(@PathVariable String id) {
		return productoService.obtener(id);
	}
```

**Aquí hay una trampa fácil de caer:** parecería lógico que un producto desactivado responda 404, ya que no aparece en el listado. **No.** El contrato dice que el detalle lo devuelve igual, con `activo: false`, y el 404 es solo cuando el id no existe.

La razón es el equipo C: si alguien tiene un producto en el carrito y ese producto se retira, Carro necesita poder consultarlo para marcarlo como no disponible. Con un 404 no podría distinguir "lo retiraron" de "ese id nunca existió".

Por eso `buscarExistente` no filtra por `activo`: ya está bien como está.

## 7. Desactivar — Historia 5

```java
	/**
	 * DELETE: soft delete (Historia 5). Es idempotente: si ya estaba inactivo no guarda nada, y así
	 * tampoco se publica otro evento cuando B4 los conecte.
	 */
	public void desactivar(String id) {
		Producto producto = buscarExistente(id);

		if (!producto.isActivo()) {
			return;
		}

		producto.desactivar();
		productoRepository.save(producto);
	}
```

Y el endpoint:

```java
	/** 204: la respuesta no lleva cuerpo, por eso el método devuelve void. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void desactivar(@PathVariable String id) {
		productoService.desactivar(id);
	}
```

Con sus `import`:

```java
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
```

Tres cosas de este método:

- **Es un borrado suave** (*soft delete*): el registro no se va de la base, solo se marca `activo: false`. Así, si mañana el equipo de Órdenes necesita saber qué producto se vendió hace tres meses, el dato sigue ahí.
- **El `if` del principio es la idempotencia.** Desactivar algo ya desactivado responde 204 igual, pero sin guardar. Cuando B4 conecte los eventos, ese `return` es lo que evita publicar un `producto.desactivado` de más y que el equipo A reindexe de gratis.
- **204 y no 200**: la respuesta no lleva cuerpo. Por eso el método es `void`.

## 8. Las pruebas

Son dos clases. La primera, `src/test/java/co/edu/uis/catalogo/controller/ProductoLecturaControllerTest.java`, **no necesita MongoDB**: `@WebMvcTest` carga solo la capa web y `@MockitoBean` reemplaza el service por un doble.

```java
package co.edu.uis.catalogo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.uis.catalogo.dto.PaginaProductos;
import co.edu.uis.catalogo.dto.ProductoResponse;
import co.edu.uis.catalogo.error.ProductoNoEncontradoException;
import co.edu.uis.catalogo.error.ValidacionFallidaException;
import co.edu.uis.catalogo.service.ProductoService;

/** Endpoints de lectura y borrado (B3): códigos, parámetros y formato de la página. */
@WebMvcTest(ProductoController.class)
class ProductoLecturaControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProductoService productoService;

	private static ProductoResponse ejemplo(boolean activo) {
		return new ProductoResponse("abc123", "Camiseta", "Algodón", new BigDecimal("49900"),
				"cat-ropa", 120, List.of(), activo);
	}

	@Test
	void listarDevuelveLaFormaDelContrato() throws Exception {
		given(productoService.listar(any(), any(Integer.class), any(Integer.class), any()))
				.willReturn(new PaginaProductos(List.of(ejemplo(true)), 42, 1, 20));

		mockMvc.perform(get("/productos"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.productos[0].id").value("abc123"))
				.andExpect(jsonPath("$.total").value(42))
				.andExpect(jsonPath("$.pagina").value(1))
				.andExpect(jsonPath("$.tamanoPagina").value(20));
	}

	@Test
	void listarSinParametrosUsaLosValoresPorDefecto() throws Exception {
		given(productoService.listar(any(), any(Integer.class), any(Integer.class), any()))
				.willReturn(new PaginaProductos(List.of(), 0, 1, 20));

		mockMvc.perform(get("/productos")).andExpect(status().isOk());

		verify(productoService).listar(null, 1, 20, "true");
	}

	@Test
	void paginaCeroResponde400() throws Exception {
		mockMvc.perform(get("/productos").param("pagina", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
				.andExpect(jsonPath("$.mensaje").value("pagina: debe ser 1 o más"));
	}

	@Test
	void tamanoPaginaMayorQueCienResponde400() throws Exception {
		mockMvc.perform(get("/productos").param("tamanoPagina", "101"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("tamanoPagina: no puede pasar de 100"));
	}

	@Test
	void paginaNoNumericaResponde400() throws Exception {
		mockMvc.perform(get("/productos").param("pagina", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("El parámetro 'pagina' tiene un valor inválido"));
	}

	@Test
	void filtroActivoInvalidoResponde400() throws Exception {
		willThrow(new ValidacionFallidaException("activo: debe ser true, false o todos"))
				.given(productoService).listar(any(), any(Integer.class), any(Integer.class), any());

		mockMvc.perform(get("/productos").param("activo", "quizas"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value("activo: debe ser true, false o todos"));
	}

	@Test
	void detalleDevuelveTambienLosInactivos() throws Exception {
		given(productoService.obtener("abc123")).willReturn(ejemplo(false));

		mockMvc.perform(get("/productos/abc123"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activo").value(false));
	}

	@Test
	void detalleDeIdInexistenteResponde404() throws Exception {
		willThrow(new ProductoNoEncontradoException("nada")).given(productoService).obtener("nada");

		mockMvc.perform(get("/productos/nada"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("PRODUCTO_NO_ENCONTRADO"));
	}

	@Test
	void borrarResponde204SinCuerpo() throws Exception {
		mockMvc.perform(delete("/productos/abc123"))
				.andExpect(status().isNoContent());

		verify(productoService).desactivar("abc123");
	}

	@Test
	void borrarUnIdInexistenteResponde404() throws Exception {
		willThrow(new ProductoNoEncontradoException("nada")).given(productoService).desactivar("nada");

		mockMvc.perform(delete("/productos/nada"))
				.andExpect(status().isNotFound());
	}

}
```

La segunda, `src/test/java/co/edu/uis/catalogo/service/ProductoServiceListadoTest.java`, **sí necesita MongoDB encendido**, y es la que atrapa el error de la paginación:

```java
package co.edu.uis.catalogo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import co.edu.uis.catalogo.dto.PaginaProductos;
import co.edu.uis.catalogo.error.ValidacionFallidaException;
import co.edu.uis.catalogo.model.Producto;
import co.edu.uis.catalogo.repository.ProductoRepository;

/**
 * Listado contra MongoDB de verdad: paginación, filtros y el total. Necesita Mongo encendido.
 *
 * <p>Es la prueba que atrapa el error más común de esta tarea: que la página 1 devuelva los
 * resultados de la 2, porque Spring cuenta desde 0 y el contrato desde 1.
 */
@SpringBootTest
class ProductoServiceListadoTest {

	@Autowired
	private ProductoService productoService;

	@Autowired
	private ProductoRepository productoRepository;

	@BeforeEach
	void prepararCatalogo() {
		productoRepository.deleteAll();

		for (int i = 1; i <= 25; i++) {
			productoRepository.save(new Producto("Camiseta " + i, "", new BigDecimal("10000"), "cat-ropa", 5,
					List.of()));
		}
		for (int i = 1; i <= 5; i++) {
			Producto retirado = new Producto("Retirado " + i, "", new BigDecimal("10000"), "cat-hogar", 5,
					List.of());
			retirado.desactivar();
			productoRepository.save(retirado);
		}
	}

	@Test
	void laPrimeraPaginaEsLaUno() {
		PaginaProductos pagina = productoService.listar(null, 1, 20, "true");

		assertThat(pagina.productos()).hasSize(20);
		assertThat(pagina.total()).isEqualTo(25);
		assertThat(pagina.pagina()).isEqualTo(1);
	}

	@Test
	void laSegundaPaginaTraeElResto() {
		PaginaProductos pagina = productoService.listar(null, 2, 20, "true");

		assertThat(pagina.productos()).hasSize(5);
		assertThat(pagina.total()).isEqualTo(25);
	}

	@Test
	void unaPaginaMasAllaDeLaUltimaDevuelveListaVaciaYElMismoTotal() {
		PaginaProductos pagina = productoService.listar(null, 99, 20, "true");

		assertThat(pagina.productos()).isEmpty();
		assertThat(pagina.total()).isEqualTo(25);
	}

	@Test
	void losDesactivadosNoSalenPorDefecto() {
		PaginaProductos pagina = productoService.listar(null, 1, 100, "true");

		assertThat(pagina.total()).isEqualTo(25);
		assertThat(pagina.productos()).allMatch(p -> p.activo());
	}

	@Test
	void conActivoFalseSalenSoloLosDesactivados() {
		PaginaProductos pagina = productoService.listar(null, 1, 100, "false");

		assertThat(pagina.total()).isEqualTo(5);
		assertThat(pagina.productos()).noneMatch(p -> p.activo());
	}

	@Test
	void conActivoTodosSalenLosTreinta() {
		assertThat(productoService.listar(null, 1, 100, "todos").total()).isEqualTo(30);
	}

	@Test
	void filtraPorCategoria() {
		assertThat(productoService.listar("cat-ropa", 1, 100, "true").total()).isEqualTo(25);
	}

	@Test
	void unaCategoriaSinProductosDevuelveListaVaciaYNoUnError() {
		PaginaProductos pagina = productoService.listar("cat-electronica", 1, 20, "true");

		assertThat(pagina.productos()).isEmpty();
		assertThat(pagina.total()).isZero();
	}

	@Test
	void unaCategoriaInexistenteTambienDevuelveListaVacia() {
		assertThat(productoService.listar("cat-inventada", 1, 20, "true").total()).isZero();
	}

	@Test
	void unFiltroActivoInvalidoEsErrorDeValidacion() {
		assertThatThrownBy(() -> productoService.listar(null, 1, 20, "quizas"))
				.isInstanceOf(ValidacionFallidaException.class)
				.hasMessage("activo: debe ser true, false o todos");
	}

}
```

Córrelas con Mongo encendido:

```powershell
.\mvnw.cmd test
```

Deben pasar las 20, más las que ya existían.

> **Ojo con `deleteAll()`**: esa prueba borra todos los productos de la base `catalogo` antes de cada caso. En desarrollo no importa, pero si tenías productos de prueba que querías conservar, ya no están.

## 9. Probarlo a mano

Con Mongo encendido, arranca el servicio y crea unos productos (pídele a Hector su `POST`, o inserta directo en Mongo):

```powershell
.\mvnw.cmd spring-boot:run
```

Con la extensión **REST Client** de VS Code, un archivo `pruebas.http` (no lo subas):

```http
### Listado por defecto: página 1, 20 por página, solo activos
GET http://localhost:8080/productos

### Segunda página
GET http://localhost:8080/productos?pagina=2

### Filtrado por categoría
GET http://localhost:8080/productos?categoria=cat-ropa

### Los desactivados (Historia 8)
GET http://localhost:8080/productos?activo=false

### Página 0 -> 400
GET http://localhost:8080/productos?pagina=0

### Tamaño de página 101 -> 400
GET http://localhost:8080/productos?tamanoPagina=101

### Detalle (cambia el id)
GET http://localhost:8080/productos/PEGA_AQUI_EL_ID

### Desactivar -> 204
DELETE http://localhost:8080/productos/PEGA_AQUI_EL_ID

### Desactivar otra vez -> 204 igual, sin error
DELETE http://localhost:8080/productos/PEGA_AQUI_EL_ID

### Y ahora el detalle debe salir con activo: false, no 404
GET http://localhost:8080/productos/PEGA_AQUI_EL_ID
```

Lo que debe pasar:

| Petición | Esperado |
|---|---|
| `GET /productos` sin nada | **200**, `pagina: 1`, `tamanoPagina: 20`, solo activos |
| `?pagina=2` | Los siguientes 20, con el mismo `total` |
| `?pagina=99` | **200** con `productos: []` y el mismo `total` |
| `?categoria=cat-hogar` sin productos | **200** con `total: 0`, no un error |
| `?categoria=cat-inventada` | **200** con `total: 0` |
| `?pagina=0` o `?tamanoPagina=101` | **400** `VALIDACION_FALLIDA` |
| `?activo=false` | Solo los desactivados |
| `?activo=quizas` | **400** con el mensaje que nombra los tres valores |
| `GET` de un producto desactivado | **200** con `activo: false` |
| `GET` de un id que no existe | **404** `PRODUCTO_NO_ENCONTRADO` |
| `DELETE` dos veces seguidas | **204** las dos veces |

Para mirar la base por dentro:

```powershell
docker exec -it catalogo-mongo mongosh catalogo --eval "db.productos.countDocuments({activo: true})"
```

## 10. Subir tu trabajo

```powershell
git status
git add .
git commit -m "Agrega el listado paginado, el detalle y el borrado de productos"
git pull origin main
.\mvnw.cmd test
git push
```

- **El `git pull origin main` antes del push no es opcional**: Hector está tocando los mismos dos archivos que tú. Si hay conflicto, aparece ahora, en tu rama, y no en el pull request.
- Abre el pull request con base `main`, título `B3: listado, detalle y borrado de productos`, y pide la revisión de Hector.
- En **Jira**, pasa tus tarjetas a *En revisión*, y a *Listo* cuando se una el PR.
- Marca en el README las casillas de las Historias 2, 3, 5 y 8.

## 11. Si algo falla

| Síntoma | Causa | Qué hacer |
|---|---|---|
| La aplicación no arranca: `No property 'xxx' found for type 'Producto'` | Un nombre de método del repositorio no coincide con un campo | Revisa las mayúsculas en `findByActivoAndCategoria` |
| `?pagina=1` devuelve los de la página 2 | Se olvidó el `- 1` en `PageRequest.of` | Sección 5.1 |
| `total` trae 20 en vez de 42 | Se usó `getContent().size()` en vez de `getTotalElements()` | Sección 5.1 |
| Un producto desactivado responde 404 | `buscarExistente` se modificó para filtrar por activo | Déjalo como estaba: el filtro va en el listado, no en el detalle |
| `DELETE` responde 200 en vez de 204 | Falta `@ResponseStatus(HttpStatus.NO_CONTENT)` | Sección 7 |
| `?tamanoPagina=101` pasa sin error | Falta la anotación `@Max` | Sección 5.2 |
| `Connection refused` al arrancar | Docker no está corriendo | `docker compose up -d` |
| Las pruebas de listado fallan con totales raros | Quedaron productos de una corrida anterior | El `deleteAll()` del `@BeforeEach` debería limpiarlos; revisa que esté |

---

_Última actualización: 2026-09-23_
