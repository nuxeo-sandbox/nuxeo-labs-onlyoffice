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

/**
 * Code-page and delimiter constants for ONLYOFFICE CSV/TXT conversions.
 *
 * @since 2025.1
 */
public interface ConversionConstants {

  int CODE_JAPANESE = 932;

  int CODE_CHINESE_TRADITIONAL = 950;

  int CODE_CENTRAL_EUROPEAN = 1250;

  int CODE_CYRILLIC = 1251;

  int CODE_UNICODE = 65001;

  int DELIMITER_NONE = 0;

  int DELIMITER_TAB = 1;

  int DELIMITER_SEMICOLON = 2;

  int DELIMITER_COLON = 3;

  int DELIMITER_COMMA = 4;

  int DELIMITER_SPACE = 5;
}
