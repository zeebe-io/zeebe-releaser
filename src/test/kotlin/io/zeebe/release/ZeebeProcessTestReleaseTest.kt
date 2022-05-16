package io.zeebe.release

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent
import org.assertj.core.api.Assertions
import org.camunda.community.eze.EmbeddedZeebeEngine
import org.camunda.community.eze.RecordStreamSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@EmbeddedZeebeEngine
class ZeebeProcessTestReleaseTest {

  lateinit var client: ZeebeClient
  lateinit var recordStream: RecordStreamSource
  lateinit var testHelper: TestHelper

  @BeforeEach
  fun beforeEach() {
    testHelper = TestHelper(client, recordStream)
    deployProcess()
  }

  @Test
  fun `should be able to create an instance`() {
    // when
    val instanceEvent = createInstance()

    // then
    Assertions.assertThat(instanceEvent.processInstanceKey).isGreaterThan(0)
  }

  @Test
  fun `should complete process`() {
    // when
    val instanceEvent = createInstance()

    // then
    testHelper.assertThatUserTaskActivated(
      instanceEvent.processInstanceKey, "upgrade-zeebe-version")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "upgrade-zeebe-version")
    testHelper.assertThatUserTaskActivated(instanceEvent.processInstanceKey, "create-release")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "create-release")
    testHelper.assertThatUserTaskActivated(
      instanceEvent.processInstanceKey, "release-on-maven-central")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "release-on-maven-central")

    testHelper.assertThatProcessIsCompleted(instanceEvent.processInstanceKey)
  }

  private fun deployProcess() {
    client
      .newDeployCommand()
      .addResourceFromClasspath("zeebe-process-test-release.bpmn")
      .send()
      .join()
  }

  private fun createInstance(): ProcessInstanceEvent {
    return client
      .newCreateInstanceCommand()
      .bpmnProcessId("zeebe-process-test-release")
      .latestVersion()
      .send()
      .join()
  }
}
