
package co.edu.uis.catalogo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.mongodb.core.MongoTemplate;

import co.edu.uis.catalogo.dto.ProductoListadoResponse;
import co.edu.uis.catalogo.error.ValidacionFallidaException;
import co.edu.uis.catalogo.model.Producto;
import co.edu.uis.catalogo.repository.ProductoRepository;

/**
 * Listado contra MongoDB de verdad: paginación, filtros y el total.
 * Necesita Mongo encendido.
 *
 * Es la prueba que atrapa el error más común de esta tarea:
 * que la página 1 devuelva los resultados de la 2, porque Spring
 * cuenta desde 0 y el contrato desde 1.
 *
 * Comprueba que se utilice catalogo-test antes de borrar los productos.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProductoServiceListadoTest {

    @Autowired
    private ProductoService productoService;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @TestConfiguration
    static class MockRabbitConfig {

        @Bean
        public RabbitTemplate rabbitTemplate() {
            return org.mockito.Mockito.mock(RabbitTemplate.class);
        }
    }

    @BeforeEach
    void prepararCatalogo() {

        assertThat(mongoTemplate.getDb().getName())
                .isEqualTo("catalogo-test");

        productoRepository.deleteAll();

        for (int i = 1; i <= 25; i++) {
            productoRepository.save(new Producto(
                    "Camiseta " + i,
                    "Algodón",
                    new BigDecimal("10000"),
                    "cat-ropa",
                    5,
                    List.of()));
        }

        for (int i = 1; i <= 5; i++) {
            Producto retirado = new Producto(
                    "Retirado " + i,
                    "Algodón",
                    new BigDecimal("10000"),
                    "cat-hogar",
                    5,
                    List.of());

            retirado.desactivar();
            productoRepository.save(retirado);
        }
    }

    @Test
    void laPrimeraPaginaEsLaUno() {
        ProductoListadoResponse pagina =
                productoService.listar(1, 20, null, "true");

        assertThat(pagina.productos()).hasSize(20);
        assertThat(pagina.total()).isEqualTo(25);
        assertThat(pagina.pagina()).isEqualTo(1);
        assertThat(pagina.tamanoPagina()).isEqualTo(20);
    }

    @Test
    void laSegundaPaginaTraeElResto() {
        ProductoListadoResponse pagina =
                productoService.listar(2, 20, null, "true");

        assertThat(pagina.productos()).hasSize(5);
        assertThat(pagina.total()).isEqualTo(25);
    }

    @Test
    void unaPaginaMasAllaDeLaUltimaDevuelveListaVaciaYElMismoTotal() {
        ProductoListadoResponse pagina =
                productoService.listar(99, 20, null, "true");

        assertThat(pagina.productos()).isEmpty();
        assertThat(pagina.total()).isEqualTo(25);
    }

    @Test
    void losDesactivadosNoSalenPorDefecto() {
        ProductoListadoResponse pagina =
                productoService.listar(1, 100, null, "true");

        assertThat(pagina.total()).isEqualTo(25);
        assertThat(pagina.productos()).allMatch(p -> p.activo());
    }

    @Test
    void conActivoFalseSalenSoloLosDesactivados() {
        ProductoListadoResponse pagina =
                productoService.listar(1, 100, null, "false");

        assertThat(pagina.total()).isEqualTo(5);
        assertThat(pagina.productos()).noneMatch(p -> p.activo());
    }

    @Test
    void conActivoTodosSalenLosTreinta() {
        assertThat(productoService.listar(1, 100, null, "todos").total())
                .isEqualTo(30);
    }

    @Test
    void filtraPorCategoria() {
        assertThat(productoService.listar(1, 100, "cat-ropa", "true").total())
                .isEqualTo(25);

        assertThat(productoService.listar(1, 100, "cat-hogar", "true").total())
                .isZero();
    }

    @Test
    void unaCategoriaSinProductosDevuelveListaVaciaYNoUnError() {
        ProductoListadoResponse pagina =
                productoService.listar(1, 20, "cat-electronica", "true");

        assertThat(pagina.productos()).isEmpty();
        assertThat(pagina.total()).isZero();
    }

    @Test
    void unaCategoriaInexistenteTambienDevuelveListaVacia() {
        assertThat(productoService.listar(1, 20, "cat-inventada", "true").total())
                .isZero();
    }

    @Test
    void unFiltroActivoInvalidoEsErrorDeValidacion() {
        assertThatThrownBy(() -> productoService.listar(1, 20, null, "quizas"))
                .isInstanceOf(ValidacionFallidaException.class)
                .hasMessage("activo: debe ser true, false o todos");
    }

    @Test
    void desactivarEsIdempotenteYNoCambiaElRegistro() {
        String id = productoService.listar(1, 1, null, "true")
                .productos()
                .getFirst()
                .id();

        assertThat(productoService.desactivar(id)).isTrue();
        assertThat(productoService.desactivar(id)).isFalse();
        assertThat(productoService.obtenerPorId(id).activo()).isFalse();
    }
}
