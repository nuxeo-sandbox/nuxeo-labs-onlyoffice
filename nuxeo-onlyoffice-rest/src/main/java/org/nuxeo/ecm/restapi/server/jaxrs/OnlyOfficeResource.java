package org.nuxeo.ecm.restapi.server.jaxrs;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
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

/**
 * This web object implements the Callback functionality specified by the OnlyOffice API. See:
 * https://api.onlyoffice.com/editors/callback
 */
@WebObject(type = "onlyoffice")
@Consumes(MediaType.WILDCARD)
@Produces(MediaType.APPLICATION_JSON)
public class OnlyOfficeResource extends DefaultObject implements OnlyOfficeTypes {

    protected static final Logger LOG = LogManager.getLogger(OnlyOfficeResource.class);

    /**
     * OnlyOffice api.js URL
     */
    public static final String URL_API = "onlyoffice.url.api";

    /**
     * Create a new document version on save callback.
     */
    public static final String VERSION_ON_SAVE = "onlyoffice.version.save";

    static final String APP_NAME = "OnlyOffice";

    protected boolean versionOnSave = false;

    private static final ObjectReader CALLBACK;

    private static final AtomicBoolean CONNECTED = new AtomicBoolean(false);

    static {
        var mapper = new ObjectMapper();
        CALLBACK = mapper.readerFor(OnlyOfficeCallback.class);
    }

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
            LOG.warn("ONLYOFFICE api.js URL has not been set, please set `onlyoffice.url.api` in nuxeo.conf."
                    + "\n  onlyoffice.url.api=http://onlyoffice.host/web-apps/apps/api/documents/api.js");
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
     * @throws Exception
     */
    @POST
    @Path("callback/{id}/{xpath:((?:(?!/@).)*)}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response postCallback(@PathParam("id") String id, @PathParam("xpath") String xpath, InputStream input) {

        /*
         * (from https://api.onlyoffice.com/editors/callback) Defines the status of the document. Can have the following
         * values: 0 - no document with the key identifier could be found, 1 - document is being edited, 2 - document is
         * ready for saving, 3 - document saving error has occurred, 4 - document is closed with no changes, 6 -
         * document is being edited, but the current document state is saved, 7 - error has occurred while force saving
         * the document.
         */
        try {
            String json = IOUtils.toString(input, Charset.defaultCharset());
            OnlyOfficeCallback callback = CALLBACK.readValue(json);

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

    /**
     * Redirect to open an OnlyOffice editor (example: for use in an iFrame)
     *
     * @param id document ID
     * @param xpath blob path
     * @param mode view mode
     * @return redirect URL
     * @throws Exception
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

            String user = getContext().getPrincipal().getName();
            String type = getOfficeType(blob.getMimeType());

            if (type == null) {
                return Response.status(Status.UNSUPPORTED_MEDIA_TYPE)
                               .entity("mime-type is not supported: " + blob.getMimeType())
                               .build();
            }

            TokenAuthenticationService tokenSvc = Framework.getService(TokenAuthenticationService.class);
            String token = tokenSvc.acquireToken(user, APP_NAME, "editor", "Browser", "rw");

            String nuxeoUrl = Framework.getProperty("nuxeo.url", "http://localhost:8080/nuxeo/");
            if (!nuxeoUrl.endsWith("/")) {
                nuxeoUrl += "/";
            }

            UriBuilder redirect = UriBuilder.fromUri(nuxeoUrl + "ui/nuxeo-onlyoffice/onlyoffice-session.jsp")
                                            .queryParam("token", token)
                                            .queryParam("id", model.getId())
                                            .queryParam("mode", mode)
                                            .queryParam("user", user)
                                            .queryParam("xpath", xpath)
                                            .queryParam("fname", blob.getFilename())
                                            .queryParam("key", blob.getDigest())
                                            .queryParam("type", type);
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
