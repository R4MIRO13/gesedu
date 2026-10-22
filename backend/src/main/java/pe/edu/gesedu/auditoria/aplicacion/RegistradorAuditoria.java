package pe.edu.gesedu.auditoria.aplicacion;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.gesedu.auditoria.dominio.CambioAuditoria;
import pe.edu.gesedu.auditoria.dominio.RegistroAuditoria;
import pe.edu.gesedu.auditoria.infraestructura.CambioAuditoriaRepository;
import pe.edu.gesedu.auditoria.infraestructura.RegistroAuditoriaRepository;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;
import pe.edu.gesedu.seguridad.aplicacion.ContextoUsuario;

/**
 * Escribe los registros de auditoría (RF-49). {@code MANDATORY}: solo puede ejecutarse dentro de la transacción
 * de la operación auditada, así el registro y la operación se confirman o se revierten juntos (DA-06).
 */
@Service
class RegistradorAuditoria {

    // Longitudes de las columnas de V9__auditoria.sql
    private static final int MAX_ACCION = 60;
    private static final int MAX_MODULO = 40;
    private static final int MAX_TIPO_ENTIDAD = 60;
    private static final int MAX_ID_ENTIDAD = 60;
    private static final int MAX_DESCRIPCION = 500;
    private static final int MAX_CAMPO = 80;

    private final RegistroAuditoriaRepository registros;
    private final CambioAuditoriaRepository cambios;
    private final ContextoUsuario contextoUsuario;
    private final Clock reloj;

    RegistradorAuditoria(RegistroAuditoriaRepository registros, CambioAuditoriaRepository cambios,
                         ContextoUsuario contextoUsuario, Clock reloj) {
        this.registros = registros;
        this.cambios = cambios;
        this.contextoUsuario = contextoUsuario;
        this.reloj = reloj;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void registrar(String accion, String modulo, String tipoEntidad, String idEntidad, String descripcion,
                   List<ContextoAuditoria.Cambio> cambiosCampo) {
        // Sin usuario autenticado el actor es «Sistema» (usuario_id NULL)
        String usuarioId = contextoUsuario.idUsuarioActual().orElse(null);
        RegistroAuditoria registro = registros.save(new RegistroAuditoria(reloj.instant(), usuarioId,
                recortar(accion, MAX_ACCION), recortar(modulo, MAX_MODULO), recortar(tipoEntidad, MAX_TIPO_ENTIDAD),
                recortar(idEntidad, MAX_ID_ENTIDAD), recortar(descripcion, MAX_DESCRIPCION)));
        for (ContextoAuditoria.Cambio cambio : cambiosCampo) {
            cambios.save(new CambioAuditoria(registro.getId(), recortar(cambio.campo(), MAX_CAMPO),
                    cambio.valorAnterior(), cambio.valorNuevo()));
        }
    }

    private static String recortar(String texto, int maximo) {
        if (texto == null || texto.length() <= maximo) {
            return texto;
        }
        return texto.substring(0, maximo);
    }
}
