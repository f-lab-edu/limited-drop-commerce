package com.mist.commerce;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.mist.commerce.support.MySqlContainerTestSupport;
import org.junit.jupiter.api.Test;

class ContainerTest extends MySqlContainerTestSupport {

    @Test
    void running() {
        assertThat(mysql.isRunning()).isTrue();
    }
}
