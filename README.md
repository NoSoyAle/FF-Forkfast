# ForkFast-FF

Sistema básico de restaurante (Productos, Inventario, Clientes, Órdenes) para el
Encargo de Evaluación Parcial N°1 — DSY1107 Desarrollo Cloud Native I.

Arquitectura:

```
Angular (MSAL) → login con Azure AD → obtiene JWT
      ↓
AWS API Gateway (protege y enruta)
      ↓
Spring Boot en EC2 (valida el JWT con filtro de seguridad, conecta a BD cloud)
```

## Estructura

```
ForkFast-FF/
├── backend/     Spring Boot (Java 17, Maven) - microservicio REST
└── frontend/    Angular 17 + MSAL
```

## 1. Backend (Spring Boot)

Requisitos: Java 17, Maven.

```bash
cd backend
mvn spring-boot:run
```

Por defecto corre en `http://localhost:8080` con una base de datos H2 en memoria
(perfecta para desarrollar sin instalar nada). La consola H2 está en
`http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:forkfastdb`, usuario `sa`).

Antes de conectar el frontend real, edita `src/main/resources/application.yml`:

```yaml
azure:
  ad:
    issuer-uri: https://login.microsoftonline.com/{tenant-id}/v2.0
    audience: api://{client-id-de-la-api}
```

Reemplaza `{tenant-id}` por el Directory (tenant) ID de tu App Registration en
Azure Portal, y `{client-id-de-la-api}` por el Application ID URI que le des a tu
API al "Exponer una API" (Expose an API) en Azure AD.

Cuando quieras usar una base de datos cloud real (por ejemplo RDS Postgres),
reemplaza el bloque `spring.datasource` por tus credenciales reales.

## 2. Frontend (Angular + MSAL)

Requisitos: Node.js 18+, npm.

```bash
cd frontend
npm install
npm start
```

Corre en `http://localhost:4200`.

Antes de iniciar sesión, edita `src/environments/environment.ts`:

```ts
azureAd: {
  clientId: '{client-id-de-la-app-frontend}',
  tenantId: '{tenant-id}',
  redirectUri: 'http://localhost:4200',
  postLogoutRedirectUri: 'http://localhost:4200',
  apiScope: 'api://{client-id-de-la-api}/access_as_user',
},
```

Estos valores salen de dos App Registrations en Azure AD: una para el frontend
(SPA, con el redirect URI de arriba) y otra para el backend/API (la que expone
el scope `access_as_user`).

## 2.5. Con Docker (recomendado para EC2)

Esto evita instalar Java, Node, Maven o nginx a mano en la instancia — solo necesitas Docker.
Además, el frontend usa nginx para redirigir `/api` al backend internamente, así que **no
hay que preocuparse de CORS ni de que cambie la IP pública**.

**Instalar Docker en Amazon Linux 2023:**

```bash
sudo dnf install -y docker
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
# cierra sesión y vuelve a conectarte para que el grupo "docker" tome efecto
```

**Instalar el plugin de Docker Compose:**

```bash
DOCKER_CONFIG=${DOCKER_CONFIG:-$HOME/.docker}
mkdir -p $DOCKER_CONFIG/cli-plugins
curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o $DOCKER_CONFIG/cli-plugins/docker-compose
chmod +x $DOCKER_CONFIG/cli-plugins/docker-compose
docker compose version
```

**Levantar todo con un comando** (desde la raíz del proyecto, donde está `docker-compose.yml`):

```bash
docker compose up -d --build
```

Esto compila ambas imágenes y deja corriendo:
- Backend en el puerto `8080`
- Frontend (nginx) en el puerto `80`, con `/api` redirigido internamente al backend

**Comandos útiles:**

```bash
docker compose ps              # ver estado de los contenedores
docker compose logs -f backend # logs del backend en vivo
docker compose logs -f frontend
docker compose down            # detener y eliminar los contenedores
docker compose up -d --build   # reconstruir después de cambios en el código
```

