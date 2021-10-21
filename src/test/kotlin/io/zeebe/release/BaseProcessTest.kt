package io.zeebe.release

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.protocol.record.intent.JobIntent
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent
import io.camunda.zeebe.protocol.record.value.BpmnElementType
import org.assertj.core.api.Assertions
import org.awaitility.kotlin.await
import org.camunda.community.eze.EmbeddedZeebeEngine
import org.camunda.community.eze.RecordStream.withElementType
import org.camunda.community.eze.RecordStream.withIntent
import org.camunda.community.eze.RecordStream.withJobType
import org.camunda.community.eze.RecordStream.withProcessInstanceKey
import org.camunda.community.eze.RecordStreamSource

@EmbeddedZeebeEngine
open class BaseProcessTest {

    lateinit var client: ZeebeClient
    lateinit var recordStream: RecordStreamSource

    fun assertThatUserTaskActivated(processInstanceKey: Long, elementId: String) {
        await.untilAsserted {
            val userTaskRecord = recordStream.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.USER_TASK)
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .findLast { record -> record.value.elementId == elementId }

            Assertions.assertThat(userTaskRecord).isNotNull
            Assertions.assertThat(userTaskRecord!!.value.elementId).isEqualTo(elementId)
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
}
