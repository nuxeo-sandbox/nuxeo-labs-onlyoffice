/*
 * (C) Copyright 2025 Hyland (http://hyland.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Damon Brown
 *     Thibaud Arguillere
 *       (Migration to LTS 2025 with the help of OpenCode / Claude Opus)
 */
package nuxeo.labs.onlyoffice.conversion;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.convert.api.ConversionException;
import org.nuxeo.ecm.core.convert.api.ConverterCheckResult;
import org.nuxeo.ecm.core.convert.extension.ConverterDescriptor;
import org.nuxeo.ecm.core.convert.extension.ExternalConverter;
import org.nuxeo.ecm.core.io.download.DownloadService;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeRegistry;
import org.nuxeo.ecm.tokenauth.service.TokenAuthenticationService;
import org.nuxeo.runtime.api.Framework;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import nuxeo.labs.onlyoffice.jwt.OnlyOfficeJwt;

/**
 * {@link ExternalConverter} ({@code office2pdf}) that delegates document conversion to the ONLYOFFICE Document Server.
 *
 * @since 2025.1
 */
public class OnlyOfficeConverter implements ExternalConverter {

    private static final Logger LOG = LogManager.getLogger(OnlyOfficeConverter.class);

    /**
     * OnlyOffice conversion URL
     */
    public static final String CONV_URL = "nuxeo.labs.onlyoffice.url.conversion";

    /**
     * OnlyOffice conversion wait
     */
    public static final String CONV_WAIT = "nuxeo.labs.onlyoffice.conversion.wait";

    public static final String CONV_PARAM_ASYNC = "async";

    public static final String CONV_PARAM_SRC_TYPE = "srcType";

    public static final String CONV_PARAM_DEST_TYPE = "destType";

    public static final String CONV_PARAM_CODE_PAGE = "codePage";

    public static final String CONV_PARAM_DELIMITER = "delimiter";

    public static final String CONV_PARAM_THUMBNAIL = "thumbnail";

    private ConverterDescriptor descriptor = null;

    private MimetypeRegistry mimeTypeRegistry = null;

    private DownloadService downloadService = null;

    private String endpoint = null;

    private long waitTime = 1000L;

    private ObjectWriter requestWriter = null;

    private ObjectReader responseReader = null;

    private ObjectMapper mapper = null;

    private List<ConversionCompatibility> compat = null;

    public OnlyOfficeConverter() {
        super();
    }

    @Override
    public void init(ConverterDescriptor descriptor) {
        this.mimeTypeRegistry = Framework.getService(MimetypeRegistry.class);
        this.downloadService = Framework.getService(DownloadService.class);

        this.descriptor = descriptor;
        this.endpoint = Framework.getProperty(CONV_URL);
        try {
            this.waitTime = Long.parseLong(Framework.getProperty(CONV_WAIT, "1000"));
        } catch (NumberFormatException nfe) {
            LOG.warn("Async Wait Time setting is invalid, ignoring: {}", Framework.getProperty(CONV_WAIT), nfe);
        }

        var mapper = new ObjectMapper();
        this.mapper = mapper;
        this.requestWriter = mapper.writerFor(ConversionRequest.class);
        this.responseReader = mapper.readerFor(ConversionResponse.class);

        try (InputStream in = getClass().getResourceAsStream("/reference/conversion_matrix.json")) {
            this.compat = mapper.readValue(in, new TypeReference<List<ConversionCompatibility>>() {
            });
        } catch (IOException iox) {
            LOG.warn("Unable to load conversion compatibility matrix", iox);
        }
    }

