package pe.edu.gesedu.arquitectura;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reglas de arquitectura (arquitectura v1.0, § 9.1 y docs/PLAN.md § 2.1 y Fase 0, tarea 8):
 * capas dentro de cada módulo, dependencias entre módulos solo a través de la capa {@code aplicacion} y las
 * convenciones de código de CLAUDE.md que se pueden verificar automáticamente.
 */
@AnalyzeClasses(packages = "pe.edu.gesedu", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    private static final List<String> MODULOS = List.of(
            "seguridad", "matricula", "pagos", "asistencia", "academico", "evaluacion", "horarios", "auditoria");

    // --- Capas dentro de un módulo: api -> aplicacion -> {dominio, infraestructura}; infraestructura -> dominio ---

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
    static final ArchRule infraestructura_solo_depende_de_dominio_dentro_del_modulo = noClasses()
            .that().resideInAPackage("pe.edu.gesedu.*.infraestructura..")
            .should().dependOnClassesThat().resideInAPackage("pe.edu.gesedu.*.aplicacion..")
            .because("infraestructura -> dominio; la aplicacion es quien usa a la infraestructura");

    // --- Entre módulos ---

    @ArchTest
    static final ArchRule compartido_no_depende_de_los_modulos = noClasses()
            .that().resideInAPackage("pe.edu.gesedu.compartido..")
            .should().dependOnClassesThat().resideInAnyPackage(MODULOS.stream()
                    .map(m -> "pe.edu.gesedu." + m + "..").toArray(String[]::new));

    @ArchTest
    static final ArchRule los_modulos_no_tienen_ciclos = slices()
            .matching("pe.edu.gesedu.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule la_auditoria_no_la_usa_ningun_otro_modulo = noClasses()
            .that().resideOutsideOfPackages("pe.edu.gesedu.auditoria..")
            .should().dependOnClassesThat().resideInAPackage("pe.edu.gesedu.auditoria..")
            .because("@Auditable vive en compartido; el aspecto es de auditoria y nadie más depende de él");

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

    // --- Convenciones de código (CLAUDE.md) ---

    @ArchTest
    static final ArchRule solo_la_capa_aplicacion_abre_transacciones = methods()
            .that().areDeclaredInClassesThat().resideOutsideOfPackage("..aplicacion..")
            .should().notBeAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
            .andShould().notBeAnnotatedWith(jakarta.transaction.Transactional.class)
            .because("@Transactional solo va en los servicios de la capa aplicacion");

    @ArchTest
    static final ArchRule ninguna_clase_fuera_de_aplicacion_es_transaccional = noClasses()
            .that().resideOutsideOfPackage("..aplicacion..")
            .should().beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
            .orShould().beAnnotatedWith(jakarta.transaction.Transactional.class);

    @ArchTest
    static final ArchRule los_controladores_no_exponen_entidades = methods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .should(noInvolucrarEntidades())
            .because("los controladores devuelven y reciben DTO, nunca entidades JPA");

    @ArchTest
    static final ArchRule las_entidades_viven_en_dominio = classes()
            .that().areAnnotatedWith(Entity.class).or().areAnnotatedWith(MappedSuperclass.class)
            .should().resideInAPackage("..dominio..");

    @ArchTest
    static final ArchRule los_repositorios_viven_en_infraestructura = classes()
            .that().areAssignableTo(Repository.class).and().areInterfaces()
            .should().resideInAPackage("..infraestructura..");

    @ArchTest
    static final ArchRule los_controladores_viven_en_api = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAnyPackage("..api..", "pe.edu.gesedu.transversal..");

    @ArchTest
    static final ArchRule el_tiempo_siempre_sale_del_reloj_inyectado = noClasses()
            .should().callMethod(Instant.class, "now")
            .orShould().callMethod(LocalDate.class, "now")
            .orShould().callMethod(LocalDateTime.class, "now")
            .orShould().callMethod(LocalTime.class, "now")
            .orShould().callMethod(OffsetDateTime.class, "now")
            .orShould().callMethod(ZonedDateTime.class, "now")
            .orShould().callMethod(System.class, "currentTimeMillis")
            .because("las fechas y horas salen del Clock inyectado, en la zona institucional");

    /**
     * Las reglas anteriores no deben pasar por no tener nada que revisar: se comprueba que el código analizado
     * contiene entidades, controladores, repositorios y servicios transaccionales.
     */
    @ArchTest
    static void las_reglas_tienen_codigo_que_revisar(JavaClasses clases) {
        assertThat(clases.that(DescribedPredicate.describe("entidad",
                (JavaClass c) -> c.isAnnotatedWith(Entity.class)))).isNotEmpty();
        assertThat(clases.that(DescribedPredicate.describe("controlador",
                (JavaClass c) -> c.isAnnotatedWith(RestController.class)))).isNotEmpty();
        assertThat(clases.that(DescribedPredicate.describe("servicio transaccional",
                (JavaClass c) -> c.getPackageName().contains(".aplicacion") && c.getMethods().stream()
                        .anyMatch(m -> m.isAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)))))
                .isNotEmpty();
        assertThat(clases.that(resideInAnyPackage("pe.edu.gesedu.compartido..", "pe.edu.gesedu.transversal..")))
                .isNotEmpty();
        assertThat(clases.that(assignableTo(Repository.class))).isNotEmpty();
    }

    private static ArchCondition<JavaMethod> noInvolucrarEntidades() {
        return new ArchCondition<>("no devolver ni recibir entidades JPA") {
            @Override
            public void check(JavaMethod metodo, ConditionEvents eventos) {
                metodo.getReturnType().getAllInvolvedRawTypes().stream().filter(ArquitecturaTest::esEntidad)
                        .forEach(t -> eventos.add(violated(metodo,
                                metodo.getFullName() + " devuelve la entidad " + t.getName())));
                metodo.getParameterTypes().forEach(parametro -> parametro.getAllInvolvedRawTypes().stream()
                        .filter(ArquitecturaTest::esEntidad)
                        .forEach(t -> eventos.add(violated(metodo,
                                metodo.getFullName() + " recibe la entidad " + t.getName()))));
            }
        };
    }

    private static boolean esEntidad(JavaClass tipo) {
        return tipo.isAnnotatedWith(Entity.class) || tipo.isAnnotatedWith(MappedSuperclass.class);
    }
}
