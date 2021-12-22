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

## Testing
This is a maven project. To test it, simply run:
```shell
mvn verify
```

These tests are written in Kotlin and use [EZE](https://github.com/camunda-community-hub/eze) (Embedded Zeebe Engine) to validate that the processes behave as expected.

A [GitHub Action](.github/workflows/build.yml) automatically runs these tests for you when you open a pull request.

## Deployment
A [GitHub Action](.github/workflows/deploy.yml) automatically deploys the processes to Camunda Cloud every time a commit is pushed to `master`.
