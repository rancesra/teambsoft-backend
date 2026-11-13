package co.edu.uis.catalogo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ActiveProfiles;

import co.edu.uis.catalogo.dto.DescuentoStockRequest;
import co.edu.uis.catalogo.dto.DescuentoStockResponse;
import co.edu.uis.catalogo.error.ProductoNoEncontradoException;
import co.edu.uis.catalogo.error.ReferenciaEnUsoException;
import co.edu.uis.catalogo.error.StockInsuficienteException;
import co.edu.uis.catalogo.error.ValidacionFallidaException;
import co.edu.uis.catalogo.model.DescuentoStock;
import co.edu.uis.catalogo.model.Producto;
import co.edu.uis.catalogo.repository.ProductoRepository;

@SpringBootTest
@ActiveProfiles("test")
class ProductoServiceDescuentoTest {

    @Autowired
    private ProductoService productoService;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private String idA;
    private String idB;
    private String idC;
    private String idBajo;
    private String idInactivo;

    @TestConfiguration
    static class MockRabbitConfig {
        @Bean
        public RabbitTemplate rabbitTemplate() {
            return org.mockito.Mockito.mock(RabbitTemplate.class);
        }
    }

    @BeforeEach
    void prepararCatalogo() {
        assertThat(mongoTemplate.getDb().getName()).isEqualTo("catalogo-test");

        productoRepository.deleteAll();
        mongoTemplate.remove(new Query(), DescuentoStock.class);

        Producto a = productoRepository.save(new Producto(
                "Prod A", "Desc A", new BigDecimal("10000"), "cat-ropa", 10, List.of()));
        Producto b = productoRepository.save(new Producto(
                "Prod B", "Desc B", new BigDecimal("20000"), "cat-ropa", 5, List.of()));
        Producto c = productoRepository.save(new Producto(
                "Prod C", "Desc C", new BigDecimal("30000"), "cat-ropa", 3, List.of()));
        Producto bajo = productoRepository.save(new Producto(
                "Prod Bajo", "Desc Bajo", new BigDecimal("5000"), "cat-ropa", 2, List.of()));

        Producto inactivo = new Producto(
                "Prod Inactivo", "Desc Inactivo",
                new BigDecimal("15000"), "cat-ropa", 5, List.of());
        inactivo.desactivar();
        inactivo = productoRepository.save(inactivo);

        idA = a.getId();
        idB = b.getId();
        idC = c.getId();
        idBajo = bajo.getId();
        idInactivo = inactivo.getId();

        assertThat(idA).isNotBlank();
        assertThat(idB).isNotBlank();
        assertThat(idC).isNotBlank();
        assertThat(idBajo).isNotBlank();
        assertThat(idInactivo).isNotBlank();

        clearInvocations(rabbitTemplate);
    }

    @Test
    void descuentoExitosoDeVariosProductosPublicaEventosDespuesDeTodosLosDescuentos() {
        DescuentoStockRequest request = new DescuentoStockRequest(List.of(
                new DescuentoStockRequest.Item(idA, 3),
                new DescuentoStockRequest.Item(idB, 2),
                new DescuentoStockRequest.Item(idC, 1)));

        DescuentoStockResponse response = productoService.descontarStock(request);

        assertThat(response.items()).hasSize(3);
        assertThat(response.items().get(0).stockRestante()).isEqualTo(7);
        assertThat(response.items().get(1).stockRestante()).isEqualTo(3);
        assertThat(response.items().get(2).stockRestante()).isEqualTo(2);

        assertThat(productoRepository.findById(idA).orElseThrow().getStock()).isEqualTo(7);
        assertThat(productoRepository.findById(idB).orElseThrow().getStock()).isEqualTo(3);
        assertThat(productoRepository.findById(idC).orElseThrow().getStock()).isEqualTo(2);
    }

