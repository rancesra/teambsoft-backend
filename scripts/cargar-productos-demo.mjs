// Carga los productos de demostración (scripts/productos-demo.json) en Catálogo.
//
// Usa la API de verdad, por Kong y con el token de un administrador de Keycloak, igual que lo haría
// la pantalla de administración. Así cada producto publica su evento "producto.creado" y Búsqueda
// lo indexa por la vía normal.
//
// Se puede correr varias veces: los productos que ya existen (mismo nombre) no se vuelven a crear.
//
// Uso, con la tienda arriba (docker compose del backend o el de integración):
//   node scripts/cargar-productos-demo.mjs
//
// Variables opcionales: KONG_URL (http://localhost:8000), KEYCLOAK_URL
// (http://localhost:8180/realms/tienda), ADMIN_USUARIO (admin) y ADMIN_CLAVE (admin123).

import { readFile } from "node:fs/promises";

const KONG = process.env.KONG_URL || "http://localhost:8000";
const KEYCLOAK = process.env.KEYCLOAK_URL || "http://localhost:8180/realms/tienda";
const USUARIO = process.env.ADMIN_USUARIO || "admin";
const CLAVE = process.env.ADMIN_CLAVE || "admin123";
const PRODUCTOS = `${KONG}/api/catalogo/productos`;

/** Token de administrador. El cliente tienda-pruebas del realm permite pedirlo con usuario y clave. */
async function pedirToken() {
  const respuesta = await fetch(`${KEYCLOAK}/protocol/openid-connect/token`, {
    method: "POST",
    body: new URLSearchParams({
      grant_type: "password",
      client_id: "tienda-pruebas",
      username: USUARIO,
      password: CLAVE,
    }),
  });
  if (!respuesta.ok) throw new Error(`Keycloak respondió ${respuesta.status}. ¿Está arriba en ${KEYCLOAK}?`);
  return (await respuesta.json()).access_token;
}

/** Los nombres de todos los productos que ya existen, activos o no, recorriendo las páginas. */
async function nombresExistentes(token) {
  const nombres = new Set();
  for (let pagina = 1; ; pagina++) {
    const respuesta = await fetch(`${PRODUCTOS}?activo=todos&pagina=${pagina}&tamanoPagina=100`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!respuesta.ok) throw new Error(`Catálogo respondió ${respuesta.status} al listar. ¿Está arriba en ${KONG}?`);
    const { productos } = await respuesta.json();
    productos.forEach((p) => nombres.add(p.nombre));
    if (productos.length < 100) return nombres;
  }
}

const productos = JSON.parse(await readFile(new URL("./productos-demo.json", import.meta.url), "utf8"));
const token = await pedirToken();
const existentes = await nombresExistentes(token);

let creados = 0;
let omitidos = 0;
for (const producto of productos) {
  if (existentes.has(producto.nombre)) {
    omitidos++;
    continue;
  }
  const respuesta = await fetch(PRODUCTOS, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(producto),
  });
  if (!respuesta.ok) {
    const error = await respuesta.json().catch(() => ({}));
    throw new Error(`No se pudo crear «${producto.nombre}»: ${respuesta.status} ${error.mensaje ?? ""}`);
  }
  creados++;
}

console.log(`Listo: ${creados} productos creados, ${omitidos} ya existían (de ${productos.length}).`);
