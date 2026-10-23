package pe.edu.gesedu.compartido.auditoria;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Acumula los cambios campo a campo de la operación {@link Auditable} en curso. El código de negocio llama a
 * {@link #registrarCambio} y el aspecto de auditoría los guarda junto con el registro.
 * Fuera de una operación auditada, {@code registrarCambio} no hace nada.
 */
public final class ContextoAuditoria {

    /** Cambio de un campo: valores ya convertidos a texto. */
    public record Cambio(String campo, String valorAnterior, String valorNuevo) {
    }

    // Una pila por hilo: una operación auditada puede invocar a otra
    private static final ThreadLocal<Deque<List<Cambio>>> PILA = ThreadLocal.withInitial(ArrayDeque::new);

    private ContextoAuditoria() {
    }

    public static void registrarCambio(String campo, Object valorAnterior, Object valorNuevo) {
        Deque<List<Cambio>> pila = PILA.get();
        if (!pila.isEmpty()) {
            pila.peek().add(new Cambio(campo, texto(valorAnterior), texto(valorNuevo)));
        }
    }

    /** Uso exclusivo del aspecto de auditoría: abre el contexto de una operación. */
    public static void abrir() {
        PILA.get().push(new ArrayList<>());
    }

    /** Uso exclusivo del aspecto de auditoría: cierra el contexto y devuelve los cambios acumulados. */
    public static List<Cambio> cerrar() {
        Deque<List<Cambio>> pila = PILA.get();
        List<Cambio> cambios = pila.pop();
        if (pila.isEmpty()) {
            PILA.remove();
        }
        return cambios;
    }

    private static String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }
}
