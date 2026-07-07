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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload returned by the ONLYOFFICE conversion service ({@code ConvertService.ashx}).
 *
 * @since 2025.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConversionResponse {

  @JsonProperty(value = "endConvert")
  private boolean endConvert = false;

  @JsonProperty(value = "fileUrl")
  private String fileUrl = null;

  @JsonProperty(value = "percent")
  private int percent = 0;

  @JsonProperty(value = "error")
  private int error = 0;

  public ConversionResponse() {
    super();
  }

  public boolean isEndConvert() {
    return endConvert;
  }

  public String getFileUrl() {
    return fileUrl;
  }

  public int getPercent() {
    return percent;
  }

  public boolean isError() {
    return this.error != 0;
  }

  public int getError() {
    return error;
  }

  public boolean isFinished() {
    return this.endConvert || isError();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + (endConvert ? 1231 : 1237);
    result = prime * result + error;
    result = prime * result + ((fileUrl == null) ? 0 : fileUrl.hashCode());
    result = prime * result + percent;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    ConversionResponse other = (ConversionResponse) obj;
    if (endConvert != other.endConvert)
      return false;
    if (error != other.error)
      return false;
    if (fileUrl == null) {
      if (other.fileUrl != null)
        return false;
    } else if (!fileUrl.equals(other.fileUrl))
      return false;
    if (percent != other.percent)
      return false;
    return true;
  }

  @Override
  public String toString() {
    return String.format("ConversionResponse [endConvert=%s, fileUrl=%s, percent=%s, error=%s]", endConvert, fileUrl,
        percent, error);
  }

}
