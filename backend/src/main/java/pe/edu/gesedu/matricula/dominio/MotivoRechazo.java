package pe.edu.gesedu.matricula.dominio;

/** Motivos con que se rechaza una solicitud (UC-02, FA-1 a FA-5). */
public enum MotivoRechazo {
    NO_ADMITIDO("No Admitido"),
    DUPLICIDAD("Duplicidad"),
    DEUDAS("Deudas"),
    SIN_VACANTE("sin Vacante"),
    VOLUNTAD_APODERADO("Voluntad del Apoderado");

    private final String etiqueta;

    MotivoRechazo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Texto del estado visible: «Rechazada por No Admitido», «Rechazada sin Vacante»… */
    public String estadoVisible() {
        return this == SIN_VACANTE ? "Rechazada " + etiqueta : "Rechazada por " + etiqueta;
    }
}
