package io.zeebe.template;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class AppTest {

  @Test
  public void testFoo() {
    String actual = new App().foo();

    Assertions.assertThat(actual).isEqualTo("Hello World");
  }
}
