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
package org.nuxeo.ecm.restapi.server.onlyoffice;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriBuilder;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.automation.core.util.DocumentHelper;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.core.api.VersioningOption;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.versioning.VersioningService;
import org.nuxeo.ecm.core.schema.FacetNames;
import org.nuxeo.ecm.platform.mimetype.MimetypeNotFoundException;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeRegistry;
import org.nuxeo.ecm.tokenauth.service.TokenAuthenticationService;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.DefaultObject;
import org.nuxeo.runtime.api.Framework;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import nuxeo.labs.onlyoffice.jwt.OnlyOfficeJwt;
import nuxeo.labs.onlyoffice.jwt.OnlyOfficeJwtException;

/**
 * This web object implements the Callback functionality specified by the OnlyOffice API. See:
 * https://api.onlyoffice.com/editors/callback
 *
 * @since 2025.1
 */
@WebObject(type = "onlyoffice")
@Consumes(MediaType.WILDCARD)
@Produces(MediaType.APPLICATION_JSON)
public class OnlyOfficeResource extends DefaultObject implements OnlyOfficeTypes {

    protected static final Logger LOG = LogManager.getLogger(OnlyOfficeResource.class);

    /**
     * OnlyOffice api.js URL
     */
    public static final String URL_API = "nuxeo.labs.onlyoffice.url.api";

    /**
     * Create a new document version on save callback.
     */
    public static final String VERSION_ON_SAVE = "nuxeo.labs.onlyoffice.version.save";

    static final String APP_NAME = "OnlyOffice";

    protected boolean versionOnSave = false;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final ObjectReader CALLBACK = MAPPER.readerFor(OnlyOfficeCallback.class);

    private static final AtomicBoolean CONNECTED = new AtomicBoolean(false);

    /*
     * (non-Javadoc)
     * @see org.nuxeo.ecm.webengine.model.impl.AbstractResource#initialize(java.lang.Object[])
     */
    @Override
    protected void initialize(Object... args) {

        this.versionOnSave = "true".equalsIgnoreCase(Framework.getProperty(VERSION_ON_SAVE, "false"));

        // Obtain API URL
        String apiUrl = Framework.getProperty(URL_API);
        if (apiUrl == null || "".equals(apiUrl.trim())) {
            LOG.warn("ONLYOFFICE api.js URL has not been set, please set `nuxeo.labs.onlyoffice.url.api` in nuxeo.conf."
                    + "\n  nuxeo.labs.onlyoffice.url.api=http://onlyoffice.host/web-apps/apps/api/documents/api.js");
        }

        // Test client
        if (!CONNECTED.get()) {
            CONNECTED.set(testAPIConnection(apiUrl));
        }
    }

