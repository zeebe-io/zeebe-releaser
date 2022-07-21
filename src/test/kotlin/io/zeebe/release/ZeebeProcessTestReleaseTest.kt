package io.zeebe.release

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent
import io.camunda.zeebe.process.test.api.ZeebeTestEngine
import io.camunda.zeebe.process.test.extension.ZeebeProcessTest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@ZeebeProcessTest
class ZeebeProcessTestReleaseTest {

  lateinit var client: ZeebeClient
  lateinit var engine: ZeebeTestEngine
  lateinit var testHelper: TestHelper

  @BeforeEach
  fun beforeEach() {
    testHelper = TestHelper(client, engine)
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
    testHelper.assertThatElementIsActive(instanceEvent, "upgrade-zeebe-version")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "upgrade-zeebe-version")
    testHelper.assertThatElementIsActive(instanceEvent, "create-release")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "create-release")
    testHelper.assertThatElementIsActive(instanceEvent, "release-on-maven-central")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "release-on-maven-central")

    testHelper.assertThatProcessIsCompleted(instanceEvent)
  }

  private fun deployProcess() {
    client
      .newDeployResourceCommand()
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
