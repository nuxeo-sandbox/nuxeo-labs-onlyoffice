package org.nuxeo.ecm.onlyoffice.conversion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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
