package pe.edu.gesedu.apoyo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;

/** Operaciones {@code @Auditable} de prueba para ejercitar el aspecto. */
@Service
public class ServiciosPrueba {

    @Transactional
    @Auditable(accion = "PRUEBA_CREAR", modulo = "PRUEBAS", tipoEntidad = "Cosa", idEntidad = "#result")
    public String crear(String nombre) {
        ContextoAuditoria.registrarCambio("nombre", null, nombre);
        ContextoAuditoria.registrarCambio("cantidad", null, 2);
        return "cosa-" + nombre;
    }

    @Transactional
    @Auditable(accion = "PRUEBA_CONDICIONAL", modulo = "PRUEBAS", tipoEntidad = "Cosa", idEntidad = "#id",
            condicion = "#audita")
    public void condicional(String id, boolean audita) {
    }

    @Transactional
    @Auditable(accion = "PRUEBA_FALLA", modulo = "PRUEBAS", tipoEntidad = "Cosa")
    public void falla() {
        throw new IllegalStateException("falla a propósito");
    }

    /** Sin {@code @Transactional}: el registro de auditoría exige la transacción de la operación. */
    @Auditable(accion = "PRUEBA_SIN_TRANSACCION", modulo = "PRUEBAS", tipoEntidad = "Cosa")
    public void sinTransaccion() {
    }

    /** La operación auditada termina bien, pero la transacción externa se revierte: el registro también. */
    @Service
    public static class Revierte {

        private final ServiciosPrueba servicios;

        Revierte(ServiciosPrueba servicios) {
            this.servicios = servicios;
        }

        @Transactional
        public void auditarYRevertir(String nombre) {
            servicios.crear(nombre);
            throw new IllegalStateException("revierte todo");
        }
    }
}
