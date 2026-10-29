-- Tabla de ShedLock (DA-08): un solo nodo ejecuta cada job aunque haya varias instancias.
-- Estructura exigida por shedlock-provider-jdbc-template.
CREATE TABLE transversal.shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
