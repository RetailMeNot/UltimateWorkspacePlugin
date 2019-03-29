package com.rmn.workspace.gradle

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

// REVIEW: idk why these attributes are all required (creator + property explicit defs...)  Maybe jackson shenanigans.
@JsonInclude(JsonInclude.Include.ALWAYS)
data class WorkspaceInfo @JsonCreator(mode = JsonCreator.Mode.DEFAULT) constructor(@JsonProperty("name")val name: String,
                                                                                   @JsonProperty("settingsPath")val settingsPath: String,
                                                                                   @JsonProperty("modules")val modules: MutableList<Module>)


data class Module @JsonCreator(mode = JsonCreator.Mode.DEFAULT) constructor(@JsonProperty("name") val name: String,
                                                                            @JsonProperty("path") val path: String,
                                                                            @JsonProperty("group") val group: String)