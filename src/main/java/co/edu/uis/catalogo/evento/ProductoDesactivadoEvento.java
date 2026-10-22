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
