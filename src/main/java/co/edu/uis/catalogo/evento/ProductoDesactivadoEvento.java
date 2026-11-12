package co.edu.uis.catalogo.evento;

/**
 * Cuerpo del evento {@code producto.desactivado} (contrato v2.4, sección 5). Lleva el id y la fecha:
 * quien lo recibe ya tiene el producto indexado, y lo único que necesita saber es cuál sacar y si el
 * aviso es más nuevo que lo último que aplicó.
 *
 * <p>Es un record aparte y no el mismo {@link ProductoEvento} con los campos vacíos, para que el
 * mensaje que sale sea exactamente el que promete el contrato, sin campos en null.
 */
public record ProductoDesactivadoEvento(String id, String fecha) {

}
