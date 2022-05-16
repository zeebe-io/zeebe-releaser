package io.zeebe.release

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent
import io.camunda.zeebe.protocol.record.intent.TimerIntent
import io.camunda.zeebe.protocol.record.value.BpmnElementType
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.camunda.community.eze.EmbeddedZeebeEngine
import org.camunda.community.eze.RecordStream.withElementType
import org.camunda.community.eze.RecordStream.withIntent
import org.camunda.community.eze.RecordStream.withProcessInstanceKey
import org.camunda.community.eze.RecordStreamSource
import org.camunda.community.eze.ZeebeEngineClock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@EmbeddedZeebeEngine
class QAProcessTest {

  lateinit var client: ZeebeClient
  lateinit var recordStream: RecordStreamSource
  lateinit var clock: ZeebeEngineClock
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
    assertThat(instanceEvent.processInstanceKey).isGreaterThan(0)
  }

  @Test
  fun `should complete when qa success and patch release`() {
    // given
    val variables = hashMapOf("qa_build_success" to "true", "release_type" to "patch")

    // when
    val instanceEvent = createInstance(variables)

    // then
    testHelper.assertThatUserTaskActivated(instanceEvent.processInstanceKey, "build-ci-with-qa")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "build-ci-with-qa")
    testHelper.assertThatUserTaskActivated(instanceEvent.processInstanceKey, "check-qa-results")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "check-qa-results")
    testHelper.assertThatProcessIsCompleted(instanceEvent.processInstanceKey)
  }

  @Test
  fun `should loop when qa failed`() {
    // given
    val variables = hashMapOf("qa_build_success" to "false")

    // when
    val instanceEvent = createInstance(variables)

    // then
    testHelper.assertThatUserTaskActivated(instanceEvent.processInstanceKey, "build-ci-with-qa")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "build-ci-with-qa")
    testHelper.assertThatUserTaskActivated(instanceEvent.processInstanceKey, "check-qa-results")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "check-qa-results")
    testHelper.assertThatUserTaskActivated(instanceEvent.processInstanceKey, "fix-problems")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "fix-problems")
    testHelper.assertThatUserTaskActivated(instanceEvent.processInstanceKey, "build-ci-with-qa", 2)
  }

  @Test
  fun `should create a benchmark when not a patch release`() {
    // given
    val date = ZonedDateTime.now()
    val variables =
      hashMapOf(
        "qa_build_success" to "true",
        "release_type" to "minor",
        "release_date" to date.format(dateFormatter),
        "release_version" to "1.3.0")

    // when
    val instanceEvent = createInstance(variables)

    // then
    testHelper.assertThatUserTaskActivated(instanceEvent.processInstanceKey, "build-ci-with-qa")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "build-ci-with-qa")
    testHelper.assertThatUserTaskActivated(instanceEvent.processInstanceKey, "check-qa-results")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "check-qa-results")
    testHelper.assertThatUserTaskActivated(instanceEvent.processInstanceKey, "setup-benchmark")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "setup-benchmark")
    testHelper.assertThatUserTaskActivated(instanceEvent.processInstanceKey, "delete-benchmark")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "delete-benchmark")
    testHelper.assertThatProcessIsCompleted(instanceEvent.processInstanceKey)
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
    testHelper.assertThatUserTaskActivated(instanceEvent.processInstanceKey, "build-ci-with-qa")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "build-ci-with-qa")
    testHelper.assertThatUserTaskActivated(instanceEvent.processInstanceKey, "check-qa-results")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "check-qa-results")
    testHelper.assertThatUserTaskActivated(instanceEvent.processInstanceKey, "setup-benchmark")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "setup-benchmark")
    client
      .newPublishMessageCommand()
      .messageName("recreate_benchmark")
      .correlationKey("1.3.0")
      .send()
      .join()
    testHelper.assertThatUserTaskActivated(instanceEvent.processInstanceKey, "build-ci-with-qa", 2)
  }

  @Test
  fun `daily task should be triggered for patch release`() {
    // given
    val variables = hashMapOf("release_type" to "patch")

    // when
    val instanceEvent = createInstance(variables)
    await.untilAsserted {
      val timerRecord =
        recordStream
          .timerRecords()
          .withIntent(TimerIntent.CREATED)
          .filter { record -> record.value.targetElementId == "daily-timer" }
          .firstOrNull()
      assertThat(timerRecord).isNotNull
    }
    clock.increaseTime(getDurationToNextWeekday())

    // then
    await.untilAsserted {
      val eventSubProcessRecord =
        recordStream
          .processInstanceRecords()
          .withProcessInstanceKey(instanceEvent.processInstanceKey)
          .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
          .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
          .firstOrNull()

      assertThat(eventSubProcessRecord).isNotNull
    }
  }

  @Test
  fun `daily task should create tasks for non-patch release`() {
    // given
    val variables =
      hashMapOf(
        "release_type" to "minor", "qa_benchmark_ok" to "false", "qa_benchmark_outdated" to "true")

    // when
    val instanceEvent = createInstance(variables)
    await.untilAsserted {
      val timerRecord =
        recordStream
          .timerRecords()
          .withIntent(TimerIntent.CREATED)
          .filter { record -> record.value.targetElementId == "daily-timer" }
          .firstOrNull()
      assertThat(timerRecord).isNotNull
    }
    clock.increaseTime(getDurationToNextWeekday())

    // then
    testHelper.assertThatUserTaskActivated(instanceEvent.processInstanceKey, "check-benchmark")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "check-benchmark")
    testHelper.assertThatUserTaskActivated(instanceEvent.processInstanceKey, "check-new-commits")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "check-new-commits")
    testHelper.assertThatUserTaskActivated(instanceEvent.processInstanceKey, "create-issue")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "create-issue")
    testHelper.assertThatUserTaskActivated(instanceEvent.processInstanceKey, "recreate-benchmark")
    testHelper.completeUserTask(instanceEvent.processInstanceKey, "recreate-benchmark")
  }

  private fun deployProcess() {
    client.newDeployCommand().addResourceFromClasspath("qa.bpmn").send().join()
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
