package io.zeebe.release

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent
import io.camunda.zeebe.model.bpmn.Bpmn
import io.camunda.zeebe.protocol.record.intent.JobIntent
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent
import io.camunda.zeebe.protocol.record.value.BpmnElementType
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.eze.EmbeddedZeebeEngine
import org.camunda.community.eze.RecordStream.withElementType
import org.camunda.community.eze.RecordStream.withIntent
import org.camunda.community.eze.RecordStream.withJobType
import org.camunda.community.eze.RecordStream.withProcessInstanceKey
import org.camunda.community.eze.RecordStreamSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.awaitility.kotlin.await

@EmbeddedZeebeEngine
class PatchReleaseProcessTest {

    // the extension injects the fields before running the test
    lateinit var client: ZeebeClient
    lateinit var recordStream: RecordStreamSource

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
        await.untilAsserted {
            val userTaskRecord = recordStream.processInstanceRecords()
                .withProcessInstanceKey(instanceEvent.processInstanceKey)
                .withElementType(BpmnElementType.USER_TASK)
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .firstOrNull()

            assertThat(userTaskRecord).isNotNull
        }

        await.untilAsserted {
            val userTaskJob = recordStream.jobRecords()
                .withJobType("io.camunda.zeebe:userTask")
                .withIntent(JobIntent.CREATED)
                .firstOrNull()

            assertThat(userTaskJob?.value?.processInstanceKey).isEqualTo(instanceEvent.processInstanceKey)

            client.newCompleteCommand(userTaskJob!!.key).send().join()
        }

        await.untilAsserted {
            val callActivityRecord = recordStream.processInstanceRecords()
                .withProcessInstanceKey(instanceEvent.processInstanceKey)
                .withElementType(BpmnElementType.CALL_ACTIVITY)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .firstOrNull()

            assertThat(callActivityRecord).isNotNull
        }

        await.untilAsserted {
            val processCompletedRecord = recordStream.processInstanceRecords()
                .withProcessInstanceKey(instanceEvent.processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .firstOrNull()

            assertThat(processCompletedRecord).isNotNull
        }
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
