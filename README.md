# Nuxeo ONLYOFFICE Integration

[![Build Status](https://qa.nuxeo.org/jenkins/buildStatus/icon?job=Sandbox/sandbox_nuxeo-onlyoffice-master)](https://qa.nuxeo.org/jenkins/view/Sandbox/job/Sandbox/job/sandbox_nuxeo-onlyoffice-master/)

In-browser integration of Nuxeo Platform and [ONLYOFFICE](https://www.onlyoffice.com/).

> [!IMPORTANT]
> Master branch is **Work in Progress** for upgrade to LTS2025. Do not use it as is.

## Dependencies

[ONLYOFFICE](https://www.onlyoffice.com/) Document Server (Run a [Docker](https://github.com/ONLYOFFICE/Docker-DocumentServer) image)

### ONLYOFFICE JWT is required

ONLYOFFICE Document Server ≥ 7.x has JWT enabled by default, and this plugin signs every payload it sends (editor config, conversion request, blob download) and verifies the JWT on the save callback. Run the Document Server with JWT **enabled** and share the same secret with Nuxeo:

- On the Document Server: `JWT_ENABLED=true` and `JWT_SECRET=<your-secret>`.
- On Nuxeo: `onlyoffice.jwt.secret=<the-same-secret>` (see [Configure](#configure-nuxeoconf)).

The secret on both sides **must match**, otherwise the editor fails to open documents (`errorCode: -20`, "The document security token is not correctly formed") and save callbacks are rejected with `401`.

JWT is on by default in the plugin (`onlyoffice.jwt.enabled=true`). It can be turned off with `onlyoffice.jwt.enabled=false` for a Document Server started with `JWT_ENABLED=false`, but this is **not recommended** and unsupported for production.

## Build and Install

Build with maven (at least 3.3)

```
mvn clean install
```
> Package built here: `nuxeo-onlyoffice-package/target`

> Install with `nuxeoctl mp-install <package>`

## Configure (nuxeo.conf)

All properties below go into your Nuxeo configuration (`nuxeo.conf`, or a
`nuxeo.conf` fragment — see [Docker deployment](#docker-deployment-browser-vs-container-urls)).
You do **not** need to activate any template — the plugin reads these properties
directly at runtime.

`nuxeo.url` is a **platform-wide** Nuxeo property (fully qualified instance URL,
such as `https://my.nuxeo.org/nuxeo`) shared by many Nuxeo features (mail, share
links, ...). It is usually already set on your instance; this plugin only reads
it. Make sure it is correct — this plugin depends on it.

Editor properties:

```
# URL to editor api.js service (required)
onlyoffice.url.api=http://onlyoffice/web-apps/apps/api/documents/api.js
# Internal Nuxeo URL used by ONLYOFFICE to download blobs and POST save
# callbacks (optional, default: falls back to nuxeo.url). See "Docker" below.
onlyoffice.url.nuxeo=http://nuxeo:8080/nuxeo
# Create version on save (optional, default: false)
onlyoffice.version.save=true|false
```

JWT properties (see [ONLYOFFICE JWT is required](#onlyoffice-jwt-is-required)):

```
# Enable JWT signing/verification (optional, default: true)
onlyoffice.jwt.enabled=true
# Shared secret — MUST match the Document Server JWT_SECRET (required when enabled)
onlyoffice.jwt.secret=<your-shared-secret>
# Signing algorithm: HS256 (default), HS384 or HS512. Must match the Document Server.
onlyoffice.jwt.algorithm=HS256
# HTTP header carrying the token (optional, default: Authorization)
onlyoffice.jwt.header=Authorization
# Token prefix (optional, default: "Bearer ")
onlyoffice.jwt.prefix=Bearer
# Token time-to-live in seconds (optional, default: 300)
onlyoffice.jwt.ttl=300
```

Conversion properties (Optional):

```
# URL to conversion service (see ONLYOFFICE docs)
onlyoffice.url.conversion=http://onlyoffice/ConvertService.ashx
# Number of millisecond to wait between polling async request
onlyoffice.conversion.wait=1000
```

### Docker deployment (browser vs container URLs)

The usual way to configure the plugin in Docker is to drop a `nuxeo.conf`
fragment into the mounted `conf.d/` directory — no template activation needed.
For example, keep a local `conf/` folder holding an `onlyoffice.conf` and mount
it in your Compose file:

```yaml
volumes:
  - ./conf:/etc/nuxeo/conf.d/:ro
```

Then put the properties below into `./conf/onlyoffice.conf`.

When Nuxeo and ONLYOFFICE run in separate containers, a single URL cannot serve
everyone: the browser and the ONLYOFFICE container reach Nuxeo through different
hostnames. The plugin uses **two** Nuxeo base URLs to handle this.

| Property | Consumed by | Must be reachable from | Purpose |
|---|---|---|---|
| `nuxeo.url` | Browser | End user's browser | "Open in Nuxeo" link |
| `onlyoffice.url.nuxeo` | ONLYOFFICE container | Document Server container | Blob download + save callback |
| `onlyoffice.url.api` | Browser | End user's browser | Loads the editor (`api.js`) |
| `onlyoffice.url.conversion` | Nuxeo (server) | Nuxeo container | `office2pdf` conversion calls |

`onlyoffice.url.nuxeo` falls back to `nuxeo.url` when unset, so on a
single-machine (non-Docker) setup you only need `nuxeo.url`.

Example for a Docker Compose setup where the ONLYOFFICE service is named
`onlyoffice` and the Nuxeo service is named `nuxeo`, fronted by a public host:

```
# Browser-facing (public)
nuxeo.url=https://nuxeo.example.com/nuxeo
onlyoffice.url.api=https://onlyoffice.example.com/web-apps/apps/api/documents/api.js

# Container-facing (internal Docker network)
onlyoffice.url.nuxeo=http://nuxeo:8080/nuxeo
onlyoffice.url.conversion=http://onlyoffice/ConvertService.ashx
```

### Deploying with nuxeo-presales-docker

If you use the [nuxeo-presales-docker](https://github.com/nuxeo-sandbox/nuxeo-presales-docker) tooling, add an `onlyoffice` service to your `docker-compose` file alongside the existing `nuxeo` / `mongo` / `opensearch` / `dashboards` services:

```yaml
services:
  nuxeo:
    # . . .
  mongo:
    # . . .
  opensearch:
    # . . .
  dashboards:
    # . . .
  onlyoffice:
    image: onlyoffice/documentserver:latest
    hostname: onlyoffice
    restart: unless-stopped
    environment:
      - "JWT_ENABLED=true"
      - "JWT_SECRET=${JWT_SECRET}"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost/healthcheck"]
      interval: 1m
      timeout: 30s
      retries: 10
      start_period: 2m
    expose:
      - 80
    ports:
      - "8888:80"
    volumes:
      - ./data/onlyoffice-test-onlyoffice:/var/www/onlyoffice/Data
      - ./logs/onlyoffice:/var/log/onlyoffice
      # Post-start patch that adds request-filtering-agent.allowPrivateIPAddress
      # to /etc/onlyoffice/documentserver/local.json. Mounted so it can be run
      # via docker compose exec once the OnlyOffice entrypoint has completed
      # its own initialization of local.json (JWT secret generation, etc.).
      - ./conf/onlyoffice-local.json:/tmp/onlyoffice-ssrf-patch.json:ro
```

Store the shared secret in your `.env` file so both the Document Server (`JWT_SECRET`) and Nuxeo (`onlyoffice.jwt.secret`) use the **same** value:

```
# .env
JWT_SECRET=change-me-to-a-strong-random-secret
```

Then, in your mounted Nuxeo `conf.d/` fragment (e.g. `./conf/onlyoffice.conf`), point the plugin at this stack and reuse the same secret:

```
# Browser-facing (public) — YOUR_NUXEO_SERVER is your published Nuxeo host
nuxeo.url=https://YOUR_NUXEO_SERVER/nuxeo
onlyoffice.url.api=http://localhost:8888/web-apps/apps/api/documents/api.js

# Container-facing (internal Docker network) — the OnlyOffice container reaches
# Nuxeo through the compose service name (here: `nuxeo`) on its internal port
onlyoffice.url.nuxeo=http://nuxeo:8080/nuxeo
onlyoffice.url.conversion=http://onlyoffice/ConvertService.ashx

# JWT — must match JWT_SECRET from the .env file above
onlyoffice.jwt.enabled=true
onlyoffice.jwt.secret=change-me-to-a-strong-random-secret
```

> The `onlyoffice.jwt.secret` value must be identical to the `JWT_SECRET` passed to the `onlyoffice` container. A mismatch causes `errorCode: -20` on open and `401` on save.

> The `onlyoffice.url.api=http://localhost:8888/...` above only works when the **browser runs on the same machine** as the containers (pure local dev). For any remote / published deployment, see the next section — the browser must reach the Document Server over public HTTPS.

### Public / HTTPS deployment (e.g. AWS): OnlyOffice needs its own hostname

The editor is loaded **by the end user's browser** (`api.js`, then a WebSocket to the Document Server). So on a published server the DS must be reachable over **public HTTPS**, not just on the internal Docker network. Since Nuxeo is served over HTTPS, an `http://` `api.js` is also blocked as mixed content.

OnlyOffice does **not** support running under a sub-path (e.g. `/onlyoffice/`), so give it a **dedicated subdomain** fronted by your TLS-terminating reverse proxy. Example (validated with the [nuxeo-presales-docker](https://github.com/nuxeo-sandbox/nuxeo-presales-docker) tooling on AWS, which uses Apache + Certbot):

1. **DNS**: add a record for the OnlyOffice subdomain (e.g. `my-instance-onlyoffice.example.com`) pointing at the same host (a `CNAME` to the Nuxeo FQDN, or an `A` record to the same IP).
2. **Reverse proxy**: add a vhost that terminates TLS and proxies the subdomain to the DS container's published port (`8888` in the compose above), **including WebSocket**. With Apache:

   ```apache
   <VirtualHost _default_:443>
       ServerName my-instance-onlyoffice.example.com
       AllowEncodedSlashes NoDecode
       ProxyPreserveHost On
       ProxyAddHeaders Off
       RequestHeader set X-Forwarded-Proto "https"

       # WebSocket (co-editing) + HTTP on the same location
       RewriteEngine On
       RewriteCond %{HTTP:Upgrade} =websocket [NC]
       RewriteRule /(.*) ws://127.0.0.1:8888/$1 [P,L]
       RewriteCond %{HTTP:Upgrade} !=websocket [NC]
       RewriteRule /(.*) http://127.0.0.1:8888/$1 [P,L]

       ProxyPass        / http://127.0.0.1:8888/
       ProxyPassReverse / http://127.0.0.1:8888/
   </VirtualHost>
   ```
   (Enable `mod_proxy_wstunnel`; let Certbot add the `SSLCertificate*` lines.)
3. **`onlyoffice.url.api`**: point it at the subdomain (browser-facing, HTTPS):

   ```
   onlyoffice.url.api=https://my-instance-onlyoffice.example.com/web-apps/apps/api/documents/api.js
   ```

The **DS → Nuxeo** direction (blob download + save callback) is separate. Prefer the internal Docker network (`onlyoffice.url.nuxeo=http://nuxeo:8080/nuxeo`) rather than the public URL — a container reaching its host's own public IP often fails (NAT hairpinning). Because that internal URL is a private IP, the Document Server's SSRF filter blocks it by default, so enable `request-filtering-agent.allowPrivateIPAddress` in the DS `local.json` (the `onlyoffice-local.json` patch mounted in the compose above).

## (Optional) Use Conversion Service

Invoke the conversion service to transform between a variety of content types.  By default, the [office2pdf contribution](/nuxeo-onlyoffice-core/src/main/resources/OSGI-INF/onlyoffice-conversion-contrib.xml) will support PDF as a destination type.  See the [ONLYOFFICE Conversion API](https://api.onlyoffice.com/editors/conversionapi) for a full conversion matrix.

### Conversion Parameters

|Key      |Description|Allowable Values|
|---------|-----------|----------------|
|async    |Async Processing|true, false     |
|srcType  |Source Type|allowable type  |
|destType |Dest Type|allowable type  |
|codePage |Code Page|code page ref   |
|delimiter|Delimiter|delimiter ref   |
|thumbnail|Thumbnail|true, false, (height):(width):(aspect)|

### Rendition example

```xml
  <extension target="org.nuxeo.ecm.platform.rendition.service.RenditionService" point="renditionDefinitions">
    <renditionDefinition name="onlyoffice">
      <label>label.rendition.onlyofficepdf</label>
      <icon>/icons/note.gif</icon>
      <contentType>application/pdf</contentType>
      <operationChain>onlyofficePdf</operationChain>
      <storeByDefault>false</storeByDefault>
    </renditionDefinition>
  </extension>

  <extension target="org.nuxeo.ecm.core.operation.OperationServiceComponent" point="chains">
    <chain id="onlyofficePdf">
      <operation id="Context.PopBlob"/>
      <operation id="Blob.RunConverter">
        <param name="converter" type="string">office2pdf</param>
        <param name="parameters" type="properties">async=false</param>
      </operation>
    </chain>
  </extension>
```

# Support

**These features are not part of the Nuxeo Production platform.**

These solutions are provided for inspiration and we encourage customers to use them as code samples and learning resources.

This is a moving project (no API maintenance, no deprecation process, etc.) If any of these solutions are found to be useful for the Nuxeo Platform in general, they will be integrated directly into platform, not maintained here.

# License

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

# About Nuxeo

Nuxeo Platform is an open source Content Services platform, written in Java. Data can be stored in both SQL & NoSQL databases.

The development of the Nuxeo Platform is mostly done by Nuxeo employees with an open development model.

The source code, documentation, roadmap, issue tracker, testing, benchmarks are all public.

Typically, Nuxeo users build different types of information management solutions for [document management](https://www.nuxeo.com/solutions/document-management/), [case management](https://www.nuxeo.com/solutions/case-management/), and [digital asset management](https://www.nuxeo.com/solutions/dam-digital-asset-management/), use cases. It uses schema-flexible metadata & content models that allows content to be repurposed to fulfill future use cases.

More information is available at [www.nuxeo.com](https://www.nuxeo.com).
