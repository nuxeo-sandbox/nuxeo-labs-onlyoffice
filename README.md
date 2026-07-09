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
> Because the plugin deploys the converter, a lot of conversions use Onlyoffice (see [the contribution](/nuxeo-onlyoffice-core/src/main/resources/OSGI-INF/onlyoffice-conversion-contrib.xml)), typically the Word/Excel/OpenOffice/HTML/... -> PDF, which includes preview, typically. So, if, for example, you configure a Nuxeo application on your localhost without using Onlyoffice, see below: "Foce Using the "any2pdf" converter".

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

#### Foce Using the "any2pdf" Converter

"office2pdf" is always loaded _after_ "any2pdf", so it always used, by default, because Nuxeo loads all converters for given mime-types and use the _last_ one. ionce the plugin is deployed, the list for thes mime-types is ["any2pdf","office2pdf"] => "office2pdf" is always used.

To avoid that, copy this XML in your Studio project. It re-declare "any2pdf" making sure it is called _after_ "office2pdf". After this XML is deployed, the list becomes ["any2pdf","office2pdf","any2pdf"] => "any2pdf". This means the smae contribution is registered twice but it is a minor overhead.

```xml
<!--
==================================================================
To be used ONLY if you don't want to use the "office2pdf" Onlyoffice converter.
Re-register the platform "any2pdf" converter so it loads LAST
Do not use it (or disable it) in Studio if you _want_ to use the "office2pdf" converter
==================================================================
Typically:
- You are on localhost and did not deploy Onlyoffice
- You are using an Onlyoffice server that is on another EC2 instance and you don't want
  to send every file to this server (when displaying a preview etc.)


Why this exists:
* The plugin registers a converter office2pdf (OnlyOffice) that claims the same source
  mime types → application/pdf as the platform's any2pdf (LibreOffice).
* Automatic/preview conversion is mime-based, not name-based. getConverterName() returns
  the _last_ converter registered for a source → destination pair. Since the plugin loads
  after the platform, office2pdf wins and routes every "convert to PDF" through the
  OnlyOffice DS — bad when DS is absent (local dev) or remote (inefficient on AWS).
* There is no "disable" mechanism for converters
* The fix: Re-declare any2pdf (identical definition) so it registers last and reclaims the`
  mime pairs for LibreOffice.
* What's preserved: office2pdf stays registered and can still be called explicitly by name
  (e.g. Blob.Convert), since name-based calls bypass the mime table.

The <require> guarantees load order (do not rely on the implicit "Studio loads last" behavior).
-->
<require>nuxeo.labs.onlyoffice.conversion</require>

<extension target="org.nuxeo.ecm.core.convert.service.ConversionServiceImpl" point="converter">
<converter name="any2pdf" class="org.nuxeo.ecm.platform.convert.plugins.LibreOfficeConverter"
           bypassIfSameMimeType="true">
  <destinationMimeType>application/pdf</destinationMimeType>

  <sourceMimeType>application/pdf</sourceMimeType>
  <sourceMimeType>application/json</sourceMimeType>
  <sourceMimeType>text/xml</sourceMimeType>
  <sourceMimeType>text/html</sourceMimeType>
  <sourceMimeType>text/plain</sourceMimeType>
  <sourceMimeType>text/partial</sourceMimeType>
  <sourceMimeType>text/rtf</sourceMimeType>
  <sourceMimeType>application/rtf</sourceMimeType>
  <sourceMimeType>text/csv</sourceMimeType>
  <sourceMimeType>text/tsv</sourceMimeType>

  <!-- Microsoft office documents -->
  <sourceMimeType>application/msword</sourceMimeType>
  <sourceMimeType>application/vnd.ms-powerpoint</sourceMimeType>
  <sourceMimeType>application/vnd.ms-excel</sourceMimeType>

  <!-- Microsoft office 2007 documents -->
  <sourceMimeType>application/vnd.openxmlformats-officedocument.wordprocessingml.document</sourceMimeType>
  <sourceMimeType>application/vnd.openxmlformats-officedocument.presentationml.presentation</sourceMimeType>
  <sourceMimeType>application/vnd.openxmlformats-officedocument.spreadsheetml.sheet</sourceMimeType>

  <!-- OpenOffice.org 1.x documents -->
  <sourceMimeType>application/vnd.sun.xml.writer</sourceMimeType>
  <sourceMimeType>application/vnd.sun.xml.writer.template</sourceMimeType>
  <sourceMimeType>application/vnd.sun.xml.impress</sourceMimeType>
  <sourceMimeType>application/vnd.sun.xml.impress.template</sourceMimeType>
  <sourceMimeType>application/vnd.sun.xml.calc</sourceMimeType>
  <sourceMimeType>application/vnd.sun.xml.calc.template</sourceMimeType>
  <sourceMimeType>application/vnd.sun.xml.draw</sourceMimeType>
  <sourceMimeType>application/vnd.sun.xml.draw.template</sourceMimeType>

  <!-- OpenOffice.org 2.x documents -->
  <sourceMimeType>application/vnd.oasis.opendocument.spreadsheet</sourceMimeType>
  <sourceMimeType>application/vnd.oasis.opendocument.spreadsheet-template</sourceMimeType>
  <sourceMimeType>application/vnd.oasis.opendocument.text</sourceMimeType>
  <sourceMimeType>application/vnd.oasis.opendocument.text-template</sourceMimeType>
  <sourceMimeType>application/vnd.oasis.opendocument.presentation</sourceMimeType>
  <sourceMimeType>application/vnd.oasis.opendocument.presentation-template</sourceMimeType>
  <sourceMimeType>application/vnd.oasis.opendocument.graphics</sourceMimeType>
  <sourceMimeType>application/vnd.oasis.opendocument.graphics-template</sourceMimeType>

  <!-- WordPerfect -->
  <sourceMimeType>application/wordperfect</sourceMimeType>

  <parameters>
    <parameter name="CommandLineName">soffice</parameter>
    <parameter name="format">pdf</parameter>
  </parameters>
</converter>
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

