package pe.edu.gesedu.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import pe.edu.gesedu.apoyo.BaseIT;
import pe.edu.gesedu.apoyo.DatosHorario;
import pe.edu.gesedu.apoyo.ServiciosPrueba;

/** UC-25: detalle con cambios, catálogos de filtros y exportación a Excel y CSV (RF-49, RF-51, RNF-08). */
class AuditoriaConsultaExportacionIT extends BaseIT {

    @Autowired
    private ServiciosPrueba servicios;

    private String correoAdmin;
    private String admin;
    private String idAdmin;
    private int anio;

    @BeforeEach
    void preparar() throws Exception {
        correoAdmin = crearUsuario("ADMINISTRADOR", true);
        admin = tokenDe(correoAdmin);
        idAdmin = idDe(correoAdmin);
        anio = new DatosHorario(jdbc).crearEscenario().anio();
    }

    /** El administrador crea un bloque por HTTP: queda un registro HORARIOS/CREAR_BLOQUE_HORARIO a su nombre. */
    private String crearBloqueComoAdmin(String inicio, String fin) throws Exception {
        Respuesta r = post("/api/v1/anios-escolares/" + anio + "/bloques",
                json.writeValueAsString(Map.of("diaSemana", "VIERNES", "horaInicio", inicio, "horaFin", fin)), admin);
        assertThat(r.estado()).isEqualTo(201);
        return r.cuerpo().get("id").asString();
    }

    private String idDelRegistro(String entidadId) {
        return jdbc.queryForObject("SELECT id FROM auditoria.registro_auditoria WHERE id_entidad_afectada = ?",
                String.class, entidadId);
    }

    // --- Detalle con los cambios ---

