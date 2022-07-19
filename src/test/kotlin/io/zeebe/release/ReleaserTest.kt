package io.zeebe.release

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.model.bpmn.Bpmn
import io.camunda.zeebe.process.test.api.ZeebeTestEngine
import io.camunda.zeebe.process.test.extension.testcontainer.ZeebeProcessTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@ZeebeProcessTest
class ReleaserTest {

  // the extension injects the fields before running the test
  lateinit var client: ZeebeClient
  lateinit var engine: ZeebeTestEngine

  @Test
  fun `should complete process`() {
    // given
    val process = Bpmn.createExecutableProcess("process").startEvent().endEvent().done()

    client.newDeployCommand().addProcessModel(process, "process.bpmn").send().join()

    // when
    val processInstanceResult =
      client
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .variables(mapOf("x" to 1))
        .withResult()
        .send()
        .join()

    // then
    assertThat(processInstanceResult.variablesAsMap).containsEntry("x", 1)
  }
}
