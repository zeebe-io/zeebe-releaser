package io.zeebe.release

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent
import io.camunda.zeebe.model.bpmn.Bpmn
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.eze.EmbeddedZeebeEngine
import org.camunda.community.eze.RecordStreamSource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
