package pe.edu.gesedu.evaluacion.dominio;

import java.util.List;

final class ValidacionLogros {

    private ValidacionLogros() {
    }

    static void exigirNoVacio(List<LogroPonderado> logros) {
        if (logros == null || logros.isEmpty()) {
            throw new IllegalArgumentException("Se necesita al menos un logro para consolidar");
        }
    }
}
