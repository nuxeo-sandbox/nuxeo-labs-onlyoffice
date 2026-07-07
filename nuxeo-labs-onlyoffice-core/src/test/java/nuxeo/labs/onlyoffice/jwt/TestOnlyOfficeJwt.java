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
package nuxeo.labs.onlyoffice.jwt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Map;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

@RunWith(FeaturesRunner.class)
@Features(RuntimeFeature.class)
public class TestOnlyOfficeJwt {

    private static final String SECRET = "a-very-secret-shared-key-1234567890";

    private void setProp(String key, String value) {
        if (value == null) {
            Framework.getProperties().remove(key);
        } else {
            Framework.getProperties().setProperty(key, value);
        }
    }

    @After
    public void cleanup() {
        setProp(OnlyOfficeJwt.JWT_ENABLED, null);
        setProp(OnlyOfficeJwt.JWT_SECRET, null);
        setProp(OnlyOfficeJwt.JWT_ALGORITHM, null);
        setProp(OnlyOfficeJwt.JWT_TTL, null);
        setProp(OnlyOfficeJwt.JWT_HEADER, null);
        setProp(OnlyOfficeJwt.JWT_PREFIX, null);
    }

    @Test
    public void enabledByDefault() {
        assertTrue(OnlyOfficeJwt.isEnabled());
    }

    @Test
    public void disabledWhenExplicitlyFalse() {
        setProp(OnlyOfficeJwt.JWT_ENABLED, "false");
        assertFalse(OnlyOfficeJwt.isEnabled());
    }

    @Test
    public void enabledEvenWithoutSecret() {
        // Enabled flag is honored regardless of secret; sign/verify then throw.
        setProp(OnlyOfficeJwt.JWT_SECRET, null);
        assertTrue(OnlyOfficeJwt.isEnabled());
    }

    @Test
    public void signWithoutSecretThrows() {
        setProp(OnlyOfficeJwt.JWT_SECRET, null);
        try {
            OnlyOfficeJwt.sign(Map.of("k", "v"));
            fail("Expected OnlyOfficeJwtException");
        } catch (OnlyOfficeJwtException e) {
            assertTrue(e.getMessage().contains(OnlyOfficeJwt.JWT_SECRET));
        }
    }

    @Test
    public void roundTripHs256() {
        assertRoundTrip("HS256");
    }

    @Test
    public void roundTripHs384() {
        assertRoundTrip("HS384");
    }

    @Test
    public void roundTripHs512() {
        assertRoundTrip("HS512");
    }

    private void assertRoundTrip(String algorithm) {
        setProp(OnlyOfficeJwt.JWT_SECRET, SECRET);
        setProp(OnlyOfficeJwt.JWT_ALGORITHM, algorithm);

        Map<String, Object> payload = Map.of("status", 2, "url", "http://ds/edited.docx", "key", "abc123");
        String jws = OnlyOfficeJwt.sign(payload);

        Map<String, Object> claims = OnlyOfficeJwt.verify(jws, null);
        assertEquals(2, ((Number) claims.get("status")).intValue());
        assertEquals("http://ds/edited.docx", claims.get("url"));
        assertEquals("abc123", claims.get("key"));
    }

    @Test
    public void verifyPrefersBodyOverHeader() {
        setProp(OnlyOfficeJwt.JWT_SECRET, SECRET);

        String bodyToken = OnlyOfficeJwt.sign(Map.of("source", "body"));
        String headerToken = "Bearer " + OnlyOfficeJwt.sign(Map.of("source", "header"));

        Map<String, Object> claims = OnlyOfficeJwt.verify(bodyToken, headerToken);
        assertEquals("body", claims.get("source"));
    }

    @Test
    public void verifyFallsBackToHeader() {
        setProp(OnlyOfficeJwt.JWT_SECRET, SECRET);

        String headerToken = "Bearer " + OnlyOfficeJwt.sign(Map.of("source", "header"));
        Map<String, Object> claims = OnlyOfficeJwt.verify(null, headerToken);
        assertEquals("header", claims.get("source"));
    }

    @Test
    public void verifyRejectsWrongSecret() {
        setProp(OnlyOfficeJwt.JWT_SECRET, SECRET);
        String jws = OnlyOfficeJwt.sign(Map.of("k", "v"));

        // Re-sign context with a different secret -> verification must fail.
        setProp(OnlyOfficeJwt.JWT_SECRET, "a-totally-different-secret-key-0987654321");
        try {
            OnlyOfficeJwt.verify(jws, null);
            fail("Expected OnlyOfficeJwtException");
        } catch (OnlyOfficeJwtException e) {
            assertTrue(e.getMessage().contains("verification failed"));
        }
    }

    @Test
    public void verifyRejectsMissingToken() {
        setProp(OnlyOfficeJwt.JWT_SECRET, SECRET);
        try {
            OnlyOfficeJwt.verify(null, null);
            fail("Expected OnlyOfficeJwtException");
        } catch (OnlyOfficeJwtException e) {
            assertTrue(e.getMessage().contains("No ONLYOFFICE JWT"));
        }
    }

}
