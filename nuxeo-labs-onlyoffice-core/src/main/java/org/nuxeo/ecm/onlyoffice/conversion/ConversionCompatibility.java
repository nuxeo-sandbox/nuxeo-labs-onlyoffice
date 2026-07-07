package org.nuxeo.ecm.onlyoffice.conversion;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ConversionCompatibility {

  @JsonProperty("name")
  private String name;

  @JsonProperty("input")
  private List<String> input;

  @JsonProperty("output")
  private List<String> output;

  @JsonProperty("disallowed")
  private Set<String> disallowed;

  public ConversionCompatibility() {
    super();
  }

  public String getName() {
    return name;
  }

  public List<String> getInput() {
    return input;
  }

  public List<String> getOutput() {
    return output;
  }

  public Set<String> getDisallowed() {
    return disallowed;
  }

  public boolean accepts(String inputType, String outputType) {
    return input.contains(inputType) && output.contains(outputType)
        && !disallowed.contains(String.format("%s-%s", inputType, outputType));
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((disallowed == null) ? 0 : disallowed.hashCode());
    result = prime * result + ((input == null) ? 0 : input.hashCode());
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    result = prime * result + ((output == null) ? 0 : output.hashCode());
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
    ConversionCompatibility other = (ConversionCompatibility) obj;
    if (disallowed == null) {
      if (other.disallowed != null)
        return false;
    } else if (!disallowed.equals(other.disallowed))
      return false;
    if (input == null) {
      if (other.input != null)
        return false;
    } else if (!input.equals(other.input))
      return false;
    if (name == null) {
      if (other.name != null)
        return false;
    } else if (!name.equals(other.name))
      return false;
    if (output == null) {
      if (other.output != null)
        return false;
    } else if (!output.equals(other.output))
      return false;
    return true;
  }

  @Override
  public String toString() {
    return String.format("ConversionCompatibility [name=%s, input=%s, output=%s, disallowed=%s]", name, input, output,
        disallowed);
  }

}
