package io.zeebe.release

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent
import io.camunda.zeebe.process.test.api.ZeebeTestEngine
import io.camunda.zeebe.process.test.extension.ZeebeProcessTest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@ZeebeProcessTest
class ZeebePostReleaseProcessTest {

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
    // branch 1
    completeNextTask(instanceEvent, "notify-others")

    // branch 2
    completeNextTask(instanceEvent, "github-release-labels")
    completeNextTask(instanceEvent, "github-generate-changelog")

    // branch 4
    completeNextTask(instanceEvent, "merge-with-main")
    completeNextTask(instanceEvent, "prepare-next-stable")

    // branch 5
    //      completeNextTask(instanceEvent, "merge-with-stable")

    // branch 6
    completeNextTask(instanceEvent, "update-getting-started")

    // after parallel gateway
    completeNextTask(instanceEvent, "build-benchmark-images")
    completeNextTask(instanceEvent, "update-long-running")

    // branch no patch
    completeNextTask(instanceEvent, "invite-team-to-celebrate")
    completeNextTask(instanceEvent, "collect-data")

    // last task
    completeNextTask(instanceEvent, "create-appointment-for-next")

    testHelper.assertThatProcessIsCompleted(instanceEvent)
  }

  @Test
  fun `should be able to complete the instance for patch release`() {
    // when
    val instanceEvent =
      createInstance(hashMapOf("release_version" to "1.0.0-alpha1", "release_type" to "alpha"))

    // then
    // branch 1
    completeNextTask(instanceEvent, "notify-others")

    // branch 2
    completeNextTask(instanceEvent, "github-release-labels")
    completeNextTask(instanceEvent, "github-generate-changelog")

    // after parallel gateway
    completeNextTask(instanceEvent, "build-benchmark-images")
    completeNextTask(instanceEvent, "update-long-running")

    // branch no patch
    completeNextTask(instanceEvent, "invite-team-to-celebrate")
    completeNextTask(instanceEvent, "collect-data")

    // last task
    completeNextTask(instanceEvent, "create-appointment-for-next")

    testHelper.assertThatProcessIsCompleted(instanceEvent)
  }

  @Test
  fun `should be able to complete the instance for alpha release`() {
    // when
    val instanceEvent =
      createInstance(hashMapOf("release_version" to "1.0.1", "release_type" to "patch"))

    // then
    // branch 1
    completeNextTask(instanceEvent, "notify-others")

    // branch 2
    completeNextTask(instanceEvent, "github-release-labels")
    completeNextTask(instanceEvent, "github-generate-changelog")

    // branch 5
    completeNextTask(instanceEvent, "merge-with-stable")

    // branch 6
    completeNextTask(instanceEvent, "update-getting-started")

    // after parallel gateway
    completeNextTask(instanceEvent, "build-benchmark-images")
    completeNextTask(instanceEvent, "update-long-running")

    // last task
    completeNextTask(instanceEvent, "create-appointment-for-next")

    testHelper.assertThatProcessIsCompleted(instanceEvent)
  }

  private fun completeNextTask(instanceEvent: ProcessInstanceEvent, elementId: String) {
    testHelper.assertThatElementIsActive(instanceEvent, elementId)
    testHelper.completeUserTask(instanceEvent.processInstanceKey, elementId)
  }

  private fun deployProcess() {
    client.newDeployCommand().addResourceFromClasspath("post_release.bpmn").send().join()
  }

  private fun createInstance(variables: Map<String, Any>): ProcessInstanceEvent {
    return client
      .newCreateInstanceCommand()
      .bpmnProcessId("zeebe-post-release")
      .latestVersion()
      .variables(variables)
      .send()
      .join()
  }
}
