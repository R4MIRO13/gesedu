package pe.edu.gesedu.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.apoyo.BaseIT;

/** Caché de cuentas y permisos (RNF-07): sirve las peticiones sin ir a la base y se invalida al cambiar la cuenta. */
class CacheCuentasIT extends BaseIT {

    @Test
    void laCuentaSeSirveDeLaCacheHastaQueSeInvalida() throws Exception {
        String correo = crearUsuario("ADMINISTRADOR", true);
        String token = tokenDe(correo);
        assertThat(get("/api/v1/prueba/horario", token).estado()).isEqualTo(200);

        // Un cambio directo en la base no se ve mientras la entrada siga en caché...
        jdbc.update("UPDATE seguridad.usuario SET activo = FALSE WHERE correo = ?", correo);
        assertThat(get("/api/v1/prueba/horario", token).estado()).isEqualTo(200);

        // ...y se aplica en la siguiente petición en cuanto el servicio la invalida
        cuentas.invalidar(idDe(correo));
        Respuesta r = get("/api/v1/prueba/horario", token);
        assertThat(r.estado()).isEqualTo(401);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("CUENTA_INACTIVA");
    }

    @Test
    void unBloqueoPorIntentosFallidosInvalidaLaCacheSolo() throws Exception {
        String correo = crearUsuario("ADMINISTRADOR", true);
        String token = tokenDe(correo);
        assertThat(get("/api/v1/prueba/horario", token).estado()).isEqualTo(200);

        for (int i = 0; i < 5; i++) {
            iniciarSesion(correo, "incorrecta");
        }

        Respuesta r = get("/api/v1/prueba/horario", token);
        assertThat(r.estado()).isEqualTo(401);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("CUENTA_BLOQUEADA");
    }

    @Test
    void cambiarLosPermisosDeUnRolSeAplicaTrasInvalidarLaCache() throws Exception {
        String correo = crearUsuario("ALUMNO", true);
        String token = tokenDe(correo);
        assertThat(get("/api/v1/prueba/auditoria", token).estado()).isEqualTo(403);

        jdbc.update("INSERT INTO seguridad.rol_permiso (rol_acceso_id, permiso_id) "
                + "SELECT r.id, p.id FROM seguridad.rol_acceso r, seguridad.permiso p "
                + "WHERE r.codigo = 'ALUMNO' AND p.codigo = 'AUDITORIA_CONSULTAR'");
        try {
            // Sigue sin verse: la entrada del alumno está en caché
            assertThat(get("/api/v1/prueba/auditoria", token).estado()).isEqualTo(403);

            cuentas.invalidarTodas();
            assertThat(get("/api/v1/prueba/auditoria", token).estado()).isEqualTo(200);
        } finally {
            jdbc.update("DELETE FROM seguridad.rol_permiso WHERE rol_acceso_id = "
                    + "(SELECT id FROM seguridad.rol_acceso WHERE codigo = 'ALUMNO') AND permiso_id = "
                    + "(SELECT id FROM seguridad.permiso WHERE codigo = 'AUDITORIA_CONSULTAR')");
            cuentas.invalidarTodas();
        }
    }
}
