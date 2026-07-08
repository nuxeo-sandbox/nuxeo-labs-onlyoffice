# Nuxeo Labs ONLYOFFICE Integration

In-browser integration of Nuxeo Platform and [ONLYOFFICE](https://www.onlyoffice.com/).

## Dependencies

[ONLYOFFICE](https://www.onlyoffice.com/) Document Server (Run a [Docker](https://github.com/ONLYOFFICE/Docker-DocumentServer) image)

## ONLYOFFICE JWT is required

> [!IMPORTANT]
> This is a breaking change from version 10.10-SNAPSHOT (for LTS 2019)

ONLYOFFICE Document Server ≥ 7.x has JWT enabled by default, and this plugin signs every payload it sends (editor config, conversion request, blob download) and verifies the JWT on the save callback. Run the Document Server with JWT **enabled** and share the same secret with Nuxeo:

- On the Document Server: `JWT_ENABLED=true` and `JWT_SECRET=<your-secret>`.
- On Nuxeo: `nuxeo.labs.onlyoffice.jwt.secret=<the-same-secret>` (see [Configure](#configure-nuxeoconf)).

The secret on both sides **must match**, otherwise the editor fails to open documents (`errorCode: -20`, "The document security token is not correctly formed") and save callbacks are rejected with `401`.

JWT is on by default in the plugin (`nuxeo.labs.onlyoffice.jwt.enabled=true`). It can be turned off with `nuxeo.labs.onlyoffice.jwt.enabled=false` for a Document Server started with `JWT_ENABLED=false`, but this is **not recommended**.

## Configure (nuxeo.conf)

### General Properties:
* `nuxeo.url` is a **platform-wide** Nuxeo property and it must be set. (https://your.server.your.company.com/nuxeo. Do not forget the ending /nuxeo). Make sure it is correct, it is used used by ONLYOFFICE to download blobs and POST save callbacks.
* `nuxeo.labs.onlyoffice.url.api`: URL to editor api.js service (required). This is the full URL to the Onlyoffice server:
  `nuxeo.labs.onlyoffice.url.api={ONLYOFFICE_SERVER_URL}/web-apps/apps/api/documents/api.js`
* `nuxeo.labs.onlyoffice.url.nuxeo` is optional and _may be_ interesting in some context, like running everything on your localhost (not recommended, it is complex, requires changes in your know_hosts, etc.). Plugin not tested in this context. Default to `nuxeo.url`
* `nuxeo.labs.onlyoffice.version.save`: Create version on save (optional, default: `false`)

### JWT properties (see [ONLYOFFICE JWT is required](#onlyoffice-jwt-is-required)):
* `nuxeo.labs.onlyoffice.jwt.enabled`: Enable JWT signing/verification (optional, default: `true`)
* `nuxeo.labs.onlyoffice.jwt.secret`: Shared secret — MUST match the Document Server JWT_SECRET (required when enabled)
* `nuxeo.labs.onlyoffice.jwt.algorithm`: Signing algorithm: HS256 (default), HS384 or HS512. Must match the Document Server.
* `nuxeo.labs.onlyoffice.jwt.header`: HTTP header carrying the token (optional, default: Authorization)
* `nuxeo.labs.onlyoffice.jwt.prefix`: Token prefix (optional, default: "Bearer ")
* `nuxeo.labs.onlyoffice.jwt.ttl`: Token time-to-live in seconds (optional, default: 300)

### Conversion properties (Optional):

* `nuxeo.labs.onlyoffice.url.conversion`: URL to conversion service (see ONLYOFFICE docs)
  `nuxeo.labs.onlyoffice.url.conversion={ONLYOFFICE_SERVER_URL}/ConvertService.ashx`
* `nuxeo.labs.onlyoffice.conversion.wait`: Number of millisecond to wait between polling async request
  `nuxeo.labs.onlyoffice.conversion.wait=1000


### Example of Typical, Simple Configuration:

```
nuxeo.labs.onlyoffice.url.api=https://my-demo-acme-onlyoffice.cloud.nuxeo.com/web-apps/apps/api/documents/api.js
# Using nuxeo.url
#nuxeo.labs.onlyoffice.url.nuxeo=

nuxeo.labs.onlyoffice.url.conversion=https://my-demo-acme-onlyoffice.cloud.nuxeo.com/ConvertService.ashx
nuxeo.labs.onlyoffice.conversion.wait=1000

# JWT — MUST equalthe DS JWT_SECRET
nuxeo.labs.onlyoffice.jwt.enabled=true
nuxeo.labs.onlyoffice.jwt.secret=<the-secret>
# All other parameters => default values
```

## (Optional) Use Conversion Service

Invoke the conversion service to transform between a variety of content types.  By default, the [office2pdf contribution](/nuxeo-onlyoffice-core/src/main/resources/OSGI-INF/onlyoffice-conversion-contrib.xml) will support PDF as a destination type.  See the [ONLYOFFICE Conversion API](https://api.onlyoffice.com/editors/conversionapi) for a full conversion matrix.

> [!WARNING]
> Because the plugin deploys the converter, a lot of conversions use Onlyoffice (see [the contribution](/nuxeo-onlyoffice-core/src/main/resources/OSGI-INF/onlyoffice-conversion-contrib.xml)). So, if, for example, you configure a Nuxeo application on your localhost without using Onlyoffice, think about _not_ installing the plugin in this configuraiton, or all the conversions will fail.

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

### How to Build and Deploy

### Build and Deploy Locally

```bash
git clone https://github.com/nuxeo-sandbox/nuxeo-labs-onlyoffice
cd nuxeo-labs-onlyoffice
mvn clean install
```

To skip unit testing, add `-DskipTests`.

The [Marketplace package](https://connect.nuxeo.com/nuxeo/site/marketplace/package/nuxeo-labs-onlyoffice) available:

```
nuxeo-labs-baf-notification-package/target/nuxeo-labs-onlyoffice-package-{VERSION}.zip
```

Install it via `nuxeoctl`:

```bash
nuxeoctl mp-install /path/to/nuxeo-labs-onlyoffice-{VERSION}.zip
```

### Deploy from Nuxeo Marketplace

This plugin is available as a package on the [Nuxeo Marketplace](https://connect.nuxeo.com/nuxeo/site/marketplace), you can just:

```bash
nuxeoctl mp-install nuxeo-onlyoffice

```

## Support

**These features are not part of the Nuxeo Production platform.**

These solutions are provided for inspiration and we encourage customers to use them as code samples and learning resources.

This is a moving project (no API maintenance, no deprecation process, etc.) If any of these solutions are found to be useful for the Nuxeo Platform in general, they will be integrated directly into the platform, not maintained here.

## License

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

## About Nuxeo

Nuxeo Platform is an open source highly scalable, cloud-native, enterprise content management product with rich multimedia support, written in Java. Data can be stored in both SQL & NoSQL databases.

The development of the Nuxeo Platform is mostly done by Nuxeo employees with an open development model.

The source code, documentation, roadmap, issue tracker, testing, benchmarks are all public.

More information is available at [Hyland/Nuxeo](https://www.hyland.com/en/solutions/products/nuxeo-platform).

