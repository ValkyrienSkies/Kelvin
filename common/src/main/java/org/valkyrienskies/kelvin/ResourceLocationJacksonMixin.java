package org.valkyrienskies.kelvin;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIncludeProperties({"namespace", "path"})
public abstract class ResourceLocationJacksonMixin {
    @JsonCreator
    public ResourceLocationJacksonMixin(@JsonProperty("namespace") String namespace, @JsonProperty("path") String path) {
    }
}