⚠️ En un `t3.micro` (1GB RAM), compilar la imagen del frontend (`npm install` dentro del
contenedor) puede quedarse sin memoria si no tienes swap activo. Revisa la sección de swap
más abajo antes de correr `docker compose up --build` por primera vez.

## 2.6. HTTPS obligatorio para el login (MSAL lo exige)

MSAL usa la Web Crypto API del navegador, que solo está disponible en un **contexto
seguro**: HTTPS, o `http://localhost` como única excepción. Si accedes por `http://TU-IP`
sin HTTPS, el login falla con `BrowserAuthError: crypto_nonexistent`.

**Genera un certificado autofirmado** (gratis, sin dominio, en la propia instancia EC2):

```bash
mkdir -p certs
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout certs/selfsigned.key \
  -out certs/selfsigned.crt \
  -subj "/CN=TU-IP-O-DOMINIO"
```

Corre esto en la raíz del proyecto (al mismo nivel que `docker-compose.yml`), que ya está
configurado para montar esa carpeta `certs/` dentro del contenedor del frontend.

Luego:
```bash
docker compose up -d --build
```

Abre `https://TU-IP` (con **https**, no http). El navegador va a mostrar una advertencia
de "certificado no válido" porque es autofirmado — es esperado. Haz clic en
"Avanzado" → "Continuar de todas formas" una vez, y el resto de la sesión funciona normal.

**No olvides:**
- Agregar el puerto **443** (HTTPS) al Security Group de la instancia, además del 80.
- Actualizar el Redirect URI en Azure AD a `https://TU-IP` (con https, no http).

## 2.7. Checklist de despliegue en EC2 (lo que ya está probado y funciona)

1. Security Group: puertos **22**, **80** y **443** abiertos. Asignar una **Elastic IP** a la instancia.
2. Azure AD → App registration → Authentication: la URL `https://TU-IP` debe estar
   **solo** bajo la plataforma **"Single-page application"** (no bajo "Web").
3. Generar los certificados en la instancia (la carpeta `certs/` está en `.gitignore`,
   no viaja con el repo): ver sección 2.6.
4. `docker compose up -d --build`.
5. Entrar por `https://TU-IP` y aceptar la advertencia del certificado autofirmado.

**Cambios importantes ya incluidos en el código (no los revertir):**
- `app.config.ts`: `initializeMsal` solo llama a `initialize()` (llamar también a
  `handleRedirectPromise()` provoca `state_not_found`).
- `msal.config.ts`: `redirectUri` usa `window.location.origin` (no una URL fija).
- `angular.json`: incluye `"polyfills": ["zone.js"]`.
- `nginx.conf`: sirve por HTTPS (MSAL exige contexto seguro) y quita el header
  `Origin` en `/api/` para evitar el 403 "Invalid CORS request" de Spring.
- `DataSeeder.java`: carga datos de prueba al arrancar el backend (H2 en memoria).

## 2.8. RabbitMQ (local y EC2)

RabbitMQ se ejecuta como otro servicio de Docker Compose. El backend se conecta a
`rabbitmq:5672` por la red interna de Docker; no es necesario abrir el puerto AMQP
en el Security Group de EC2. La interfaz de administración queda ligada a localhost
para no exponerla públicamente.

**Levantar RabbitMQ y el backend para probar la mensajería:**

```bash
cp .env.example .env   # opcional: define credenciales propias antes del primer arranque
docker compose up -d --build backend
docker compose ps
```

Compose espera a que el healthcheck de RabbitMQ responda antes de arrancar el backend.
La interfaz de administración está en `http://localhost:15673`. Sin archivo `.env`,
las credenciales de prueba son `forkfast` / `forkfast`; `.env.example` contiene esos
mismos valores para que se pueda copiar y luego cambiar la contraseña. En esta máquina
los puertos publicados del host son `5673` y `15673`; dentro de Docker RabbitMQ sigue
usando los puertos estándar `5672` y `15672`.

