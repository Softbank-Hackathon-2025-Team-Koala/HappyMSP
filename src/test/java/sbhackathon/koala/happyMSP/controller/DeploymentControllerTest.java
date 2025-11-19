package sbhackathon.koala.happyMSP.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import sbhackathon.koala.happyMSP.dto.DeploymentRequest;
import sbhackathon.koala.happyMSP.dto.ServiceDeployRequest;
import sbhackathon.koala.happyMSP.service.K8sDeploymentService;
import software.amazon.awssdk.services.ecr.EcrClient;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DeploymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private K8sDeploymentService k8sDeploymentService;

    @MockBean
    private EcrClient ecrClient;  // AWS SDK Mock

    @Test
    void deploy_성공() throws Exception {
        // given
        DeploymentRequest request = new DeploymentRequest(
                "msa-demo",
                Arrays.asList(
                        new ServiceDeployRequest("apigateway", "242552818991.dkr.ecr.us-east-1.amazonaws.com/msa-demo-apigateway:82c96df"),
                        new ServiceDeployRequest("auth", "242552818991.dkr.ecr.us-east-1.amazonaws.com/msa-demo-auth:82c96df")
                )
        );

        doNothing().when(k8sDeploymentService).deploy(any(DeploymentRequest.class));

        // when & then
        mockMvc.perform(post("/api/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("배포가 성공적으로 완료되었습니다."))
                .andExpect(jsonPath("$.deployedServices[0]").value("apigateway"))
                .andExpect(jsonPath("$.deployedServices[1]").value("auth"));
    }

    @Test
    void deploy_프로젝트명_없음() throws Exception {
        // given
        DeploymentRequest request = new DeploymentRequest(
                null,
                Arrays.asList(
                        new ServiceDeployRequest("apigateway", "242552818991.dkr.ecr.us-east-1.amazonaws.com/msa-demo-apigateway:82c96df")
                )
        );

        // when & then
        mockMvc.perform(post("/api/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("VALIDATION_ERROR"));
    }

    @Test
    void deploy_서비스목록_없음() throws Exception {
        // given
        DeploymentRequest request = new DeploymentRequest(
                "msa-demo",
                Collections.emptyList()
        );

        // when & then
        mockMvc.perform(post("/api/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("VALIDATION_ERROR"));
    }

    @Test
    void deploy_배포_실패() throws Exception {
        // given
        DeploymentRequest request = new DeploymentRequest(
                "msa-demo",
                Arrays.asList(
                        new ServiceDeployRequest("apigateway", "242552818991.dkr.ecr.us-east-1.amazonaws.com/msa-demo-apigateway:82c96df")
                )
        );

        doThrow(new RuntimeException("kubectl apply 실패"))
                .when(k8sDeploymentService).deploy(any(DeploymentRequest.class));

        // when & then
        mockMvc.perform(post("/api/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }
}

