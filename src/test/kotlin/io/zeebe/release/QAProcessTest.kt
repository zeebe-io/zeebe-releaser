package io.zeebe.release

import io.camunda.zeebe.client.api.response.ProcessInstanceEvent
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent
import io.camunda.zeebe.protocol.record.value.BpmnElementType
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.camunda.community.eze.RecordStream.withElementType
import org.camunda.community.eze.RecordStream.withIntent
import org.camunda.community.eze.RecordStream.withProcessInstanceKey
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.*


class QAProcessTest : BaseProcessTest() {

    @BeforeEach
    fun beforeEach() {
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
        val variables = hashMapOf(
            "qa_build_success" to "true",
            "release_type" to "patch")

        // when
        val instanceEvent = createInstance(variables)

        // then
        assertThatUserTaskActivated(instanceEvent.processInstanceKey, "build-ci-with-qa")
        completeUserTask(instanceEvent.processInstanceKey, "build-ci-with-qa")
        assertThatUserTaskActivated(instanceEvent.processInstanceKey, "check-qa-results")
        completeUserTask(instanceEvent.processInstanceKey, "check-qa-results")
        assertThatProcessIsCompleted(instanceEvent.processInstanceKey)
    }

    @Test
    fun `should loop when qa failed`() {
        // given
        val variables = hashMapOf("qa_build_success" to "false")

        // when
        val instanceEvent = createInstance(variables)

        // then
        assertThatUserTaskActivated(instanceEvent.processInstanceKey, "build-ci-with-qa")
        completeUserTask(instanceEvent.processInstanceKey, "build-ci-with-qa")
        assertThatUserTaskActivated(instanceEvent.processInstanceKey, "check-qa-results")
        completeUserTask(instanceEvent.processInstanceKey, "check-qa-results")
        assertThatUserTaskActivated(instanceEvent.processInstanceKey, "fix-problems")
        completeUserTask(instanceEvent.processInstanceKey, "fix-problems")
        assertThatUserTaskActivated(instanceEvent.processInstanceKey, "build-ci-with-qa")
        completeUserTask(instanceEvent.processInstanceKey, "build-ci-with-qa")
    }

    @Test
    fun `should create a benchmark when not a patch release`() {
        // given
        val date = ZonedDateTime.now()
        val variables = hashMapOf(
            "qa_build_success" to "true",
            "release_type" to "minor",
            "release_date" to date.format(dateFormatter))

        // when
        val instanceEvent = createInstance(variables)

        // then
        assertThatUserTaskActivated(instanceEvent.processInstanceKey, "build-ci-with-qa")
        completeUserTask(instanceEvent.processInstanceKey, "build-ci-with-qa")
        assertThatUserTaskActivated(instanceEvent.processInstanceKey, "check-qa-results")
        completeUserTask(instanceEvent.processInstanceKey, "check-qa-results")
        assertThatUserTaskActivated(instanceEvent.processInstanceKey, "setup-benchmark")
        completeUserTask(instanceEvent.processInstanceKey, "setup-benchmark")
        assertThatUserTaskActivated(instanceEvent.processInstanceKey, "delete-benchmark")
        completeUserTask(instanceEvent.processInstanceKey, "delete-benchmark")
        assertThatProcessIsCompleted(instanceEvent.processInstanceKey)
    }

    @Test
    fun `should rebuild with QA when mesage is triggered`() {
        // given
        val date = ZonedDateTime.now().plusHours(1)
        val variables = hashMapOf(
            "qa_build_success" to "true",
            "release_type" to "minor",
            "release_date" to dateFormatter.format(date))

        // when
        val instanceEvent = createInstance(variables)

        // then
        assertThatUserTaskActivated(instanceEvent.processInstanceKey, "build-ci-with-qa")
        completeUserTask(instanceEvent.processInstanceKey, "build-ci-with-qa")
        assertThatUserTaskActivated(instanceEvent.processInstanceKey, "check-qa-results")
        completeUserTask(instanceEvent.processInstanceKey, "check-qa-results")
        assertThatUserTaskActivated(instanceEvent.processInstanceKey, "setup-benchmark")
        completeUserTask(instanceEvent.processInstanceKey, "setup-benchmark")
        await.untilAsserted {
            val timerRecord = recordStream.timerRecords().lastOrNull()

            assertThat(timerRecord).isNotNull
            assertThat(timerRecord!!.value.processInstanceKey).isEqualTo(instanceEvent.processInstanceKey)
        }
        client.newPublishMessageCommand()
            .messageName("recreate_benchmark")
            .correlationKey("release_version")
            .send()
            .join()
        assertThatUserTaskActivated(instanceEvent.processInstanceKey, "build-ci-with-qa")
        completeUserTask(instanceEvent.processInstanceKey, "build-ci-with-qa")
    }

    @Test
    fun `daily task should be triggered for patch release`() {
        // given
        val variables = hashMapOf("release_type" to "patch")

        // when
        val instanceEvent = createInstance(variables)
        clock.increaseTime(Duration.ofDays(1))

        // then
        await.untilAsserted {
            val eventSubProcessRecord = recordStream.processInstanceRecords()
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
        val variables = hashMapOf(
            "release_type" to "minor",
            "qa_benchmark_ok" to "false",
            "qa_benchmark_outdated" to "true")

        // when
        val instanceEvent = createInstance(variables)
        clock.increaseTime(Duration.ofDays(1))

        // then
        assertThatUserTaskActivated(instanceEvent.processInstanceKey, "check-benchmark")
        completeUserTask(instanceEvent.processInstanceKey, "check-benchmark")
        assertThatUserTaskActivated(instanceEvent.processInstanceKey, "check-new-commits")
        completeUserTask(instanceEvent.processInstanceKey, "check-new-commits")
        assertThatUserTaskActivated(instanceEvent.processInstanceKey, "create-issue")
        completeUserTask(instanceEvent.processInstanceKey, "create-issue")
        assertThatUserTaskActivated(instanceEvent.processInstanceKey, "recreate-benchmark")
        completeUserTask(instanceEvent.processInstanceKey, "recreate-benchmark")
    }

    private fun deployProcess() {
        client.newDeployCommand()
            .addResourceFromClasspath("qa.bpmn")
            .send()
            .join()
    }

    private fun createInstance(variables: Map<String, Any>): ProcessInstanceEvent {
        val instanceEvent = client.newCreateInstanceCommand()
            .bpmnProcessId("zeebe-release-qa")
            .latestVersion()
            .variables(variables)
            .send()
            .join()

        await.untilAsserted {
            val processRecord = recordStream.processInstanceRecords()
                .withProcessInstanceKey(instanceEvent.processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .firstOrNull()

            assertThat(processRecord).isNotNull
        }

        return instanceEvent
    }
}
