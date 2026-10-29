package pe.edu.gesedu.apoyo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pe.edu.gesedu.seguridad.aplicacion.CacheCuentas;

/**
 * Base de las pruebas de integración: PostgreSQL 18 real (Testcontainers) con los roles reales
 * ({@code gesedu_migrador} para Flyway y {@code gesedu_app} para la aplicación), creados por el mismo script
 * que usa docker-compose. El contenedor es único para toda la ejecución.
 */
// El límite de peticiones a /auth/* se sube: las pruebas inician sesión muchas veces desde la misma IP.
// LimiteAuthIT lo baja con su propio @SpringBootTest.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "gesedu.seguridad.limite-auth-por-minuto=100000")
public abstract class BaseIT {

    public static final String CONTRASENA = "Gesedu2027!";

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18")
            .withUsername("postgres")
            .withPassword("postgres")
            .withDatabaseName("postgres")
            .withCopyFileToContainer(MountableFile.forHostPath("../infra/postgres/init/01-roles-y-bd.sql"),
                    "/docker-entrypoint-initdb.d/01-roles-y-bd.sql")
            // El script de inicio reinicia el servidor: hay que esperar el segundo "ready"
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));

    /** Carpeta de los archivos subidos durante las pruebas (GestorArchivosDisco). */
    protected static final java.nio.file.Path ARCHIVOS = java.nio.file.Path.of(
            System.getProperty("java.io.tmpdir"), "gesedu-archivos-pruebas");

    static {
        POSTGRES.start();
    }

    /**
     * Base de datos a la que apunta la aplicación de las pruebas. Una prueba que necesite la suya (la de la semilla
     * de demo) la fija en su {@code @BeforeAll}, antes de que se cree su contexto, y la restablece en {@code @AfterAll}.
     * La URL se lee al crear el contexto, no al registrar la propiedad.
     */
    protected static volatile String baseDeDatos = "gesedu";

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> "jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(5432) + "/" + baseDeDatos);
        registro.add("spring.datasource.username", () -> "gesedu_app");
        registro.add("spring.datasource.password", () -> "gesedu_app");
        registro.add("spring.flyway.user", () -> "gesedu_migrador");
        registro.add("spring.flyway.password", () -> "gesedu_migrador");
        registro.add("gesedu.seguridad.jwt-secreto", () -> "clave-de-pruebas-de-al-menos-32-caracteres-0123456789");
        registro.add("gesedu.archivos.secreto-url", () -> "secreto-de-pruebas-para-las-url-de-archivos-0123456789");
        registro.add("gesedu.archivos.directorio", () -> ARCHIVOS.toString());
        registro.add("gesedu.notificaciones.reintento-ms", () -> "20");
    }

    /** Respuesta HTTP ya leída: estado, tipo de contenido, cuerpo JSON y cabeceras. */
    public record Respuesta(int estado, String tipoContenido, JsonNode cuerpo, java.net.http.HttpHeaders cabeceras) {

        /** Valor de la cookie {@code nombre} que la respuesta manda con Set-Cookie, o {@code null}. */
        public String valorCookie(String nombre) {
            return cabeceras.allValues("Set-Cookie").stream()
                    .filter(c -> c.startsWith(nombre + "="))
                    .map(c -> c.substring(nombre.length() + 1, c.indexOf(';') < 0 ? c.length() : c.indexOf(';')))
                    .findFirst().orElse(null);
        }

        public String cookieCompleta(String nombre) {
            return cabeceras.allValues("Set-Cookie").stream().filter(c -> c.startsWith(nombre + "="))
                    .findFirst().orElse(null);
        }
    }

    @Value("${local.server.port}")
    private int puerto;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected CacheCuentas cuentas;

    @Autowired
    protected PasswordEncoder codificador;

    @Autowired
    protected JsonMapper json;

    private final HttpClient cliente = HttpClient.newHttpClient();

    protected Respuesta post(String ruta, String cuerpo, String token) throws IOException, InterruptedException {
        return enviar(base(ruta).POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .header("Content-Type", "application/json"), token);
    }

    /** POST que lleva una cookie (por ejemplo, el refresh token). */
    protected Respuesta postConCookie(String ruta, String token, String cookie) throws IOException, InterruptedException {
        HttpRequest.Builder peticion = base(ruta).POST(HttpRequest.BodyPublishers.noBody());
        if (cookie != null) {
            peticion.header("Cookie", cookie);
        }
        return enviar(peticion, token);
    }

    /** Las pruebas que modifican cuentas directamente en la base deben invalidar la caché de cuentas (RNF-07). */
    protected void invalidarCuentas() {
        cuentas.invalidarTodas();
    }

    /** Petición con cualquier método; {@code ifMatch} se manda como cabecera If-Match (ediciones con versión). */
    protected Respuesta enviar(String metodo, String ruta, String cuerpo, String token, String ifMatch)
            throws IOException, InterruptedException {
        HttpRequest.Builder peticion = base(ruta).method(metodo,
                cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo));
        if (cuerpo != null) {
            peticion.header("Content-Type", "application/json");
        }
        if (ifMatch != null) {
            peticion.header("If-Match", ifMatch);
        }
        return enviar(peticion, token);
    }

    /** Respuesta binaria (archivos descargables): estado, cabeceras y bytes. */
    public record Descarga(int estado, java.net.http.HttpHeaders cabeceras, byte[] contenido) {
    }

    protected Descarga descargar(String ruta, String token) throws IOException, InterruptedException {
        HttpRequest.Builder peticion = base(ruta).GET();
        if (token != null) {
            peticion.header("Authorization", "Bearer " + token);
        }
        HttpResponse<byte[]> respuesta = cliente.send(peticion.build(), HttpResponse.BodyHandlers.ofByteArray());
        return new Descarga(respuesta.statusCode(), respuesta.headers(), respuesta.body());
    }

    /** El ETag de la respuesta (por ejemplo, {@code "3"}) o {@code null}. */
    protected static String etag(Respuesta respuesta) {
        return respuesta.cabeceras().firstValue("ETag").orElse(null);
    }

    protected Respuesta get(String ruta, String token) throws IOException, InterruptedException {
        return enviar(base(ruta).GET(), token);
    }

    protected Respuesta iniciarSesion(String correo, String contrasena) throws IOException, InterruptedException {
        return post("/api/v1/auth/inicio-sesion",
                json.writeValueAsString(java.util.Map.of("correo", correo, "contrasena", contrasena)), null);
    }

    /** Inicia sesión con {@link #CONTRASENA} y devuelve el token de acceso. */
    protected String tokenDe(String correo) throws IOException, InterruptedException {
        return iniciarSesion(correo, CONTRASENA).cuerpo().get("tokenAcceso").asString();
    }

    private HttpRequest.Builder base(String ruta) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta));
    }

    private Respuesta enviar(HttpRequest.Builder peticion, String token) throws IOException, InterruptedException {
        if (token != null) {
            peticion.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> respuesta = cliente.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
        String cuerpo = respuesta.body();
        String tipo = respuesta.headers().firstValue("Content-Type").orElse("");
        // Solo se interpretan cuerpos JSON (la Swagger UI responde HTML)
        return new Respuesta(respuesta.statusCode(), tipo,
                cuerpo == null || cuerpo.isBlank() || !tipo.contains("json") ? null : json.readTree(cuerpo),
                respuesta.headers());
    }

    /** Crea una cuenta con la contraseña {@link #CONTRASENA} y devuelve su correo (único por llamada). */
    protected String crearUsuario(String rolCodigo, boolean activo) {
        String id = UUID.randomUUID().toString();
        String correo = "prueba-" + id.substring(0, 8) + "@test.gesedu.pe";
        jdbc.update("""
                INSERT INTO seguridad.usuario (id, nombres, apellidos, documento_identidad, correo,
                                               hash_contrasena, activo, rol_acceso_id)
                SELECT ?, 'Prueba', 'Usuario', ?, ?, ?, ?, r.id FROM seguridad.rol_acceso r WHERE r.codigo = ?
                """, id, id.substring(0, 20), correo, codificador.encode(CONTRASENA), activo, rolCodigo);
        return correo;
    }

    protected String idDe(String correo) {
        return jdbc.queryForObject("SELECT id FROM seguridad.usuario WHERE correo = ?", String.class, correo);
    }

    /** Conexión JDBC directa como el rol indicado (para probar permisos de la base de datos). */
    protected static Connection conectar(String usuario, String contrasena) throws SQLException {
        return DriverManager.getConnection("jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(5432) + "/gesedu", usuario, contrasena);
    }
}
