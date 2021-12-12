# Zeebe Releaser
Project to automate and visualise the Zeebe release process.

* [Camunda Cloud Cluster](https://console.cloud.ultrawombat.com/org/6ff582aa-a62e-4a28-aac7-4d2224d8c58a/cluster/b051f158-2779-479f-84dc-1f16c8f808df)
* [Camunda Cloud Tasklist](https://bru-3.tasklist.ultrawombat.com/b051f158-2779-479f-84dc-1f16c8f808df)

## Getting started
To deploy the release process to Camunda Cloud (or Zeebe), run:
```shell
./deployProcesses.sh
```

To start the release orchestrator, run:
```shell
./startZeebeReleaseOrchestrator.sh
```
This will start a long living instance that orchestrate our release cadence.

To start a release that is outside of our release cadence (for eg:- a patch release), run:
```shell
./startPatchRelease.sh
```

// Not yet supported
To cancel the current release, run:
```shell
./abortRelease.sh <startDateTime>
```

# zeebe-maven-template

This repo was generated using the zeebe-maven-template.

Empty maven project with defaults that integrate with the Zeebe build pipeline

## Usage

Use this as a template for new Zeebe projects.

Change the artifact, name and version in `pom.xml` and `.ci/scripts/github-release.sh`.

Adding [Contributing to this project](https://gist.github.com/jwulf/2c7f772570bfc8654b0a0a783a3f165e) to the repo.

(This is not part of the template as the text might change. Please copy the latest version.)

## Features

- IDE integration
  - https://editorconfig.org/
- GitHub Integration
  - Dependabot enabled for Maven dependencies (see `.github/dependabot`)
  - BORS config (configured to require no review approvals; edit `bors.toml/required_approvals` to change this)
  - LGTM config
- Maven POM
  - Zeebe reprositories
  - Release to Maven, Zeebe and GitHub
  - Google Code Formatter
  - JUnit 5
  - AssertJ
  - Surefire Plugin
  - JaCoCo Plugin (test coverage)
  - flaky test extractor
- Jenkinsfile
  - Rerun failed builds
  - Show last commit in build display name
  - Archie test reports and JVM dump files in case of failure
  - Publish test coverage to Jenkins and also archive test coverae reports as part of the build artifact
  - Detect flaky tests and publish them to Jenkins

## Versions

Different versions are represented in different branches

- `master` - Java 11
- `stable/java8` - Java 8
