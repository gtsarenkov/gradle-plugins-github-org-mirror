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
import org.slf4j.event.Level

class CloneMirrorReposTask extends DefaultTask {
    @Input
    final Property<String> githubOrg = project.objects.property(String.class)

    @Input
    @Optional
    final Property<String> githubApiToken

    @Input
    @Optional
    final Property<Boolean> skipClone = project.objects.property(Boolean.class).convention(false)

    @Input
    final Property<Integer> perPage = project.objects.property(Integer.class).convention(200)

    @OutputDirectory
    final DirectoryProperty targetDir

    private final _headers = ['User-Agent': "${project.name}/${project.version}"]

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
        def headers = _headers
        if (githubApiToken.present) {
            headers['Authorization'] = "token $githubApiToken"
        }

        def query = ['per_page': perPage.getOrElse(100)]
        def path = "/orgs/${githubOrg.get()}/repos"
        def repos = []

        while (path) {
            def response = restClient.get(
                    path: path,
                    contentType: ContentType.JSON,
                    headers: headers,
                    query: query,
            )

            if (response.status != 200) {
                logger.error "Failed to fetch repositories, status: ${response.status}"
                throw new GradleException("Failed to fetch repositories, status: ${response.status}")
            }

            repos.addAll(response.data.collect { it.clone_url })

            path = null  // Reset path for the next iteration
            def linkHeader = response.headers.find { it.name == 'Link' }?.value

            if (linkHeader) {
                def linkPattern = ~/.*<(.+?)>; rel="next".*/
                def matcher = linkPattern.matcher(linkHeader)

                if (matcher.find()) {
                    String nextUrl = matcher.group(1)
                    // Parse the URL using URI
                    URI uri = new URI(nextUrl)
                    path = uri.getPath() // Gets the raw path

                    // Convert query into a map
                    def nextQuery = uri.getQuery()
                    query.clear() // Clear existing query
                    nextQuery.split('&').each { param ->
                        def parts = param.split('=')
                        if (parts.size() == 2) {
                            query[parts[0]] = parts[1]
                        }
                    }
                }
            }
        }

        repos.each { repoUrl ->
            def repoName = repoUrl.tokenize('/').last().replace('.git', '')
            def command = ["git", "clone", "--mirror", repoUrl, new File(targetDir.get().asFile, "${repoName}.git").absolutePath]
            logger.lifecycle "Executing: ${command.join(' ')}"
            if (!skipClone.getOrElse(false)) {
                def process = command.execute()
                process.waitFor()
                println process.text
            }
        }
    }
}