    private boolean testAPIConnection(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        var get = new HttpGet(url);
        get.setHeader("Accept", "application/javascript");
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build();
                CloseableHttpResponse response = httpClient.execute(get)) {
            int status = response.getStatusLine().getStatusCode();
            if (status < 200 || status >= 400) {
                LOG.warn("Unable to reach ONLYOFFICE API: {} {}", status, response.getStatusLine().getReasonPhrase());
                return false;
            }
            LOG.info("Connected to ONLYOFFICE Editor [{}] ({})", url, status);
            return true;
        } catch (Exception ex) {
            LOG.warn("Error connecting to ONLYOFFICE API", ex);
            return false;
        }
    }

    /**
     * Callback endpoint for OnlyOffice
     *
     * @param id document ID
     * @param xpath blob path
     * @param input input stream JSON
     * @return save status
     */
    @POST
    @Path("callback/{id}/{xpath:((?:(?!/@).)*)}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response postCallback(@PathParam("id") String id, @PathParam("xpath") String xpath,
            @Context HttpHeaders headers, InputStream input) {

        /*
         * (from https://api.onlyoffice.com/editors/callback) Defines the status of the document. Can have the following
         * values: 0 - no document with the key identifier could be found, 1 - document is being edited, 2 - document is
         * ready for saving, 3 - document saving error has occurred, 4 - document is closed with no changes, 6 -
         * document is being edited, but the current document state is saved, 7 - error has occurred while force saving
         * the document.
         */
        try {
            String json = IOUtils.toString(input, StandardCharsets.UTF_8);
            OnlyOfficeCallback callback = CALLBACK.readValue(json);

            // Verify the ONLYOFFICE JWT before touching any document. In 7.1+
            // tokenRequiredParams mode the token payload is the authoritative
            // callback, so rebuild the DTO from the verified claims.
            if (OnlyOfficeJwt.isEnabled()) {
                String authHeader = headers.getHeaderString(OnlyOfficeJwt.headerName());
                Map<String, Object> claims = OnlyOfficeJwt.verify(callback.getToken(), authHeader);
                callback = CALLBACK.readValue(MAPPER.writeValueAsString(claims));
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("JSON: {}", json);
                LOG.debug(callback.toString());
            }

            LOG.debug(callback.toString());

            int status = callback.getStatus();
            if (status >= 1 && status <= 6) {
                CoreSession session = getContext().getCoreSession();
                DocumentModel model = session.getDocument(new IdRef(id));

                // Record editors for current document
                List<String> editors = callback.getUsers();
                if (!model.hasFacet("onlyoffice")) {
                    model.addFacet("onlyoffice");
                }

                if (callback.getUrl() != null) {
                    Blob original = (Blob) model.getPropertyValue(xpath);
                    if (original == null) {
                        BlobHolder bh = model.getAdapter(BlobHolder.class);
                        if (bh != null) {
                            original = bh.getBlob();
                        }
                    }

                    // Determine the final filename + mime for the incoming blob.
                    // OnlyOffice may return a different extension than the source
                    // (typical case: `.doc` opened, saved back as `.docx` because
                    // `assemblyFormatAsOrigin` failed for a legacy format). The
                    // `filetype` callback field, when present, is authoritative.
                    String targetFilename = original.getFilename();
                    String targetMime = original.getMimeType();
                    String callbackExt = callback.getFileType();
                    if (StringUtils.isNotBlank(callbackExt)) {
                        callbackExt = callbackExt.toLowerCase();
                        String currentExt = extensionOf(targetFilename);
                        if (!callbackExt.equals(currentExt)) {
                            targetFilename = replaceExtension(targetFilename, callbackExt);
                            targetMime = resolveMime(callbackExt, targetMime);
                            LOG.info("OnlyOffice returned a different filetype for {}: {} -> {} (mime: {})",
                                    original.getFilename(), currentExt, callbackExt, targetMime);
                        }
                    }

                    var get = new HttpGet(callback.getUrl());
                    get.setHeader("Accept", MediaType.WILDCARD);
                    // ONLYOFFICE verifies the token in the header for GET requests.
                    if (OnlyOfficeJwt.isEnabled()) {
                        get.setHeader(OnlyOfficeJwt.headerName(),
                                OnlyOfficeJwt.authorizationHeaderValue(Map.of("url", callback.getUrl())));
                    }
                    Blob saved;
                    try (CloseableHttpClient httpClient = HttpClientBuilder.create().build();
                            CloseableHttpResponse response = httpClient.execute(get);
                            InputStream stream = response.getEntity().getContent()) {
                        saved = Blobs.createBlob(stream, targetMime, original.getEncoding());
                        saved.setFilename(targetFilename);
                    }

                    DocumentHelper.addBlob(model.getProperty(xpath), saved);

                    // Status is 2 or 3
                    if (status < 4 && this.versionOnSave) {
                        saveVersion(model, callback.getStatus() == 2);
                        // Clear editors on save
                        editors = null;
                    }
                }

                model.setProperty("onlyoffice", "editors", editors);

                // Remove lock obtained on edit request
                // Don't unlock on status 6
                if (status >= 2 && status < 6 && model.isLocked()) {
                    model.removeLock();
                }

                session.saveDocument(model);
                session.save();
            }
        } catch (OnlyOfficeJwtException jwtEx) {
            LOG.warn("ONLYOFFICE callback JWT verification failed", jwtEx);
            return Response.status(Status.UNAUTHORIZED).entity("{\"error\":1}").build();
        } catch (IOException iox) {
            LOG.error("Error saving ONLYOFFICE callback", iox);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("{\"error\":1}").build();
        }

        return Response.status(Status.OK).entity("{\"error\":0}").build();
    }

    private DocumentModel saveVersion(DocumentModel doc, boolean major) {
        if (!doc.hasFacet(FacetNames.VERSIONABLE)) {
            LOG.warn("Unable to save version for OnlyOffice document: '{}'", doc.getId());
            return doc;
        }

        VersioningOption vo = major ? VersioningOption.MAJOR : VersioningOption.MINOR;
        doc.putContextData(VersioningService.VERSIONING_OPTION, vo);
        return doc;
    }

    /**
     * Returns the lowercase extension of the given filename (without the dot), or {@code null} if the filename has no
     * extension.
     */
    private static String extensionOf(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1).toLowerCase();
    }

    /**
     * Replaces the extension of {@code filename} with {@code newExtension} (which must not contain a leading dot).
     * If {@code filename} has no extension, {@code newExtension} is appended.
     */
    private static String replaceExtension(String filename, String newExtension) {
        if (filename == null || filename.isBlank()) {
            return "document." + newExtension;
        }
        int dot = filename.lastIndexOf('.');
        String base = (dot < 0) ? filename : filename.substring(0, dot);
        return base + "." + newExtension;
    }

    /**
     * Resolves a mime-type for the given extension via {@link MimetypeRegistry}. Falls back to {@code fallback} (the
     * source blob's mime-type) if the extension is unknown or the registry is unavailable.
     */
    private static String resolveMime(String extension, String fallback) {
        try {
            MimetypeRegistry registry = Framework.getService(MimetypeRegistry.class);
            if (registry != null) {
                return registry.getMimetypeFromExtension(extension);
            }
        } catch (MimetypeNotFoundException mnfe) {
            LOG.warn("No mime-type registered for extension '.{}', falling back to '{}'", extension, fallback);
        } catch (Exception ex) {
            LOG.warn("Error resolving mime-type for extension '.{}', falling back to '{}'", extension, fallback, ex);
        }
        return fallback;
    }

    private String getOfficeType(String mime) {
        if (TEXT_TYPES.contains(mime)) {
            return TEXT;
        } else if (PRESENTATION_TYPES.contains(mime)) {
            return PRESENTATION;
        } else if (SPREADSHEET_TYPES.contains(mime)) {
            return SPREADSHEET;
        }
        return null;
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) {
            return null;
        }
        url = url.trim();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * Builds the ONLYOFFICE editor config (as a JSON object), signed with a JWT when JWT is enabled.
     * <p>
     * The config is assembled server-side (rather than in the JSP) because the JWT must be signed with the shared
     * secret, which must never reach the browser. The document/callback URLs use the internal (container-facing) base
     * ({@code nuxeo.labs.onlyoffice.url.nuxeo}, falling back to {@code nuxeo.url}); the "Open in Nuxeo" share link uses the public
     * base ({@code nuxeo.url}). Client-only fields ({@code type}, {@code events}) are added by the JSP.
     *
     * @param id document ID
     * @param xpath blob xpath
     * @param mode editor mode ({@code desktop} / {@code embedded})
     * @return the signed config JSON
     */
    @GET
    @Path("config/{id}/{xpath:((?:(?!/@).)*)}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConfig(@PathParam("id") String id, @PathParam("xpath") String xpath,
            @QueryParam("mode") String mode) {

        if (StringUtils.isBlank(id)) {
            return Response.status(Status.NOT_FOUND).build();
        }
        if (StringUtils.isBlank(xpath)) {
            xpath = "file:content";
        }
        if (StringUtils.isBlank(mode)) {
            mode = "desktop";
        }

        try {
            CoreSession session = getContext().getCoreSession();
            DocumentModel model = session.getDocument(new IdRef(id));

            Blob blob = (Blob) model.getPropertyValue(xpath);
            if (blob == null) {
                BlobHolder bh = model.getAdapter(BlobHolder.class);
                if (bh != null) {
                    blob = bh.getBlob();
                }
            }
            if (blob == null) {
                LOG.warn("Blob not found for OnlyOffice editor: {}/{}", id, xpath);
                return Response.status(Status.NOT_FOUND).build();
            }

            String documentType = getOfficeType(blob.getMimeType());
            if (documentType == null) {
                return Response.status(Status.UNSUPPORTED_MEDIA_TYPE)
                               .entity("mime-type is not supported: " + blob.getMimeType())
                               .build();
            }

            String user = getContext().getPrincipal().getName();
            TokenAuthenticationService tokenSvc = Framework.getService(TokenAuthenticationService.class);
            String token = tokenSvc.acquireToken(user, APP_NAME, "editor", "Browser", "rw");

            // Public base = browser-facing (nuxeo.url); internal base =
            // container-facing (nuxeo.labs.onlyoffice.url.nuxeo, falling back to nuxeo.url).
            String publicBase = stripTrailingSlash(Framework.getProperty("nuxeo.url", "http://localhost:8080/nuxeo/"));
            String internalBase = stripTrailingSlash(Framework.getProperty("nuxeo.labs.onlyoffice.url.nuxeo"));
            if (internalBase == null) {
                internalBase = publicBase;
            }

            String fname = blob.getFilename();
            String fileType = extensionOf(fname);
            boolean writable = !"embedded".equals(mode);

            String blobUrl = internalBase + "/nxfile/default/" + id + "/" + xpath + "/" + fname + "?inline=true&token="
                    + token;
            String callbackUrl = internalBase + "/api/v1/onlyoffice/callback/" + id + "/" + xpath + "?token=" + token;
            String shareUrl = publicBase + "/ui/#!/doc/" + id;

            Map<String, Object> permissions = new LinkedHashMap<>();
            permissions.put("chat", writable);
            permissions.put("comment", writable);
            permissions.put("edit", writable);
            permissions.put("review", writable);
            permissions.put("print", true);
            permissions.put("download", true);

            Map<String, Object> document = new LinkedHashMap<>();
            document.put("fileType", fileType);
            document.put("key", blob.getDigest());
            document.put("permissions", permissions);
            document.put("title", fname);
            document.put("url", blobUrl);

            Map<String, Object> customization = new LinkedHashMap<>();
            customization.put("autosave", false);
            customization.put("compactToolbar", false);
            customization.put("forcesave", false);
            customization.put("showReviewChanges", false);
            customization.put("zoom", 100);

            Map<String, Object> embedded = new LinkedHashMap<>();
            embedded.put("saveUrl", blobUrl);
            embedded.put("shareUrl", shareUrl);
            embedded.put("toolbarDocked", "top");

            Map<String, Object> editorUser = new LinkedHashMap<>();
            editorUser.put("id", user);
            editorUser.put("name", user);

            Map<String, Object> editorConfig = new LinkedHashMap<>();
            editorConfig.put("callbackUrl", callbackUrl);
            editorConfig.put("customization", customization);
            editorConfig.put("embedded", embedded);
            editorConfig.put("lang", "en-US");
            editorConfig.put("mode", "edit");
            editorConfig.put("user", editorUser);

            Map<String, Object> config = new LinkedHashMap<>();
            config.put("documentType", documentType);
            config.put("document", document);
            config.put("editorConfig", editorConfig);
            config.put("height", "100%");
            config.put("width", "100%");

            // Sign the config (document + editorConfig, per the ONLYOFFICE spec) and
            // embed the JWS as the top-level `token` field.
            if (OnlyOfficeJwt.isEnabled()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("document", document);
                payload.put("editorConfig", editorConfig);
                config.put("token", OnlyOfficeJwt.sign(payload));
            }

            return Response.ok(MAPPER.writeValueAsString(config)).build();
        } catch (DocumentNotFoundException | PropertyException pex) {
            LOG.warn("Error building OnlyOffice config", pex);
            return Response.status(Status.NOT_FOUND).build();
        } catch (OnlyOfficeJwtException jwtEx) {
            LOG.error("Cannot sign OnlyOffice config (check nuxeo.labs.onlyoffice.jwt.* settings)", jwtEx);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        } catch (IOException iox) {
            LOG.error("Error serializing OnlyOffice config", iox);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Redirect to open an OnlyOffice editor (example: for use in an iFrame)
     *
     * @param id document ID
     * @param xpath blob path
     * @param mode view mode
     * @return redirect URL
     */
    @GET
    @Path("editor/{id}/{xpath:((?:(?!/@).)*)}")
    public Response sendRedirect(@PathParam("id") String id, @PathParam("xpath") String xpath,
            @QueryParam("mode") String mode) {

        if (StringUtils.isBlank(id)) {
            return Response.status(Status.NOT_FOUND).build();
        }

        if (StringUtils.isBlank(xpath)) {
            xpath = "file:content";
        }

        if (StringUtils.isBlank(mode)) {
            mode = "desktop";
        }

        try {
            CoreSession session = getContext().getCoreSession();
            DocumentModel model = session.getDocument(new IdRef(id));

            Blob blob = (Blob) model.getPropertyValue(xpath);
            if (blob == null) {
                BlobHolder bh = model.getAdapter(BlobHolder.class);
                if (bh != null) {
                    blob = bh.getBlob();
                }
            }

            if (blob == null) {
                LOG.warn("Blob not found for OnlyOffice editor: {}/{}", id, xpath);
                return Response.status(Status.NOT_FOUND).build();
            }

            String type = getOfficeType(blob.getMimeType());

            if (type == null) {
                return Response.status(Status.UNSUPPORTED_MEDIA_TYPE)
                               .entity("mime-type is not supported: " + blob.getMimeType())
                               .build();
            }

            // Redirect (browser-facing) to the editor bootstrap JSP. The JSP fetches
            // the fully-built, JWT-signed config from `getConfig` below; no token is
            // minted here anymore (it is minted server-side when the config is built).
            String nuxeoUrl = Framework.getProperty("nuxeo.url", "http://localhost:8080/nuxeo/");
            if (!nuxeoUrl.endsWith("/")) {
                nuxeoUrl += "/";
            }

            UriBuilder redirect = UriBuilder.fromUri(nuxeoUrl + "ui/nuxeo-onlyoffice/onlyoffice-session.jsp")
                                            .queryParam("id", model.getId())
                                            .queryParam("mode", mode)
                                            .queryParam("xpath", xpath);
            URI location = redirect.build();

            return Response.temporaryRedirect(location).build();
        } catch (DocumentNotFoundException | PropertyException pex) {
            LOG.warn("Error performing redirect", pex);
            return Response.status(Status.NOT_FOUND).build();
        } catch (Exception ex) {
            LOG.error("Unexpected error with redirect", ex);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

}
