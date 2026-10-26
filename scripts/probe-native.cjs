const fs = require("fs");
const {parseEnv} = require("node:util");
async function main() {
  const env = parseEnv(fs.readFileSync(process.env.ENV_FILE || "/opt/cronmaster/.env", "utf8"));
  const port = env.PORT || "3000";
  if (!/^\d+$/.test(port) || +port < 1 || +port > 65535) throw new Error("PORT no válido");
  const base = `http://127.0.0.1:${port}`;
  const headers = {"Content-Type":"application/json"};
  if (env.API_KEY) headers.Authorization = `Bearer ${env.API_KEY}`;
  else if (env.AUTH_PASSWORD) {
    const login = await fetch(base + "/api/auth/login", {
      method:"POST", headers, redirect:"manual", signal:AbortSignal.timeout(15000),
      body:JSON.stringify({password:env.AUTH_PASSWORD})
    });
    if (!login.ok || (await login.json()).success !== true) throw new Error("Login rechazado");
    headers.Cookie = login.headers.getSetCookie().map(v=>v.split(";")[0]).join("; ");
    if (!headers.Cookie) throw new Error("No se recibió cookie");
  } else throw new Error("Configura API_KEY o AUTH_PASSWORD; este probe no implementa OIDC");
  const response = await fetch(base + "/api/native", {
    method:"POST", headers, redirect:"manual", signal:AbortSignal.timeout(15000),
    body:JSON.stringify({op:"capabilities",args:{}})
  });
  if (!response.ok) throw new Error(`/api/native HTTP ${response.status}`);
  const data = await response.json();
  if (data.success !== true || data.version !== 1) throw new Error("Extensión incompatible");
  console.log("Acceso autenticado a /api/native: OK");
}
main().catch(e=>{console.error(e.message);process.exit(1);});
