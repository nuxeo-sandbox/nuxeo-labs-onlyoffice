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

import org.nuxeo.ecm.core.api.NuxeoException;

/**
 * Raised when an ONLYOFFICE JWT cannot be signed (missing secret / bad algorithm) or verified (bad signature / expired /
 * missing token).
 *
 * @since 2025.1
 */
public class OnlyOfficeJwtException extends NuxeoException {

    private static final long serialVersionUID = 1L;

    public OnlyOfficeJwtException(String message) {
        super(message);
    }

    public OnlyOfficeJwtException(String message, Throwable cause) {
        super(message, cause);
    }

}