    @Override
    public BlobHolder convert(BlobHolder blobHolder, Map<String, Serializable> parameters) throws ConversionException {
        Blob originalBlob = blobHolder.getBlob();
        String path = blobHolder.getFilePath();

        String srcType = findType(originalBlob.getMimeType(), originalBlob, parameters.get(CONV_PARAM_SRC_TYPE));
        String destType = findType(this.descriptor.getDestinationMimeType(), null, parameters.get(CONV_PARAM_DEST_TYPE));

        // Check compatibility
        boolean compatConversion = false;
        for (ConversionCompatibility cc : this.compat) {
            if (cc.accepts(srcType, destType)) {
                compatConversion = true;
                break;
            }
        }
        if (!compatConversion) {
            String errMsg = String.format("Incompatible conversion: %s -> %s for blob %s", srcType, destType,
                    originalBlob.getFilename());
            LOG.error(errMsg);
            throw new ConversionException(errMsg);
        }

        TokenAuthenticationService tokens = Framework.getService(TokenAuthenticationService.class);
        String token = null;

        Blob conversion;
        try {
            // Prepare blob
            String storeKey = this.downloadService.storeBlobs(Collections.singletonList(originalBlob));
            token = tokens.acquireToken("Administrator", "ONLYOFFICE", storeKey, "ONLYOFFICE Conversion Service", "rw");

            String nuxeoUrl = Framework.getProperty("nuxeo.url");
            String url = nuxeoUrl + "/" + this.downloadService.getDownloadUrl(storeKey) + "?token=" + token;

            // Create request
            var request = new ConversionRequest();
            request.setAsync("true".equals(getParam(parameters, CONV_PARAM_ASYNC, "true")));
            request.setKey(storeKey);

            request.setFileType(srcType);

            request.setOutputType(destType);
            request.setTitle(newFilename(originalBlob.getFilename(), destType));
            request.setUrl(url);

            if ("csv".equals(srcType)) {
                handleCodePage(request, parameters);
                handleDelimeter(request, parameters);
            } else if ("txt".equals(srcType)) {
                handleCodePage(request, parameters);
            }

            if ("bmp".equals(destType) || "gif".equals(destType) || "jpg".equals(destType) || "png".equals(destType)) {
                if (parameters.containsKey("thumbnail")) {
                    handleThumbnail(request, parameters);
                }
            }

            LOG.debug("{}", request);

            conversion = convert(request);
        } catch (IOException | InterruptedException | ConversionException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ConversionException("Cannot convert " + path + " to " + this.descriptor.getDestinationMimeType(),
                    e);
        } finally {
            if (token != null) {
                tokens.revokeToken(token);
            }
        }
        return new SimpleBlobHolder(conversion);
    }

    private void handleDelimeter(ConversionRequest request, Map<String, Serializable> parameters) {
        Serializable value = parameters.get(CONV_PARAM_DELIMITER);
        int delimiter = ConversionConstants.DELIMITER_COMMA;
        if (value != null) {
            if (";".equals(value)) {
                delimiter = ConversionConstants.DELIMITER_SEMICOLON;
            } else if (":".equals(value)) {
                delimiter = ConversionConstants.DELIMITER_COLON;
            } else {
                try {
                    delimiter = Integer.parseInt(value.toString());
                    if (delimiter < 0 || delimiter > 5) {
                        delimiter = ConversionConstants.DELIMITER_COMMA;
                        LOG.warn("Delimiter setting is invalid, ignoring: {}", value);
                    }
                } catch (NumberFormatException nfe) {
                    LOG.warn("Delimiter setting is invalid, ignoring: {}", value, nfe);
                }
            }
        }
        request.setDelimiter(delimiter);
    }

    private void handleCodePage(ConversionRequest request, Map<String, Serializable> parameters) {
        Serializable value = parameters.get(CONV_PARAM_CODE_PAGE);
        int code = ConversionConstants.CODE_UNICODE;
        if (value != null) {
            // TODO: Check reference table
            try {
                code = Integer.parseInt(value.toString());
            } catch (NumberFormatException nfe) {
                LOG.warn("Code page setting is invalid, ignoring: {}", value, nfe);
            }
        }
        request.setCodePage(code);
    }

    private void handleThumbnail(ConversionRequest request, Map<String, Serializable> parameters) {
        Serializable value = parameters.get(CONV_PARAM_THUMBNAIL);
        if (!"false".equals(value)) {
            return;
        }
        ConversionRequest.Thumbnail thumb = request.createThumbnail();
        if (value != null) {
            if ("true".equals(value)) {
                return;
            }
            String[] size = value.toString().split(":");
            try {
                if (size.length > 0) {
                    thumb.setHeight(Integer.parseInt(size[0]));
                }
                if (size.length > 1) {
                    thumb.setWidth(Integer.parseInt(size[1]));
                } else if (size.length > 0) {
                    thumb.setWidth(thumb.getHeight());
                }
                if (size.length > 2) {
                    thumb.setAspect(Integer.parseInt(size[2]));
                }
            } catch (NumberFormatException nfe) {
                LOG.warn("Height/Width/Aspect setting for thumbnail is not specific, ignoring: {}", value, nfe);
            }
        }
    }

