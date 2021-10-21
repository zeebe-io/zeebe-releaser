package io.zeebe.release

import io.camunda.zeebe.client.api.response.ProcessInstanceEvent
import io.camunda.zeebe.model.bpmn.Bpmn
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent
import io.camunda.zeebe.protocol.record.value.BpmnElementType
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.camunda.community.eze.RecordStream.withElementType
import org.camunda.community.eze.RecordStream.withIntent
import org.camunda.community.eze.RecordStream.withProcessInstanceKey
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PatchReleaseProcessTest: BaseProcessTest() {

    @BeforeEach
    fun beforeEach() {
        deployProcess()
        deployCallActivityMockProcess()
    }

    @Test
    fun `should be able to create an instance`() {
        // when
        val instanceEvent = createInstance()

        // then
        assertThat(instanceEvent.processInstanceKey).isGreaterThan(0)
    }

    @Test
    fun `should complete process`() {
        // when
        val instanceEvent = createInstance()

        // then
        assertThatUserTaskActivated(instanceEvent.processInstanceKey, "collect-required-data")
        completeUserTask(instanceEvent.processInstanceKey, "collect-required-data")

        await.untilAsserted {
            val callActivityRecord = recordStream.processInstanceRecords()
                .withProcessInstanceKey(instanceEvent.processInstanceKey)
                .withElementType(BpmnElementType.CALL_ACTIVITY)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .firstOrNull()

            assertThat(callActivityRecord).isNotNull
        }

        assertThatProcessIsCompleted(instanceEvent.processInstanceKey)
    }

    private fun deployProcess() {
        client.newDeployCommand()
            .addResourceFromClasspath("patch_release.bpmn")
            .send()
            .join()
    }

    private fun deployCallActivityMockProcess() {
        client.newDeployCommand()
            .addProcessModel(Bpmn.createExecutableProcess("zeebe-release-process")
                .startEvent()
                .endEvent()
                .done(), "zeebe-release-process.bpmn")
            .send()
            .join()
    }

    private fun createInstance(): ProcessInstanceEvent {
        return client.newCreateInstanceCommand()
            .bpmnProcessId("zeebe-patch-release-process")
            .latestVersion()
            .send()
            .join()
    }
}