El endpoint de prueba está protegido por el mismo JWT de Azure que el resto de `/api`:

```bash
curl -i -X POST http://localhost:8080/api/mensajeria/prueba \
  -H "Authorization: Bearer <TOKEN_AZURE>" \
  -H "Content-Type: application/json" \
  -d '{"mensaje":"Prueba RabbitMQ desde ForkFast"}'
```

Para obtener el token, inicia sesión en el frontend y copia el `Bearer` de una
solicitud a `/api` desde la pestaña **Network/Red** de las herramientas del navegador.
En EC2, reemplaza la URL local por `https://TU-IP/api/mensajeria/prueba`. El endpoint
devuelve `202 Accepted`; después, verifica el consumidor con:

```bash
docker compose logs -f backend
```

En los logs debe aparecer `Mensaje recibido desde RabbitMQ` para la cola
`forkfast.prueba`, y la cola debería mostrar un consumidor activo.
También se puede abrir `http://localhost:15673`, entrar a **Queues and Streams** y
confirmar que `forkfast.prueba` muestra un consumidor.

El host, puerto, usuario y contraseña del backend se configuran con las variables
`RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER` y `RABBITMQ_PASS`. En Compose se
inyectan automáticamente; los valores de usuario/contraseña se pueden cambiar con
esas variables de entorno o en un archivo `.env` local (no se sube a Git).
Configura `.env` antes de la primera ejecución: RabbitMQ conserva usuarios en el
volumen persistente y no reemplaza credenciales ya inicializadas al cambiar variables.

Si ejecutas Spring Boot directamente en el host y RabbitMQ con Compose, importa las
variables y conecta al puerto publicado del host:

```bash
set -a
source .env
set +a
export RABBITMQ_HOST=localhost
export RABBITMQ_PORT="$RABBITMQ_AMQP_HOST_PORT"
mvn -f backend/pom.xml spring-boot:run
```

**Acceder a la UI de RabbitMQ en EC2 sin exponer el puerto:** desde tu computador,
abre un túnel SSH y luego visita la dirección local:

```bash
ssh -L 15673:127.0.0.1:15673 ec2-user@TU-IP-EC2
```

Visita `http://localhost:15673`. Cambia las credenciales predeterminadas antes de
usar el despliegue fuera de una prueba local.

## 3. Endpoints del backend

Todos bajo `/api`, protegidos y requieren el JWT de Azure AD en el header
`Authorization: Bearer {token}` (MSAL lo agrega automático vía el interceptor):

- `GET/POST/PUT/DELETE /api/productos`
- `GET/POST/PUT/DELETE /api/inventarios`
- `GET/POST/PUT/DELETE /api/clientes`
- `GET/POST/PUT/DELETE /api/ordenes`
- `POST /api/mensajeria/prueba` — publica un mensaje de prueba en RabbitMQ

## 4. Siguientes pasos para cumplir el encargo completo

- [ ] Dividir el backend en varios microservicios (por ejemplo: `productos-service`,
      `clientes-service`, `ordenes-service`) en vez de un solo módulo — la pauta
      pide explícitamente "varios microservicios".
- [ ] Agregar el componente BFF que valida el JWT antes de reenviar al API Gateway
      (indicador con 40% de ponderación en la pauta).
- [ ] Desplegar el/los backend(s) en instancias EC2.
- [ ] Configurar AWS API Gateway delante de las instancias EC2.
- [ ] Conectar a una base de datos cloud real (RDS) en vez de H2.
- [ ] Crear los dos App Registrations en Azure AD (frontend SPA + API) y completar
      los placeholders en `environment.ts` y `application.yml`.
- [ ] Subir frontend y backend como repositorios GitHub separados, cada uno con
      su `.gitignore` correspondiente (ya incluidos en este starter).

## Notas de recursos (8GB disco / 1GB RAM)

Ver sección "Liberar puertos y recursos" en la respuesta del chat para instrucciones
sobre cómo liberar puertos ocupados (8080, 4200) en tu máquina local.
