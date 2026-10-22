package co.edu.uis.catalogo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
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
import org.springframework.test.context.ActiveProfiles;

import co.edu.uis.catalogo.dto.DescuentoStockRequest;
import co.edu.uis.catalogo.dto.DescuentoStockResponse;
import co.edu.uis.catalogo.error.StockInsuficienteException;
import co.edu.uis.catalogo.error.ValidacionFallidaException;
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
}