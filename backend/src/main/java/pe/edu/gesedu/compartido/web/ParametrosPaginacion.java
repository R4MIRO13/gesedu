package pe.edu.gesedu.compartido.web;

import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import pe.edu.gesedu.compartido.error.CodigoError;
import pe.edu.gesedu.compartido.error.ExcepcionNegocio;

/**
 * Convierte {@code ?page=&size=&sort=campo,asc} en un {@link Pageable} (docs/PLAN.md § 5.1): tamaño máximo 100
 * y lista blanca de campos ordenables, para que nadie ordene por una columna sin índice o inexistente.
 */
public final class ParametrosPaginacion {

    public static final int TAMANO_POR_DEFECTO = 20;
    public static final int TAMANO_MAXIMO = 100;

    private ParametrosPaginacion() {
    }

    public static Pageable de(Integer pagina, Integer tamano, String orden, Set<String> camposOrdenables,
            Sort ordenPorDefecto) {
        int p = pagina == null ? 0 : pagina;
        int t = tamano == null ? TAMANO_POR_DEFECTO : tamano;
        if (p < 0 || t < 1 || t > TAMANO_MAXIMO) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA,
                    "page debe ser >= 0 y size debe estar entre 1 y " + TAMANO_MAXIMO);
        }
        return PageRequest.of(p, t, orden == null || orden.isBlank() ? ordenPorDefecto
                : ordenamiento(orden, camposOrdenables));
    }

    private static Sort ordenamiento(String orden, Set<String> camposOrdenables) {
        String[] partes = orden.split(",");
        String campo = partes[0].trim();
        boolean valido = camposOrdenables.contains(campo) && partes.length <= 2;
        Sort.Direction direccion = Sort.Direction.ASC;
        if (valido && partes.length == 2) {
            switch (partes[1].trim().toLowerCase()) {
                case "asc" -> direccion = Sort.Direction.ASC;
                case "desc" -> direccion = Sort.Direction.DESC;
                default -> valido = false;
            }
        }
        if (!valido) {
            throw new ExcepcionNegocio(CodigoError.VALIDACION_INVALIDA,
                    "sort debe ser uno de " + camposOrdenables + ", opcionalmente seguido de ,asc o ,desc");
        }
        return Sort.by(direccion, campo);
    }
}
