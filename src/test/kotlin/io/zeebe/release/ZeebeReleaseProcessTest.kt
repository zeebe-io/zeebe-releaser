package io.zeebe.release

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent
import io.camunda.zeebe.model.bpmn.Bpmn
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.assertj.core.api.Assertions
import org.camunda.community.eze.EmbeddedZeebeEngine
import org.camunda.community.eze.RecordStreamSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@EmbeddedZeebeEngine
class ZeebeReleaseProcessTest {

  lateinit var client: ZeebeClient
  lateinit var recordStream: RecordStreamSource
  lateinit var testHelper: TestHelper
  private val dateFormatter: DateTimeFormatter =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

  @BeforeEach
  fun beforeEach() {
    testHelper = TestHelper(client, recordStream)
    deployProcess()
    deployFakeChildProcess("zeebe-release-qa")
    deployFakeChildProcess("zeebe-process-test-release")
    deployFakeChildProcess(
        "zeebe-post-release",
        hashMapOf(
            "release_date" to
                "date and time(\"${
                            dateFormatter.format(
                                ZonedDateTime.now().plusHours(1)
                            )
                        }\")",
            "code_freeze_date" to "date and time(\"${dateFormatter.format(ZonedDateTime.now())}\")",
            "release_type" to "\"minor\"",
            "release_manager" to "\"The R4l34z0r\""))
  }

  @Test
  fun `should be able to create an instance`() {
    // when
    val instanceEvent = createInstance(emptyMap())

    // then
    Assertions.assertThat(instanceEvent.processInstanceKey).isGreaterThan(0)
  }

  @Test
  fun `should be able to complete the instance`() {
    // when
    val instanceEvent =
        createInstance(hashMapOf("release_version" to "1.0.0", "release_type" to "major"))

    // then
    testHelper.assertThatUserTaskActivated(
        instanceEvent.processInstanceKey, "create-release-branch")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "create-release-branch")
    testHelper.assertThatUserTaskActivated(
        instanceEvent.processInstanceKey, "create-code-freeze-backport-label")
    testHelper.completeUserTask(
        instanceEvent.processInstanceKey, "create-code-freeze-backport-label")
    testHelper.assertThatUserTaskActivated(
        instanceEvent.processInstanceKey, "collect-release-notes")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "collect-release-notes")
    testHelper.assertThatCalledProcessActivated(
        instanceEvent.processInstanceKey, "zeebe-release-qa")
    testHelper.assertThatCalledProcessCompleted(
        instanceEvent.processInstanceKey, "zeebe-release-qa")
    testHelper.assertThatUserTaskActivated(instanceEvent.processInstanceKey, "trigger-release")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "trigger-release")
    testHelper.assertThatUserTaskActivated(
        instanceEvent.processInstanceKey, "release-on-maven-central")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "release-on-maven-central")
    testHelper.assertThatCalledProcessActivated(
        instanceEvent.processInstanceKey, "zeebe-process-test-release")
    testHelper.assertThatCalledProcessCompleted(
        instanceEvent.processInstanceKey, "zeebe-process-test-release")
    testHelper.assertThatCalledProcessActivated(
        instanceEvent.processInstanceKey, "zeebe-post-release")
    testHelper.assertThatCalledProcessCompleted(
        instanceEvent.processInstanceKey, "zeebe-post-release")

    testHelper.assertThatProcessIsCompleted(instanceEvent.processInstanceKey)
  }

  @Test
  fun `should be able to complete patch release`() {
    // when
    val instanceEvent =
        createInstance(hashMapOf("release_version" to "1.0.0", "release_type" to "patch"))

    // then
    testHelper.assertThatUserTaskActivated(
        instanceEvent.processInstanceKey, "create-release-branch")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "create-release-branch")
    testHelper.assertThatCalledProcessActivated(
        instanceEvent.processInstanceKey, "zeebe-release-qa")
    testHelper.assertThatCalledProcessCompleted(
        instanceEvent.processInstanceKey, "zeebe-release-qa")
    testHelper.assertThatUserTaskActivated(instanceEvent.processInstanceKey, "trigger-release")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "trigger-release")
    testHelper.assertThatUserTaskActivated(
        instanceEvent.processInstanceKey, "release-on-maven-central")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "release-on-maven-central")
    testHelper.assertThatCalledProcessActivated(
        instanceEvent.processInstanceKey, "zeebe-process-test-release")
    testHelper.assertThatCalledProcessCompleted(
        instanceEvent.processInstanceKey, "zeebe-process-test-release")
    testHelper.assertThatCalledProcessActivated(
        instanceEvent.processInstanceKey, "zeebe-post-release")
    testHelper.assertThatCalledProcessCompleted(
        instanceEvent.processInstanceKey, "zeebe-post-release")

    testHelper.assertThatProcessIsCompleted(instanceEvent.processInstanceKey)
  }

  private fun deployProcess() {
    client.newDeployCommand().addResourceFromClasspath("zeebe_release.bpmn").send().join()
  }

  private fun deployFakeChildProcess(
      name: String,
      outputVariables: Map<String, String> = emptyMap()
  ) {
    val builder = Bpmn.createExecutableProcess(name).startEvent()
    outputVariables.forEach { builder.zeebeOutputExpression(it.value, it.key) }
    val model = builder.done()
    client
        .newDeployCommand()
        .addProcessModel(model, name)
        .addResourceFromClasspath("zeebe_release.bpmn")
        .send()
        .join()
  }

  private fun createInstance(variables: Map<String, Any>): ProcessInstanceEvent {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId("zeebe-release-process")
        .latestVersion()
        .variables(variables)
        .send()
        .join()
  }
}
