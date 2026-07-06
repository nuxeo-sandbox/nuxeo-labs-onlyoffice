# Nuxeo ONLYOFFICE Integration

[![Build Status](https://qa.nuxeo.org/jenkins/buildStatus/icon?job=Sandbox/sandbox_nuxeo-onlyoffice-master)](https://qa.nuxeo.org/jenkins/view/Sandbox/job/Sandbox/job/sandbox_nuxeo-onlyoffice-master/)

In-browser integration of Nuxeo Platform and [ONLYOFFICE](https://www.onlyoffice.com/).

> [!IMPORTANT]
> Master branch is **Work in Progress** for upgrade to LTS2025. Do not use it as is.

## Dependencies

[ONLYOFFICE](https://www.onlyoffice.com/) Document Server (Run a [Docker](https://github.com/ONLYOFFICE/Docker-DocumentServer) image)

### ONLYOFFICE JWT must be disabled

This version of the plugin does **not** sign the editor config with JWT and does **not** verify JWT on the save callback. Since ONLYOFFICE Document Server ≥ 7.x has JWT enabled by default, the editor will fail to open documents with the error `errorCode: -20` ("The document security token is not correctly formed") unless JWT is disabled on the server side.

When starting the ONLYOFFICE Docker container, explicitly set `JWT_ENABLED=false`:

```
docker run -i -t -d -p 80:80 \
    -e JWT_ENABLED=false \
    onlyoffice/documentserver
```

For a non-Docker install, set `services.CoAuthoring.token.enable.browser`, `services.CoAuthoring.token.enable.request.inbox` and `services.CoAuthoring.token.enable.request.outbox` to `false` in the server's `local.json`.

Proper JWT support is on the roadmap.

## Build and Install

Build with maven (at least 3.3)

```
mvn clean install
```
> Package built here: `nuxeo-onlyoffice-package/target`

> Install with `nuxeoctl mp-install <package>`

## Configure (nuxeo.conf)

The `nuxeo.url` property must be the fully qualified URL for your nuxeo instance, such as `https://my.nuxeo.org/nuxeo`

Editor properties:

```
# URL to editor api.js service (required)
onlyoffice.url.api=http://onlyoffice/web-apps/apps/api/documents/api.js
# Create version on save (optional, default: false)
onlyoffice.version.save=true|false
```

Conversion properties (Optional):

```
# URL to conversion service (see ONLYOFFICE docs)
onlyoffice.url.conversion=http://onlyoffice/ConvertService.ashx
# Number of millisecond to wait between polling async request
onlyoffice.conversion.wait=1000
```

> (!) When using Docker, use an accessible hostname for the IP URLs.

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
