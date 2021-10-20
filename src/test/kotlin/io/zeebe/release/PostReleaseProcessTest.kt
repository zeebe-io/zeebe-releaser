package io.zeebe.release

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.DeploymentEvent
import io.camunda.zeebe.protocol.record.intent.IncidentIntent
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.eze.EmbeddedZeebeEngine
import org.camunda.community.eze.RecordStreamSource
import org.camunda.community.eze.ZeebeEngineClock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.awaitility.kotlin.await
import org.camunda.community.eze.RecordStream.withIntent

@EmbeddedZeebeEngine
class PostReleaseProcessTest {

    val POST_RELEASE_PROCESS = this::class.java.getResource("post_release.bpmn")

    // the extension injects the fields before running the test
    lateinit var client: ZeebeClient
    lateinit var recordStream: RecordStreamSource
    lateinit var clock: ZeebeEngineClock

    @BeforeEach
    fun `set up tests`() {
        deployProcess()
    }

    @Test
    fun `should be able to create an instance`() {
        // given

        // when
        val instanceEvent =
            createInstance()

        // then
        assertThat(instanceEvent.processInstanceKey).isPositive().isGreaterThan(0)
    }

    @Test
    fun `should create incident without payload`() {
        // given

        // when
        val instanceEvent = createInstance()

        // then
        await.untilAsserted {
            val incidentCreated = recordStream
                .incidentRecords()
                .withIntent(IncidentIntent.CREATED)
                .firstOrNull()

            assertThat(incidentCreated).isNotNull
            assertThat(incidentCreated?.value?.processInstanceKey).isEqualTo(instanceEvent.processInstanceKey)
        }
    }

    private fun createInstance() = client.newCreateInstanceCommand()
        .bpmnProcessId("zeebe-post-release")
        .latestVersion()
        .send()
        .join()

    private fun deployProcess(): DeploymentEvent {
        val deploymentEvent = client.newDeployCommand()
            .addResourceFromClasspath("post_release.bpmn")
            .send()
            .join()
        return deploymentEvent
    }
}
