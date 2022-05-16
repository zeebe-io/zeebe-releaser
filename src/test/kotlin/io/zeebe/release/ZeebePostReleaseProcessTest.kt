package io.zeebe.release

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent
import java.time.format.DateTimeFormatter
import org.assertj.core.api.Assertions
import org.camunda.community.eze.EmbeddedZeebeEngine
import org.camunda.community.eze.RecordStreamSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@EmbeddedZeebeEngine
class ZeebePostReleaseProcessTest {

  lateinit var client: ZeebeClient
  lateinit var recordStream: RecordStreamSource
  lateinit var testHelper: TestHelper
  private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

  @BeforeEach
  fun beforeEach() {
    testHelper = TestHelper(client, recordStream)
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
    completeNextTask(instanceEvent.processInstanceKey, "notify-others")

    // branch 2
    completeNextTask(instanceEvent.processInstanceKey, "github-release-labels")
    completeNextTask(instanceEvent.processInstanceKey, "github-generate-changelog")

    // branch 4
    completeNextTask(instanceEvent.processInstanceKey, "merge-with-main")
    completeNextTask(instanceEvent.processInstanceKey, "prepare-next-stable")

    // branch 5
    //      completeNextTask(instanceEvent.processInstanceKey, "merge-with-stable")

    // branch 6
    completeNextTask(instanceEvent.processInstanceKey, "update-getting-started")

    // after parallel gateway
    completeNextTask(instanceEvent.processInstanceKey, "build-benchmark-images")
    completeNextTask(instanceEvent.processInstanceKey, "update-long-running")

    // branch no patch
    completeNextTask(instanceEvent.processInstanceKey, "invite-team-to-celebrate")
    completeNextTask(instanceEvent.processInstanceKey, "collect-data")

    // last task
    completeNextTask(instanceEvent.processInstanceKey, "create-appointment-for-next")

    testHelper.assertThatProcessIsCompleted(instanceEvent.processInstanceKey)
  }

  @Test
  fun `should be able to complete the instance for patch release`() {
    // when
    val instanceEvent =
      createInstance(hashMapOf("release_version" to "1.0.0-alpha1", "release_type" to "alpha"))

    // then
    // branch 1
    completeNextTask(instanceEvent.processInstanceKey, "notify-others")

    // branch 2
    completeNextTask(instanceEvent.processInstanceKey, "github-release-labels")
    completeNextTask(instanceEvent.processInstanceKey, "github-generate-changelog")

    // after parallel gateway
    completeNextTask(instanceEvent.processInstanceKey, "build-benchmark-images")
    completeNextTask(instanceEvent.processInstanceKey, "update-long-running")

    // branch no patch
    completeNextTask(instanceEvent.processInstanceKey, "invite-team-to-celebrate")
    completeNextTask(instanceEvent.processInstanceKey, "collect-data")

    // last task
    completeNextTask(instanceEvent.processInstanceKey, "create-appointment-for-next")

    testHelper.assertThatProcessIsCompleted(instanceEvent.processInstanceKey)
  }

  @Test
  fun `should be able to complete the instance for alpha release`() {
    // when
    val instanceEvent =
      createInstance(hashMapOf("release_version" to "1.0.1", "release_type" to "patch"))

    // then
    // branch 1
    completeNextTask(instanceEvent.processInstanceKey, "notify-others")

    // branch 2
    completeNextTask(instanceEvent.processInstanceKey, "github-release-labels")
    completeNextTask(instanceEvent.processInstanceKey, "github-generate-changelog")

    // branch 5
    completeNextTask(instanceEvent.processInstanceKey, "merge-with-stable")

    // branch 6
    completeNextTask(instanceEvent.processInstanceKey, "update-getting-started")

    // after parallel gateway
    completeNextTask(instanceEvent.processInstanceKey, "build-benchmark-images")
    completeNextTask(instanceEvent.processInstanceKey, "update-long-running")

    // last task
    completeNextTask(instanceEvent.processInstanceKey, "create-appointment-for-next")

    testHelper.assertThatProcessIsCompleted(instanceEvent.processInstanceKey)
  }

  private fun completeNextTask(key: Long, elementId: String) {
    testHelper.assertThatUserTaskActivated(key, elementId)
    testHelper.completeUserTask(key, elementId)
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
