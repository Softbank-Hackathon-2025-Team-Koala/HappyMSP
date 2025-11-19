package sbhackathon.koala.happyMSP.infra;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class KubectlExecutorManualTest {

    private static final Logger logger = LoggerFactory.getLogger(KubectlExecutorManualTest.class);

    @Test
    void kubectl_사용가능_확인() {
        // given
        KubectlExecutor executor = new KubectlExecutor();

        // when
        boolean available = executor.isKubectlAvailable();

        // then
        logger.info("kubectl 사용 가능: {}", available);
        assertThat(available).isTrue();
    }
}

