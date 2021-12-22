package io.zeebe.release

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.protocol.record.intent.JobIntent
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent
import io.camunda.zeebe.protocol.record.value.BpmnElementType
import org.assertj.core.api.Assertions
import org.awaitility.kotlin.await
import org.camunda.community.eze.RecordStream.withElementType
import org.camunda.community.eze.RecordStream.withIntent
import org.camunda.community.eze.RecordStream.withProcessInstanceKey
import org.camunda.community.eze.RecordStreamSource

class TestHelper(val client: ZeebeClient, val recordStream: RecordStreamSource) {

    fun assertThatUserTaskActivated(processInstanceKey: Long, elementId: String, amount: Int = 1) {
        await.untilAsserted {
            val amountOfActivatedTasks = recordStream.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.USER_TASK)
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .filter { record -> record.value.elementId == elementId }
                .count()

            Assertions.assertThat(amountOfActivatedTasks).isEqualTo(amount)
        }
    }

    fun completeUserTask(processInstanceKey: Long, elementId: String) {
        await.untilAsserted {
            val userTaskJob = recordStream.jobRecords()
                .withJobType("io.camunda.zeebe:userTask")
                .withIntent(JobIntent.CREATED)
                .findLast { record -> record.value.elementId == elementId }

            Assertions.assertThat(userTaskJob).isNotNull
            Assertions.assertThat(userTaskJob?.value?.processInstanceKey).isEqualTo(processInstanceKey)
            Assertions.assertThat(userTaskJob?.value?.elementId).isEqualTo(elementId)
            client.newCompleteCommand(userTaskJob!!.key).send().join()
        }
    }

    fun assertThatProcessIsCompleted(processInstanceKey: Long) {
        await.untilAsserted {
            val processCompletedRecord = recordStream.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .firstOrNull()

            Assertions.assertThat(processCompletedRecord).isNotNull
        }
    }

    fun assertThatCalledProcessActivated(processInstanceKey: Long, processId: String) {
        await.untilAsserted {
            Assertions.assertThat(recordStream.processInstanceRecords()
                .withParentProcessInstanceKey(processInstanceKey)
                .withBpmnProcessId(processId)
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .firstOrNull()
            ).isNotNull
        }
    }

    fun assertThatCalledProcessCompleted(processInstanceKey: Long, processId: String) {
        await.untilAsserted {
            Assertions.assertThat(recordStream.processInstanceRecords()
                .withParentProcessInstanceKey(processInstanceKey)
                .withBpmnProcessId(processId)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .firstOrNull()
            ).isNotNull
        }
    }
}
