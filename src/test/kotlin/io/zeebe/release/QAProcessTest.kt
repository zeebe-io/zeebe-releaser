package io.zeebe.release

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent
import io.camunda.zeebe.process.test.api.ZeebeTestEngine
import io.camunda.zeebe.process.test.assertions.BpmnAssert
import io.camunda.zeebe.process.test.extension.ZeebeProcessTest
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@ZeebeProcessTest
class QAProcessTest {

  lateinit var client: ZeebeClient
  lateinit var engine: ZeebeTestEngine
  lateinit var testHelper: TestHelper
  private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

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
    assertThat(instanceEvent.processInstanceKey).isGreaterThan(0)
  }

  @Test
  fun `should complete when qa success and patch release`() {
    // given
    val variables = hashMapOf("qa_build_success" to "true", "release_type" to "patch")

    // when
    val instanceEvent = createInstance(variables)

    // then
    testHelper.assertThatElementIsActive(instanceEvent, "build-ci-with-qa")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "build-ci-with-qa")
    testHelper.assertThatElementIsActive(instanceEvent, "check-qa-results")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "check-qa-results")
    testHelper.assertThatProcessIsCompleted(instanceEvent)
  }

  @Test
  fun `should loop when qa failed`() {
    // given
    val variables = hashMapOf("qa_build_success" to "false")

    // when
    val instanceEvent = createInstance(variables)

    // then
    testHelper.assertThatElementIsActive(instanceEvent, "build-ci-with-qa")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "build-ci-with-qa")
    testHelper.assertThatElementIsActive(instanceEvent, "check-qa-results")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "check-qa-results")
    testHelper.assertThatElementIsActive(instanceEvent, "fix-problems")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "fix-problems")
    testHelper.assertThatElementIsActive(instanceEvent, "build-ci-with-qa")
  }

  @Test
  fun `should create a benchmark when not a patch release`() {
    // given
    val date = ZonedDateTime.now()
    val releaseDate = date.plusHours(1)
    val variables =
      hashMapOf(
        "qa_build_success" to "true",
        "release_type" to "minor",
        "release_date" to releaseDate.format(dateFormatter),
        "release_version" to "1.3.0")

    // when
    val instanceEvent = createInstance(variables)

    // then
    testHelper.assertThatElementIsActive(instanceEvent, "build-ci-with-qa")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "build-ci-with-qa")
    testHelper.assertThatElementIsActive(instanceEvent, "check-qa-results")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "check-qa-results")
    testHelper.assertThatElementIsActive(instanceEvent, "setup-benchmark")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "setup-benchmark")
    engine.waitForIdleState(Duration.ofSeconds(3))
    engine.increaseTime(Duration.ofHours(1))
    engine.waitForIdleState(Duration.ofSeconds(3))
    testHelper.assertThatElementIsActive(instanceEvent, "delete-benchmark")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "delete-benchmark")
    testHelper.assertThatProcessIsCompleted(instanceEvent)
  }

  @Test
  fun `should rebuild with QA when message is triggered`() {
    // given
    val date = ZonedDateTime.now().plusHours(1)
    val variables =
      hashMapOf(
        "qa_build_success" to "true",
        "release_type" to "minor",
        "release_date" to dateFormatter.format(date),
        "release_version" to "1.3.0")

    // when
    val instanceEvent = createInstance(variables)

    // then
    testHelper.assertThatElementIsActive(instanceEvent, "build-ci-with-qa")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "build-ci-with-qa")
    testHelper.assertThatElementIsActive(instanceEvent, "check-qa-results")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "check-qa-results")
    testHelper.assertThatElementIsActive(instanceEvent, "setup-benchmark")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "setup-benchmark")
    client
      .newPublishMessageCommand()
      .messageName("recreate_benchmark")
      .correlationKey("1.3.0")
      .send()
      .join()
    testHelper.assertThatElementIsActive(instanceEvent, "build-ci-with-qa")
  }

  @Test
  fun `daily task should be triggered for patch release`() {
    // given
    val variables = hashMapOf("release_type" to "patch")

    // when
    val instanceEvent = createInstance(variables)
    engine.waitForIdleState(Duration.ofSeconds(3))
    engine.increaseTime(getDurationToNextWeekday())
    engine.waitForIdleState(Duration.ofSeconds(3))

    // then
    BpmnAssert.assertThat(instanceEvent).hasPassedElement("daily-task-event-subprocess")
  }

  @Test
  fun `daily task should create tasks for non-patch release`() {
    // given
    val variables =
      hashMapOf(
        "release_type" to "minor", "qa_benchmark_ok" to "false", "qa_benchmark_outdated" to "true")

    // when
    val instanceEvent = createInstance(variables)
    engine.waitForIdleState(Duration.ofSeconds(3))
    engine.increaseTime(getDurationToNextWeekday())

    // then
    testHelper.assertThatElementIsActive(instanceEvent, "check-benchmark")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "check-benchmark")
    testHelper.assertThatElementIsActive(instanceEvent, "check-new-commits")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "check-new-commits")
    testHelper.assertThatElementIsActive(instanceEvent, "create-issue")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "create-issue")
    testHelper.assertThatElementIsActive(instanceEvent, "recreate-benchmark")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "recreate-benchmark")
  }

  private fun deployProcess() {
    client.newDeployResourceCommand().addResourceFromClasspath("qa.bpmn").send().join()
  }

  private fun createInstance(variables: Map<String, Any>): ProcessInstanceEvent {
    return client
      .newCreateInstanceCommand()
      .bpmnProcessId("zeebe-release-qa")
      .latestVersion()
      .variables(variables)
      .send()
      .join()
  }

  private fun getDurationToNextWeekday(): Duration {
    val calendar = Calendar.getInstance()
    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    return if (dayOfWeek == 6) Duration.ofDays(3) else Duration.ofDays(1)
  }
}
