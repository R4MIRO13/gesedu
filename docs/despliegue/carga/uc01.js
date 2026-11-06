// Prueba de carga de UC-01 «Solicitar matrícula» (RNF-12, RNF-03): 200 apoderados virtuales, cada uno con su cuenta.
// Cada iteración recorre el asistente: opciones -> URL de subida -> subida del certificado -> solicitud (alumno nuevo).
// Umbral: p95 < 2 s. Ver docs/despliegue/carga/README.md para cómo correrla.
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const USUARIOS = Number(__ENV.USUARIOS || 200);
const ESCENARIO = __ENV.ESCENARIO || 'sostenida';
const DURACION = __ENV.DURACION || '2m';
const ANIO = Number(__ENV.ANIO || 2027);
// Los DNI de los alumnos deben ser únicos entre corridas: la semilla cambia por corrida
const SEMILLA = Number(__ENV.SEMILLA || Math.floor(Date.now() / 1000) % 1000);

export const options = {
  setupTimeout: '5m',
  scenarios: {
    // pico: los 200 apoderados envían su solicitud a la vez, una sola vez (RNF-12, «picos de 200 concurrentes»).
    // sostenida (por omisión): cada apoderado repite el asistente sin parar durante DURACION (prueba de estrés).
    apoderados: ESCENARIO === 'pico'
      ? { executor: 'per-vu-iterations', vus: USUARIOS, iterations: 1, maxDuration: '3m' }
      : { executor: 'constant-vus', vus: USUARIOS, duration: DURACION },
  },
  thresholds: {
    'http_req_duration{paso:solicitud}': ['p(95)<2000'],
    'http_req_duration{paso:opciones}': ['p(95)<2000'],
    'http_req_duration{paso:subida-url}': ['p(95)<2000'],
    'http_req_duration{paso:subida}': ['p(95)<2000'],
    http_req_duration: ['p(95)<2000'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

const JSON_H = { 'Content-Type': 'application/json' };

export function setup() {
  // Inicios de sesión por lotes de 20 (BCrypt es lento a propósito); el token vale 15 min, más que la corrida
  const tokens = [];
  for (let desde = 1; desde <= USUARIOS; desde += 20) {
    const lote = [];
    for (let n = desde; n < desde + 20 && n <= USUARIOS; n++) {
      const correo = `carga${String(n).padStart(3, '0')}@carga.gesedu.pe`;
      lote.push(['POST', `${BASE}/api/v1/auth/inicio-sesion`,
        JSON.stringify({ correo, contrasena: 'Gesedu2027!' }), { headers: JSON_H, tags: { paso: 'login' } }]);
    }
    for (const r of http.batch(lote)) {
      if (r.status !== 200) throw new Error(`login: ${r.status} ${r.body}`);
      tokens.push(r.json('tokenAcceso'));
    }
  }
  return { tokens };
}

export default function (datos) {
  const token = datos.tokens[(__VU - 1) % datos.tokens.length];
  const auth = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };

  const op = http.get(`${BASE}/api/v1/solicitudes-matricula/opciones?anio=${ANIO}`, { headers: auth, tags: { paso: 'opciones' } });
  if (!check(op, { 'opciones 200': (r) => r.status === 200 })) return;
  const gradoId = op.json('grados.0.id');

  const pdf = '%PDF-1.4 certificado de carga';
  const sub = http.post(`${BASE}/api/v1/solicitudes-matricula/archivos`,
    JSON.stringify({ nombreArchivo: 'certificado.pdf', contentType: 'application/pdf', tamanoBytes: pdf.length }),
    { headers: auth, tags: { paso: 'subida-url' } });
  if (!check(sub, { 'url de subida 200': (r) => r.status === 200 })) return;
  const clave = sub.json('claveObjeto');
  const urlSubida = sub.json('subida.url');

  const put = http.put(`${BASE}${urlSubida}`, pdf, { headers: { 'Content-Type': 'application/pdf' }, tags: { paso: 'subida' } });
  if (!check(put, { 'subida 200': (r) => r.status === 200 })) return;

  const dni = String(10000000 + ((SEMILLA * 20000 + (__VU - 1) * 100 + (__ITER % 100)) % 80000000));
  const sol = http.post(`${BASE}/api/v1/solicitudes-matricula`, JSON.stringify({
    anio: ANIO, tipoEstudiante: 'NUEVO_INGRESO', gradoId,
    alumno: { nombres: `Alumno${__VU}`, apellidos: `Carga ${__ITER}`, documento: dni, fechaNacimiento: '2013-05-05', parentesco: 'PADRE' },
    telefonoApoderado: '987654321',
    documentos: [{ tipo: 'CERTIFICADO_ESTUDIOS', claveObjeto: clave, nombreArchivo: 'certificado.pdf', contentType: 'application/pdf', tamanoBytes: pdf.length }],
  }), { headers: auth, tags: { paso: 'solicitud' } });
  check(sol, { 'solicitud 201': (r) => r.status === 201 });

  sleep(1);
}
