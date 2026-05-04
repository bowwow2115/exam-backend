package com.psh.exam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.psh.exam.account.AccountDtos.LoginRequest;
import com.psh.exam.account.AccountDtos.SignUpRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityPublicApiIntegrationTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @Autowired
    SecurityPublicApiIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    void publicSignupIgnoresInvalidAuthorizationHeader() throws Exception {
        SignUpRequest request = new SignUpRequest(
                "user-" + UUID.randomUUID() + "@example.com",
                "password123",
                "public user"
        );

        mockMvc.perform(post("/api/accounts/signup")
                        .header("Authorization", "Basic invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void loginReturnsJwtToken() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String password = "password123";
        signUp(email, password);

        mockMvc.perform(post("/api/accounts/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.account.email").value(email));
    }

    @Test
    void examListRequiresJwtToken() throws Exception {
        mockMvc.perform(get("/api/exams"))
                .andExpect(status().isUnauthorized());

        String token = login("user-" + UUID.randomUUID() + "@example.com", "password123");

        mockMvc.perform(get("/api/exams")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void signUp(String email, String password) throws Exception {
        SignUpRequest request = new SignUpRequest(email, password, "test user");
        mockMvc.perform(post("/api/accounts/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private String login(String email, String password) throws Exception {
        signUp(email, password);
        String response = mockMvc.perform(post("/api/accounts/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
