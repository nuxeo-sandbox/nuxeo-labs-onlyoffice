package org.nuxeo.ecm.onlyoffice.conversion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConversionRequest {

  public static class Thumbnail {

    @JsonProperty("aspect")
    private int aspect = 0;

    @JsonProperty("first")
    private boolean first = true;

    @JsonProperty("height")
    private int height = 100;

    @JsonProperty("width")
    private int width = 100;

    public Thumbnail() {
      super();
    }

    public int getAspect() {
      return aspect;
    }

    public void setAspect(int aspect) {
      this.aspect = aspect;
    }

    public boolean isFirst() {
      return first;
    }

    public void setFirst(boolean first) {
      this.first = first;
    }

    public int getHeight() {
      return height;
    }

    public void setHeight(int height) {
      this.height = height;
    }

    public int getWidth() {
      return width;
    }

    public void setWidth(int width) {
      this.width = width;
    }

    @Override
    public String toString() {
      return String.format("Thumbnail [aspect=%s, first=%s, height=%s, width=%s]", aspect, first, height, width);
    }

  }

  @JsonProperty("async")
  private boolean async = true;

  @JsonProperty("codePage")
  private Integer codePage = null;

  @JsonProperty("delimiter")
  private Integer delimiter = null;

  @JsonProperty("filetype")
  private String fileType = null;

  @JsonProperty("key")
  private String key = null;

  @JsonProperty("outputtype")
  private String outputType = null;

  @JsonProperty("thumbnail")
  private Thumbnail thumbnail = null;

  @JsonProperty("title")
  private String title = null;

  @JsonProperty("url")
  private String url = null;

  public ConversionRequest() {
    super();
  }

  public boolean isAsync() {
    return async;
  }

  public void setAsync(boolean async) {
    this.async = async;
  }

  public Integer getCodePage() {
    return codePage;
  }

  public void setCodePage(Integer codePage) {
    this.codePage = codePage;
  }

  public Integer getDelimiter() {
    return delimiter;
  }

  public void setDelimiter(Integer delimiter) {
    this.delimiter = delimiter;
  }

  public String getFileType() {
    return fileType;
  }

  public void setFileType(String fileType) {
    this.fileType = fileType;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getOutputType() {
    return outputType;
  }

  public void setOutputType(String outputType) {
    this.outputType = outputType;
  }

  public Thumbnail createThumbnail() {
    return thumbnail = new Thumbnail();
  }

  public Thumbnail getThumbnail() {
    return thumbnail;
  }

  public void setThumbnail(Thumbnail thumbnail) {
    this.thumbnail = thumbnail;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  @Override
  public String toString() {
    return String.format("ConversionRequest [fileType=%s, key=%s, outputType=%s, thumbnail=%s, title=%s, url=%s]",
        fileType, key, outputType, thumbnail, title, url);
  }

}
