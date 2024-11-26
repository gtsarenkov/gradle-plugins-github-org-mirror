package com.jdisc.toolchain.tasks

import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

class CloneMirrorReposTask extends DefaultTask {
    @Input
    final Property<String> githubOrg = project.objects.property(String.class)

    @Input
    @Optional
    final Property<String> githubApiToken

    @Input
    @Optional
    final Property<Boolean> skipClone = project.objects.property(Boolean.class).convention(false)

    @OutputDirectory
    final DirectoryProperty targetDir

    CloneMirrorReposTask() {
        githubApiToken = project.objects.property(String.class).convention(System.getenv('GITHUB_API_TOKEN'))
        targetDir = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir("git").get())
    }

    @TaskAction
    void cloneMirrorRepos() {
        if (!githubOrg.present) {
            throw new GradleException("You must specify githubOrg to clone")
        } else {
            logger.debug("Use GitHub organization to clone ${githubOrg.get()}")
        }

        if (!targetDir.present) {
            throw new GradleException("You must specify targetDir to clone. If not set default ${project.layout.buildDirectory.dir(githubOrg).get()}")
        } else {
            logger.debug("Use target directory ${targetDir}")
        }

        if (!githubApiToken.present) {
            logger.warn("Unauthenticated access to GitHub API has restriction.")
            logger.warn("Please generate Personal Access Token and set it via 'githubApiToken' property or 'GITHUB_API_TOKEN' environment variable")
        } else {
            logger.debug("Use GitHub API token ${githubApiToken}")
        }

        def restClient = new RESTClient('https://api.github.com/')
        def headers = ['User-Agent': "${project.name}/${project.version}"]
        if (githubApiToken.present) {
            headers['Authorization'] = "token $githubApiToken"
        }

        def path = "/orgs/${githubOrg.get()}/repos"
        def response = restClient.get(
                path: path,
                contentType: ContentType.JSON,
//                query: [per_page: 200],
                headers: headers
        )

        if (response.status != 200) {
            println "Failed to fetch repositories, status: ${response.status}"
            return
        }

        def repos = response.data.collect { it.clone_url }

        repos.each { repoUrl ->
            def repoName = repoUrl.tokenize('/').last().replace('.git', '')
            def command = ["git", "clone", "--mirror", repoUrl, new File(targetDir.get().asFile, "${repoName}.git").absolutePath]
            logger.lifecycle "Executing: ${command.join(' ')}"
            if (!skipClone) {
                def process = command.execute()
                process.waitFor()
                println process.text
            }
        }
    }
}
