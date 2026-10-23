package pe.edu.gesedu.compartido.error;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Error de negocio que el manejador global convierte en una respuesta RFC 9457 con el estado y el
 * {@code codigo} de {@link CodigoError}. Las propiedades extra (por ejemplo {@code bloqueadoHasta})
 * se agregan al cuerpo de la respuesta.
 */
public class ExcepcionNegocio extends RuntimeException {

    private final CodigoError codigo;
    private final Map<String, Object> propiedades = new LinkedHashMap<>();

    public ExcepcionNegocio(CodigoError codigo) {
        this(codigo, codigo.titulo());
    }

    public ExcepcionNegocio(CodigoError codigo, String detalle) {
        super(detalle);
        this.codigo = codigo;
    }

    public ExcepcionNegocio con(String propiedad, Object valor) {
        propiedades.put(propiedad, valor);
        return this;
    }

    public CodigoError codigo() {
        return codigo;
    }

    public Map<String, Object> propiedades() {
        return Map.copyOf(propiedades);
    }
}