    private String getParam(Map<String, Serializable> parameters, String key, String defVal) {
        if (parameters.containsKey(key)) {
            return String.valueOf(parameters.get(key));
        }
        return defVal;
    }

    private String findType(String mimeType, Blob blob, Serializable param) {
        if (mimeType == null && blob != null) {
            mimeType = blob.getMimeType();
            if (mimeType == null) {
                // Get from file
                mimeType = this.mimeTypeRegistry.getMimetypeFromBlob(blob);
            }
        }

        if (mimeType != null) {
            List<String> types = this.mimeTypeRegistry.getExtensionsFromMimetypeName(mimeType);
            if (!types.isEmpty()) {
                return types.get(0);
            }
        } else if (param != null) {
            return param.toString();
        }

        throw new ConversionException("Unable to locate extension for type: " + mimeType);
    }

    private String newFilename(String original, String ext) {
        if (original == null) {
            return UUID.randomUUID().toString() + "." + ext;
        }
        int idx = original.lastIndexOf('.');
        if (idx > 0) {
            return original.substring(0, idx) + ext;
        } else {
            return original + "." + ext;
        }
    }

    @Override
    public ConverterCheckResult isConverterAvailable() {
        if (this.endpoint == null || "".equals(this.endpoint.trim())) {
            String err = "Please configure `nuxeo.labs.onlyoffice.url.conversion` to enable conversion service.";
            LOG.warn("ONLYOFFICE conversion disabled: {}", err);
            return new ConverterCheckResult("ONLYOFFICE installation required", err);
        }
        return new ConverterCheckResult();
    }

    private Blob convert(ConversionRequest request) throws IOException, InterruptedException {
        // Request string remains the same for the life of the conversion
        String json = this.requestWriter.writeValueAsString(request);

        boolean running = true;
        ConversionResponse response = null;

        // Process request
        while (running) {
            // Ask for conversion / ask for update
            response = submitRequest(json);

            // Break loop if complete/error
            if (response.isFinished()) {
                running = false;
                break;
            }

            // Wait a bit...
            LOG.debug("Waiting for {}", request);
            Thread.sleep(this.waitTime);
        }

        // Handle response
        LOG.debug("{}", response);
        if (response.isError()) {
            // Report error and exit
            return null;
        } else {
            // Retrieve content
            return retrieveResponse(response);
        }
    }

    private ConversionResponse submitRequest(String request) throws IOException {
        var post = new HttpPost(this.endpoint);
        post.setHeader("Accept", "application/json");

        // When JWT is enabled, ONLYOFFICE (7.1+) expects the token in the body for
        // POST requests: sign the request payload, add it back as a `token` field,
        // and also set the header token (some Document Server configs require both).
        String body = request;
        if (OnlyOfficeJwt.isEnabled()) {
            Map<String, Object> payload = this.mapper.readValue(request, new TypeReference<Map<String, Object>>() {
            });
            String jwt = OnlyOfficeJwt.sign(payload);
            payload.put("token", jwt);
            body = this.mapper.writeValueAsString(payload);
            post.setHeader(OnlyOfficeJwt.headerName(), OnlyOfficeJwt.headerPrefix() + jwt);
        }

        post.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build();
                CloseableHttpResponse response = httpClient.execute(post);
                InputStream content = response.getEntity().getContent()) {
            return this.responseReader.readValue(content);
        }
    }

    private Blob retrieveResponse(ConversionResponse response) throws IOException {
        var get = new HttpGet(response.getFileUrl());
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build();
                CloseableHttpResponse httpResponse = httpClient.execute(get);
                InputStream data = httpResponse.getEntity().getContent()) {
            return Blobs.createBlob(data);
        }
    }

}
