package io.zeebe.release

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent
import io.camunda.zeebe.model.bpmn.Bpmn
import io.camunda.zeebe.process.test.api.ZeebeTestEngine
import io.camunda.zeebe.process.test.assertions.BpmnAssert
import io.camunda.zeebe.process.test.extension.testcontainer.ZeebeProcessTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

@ZeebeProcessTest
class PatchReleaseProcessTest {

    lateinit var client: ZeebeClient
    lateinit var engine: ZeebeTestEngine
    lateinit var testHelper: TestHelper

    @BeforeEach
    fun beforeEach() {
        testHelper = TestHelper(client, engine)
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
        testHelper.assertThatElementIsActive(instanceEvent, "collect-required-data"
        )
        testHelper.completeUserTask(instanceEvent.processInstanceKey, "collect-required-data")

        engine.waitForIdleState(Duration.ofSeconds(1))
        BpmnAssert.assertThat(instanceEvent).isCompleted
    }

    private fun deployProcess() {
        client.newDeployResourceCommand()
            .addResourceFromClasspath("patch_release.bpmn")
            .addResourceFromClasspath("get_slack_id.dmn")
            .send().join()
    }

    private fun deployCallActivityMockProcess() {
        client
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess("zeebe-release-process").startEvent().endEvent().done(),
                "zeebe-release-process.bpmn"
            )
            .send()
            .join()
    }

    private fun createInstance(): ProcessInstanceEvent {
        return client
            .newCreateInstanceCommand()
            .bpmnProcessId("zeebe-patch-release-process")
            .latestVersion()
            .variables(mapOf("release_manager" to "remco"))
            .send()
            .join()
    }
}
