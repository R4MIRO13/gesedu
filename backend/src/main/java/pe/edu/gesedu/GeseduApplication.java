package pe.edu.gesedu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * GESEDU: sistema de gestión integral de un colegio de secundaria.
 * Monolito modular: un paquete por módulo (seguridad, matricula, pagos, asistencia, academico,
 * evaluacion, horarios y auditoria), más compartido y transversal (arquitectura v1.0, § 9.1).
 */
@SpringBootApplication
public class GeseduApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeseduApplication.class, args);
    }
}
