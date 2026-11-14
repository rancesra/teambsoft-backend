package co.edu.uis.catalogo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import co.edu.uis.catalogo.dto.DescuentoStockRequest;
import co.edu.uis.catalogo.model.Producto;
import co.edu.uis.catalogo.repository.ProductoRepository;
import co.edu.uis.catalogo.service.ProductoService;

/**
 * Comprueba la carga de los productos de prueba (contrato v2.4, sección 10.5): los ids fijos, que el
 * desactivado nazca desactivado, que publique un evento por producto creado y que un segundo arranque
 * no pise nada.
 *
 * <p>Activa los perfiles {@code test} (base {@code catalogo-test}) e {@code integracion} (el que crea
 * este componente). RabbitMQ se reemplaza por un mock para contar los eventos sin necesitarlo
 * encendido.
 */
@SpringBootTest
@ActiveProfiles({ "test", "integracion" })
class CargaProductosPruebaTest {

	private static final List<String> IDS = List.of(
			"674000000000000000000001", "674000000000000000000002", "674000000000000000000003",
			"674000000000000000000004", "674000000000000000000005", "674000000000000000000006");

	@Autowired
	private CargaProductosPrueba carga;

	@Autowired
	private ProductoRepository productoRepository;

	@Autowired
	private ProductoService productoService;

	@Autowired
	private MongoTemplate mongoTemplate;

	@MockitoBean
	private RabbitTemplate rabbitTemplate;

	@BeforeEach
	void baseVacia() {
		assertThat(mongoTemplate.getDb().getName()).isEqualTo("catalogo-test");
		productoRepository.deleteAll();
		clearInvocations(rabbitTemplate);
	}

	@Test
	void creaLosSeisConSusIdsFijosYAnunciaCadaUno() {
		carga.run(null);

		assertThat(productoRepository.findAllById(IDS)).hasSize(6);
		assertThat(productoRepository.findById("674000000000000000000003").orElseThrow().getStock()).isEqualTo(3);
		assertThat(productoRepository.findById("674000000000000000000004").orElseThrow().getStock()).isZero();
		assertThat(productoRepository.findById("674000000000000000000006").orElseThrow().isActivo()).isFalse();
		verify(rabbitTemplate, times(6)).convertAndSend(anyString(), eq("producto.creado"), any(Object.class));
	}

	@Test
	void unSegundoArranqueNoPisaLoQueCambioNiVuelveAAnunciar() {
		carga.run(null);
		productoService.descontarStock(new DescuentoStockRequest(
				List.of(new DescuentoStockRequest.Item("674000000000000000000001", 4))));
		clearInvocations(rabbitTemplate);

		carga.run(null);

		assertThat(productoRepository.count()).isEqualTo(6);
		assertThat(productoRepository.findById("674000000000000000000001").orElseThrow().getStock()).isEqualTo(96);
		verifyNoInteractions(rabbitTemplate);
	}

	@Test
	void soloCreaLosQueFaltan() {
		carga.run(null);
		productoRepository.deleteById("674000000000000000000002");
		clearInvocations(rabbitTemplate);

		carga.run(null);

		assertThat(productoRepository.count()).isEqualTo(6);
		verify(rabbitTemplate, times(1)).convertAndSend(anyString(), eq("producto.creado"), any(Object.class));
	}

	@Test
	void losIdsSonObjectIdValidos() {
		for (Producto producto : CargaProductosPrueba.productos()) {
			assertThat(producto.getId()).matches("[0-9a-f]{24}");
		}
	}

}
