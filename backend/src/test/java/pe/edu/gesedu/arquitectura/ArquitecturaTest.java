package pe.edu.gesedu.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import java.util.List;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Reglas de arquitectura (arquitectura v1.0, § 9.1 y docs/PLAN.md § 2.1):
 * capas dentro de cada módulo y dependencias entre módulos solo a través de la capa {@code aplicacion}.
 */
@AnalyzeClasses(packages = "pe.edu.gesedu", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    private static final List<String> MODULOS = List.of(
            "seguridad", "matricula", "pagos", "asistencia", "academico", "evaluacion", "horarios", "auditoria");

    @ArchTest
    static final ArchRule dominio_no_depende_de_otras_capas = noClasses()
            .that().resideInAPackage("pe.edu.gesedu.*.dominio..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "pe.edu.gesedu.*.api..", "pe.edu.gesedu.*.aplicacion..", "pe.edu.gesedu.*.infraestructura..")
            .because("el dominio contiene entidades y reglas de negocio puras");

    @ArchTest
    static final ArchRule api_no_usa_infraestructura = noClasses()
            .that().resideInAPackage("pe.edu.gesedu.*.api..")
            .should().dependOnClassesThat().resideInAPackage("pe.edu.gesedu.*.infraestructura..")
            .because("los controladores solo hablan con los servicios de aplicación");

    @ArchTest
    static final ArchRule aplicacion_no_depende_de_api = noClasses()
            .that().resideInAPackage("pe.edu.gesedu.*.aplicacion..")
            .should().dependOnClassesThat().resideInAPackage("pe.edu.gesedu.*.api..");

    @ArchTest
    static final ArchRule infraestructura_no_depende_de_api = noClasses()
            .that().resideInAPackage("pe.edu.gesedu.*.infraestructura..")
            .should().dependOnClassesThat().resideInAPackage("pe.edu.gesedu.*.api..");

    @ArchTest
    static final ArchRule compartido_no_depende_de_los_modulos = noClasses()
            .that().resideInAPackage("pe.edu.gesedu.compartido..")
            .should().dependOnClassesThat().resideInAnyPackage(MODULOS.stream()
                    .map(m -> "pe.edu.gesedu." + m + "..").toArray(String[]::new));

    @ArchTest
    static final ArchRule los_modulos_no_tienen_ciclos = slices()
            .matching("pe.edu.gesedu.(*)..")
            .should().beFreeOfCycles();

    /**
     * Un módulo solo usa a otro a través de su capa {@code aplicacion}: nunca sus controladores,
     * entidades ni repositorios.
     */
    @ArchTest
    static void un_modulo_solo_usa_a_otro_mediante_su_capa_aplicacion(JavaClasses clases) {
        for (String origen : MODULOS) {
            for (String destino : MODULOS) {
                if (origen.equals(destino)) {
                    continue;
                }
                noClasses()
                        .that().resideInAPackage("pe.edu.gesedu." + origen + "..")
                        .should().dependOnClassesThat().resideInAnyPackage(
                                "pe.edu.gesedu." + destino + ".api..",
                                "pe.edu.gesedu." + destino + ".dominio..",
                                "pe.edu.gesedu." + destino + ".infraestructura..")
                        .because("el módulo " + origen + " solo puede usar la capa aplicacion del módulo " + destino)
                        .check(clases);
            }
        }
    }
}
