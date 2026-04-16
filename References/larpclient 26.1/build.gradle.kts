plugins {
    base
}

tasks.named("build") {
    dependsOn(":larp:build", ":larp-addon:build")
}

tasks.named("check") {
    dependsOn(":larp:check", ":larp-addon:check")
}

tasks.named("clean") {
    dependsOn(":larp:clean", ":larp-addon:clean")
}

tasks.register("buildLarp") {
    group = "build"
    description = "Builds the public Larp mod."
    dependsOn(":larp:buildLarp")
}

tasks.register("runLarp") {
    group = "fabric"
    description = "Runs the public Larp mod."
    dependsOn(":larp:runClientLarp")
}

tasks.register("buildAddon") {
    group = "build"
    description = "Builds the addon workspace."
    dependsOn(":larp-addon:build")
}

tasks.register("buildMods") {
    group = "build"
    description = "Builds the regular Larp jar and the regular addon jar."
    dependsOn(":larp:build", ":larp-addon:build")
}

tasks.register("runAddon") {
    group = "fabric"
    description = "Runs the addon workspace."
    dependsOn(":larp-addon:runClient")
}

tasks.register("buildPublic") {
    group = "build"
    description = "Alias for buildLarp."
    dependsOn("buildLarp")
}

tasks.register("runClientLarp") {
    group = "fabric"
    description = "Alias for runLarp."
    dependsOn("runLarp")
}

tasks.register("runClientAddon") {
    group = "fabric"
    description = "Alias for runAddon."
    dependsOn("runAddon")
}
