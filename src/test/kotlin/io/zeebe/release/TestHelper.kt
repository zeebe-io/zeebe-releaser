package io.zeebe.release

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent
import io.camunda.zeebe.process.test.api.ZeebeTestEngine
import io.camunda.zeebe.process.test.assertions.BpmnAssert
import io.camunda.zeebe.process.test.filters.RecordStream
import io.camunda.zeebe.process.test.filters.StreamFilter
import io.camunda.zeebe.protocol.record.Record
import io.camunda.zeebe.protocol.record.intent.JobIntent
import io.camunda.zeebe.protocol.record.value.JobRecordValue
import org.assertj.core.api.Assertions
import org.awaitility.kotlin.await
import java.time.Duration
import java.util.stream.Collectors

class TestHelper(val client: ZeebeClient, val engine: ZeebeTestEngine) {

    fun assertThatElementIsActive(processInstanceEvent: ProcessInstanceEvent, elementId: String) {
        engine.waitForIdleState(Duration.ofSeconds(3))
        BpmnAssert.assertThat(processInstanceEvent).isWaitingAtElements(elementId)
    }

    fun completeUserTask(processInstanceKey: Long, elementId: String) {
        await.untilAsserted {
            val userTaskJobs =
                StreamFilter.jobRecords(RecordStream.of(engine.recordStreamSource))
                    .withElementId(elementId)
                    .withIntent(JobIntent.CREATED)
                    .stream().collect(Collectors.toList())

            var userTaskJob: Record<JobRecordValue>? = null
            if (userTaskJobs.size != 0) {
                userTaskJob = userTaskJobs[userTaskJobs.size - 1]
            }

            Assertions.assertThat(userTaskJob).isNotNull
            Assertions.assertThat(userTaskJob?.value?.processInstanceKey).isEqualTo(processInstanceKey)
            Assertions.assertThat(userTaskJob?.value?.elementId).isEqualTo(elementId)
            client.newCompleteCommand(userTaskJob!!.key).send().join()
        }
        engine.waitForIdleState(Duration.ofSeconds(3))
    }

    fun assertThatProcessIsCompleted(processInstanceEvent: ProcessInstanceEvent) {
        engine.waitForIdleState(Duration.ofSeconds(3))
        BpmnAssert.assertThat(processInstanceEvent).isCompleted;
    }

    fun assertThatCalledProcessActivated(processInstanceEvent: ProcessInstanceEvent, processId: String) {
        engine.waitForIdleState(Duration.ofSeconds(3))
        BpmnAssert.assertThat(processInstanceEvent)
            .extractingLatestCalledProcess(processId)
            .isActive
    }

    fun assertThatCalledProcessCompleted(processInstanceEvent: ProcessInstanceEvent, processId: String) {
        engine.waitForIdleState(Duration.ofSeconds(3))
        BpmnAssert.assertThat(processInstanceEvent)
            .extractingLatestCalledProcess(processId)
            .isCompleted
    }
}
