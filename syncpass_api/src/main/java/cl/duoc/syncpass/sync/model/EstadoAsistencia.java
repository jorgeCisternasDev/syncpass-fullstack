package cl.duoc.syncpass.sync.model;

/**
 * Estado de una fila de {@code asistencias} (contrato v2, sección 2.2).
 * {@code PENDIENTE} no es un estado de fila: es un estado de pantalla de la app archivada.
 */
public enum EstadoAsistencia {
    INSCRITO,
    PRESENTE;

    /** {@code validado} ya no es dato de entrada: se deriva del estado. */
    public boolean validado() {
        return this == PRESENTE;
    }
}
