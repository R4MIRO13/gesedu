package pe.edu.gesedu.auditoria.aplicacion;

import java.util.List;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import pe.edu.gesedu.compartido.auditoria.Auditable;
import pe.edu.gesedu.compartido.auditoria.ContextoAuditoria;

/**
 * Aspecto de {@link Auditable} (DA-06). Se ejecuta DENTRO de la transacción de la operación
 * ({@code ORDEN} es más bajo en precedencia que el de la transacción, ver {@code TransaccionesConfig}): el
 * registro se escribe cuando la operación terminó bien y se confirma o revierte con ella.
 */
@Aspect
@Component
@Order(AspectoAuditable.ORDEN)
class AspectoAuditable {

    /** Más interno que {@code TransaccionesConfig.ORDEN_TRANSACCION}. */
    static final int ORDEN = Ordered.LOWEST_PRECEDENCE;

    private final SpelExpressionParser analizador = new SpelExpressionParser();
    private final RegistradorAuditoria registrador;

    AspectoAuditable(RegistradorAuditoria registrador) {
        this.registrador = registrador;
    }

    @Around("@annotation(auditable)")
    Object auditar(ProceedingJoinPoint punto, Auditable auditable) throws Throwable {
        ContextoAuditoria.abrir();
        Object resultado;
        List<ContextoAuditoria.Cambio> cambios;
        try {
            resultado = punto.proceed();
        } finally {
            cambios = ContextoAuditoria.cerrar();
        }

        EvaluationContext contexto = contexto(punto, resultado);
        if (!auditable.condicion().isBlank()
                && !Boolean.TRUE.equals(analizador.parseExpression(auditable.condicion()).getValue(contexto, Boolean.class))) {
            return resultado;
        }
        String idEntidad = auditable.idEntidad().isBlank() ? null
                : valorComoTexto(analizador.parseExpression(auditable.idEntidad()).getValue(contexto));
        String descripcion = auditable.descripcion().isBlank()
                ? auditable.accion() + " " + auditable.tipoEntidad() + (idEntidad == null ? "" : " " + idEntidad)
                : auditable.descripcion();
        registrador.registrar(auditable.accion(), auditable.modulo(), auditable.tipoEntidad(), idEntidad, descripcion,
                cambios);
        return resultado;
    }

    /** Los parámetros del método se ven por nombre ({@code #usuario.id}) y el retorno como {@code #result}. */
    private static EvaluationContext contexto(ProceedingJoinPoint punto, Object resultado) {
        StandardEvaluationContext contexto = new StandardEvaluationContext();
        String[] nombres = ((MethodSignature) punto.getSignature()).getParameterNames();
        Object[] argumentos = punto.getArgs();
        for (int i = 0; nombres != null && i < nombres.length; i++) {
            contexto.setVariable(nombres[i], argumentos[i]);
        }
        contexto.setVariable("result", resultado);
        return contexto;
    }

    private static String valorComoTexto(Object valor) {
        return valor == null ? null : valor.toString();
    }
}
