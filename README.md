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
