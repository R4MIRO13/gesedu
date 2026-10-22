package pe.edu.gesedu.compartido.paginacion;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Respuesta paginada de la API (docs/PLAN.md § 5.1): {@code {contenido, pagina, tamano, totalElementos, totalPaginas}}. */
public record Pagina<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {

    public static <E, T> Pagina<T> de(Page<E> pagina, Function<E, T> conversion) {
        return new Pagina<>(pagina.getContent().stream().map(conversion).toList(), pagina.getNumber(),
                pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
    }
}
