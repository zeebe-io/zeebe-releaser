package io.zeebe.release

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.model.bpmn.Bpmn
import org.assertj.core.api.Assertions.assertThat
import org.camunda.community.eze.EmbeddedZeebeEngine
import org.camunda.community.eze.RecordStreamSource
import org.camunda.community.eze.ZeebeEngineClock
import org.junit.jupiter.api.Test

@EmbeddedZeebeEngine
class PostReleaseProcessTest {

    val POST_RELEASE_PROCESS = this::class.java.getResource("post_release.bpmn")

    // the extension injects the fields before running the test
    lateinit var client: ZeebeClient
    lateinit var recordStream: RecordStreamSource
    lateinit var clock: ZeebeEngineClock

    @Test
    fun `should deploy process`() {
        // given
        
        // when
        val deploymentEvent = client.newDeployCommand()
            .addResourceFromClasspath("post_release.bpmn")
            .send()
            .join()
        
        // then deployment was successful
        assertThat(deploymentEvent.key).isPositive().isGreaterThan(0)
    }
}
