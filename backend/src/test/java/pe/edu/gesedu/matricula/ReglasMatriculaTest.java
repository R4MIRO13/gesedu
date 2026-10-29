package pe.edu.gesedu.matricula;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.compartido.dominio.Dinero;
import pe.edu.gesedu.matricula.dominio.EstadoAdmision;
import pe.edu.gesedu.matricula.dominio.EstadoDocumentacion;
import pe.edu.gesedu.matricula.dominio.MotivoRechazo;
import pe.edu.gesedu.matricula.dominio.ReglasMatricula;
import pe.edu.gesedu.matricula.dominio.ReglasMatricula.Regla;
import pe.edu.gesedu.matricula.dominio.ReglasMatricula.Validacion;
import pe.edu.gesedu.matricula.dominio.TipoEstudiante;

/** Validaciones de UC-02 por tipo de estudiante (C-37). */
class ReglasMatriculaTest {

    private static final Dinero SIN_DEUDA = Dinero.CERO;

    private static Validacion de(java.util.List<Validacion> validaciones, Regla regla) {
        return validaciones.stream().filter(v -> v.regla() == regla).findFirst().orElseThrow();
    }

    @Test
    void unRecurrenteAlDiaSeApruebaEnTodo() {
        var v = ReglasMatricula.evaluar(TipoEstudiante.RECURRENTE, EstadoDocumentacion.COMPLETA,
                EstadoAdmision.NO_APLICA, false, SIN_DEUDA);

        assertThat(v).hasSize(4);
        assertThat(ReglasMatricula.todasAprobadas(v)).isTrue();
    }

    @Test
    void unNuevoIngresoNecesitaEstarAdmitido() {
        var noAdmitido = ReglasMatricula.evaluar(TipoEstudiante.NUEVO_INGRESO, EstadoDocumentacion.COMPLETA,
                EstadoAdmision.NO_ADMITIDO, false, SIN_DEUDA);
        var pendiente = ReglasMatricula.evaluar(TipoEstudiante.NUEVO_INGRESO, EstadoDocumentacion.COMPLETA,
                EstadoAdmision.NO_APLICA, false, SIN_DEUDA);
        var admitido = ReglasMatricula.evaluar(TipoEstudiante.NUEVO_INGRESO, EstadoDocumentacion.COMPLETA,
                EstadoAdmision.ADMITIDO, false, SIN_DEUDA);

        assertThat(de(noAdmitido, Regla.ADMISION).motivo()).isEqualTo(MotivoRechazo.NO_ADMITIDO);
        assertThat(de(pendiente, Regla.ADMISION).aprobada()).isFalse();
        assertThat(de(pendiente, Regla.ADMISION).motivo()).as("falta registrar: no se rechaza").isNull();
        assertThat(ReglasMatricula.todasAprobadas(admitido)).isTrue();
    }

    @Test
    void laMatriculaActivaEsDuplicidadParaAmbosTipos() {
        for (TipoEstudiante tipo : TipoEstudiante.values()) {
            var v = ReglasMatricula.evaluar(tipo, EstadoDocumentacion.COMPLETA, EstadoAdmision.ADMITIDO, true, SIN_DEUDA);
            assertThat(de(v, Regla.MATRICULA_ACTIVA).motivo()).isEqualTo(MotivoRechazo.DUPLICIDAD);
        }
    }

    @Test
    void laDeudaSoloBloqueaAlRecurrenteYMencionaElImporte() {
        Dinero deuda = Dinero.de(new BigDecimal("700"));

        var recurrente = ReglasMatricula.evaluar(TipoEstudiante.RECURRENTE, EstadoDocumentacion.COMPLETA,
                EstadoAdmision.NO_APLICA, false, deuda);
        var nuevo = ReglasMatricula.evaluar(TipoEstudiante.NUEVO_INGRESO, EstadoDocumentacion.COMPLETA,
                EstadoAdmision.ADMITIDO, false, deuda);

        assertThat(de(recurrente, Regla.DEUDAS).aprobada()).isFalse();
        assertThat(de(recurrente, Regla.DEUDAS).mensaje()).contains("S/ 700.00");
        assertThat(de(recurrente, Regla.DEUDAS).motivo()).isEqualTo(MotivoRechazo.DEUDAS);
        assertThat(ReglasMatricula.todasAprobadas(nuevo)).isTrue();
    }

    @Test
    void laDocumentacionIncompletaBloqueaSinMotivoDeRechazo() {
        var v = ReglasMatricula.evaluar(TipoEstudiante.RECURRENTE, EstadoDocumentacion.INCOMPLETA,
                EstadoAdmision.NO_APLICA, false, SIN_DEUDA);

        assertThat(de(v, Regla.DOCUMENTACION).aprobada()).isFalse();
        assertThat(de(v, Regla.DOCUMENTACION).motivo()).isNull();
    }

    @Test
    void losEstadosVisiblesCoincidenConElWireframe() {
        assertThat(MotivoRechazo.NO_ADMITIDO.estadoVisible()).isEqualTo("Rechazada por No Admitido");
        assertThat(MotivoRechazo.SIN_VACANTE.estadoVisible()).isEqualTo("Rechazada sin Vacante");
        assertThat(MotivoRechazo.VOLUNTAD_APODERADO.estadoVisible()).isEqualTo("Rechazada por Voluntad del Apoderado");
    }
}
