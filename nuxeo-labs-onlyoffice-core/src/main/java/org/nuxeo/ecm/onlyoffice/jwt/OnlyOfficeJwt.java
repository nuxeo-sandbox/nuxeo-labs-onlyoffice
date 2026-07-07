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
 */
package org.nuxeo.ecm.onlyoffice.jwt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.runtime.api.Framework;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Helper for the ONLYOFFICE JWT (JSON Web Token) protocol.
 * <p>
 * When JWT is enabled on the Document Server ({@code JWT_ENABLED=true}), every payload exchanged with ONLYOFFICE must be
 * signed / verified with a shared secret. This helper signs the payloads Nuxeo sends (editor config, conversion request,
 * blob download) and verifies the payloads ONLYOFFICE sends back (save callback).
 * <p>
 * We use {@code com.auth0:java-jwt} directly rather than Nuxeo's {@code JWTService} because the latter is hard-coded to
 * HS512 and requires a logged-in {@code NuxeoPrincipal}, whereas ONLYOFFICE defaults to HS256 and the callback handler
 * runs unauthenticated until the token is verified.
 *
 * @since 2025.1
 */
public final class OnlyOfficeJwt {

    private static final Logger LOG = LogManager.getLogger(OnlyOfficeJwt.class);

    /** Master switch. Defaults to {@code true} — JWT is required by default. */
    public static final String JWT_ENABLED = "onlyoffice.jwt.enabled";

    /** Shared HMAC secret. Must match the Document Server {@code JWT_SECRET}. */
    public static final String JWT_SECRET = "onlyoffice.jwt.secret";

    /** Signing algorithm: {@code HS256} (default), {@code HS384} or {@code HS512}. */
    public static final String JWT_ALGORITHM = "onlyoffice.jwt.algorithm";

    /** HTTP header carrying the token for header-based requests. Defaults to {@code Authorization}. */
    public static final String JWT_HEADER = "onlyoffice.jwt.header";

    /** Prefix prepended to (and stripped from) the header token. Defaults to {@code "Bearer "}. */
    public static final String JWT_PREFIX = "onlyoffice.jwt.prefix";

    /** Token time-to-live in seconds. Defaults to {@code 300}. */
    public static final String JWT_TTL = "onlyoffice.jwt.ttl";

    public static final String DEFAULT_ALGORITHM = "HS256";

    public static final String DEFAULT_HEADER = "Authorization";

    public static final String DEFAULT_PREFIX = "Bearer ";

    public static final int DEFAULT_TTL = 300;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OnlyOfficeJwt() {
        // Utility class
    }

    /**
     * Returns {@code true} unless {@code onlyoffice.jwt.enabled} is explicitly set to {@code false}. When enabled but no
     * secret is configured, {@link #sign} / {@link #verify} throw — the misconfiguration is surfaced loudly rather than
     * silently disabling security.
     */
    public static boolean isEnabled() {
        return !"false".equalsIgnoreCase(Framework.getProperty(JWT_ENABLED, "true"));
    }

    public static String headerName() {
        return Framework.getProperty(JWT_HEADER, DEFAULT_HEADER);
    }

    public static String headerPrefix() {
        return Framework.getProperty(JWT_PREFIX, DEFAULT_PREFIX);
    }

    /**
     * Signs the given claims and returns the compact JWS. Adds {@code iat} + {@code exp}.
     *
     * @throws OnlyOfficeJwtException if the secret is missing or the algorithm is unsupported
     */
    public static String sign(Map<String, Object> claims) {
        Algorithm algorithm = algorithm();
        Instant now = Instant.now();
        return JWT.create()
                  .withPayload(claims)
                  .withIssuedAt(now)
                  .withExpiresAt(now.plusSeconds(ttl()))
                  .sign(algorithm);
    }

    /**
     * Signs the given claims and returns the value for the configured header, i.e. {@code "<prefix><jws>"} (default
     * {@code "Bearer <jws>"}).
     */
    public static String authorizationHeaderValue(Map<String, Object> claims) {
        return headerPrefix() + sign(claims);
    }

    /**
     * Verifies an ONLYOFFICE callback token and returns its decoded payload as a map.
     * <p>
     * The token is looked up in the request body first (the {@code token} JSON field, which is the ONLYOFFICE 7.1+
     * default for POST requests), then in the configured header. The returned map is the authoritative payload: in
     * {@code tokenRequiredParams=true} mode the whole callback is inside the token and the outer JSON must be ignored.
     *
     * @param bodyToken the {@code token} field from the callback JSON body, may be {@code null}
     * @param headerValue the raw value of the configured header, may be {@code null}
     * @return the decoded, verified payload
     * @throws OnlyOfficeJwtException if no token is present or verification fails
     */
    public static Map<String, Object> verify(String bodyToken, String headerValue) {
        String token = bodyToken;
        if (isBlank(token) && !isBlank(headerValue)) {
            String prefix = headerPrefix();
            token = headerValue.startsWith(prefix) ? headerValue.substring(prefix.length()) : headerValue;
        }
        if (isBlank(token)) {
            throw new OnlyOfficeJwtException("No ONLYOFFICE JWT found (neither in body nor in header '" + headerName()
                    + "')");
        }
        try {
            JWTVerifier verifier = JWT.require(algorithm()).build();
            DecodedJWT decoded = verifier.verify(token.trim());
            String payloadJson = new String(Base64.getUrlDecoder().decode(decoded.getPayload()),
                    StandardCharsets.UTF_8);
            return MAPPER.readValue(payloadJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (JWTVerificationException | IOException | IllegalArgumentException e) {
            throw new OnlyOfficeJwtException("ONLYOFFICE JWT verification failed: " + e.getMessage(), e);
        }
    }

    private static Algorithm algorithm() {
        String secret = Framework.getProperty(JWT_SECRET);
        if (isBlank(secret)) {
            throw new OnlyOfficeJwtException(
                    "ONLYOFFICE JWT is enabled but '" + JWT_SECRET + "' is not configured in nuxeo.conf");
        }
        String name = Framework.getProperty(JWT_ALGORITHM, DEFAULT_ALGORITHM).trim().toUpperCase();
        switch (name) {
            case "HS256":
                return Algorithm.HMAC256(secret);
            case "HS384":
                return Algorithm.HMAC384(secret);
            case "HS512":
                return Algorithm.HMAC512(secret);
            default:
                throw new OnlyOfficeJwtException("Unsupported '" + JWT_ALGORITHM + "' value: " + name
                        + " (expected HS256, HS384 or HS512)");
        }
    }

    private static int ttl() {
        String value = Framework.getProperty(JWT_TTL, String.valueOf(DEFAULT_TTL));
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException nfe) {
            LOG.warn("Invalid '{}' value '{}', using default {}s", JWT_TTL, value, DEFAULT_TTL);
            return DEFAULT_TTL;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
