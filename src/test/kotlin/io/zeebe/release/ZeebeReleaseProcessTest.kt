package io.zeebe.release

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent
import io.camunda.zeebe.model.bpmn.Bpmn
import io.camunda.zeebe.process.test.api.ZeebeTestEngine
import io.camunda.zeebe.process.test.extension.ZeebeProcessTest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@ZeebeProcessTest
class ZeebeReleaseProcessTest {

  lateinit var client: ZeebeClient
  lateinit var engine: ZeebeTestEngine
  lateinit var testHelper: TestHelper
  private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

  @BeforeEach
  fun beforeEach() {
    testHelper = TestHelper(client, engine)
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
      createInstance(
        hashMapOf(
          "release_version" to "1.0.0", "release_type" to "major", "release_manager" to "remco"))

    // then
    testHelper.assertThatElementIsActive(instanceEvent, "create-release-branch")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "create-release-branch")
    testHelper.assertThatElementIsActive(instanceEvent, "create-code-freeze-backport-label")
    testHelper.completeUserTask(
      instanceEvent.processInstanceKey, "create-code-freeze-backport-label")
    testHelper.assertThatElementIsActive(instanceEvent, "collect-release-notes")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "collect-release-notes")
    testHelper.assertThatCalledProcessCompleted(instanceEvent, "zeebe-release-qa")
    testHelper.assertThatElementIsActive(instanceEvent, "trigger-release")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "trigger-release")
    testHelper.assertThatElementIsActive(instanceEvent, "release-on-maven-central")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "release-on-maven-central")
    testHelper.assertThatCalledProcessCompleted(instanceEvent, "zeebe-process-test-release")
    testHelper.assertThatCalledProcessCompleted(instanceEvent, "zeebe-post-release")

    testHelper.assertThatProcessIsCompleted(instanceEvent)
  }

  @Test
  fun `should be able to complete patch release`() {
    // when
    val instanceEvent =
      createInstance(
        hashMapOf(
          "release_version" to "1.0.0", "release_type" to "patch", "release_manager" to "remco"))

    // then
    testHelper.assertThatElementIsActive(instanceEvent, "create-release-branch")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "create-release-branch")
    testHelper.assertThatCalledProcessCompleted(instanceEvent, "zeebe-release-qa")
    testHelper.assertThatElementIsActive(instanceEvent, "trigger-release")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "trigger-release")
    testHelper.assertThatElementIsActive(instanceEvent, "release-on-maven-central")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "release-on-maven-central")
    testHelper.assertThatCalledProcessCompleted(instanceEvent, "zeebe-process-test-release")
    testHelper.assertThatCalledProcessCompleted(instanceEvent, "zeebe-post-release")

    testHelper.assertThatProcessIsCompleted(instanceEvent)
  }

  private fun deployProcess() {
    client.newDeployResourceCommand().addResourceFromClasspath("zeebe_release.bpmn").send().join()
  }

  private fun deployFakeChildProcess(
    name: String,
    outputVariables: Map<String, String> = emptyMap()
  ) {
    val builder = Bpmn.createExecutableProcess(name).startEvent()
    outputVariables.forEach { builder.zeebeOutputExpression(it.value, it.key) }
    val model = builder.done()
    client.newDeployResourceCommand().addProcessModel(model, "$name.bpmn").send().join()
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
