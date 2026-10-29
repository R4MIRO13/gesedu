package pe.edu.gesedu.horarios.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import pe.edu.gesedu.compartido.dominio.EntidadBase;

/** Un bloque horario que el docente marcó como disponible. */
@Entity
@Table(name = "bloque_disponible", schema = "horarios")
public class BloqueDisponible extends EntidadBase {

    @Column(name = "disponibilidad_id", nullable = false, length = 36)
    private String disponibilidadId;

    @Column(name = "bloque_id", nullable = false, length = 36)
    private String bloqueId;

    protected BloqueDisponible() {
    }

    public String getDisponibilidadId() {
        return disponibilidadId;
    }

    public String getBloqueId() {
        return bloqueId;
    }
}