    @Test
    void stockInsuficienteEnProductoPosteriorRevierteAnterioresYNoPublicaEventos() {
        DescuentoStockRequest request = new DescuentoStockRequest(List.of(
                new DescuentoStockRequest.Item(idA, 3),
                new DescuentoStockRequest.Item(idBajo, 5)));

        assertThatThrownBy(() -> productoService.descontarStock(request))
                .isInstanceOf(StockInsuficienteException.class);

        assertThat(productoRepository.findById(idA).orElseThrow().getStock()).isEqualTo(10);
        assertThat(productoRepository.findById(idBajo).orElseThrow().getStock()).isEqualTo(2);

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void productoDesactivadoDespuesDeProcesarAnteriorRevierteYNoPublicaEventos() {
        DescuentoStockRequest request = new DescuentoStockRequest(List.of(
                new DescuentoStockRequest.Item(idA, 3),
                new DescuentoStockRequest.Item(idInactivo, 1)));

        assertThatThrownBy(() -> productoService.descontarStock(request))
                .isInstanceOf(ValidacionFallidaException.class)
                .hasMessageContaining("desactivado");

        assertThat(productoRepository.findById(idA).orElseThrow().getStock()).isEqualTo(10);
        assertThat(productoRepository.findById(idInactivo).orElseThrow().getStock()).isEqualTo(5);

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void descuentoConUnSoloProductoFuncionaCorrectamente() {
        DescuentoStockRequest request = new DescuentoStockRequest(List.of(
                new DescuentoStockRequest.Item(idB, 2)));

        DescuentoStockResponse response = productoService.descontarStock(request);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().stockRestante()).isEqualTo(3);
        assertThat(productoRepository.findById(idB).orElseThrow().getStock()).isEqualTo(3);
    }

    @Test
    void descuentoConProductoRepetidoLanzaError() {
        DescuentoStockRequest request = new DescuentoStockRequest(List.of(
                new DescuentoStockRequest.Item(idA, 1),
                new DescuentoStockRequest.Item(idA, 1)));

        assertThatThrownBy(() -> productoService.descontarStock(request))
                .isInstanceOf(ValidacionFallidaException.class)
                .hasMessageContaining("repetido");
    }

    // ---- v2.4 ----

    /** Antes de la v2.4, el 404 del segundo ítem dejaba descontado el primero. */
    @Test
    void productoInexistenteDespuesDeUnoValidoNoDescuentaNada() {
        DescuentoStockRequest request = new DescuentoStockRequest(List.of(
                new DescuentoStockRequest.Item(idA, 3),
                new DescuentoStockRequest.Item("000000000000000000000000", 1)));

        assertThatThrownBy(() -> productoService.descontarStock(request))
                .isInstanceOf(ProductoNoEncontradoException.class);

        assertThat(stock(idA)).isEqualTo(10);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void stockInsuficienteListaTodosLosFaltantesConElStockQueHabia() {
        DescuentoStockRequest request = new DescuentoStockRequest(List.of(
                new DescuentoStockRequest.Item(idA, 3),
                new DescuentoStockRequest.Item(idBajo, 5),
                new DescuentoStockRequest.Item(idC, 4)));

        assertThatThrownBy(() -> productoService.descontarStock(request))
                .isInstanceOf(StockInsuficienteException.class)
                .hasMessage("No hay stock suficiente para 2 productos")
                .extracting(e -> ((StockInsuficienteException) e).getItems())
                .isEqualTo(List.of(
                        new StockInsuficienteException.Faltante(idBajo, 5, 2),
                        new StockInsuficienteException.Faltante(idC, 4, 3)));

        assertThat(stock(idA)).isEqualTo(10);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void mismaReferenciaConLosMismosItemsRespondeLoMismoSinDescontarOtraVez() {
        DescuentoStockResponse primera = productoService.descontarStock(new DescuentoStockRequest("chk_1", List.of(
                new DescuentoStockRequest.Item(idA, 3),
                new DescuentoStockRequest.Item(idB, 2))));

        // El reintento trae los mismos ítems en otro orden: sigue siendo la misma compra.
        DescuentoStockResponse reintento = productoService.descontarStock(new DescuentoStockRequest("chk_1", List.of(
                new DescuentoStockRequest.Item(idB, 2),
                new DescuentoStockRequest.Item(idA, 3))));

        assertThat(reintento).isEqualTo(primera);
        assertThat(reintento.referencia()).isEqualTo("chk_1");
        assertThat(stock(idA)).isEqualTo(7);
        assertThat(stock(idB)).isEqualTo(3);
        // Dos eventos, uno por producto, y solo del primer descuento.
        verify(rabbitTemplate, times(2)).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void mismaReferenciaConOtrosItemsRespondeReferenciaEnUsoYNoDescuenta() {
        productoService.descontarStock(new DescuentoStockRequest("chk_1",
                List.of(new DescuentoStockRequest.Item(idA, 3))));

        assertThatThrownBy(() -> productoService.descontarStock(new DescuentoStockRequest("chk_1",
                List.of(new DescuentoStockRequest.Item(idA, 4)))))
                .isInstanceOf(ReferenciaEnUsoException.class)
                .hasMessageContaining("otros productos o cantidades");

        assertThat(stock(idA)).isEqualTo(7);
    }

    @Test
    void unDescuentoFallidoNoRegistraLaReferencia() {
        assertThatThrownBy(() -> productoService.descontarStock(new DescuentoStockRequest("chk_1",
                List.of(new DescuentoStockRequest.Item(idBajo, 5)))))
                .isInstanceOf(StockInsuficienteException.class);

        DescuentoStockResponse respuesta = productoService.descontarStock(new DescuentoStockRequest("chk_1",
                List.of(new DescuentoStockRequest.Item(idBajo, 2))));

        assertThat(respuesta.items().getFirst().stockRestante()).isZero();
    }

    @Test
    void devolverStockRestauraElStockYCierraLaReferencia() {
        productoService.descontarStock(new DescuentoStockRequest("chk_1", List.of(
                new DescuentoStockRequest.Item(idA, 3),
                new DescuentoStockRequest.Item(idB, 2))));
        clearInvocations(rabbitTemplate);

        productoService.devolverStock("chk_1");

        assertThat(stock(idA)).isEqualTo(10);
        assertThat(stock(idB)).isEqualTo(5);
        verify(rabbitTemplate, times(2)).convertAndSend(anyString(), eq("producto.actualizado"), any(Object.class));

        assertThatThrownBy(() -> productoService.descontarStock(new DescuentoStockRequest("chk_1", List.of(
                new DescuentoStockRequest.Item(idA, 3),
                new DescuentoStockRequest.Item(idB, 2)))))
                .isInstanceOf(ReferenciaEnUsoException.class)
                .hasMessageContaining("ya se devolvió");
        assertThat(stock(idA)).isEqualTo(10);
    }

    @Test
    void devolverDosVecesDevuelveUnaSola() {
        productoService.descontarStock(new DescuentoStockRequest("chk_1",
                List.of(new DescuentoStockRequest.Item(idA, 3))));

        productoService.devolverStock("chk_1");
        clearInvocations(rabbitTemplate);
        productoService.devolverStock("chk_1");

        assertThat(stock(idA)).isEqualTo(10);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void devolverUnaReferenciaQueNoExisteNoHaceNada() {
        productoService.devolverStock("no-existe");

        assertThat(stock(idA)).isEqualTo(10);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void devolverStockFuncionaAunqueElProductoSeHayaDesactivado() {
        productoService.descontarStock(new DescuentoStockRequest("chk_1",
                List.of(new DescuentoStockRequest.Item(idA, 3))));
        productoService.desactivar(idA);

        productoService.devolverStock("chk_1");

        assertThat(stock(idA)).isEqualTo(10);
        assertThat(productoRepository.findById(idA).orElseThrow().isActivo()).isFalse();
    }

    private int stock(String id) {
        return productoRepository.findById(id).orElseThrow().getStock();
    }
}