    @Test
    void elDetalleTraeElRegistroYSusCambiosDeCampo() throws Exception {
        String nombre = "det-" + System.nanoTime();
        servicios.crear(nombre);

        Respuesta r = get("/api/v1/auditoria/registros/" + idDelRegistro("cosa-" + nombre), admin);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("registro").get("accion").asString()).isEqualTo("PRUEBA_CREAR");
        assertThat(r.cuerpo().get("registro").get("usuario").isNull()).as("actor «Sistema»").isTrue();
        List<JsonNode> cambios = r.cuerpo().get("cambios").valueStream().toList();
        assertThat(cambios.stream().map(c -> c.get("campo").asString()).toList()).containsExactly("cantidad", "nombre");
        JsonNode cambioNombre = cambios.get(1);
        assertThat(cambioNombre.get("valorAnterior").isNull()).isTrue();
        assertThat(cambioNombre.get("valorNuevo").asString()).isEqualTo(nombre);
    }

    @Test
    void elDetalleDeUnIdInexistenteResponde404YSoloLoVeQuienAudita() throws Exception {
        assertThat(get("/api/v1/auditoria/registros/no-existe", admin).estado()).isEqualTo(404);
        for (String rol : List.of("ALUMNO", "APODERADO", "DOCENTE")) {
            assertThat(get("/api/v1/auditoria/registros/no-existe", tokenDe(crearUsuario(rol, true))).estado())
                    .as(rol).isEqualTo(403);
        }
    }

    // --- Catálogos de los filtros ---

    @Test
    void losCatalogosListanModulosAccionesYUsuariosConRegistros() throws Exception {
        crearBloqueComoAdmin("07:00", "07:45");

        Respuesta r = get("/api/v1/auditoria/catalogos", admin);

        assertThat(r.estado()).isEqualTo(200);
        JsonNode horarios = r.cuerpo().get("modulos").valueStream()
                .filter(m -> m.get("modulo").asString().equals("HORARIOS")).findFirst().orElseThrow();
        assertThat(horarios.get("acciones").valueStream().map(JsonNode::asString).toList())
                .contains("CREAR_BLOQUE_HORARIO");
        assertThat(r.cuerpo().get("usuarios").valueStream().map(u -> u.get("id").asString()).toList())
                .contains(idAdmin);
        // Ordenados por nombre de módulo
        List<String> modulos = r.cuerpo().get("modulos").valueStream().map(m -> m.get("modulo").asString()).toList();
        assertThat(modulos).isSorted();
        assertThat(get("/api/v1/auditoria/catalogos", tokenDe(crearUsuario("ALUMNO", true))).estado()).isEqualTo(403);
    }

    // --- Exportación a Excel ---

    @Test
    void exportarAExcelDevuelveLosMismosRegistrosQueLaConsulta() throws Exception {
        crearBloqueComoAdmin("07:00", "07:45");
        crearBloqueComoAdmin("07:45", "08:30");
        String filtro = "usuarioId=" + idAdmin + "&modulo=HORARIOS";

        Respuesta lista = get("/api/v1/auditoria/registros?" + filtro, admin);
        Descarga excel = descargar("/api/v1/auditoria/registros/exportacion?" + filtro + "&formato=xlsx", admin);

        assertThat(excel.estado()).isEqualTo(200);
        assertThat(excel.cabeceras().firstValue("Content-Type").orElse(""))
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(excel.cabeceras().firstValue("Content-Disposition").orElse(""))
                .matches("attachment; filename=\"?auditoria-\\d{8}-\\d{4}\\.xlsx\"?");
        Sheet hoja = new XSSFWorkbook(new ByteArrayInputStream(excel.contenido())).getSheetAt(0);
        assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Fecha y hora");
        assertThat(hoja.getLastRowNum()).isEqualTo(lista.cuerpo().get("totalElementos").asInt()).isEqualTo(2);
        assertThat(hoja.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Prueba Usuario");
        assertThat(hoja.getRow(1).getCell(2).getStringCellValue()).isEqualTo("CREAR_BLOQUE_HORARIO");
        assertThat(hoja.getRow(1).getCell(3).getStringCellValue()).isEqualTo("HORARIOS");
        assertThat(hoja.getRow(1).getCell(7).getStringCellValue()).contains("horaInicio: ∅ → 07:45");
    }

    @Test
    void exportarACsvTraeBomUtf8EncabezadosYLasFilas() throws Exception {
        crearBloqueComoAdmin("07:00", "07:45");

        Descarga csv = descargar("/api/v1/auditoria/registros/exportacion?usuarioId=" + idAdmin
                + "&modulo=HORARIOS&formato=csv", admin);

        assertThat(csv.estado()).isEqualTo(200);
        assertThat(csv.cabeceras().firstValue("Content-Type").orElse("")).startsWith("text/csv");
        assertThat(csv.cabeceras().firstValue("Content-Disposition").orElse("")).contains(".csv");
        String texto = new String(csv.contenido(), StandardCharsets.UTF_8);
        assertThat(texto).startsWith("﻿Fecha y hora,Usuario,Acción,Módulo,Entidad,Id de la entidad,Descripción,Cambios\r\n");
        String[] lineas = texto.split("\r\n");
        assertThat(lineas).hasSize(2);
        assertThat(lineas[1]).contains("Prueba Usuario,CREAR_BLOQUE_HORARIO,HORARIOS,BloqueHorario");
    }

    @Test
    void laExportacionRespetaLosFiltrosDeFecha() throws Exception {
        crearBloqueComoAdmin("07:00", "07:45");

        Descarga fueraDeRango = descargar("/api/v1/auditoria/registros/exportacion?usuarioId=" + idAdmin
                + "&modulo=HORARIOS&desde=2000-01-01&hasta=2000-12-31&formato=csv", admin);

        assertThat(new String(fueraDeRango.contenido(), StandardCharsets.UTF_8).split("\r\n")).hasSize(1); // solo el encabezado
        assertThat(get("/api/v1/auditoria/registros/exportacion?desde=2027-02-01&hasta=2027-01-01", admin).estado())
                .isEqualTo(400);
    }

    @Test
    void laExportacionQuedaAuditadaConElFormatoYLosFiltros() throws Exception {
        crearBloqueComoAdmin("07:00", "07:45");
        descargar("/api/v1/auditoria/registros/exportacion?modulo=HORARIOS&usuarioId=" + idAdmin + "&formato=csv", admin);

        Map<String, Object> registro = jdbc.queryForMap("SELECT * FROM auditoria.registro_auditoria WHERE accion = "
                + "'EXPORTAR_AUDITORIA' AND usuario_id = ?", idAdmin);
        assertThat(registro.get("modulo")).isEqualTo("AUDITORIA");
        List<String> campos = jdbc.queryForList("SELECT campo FROM auditoria.cambio_auditoria WHERE "
                + "registro_auditoria_id = ? ORDER BY campo", String.class, registro.get("id"));
        assertThat(campos).contains("formato", "registros", "filtro.modulo", "filtro.usuarioId");
        // La propia exportación no aparece dentro del archivo que acaba de generar
        assertThat(registro.get("fecha_hora")).isNotNull();
    }

    @Test
    void soloQuienTieneAuditoriaExportarPuedeExportarYElFormatoDebeSerValido() throws Exception {
        for (String rol : List.of("ALUMNO", "APODERADO", "DOCENTE")) {
            assertThat(descargar("/api/v1/auditoria/registros/exportacion", tokenDe(crearUsuario(rol, true))).estado())
                    .as(rol).isEqualTo(403);
        }
        assertThat(descargar("/api/v1/auditoria/registros/exportacion", null).estado()).isEqualTo(401);
        assertThat(descargar("/api/v1/auditoria/registros/exportacion?formato=pdf", admin).estado()).isEqualTo(400);
    }
}
