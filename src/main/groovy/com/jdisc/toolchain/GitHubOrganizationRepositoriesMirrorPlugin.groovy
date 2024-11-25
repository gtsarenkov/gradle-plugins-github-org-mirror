package com.jdisc.toolchain


import org.gradle.api.Project
import org.gradle.api.Plugin

class GitHubOrganizationRepositoriesMirrorPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.tasks.register('cloneAndMirrorRepos', CloneAndMirrorReposTask)
    }
}
