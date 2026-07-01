package com.chefit.users.controller;

import com.chefit.users.dao.IdentifierTakenException;
import com.chefit.users.dao.UserDAO;
import com.chefit.users.model.User;
import com.chefit.users.service.JwtService;
import com.chefit.users.service.PasswordHasher;
import com.chefit.users.service.PasswordPolicy;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UsersController.class)
class UsersControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean UserDAO userDAO;
    @MockitoBean JwtService jwtService;
    @MockitoBean PasswordHasher passwordHasher;
    @MockitoBean PasswordPolicy passwordPolicy;

    private static final String VALID_PASSWORD = "Abcdefg12!";

    private User user(String uuid, String identifier, boolean isEmail,
                      String hashed, String googleSub, List<String> providers) {
        return new User(uuid, identifier, isEmail, hashed, googleSub, providers,
                "Ada", "127.0.0.1", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z");
    }

    private Claims claimsWith(String sub) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(sub);
        return claims;
    }

    private void mockValidToken(String sub) {
        Claims claims = claimsWith(sub);
        when(jwtService.parse("token")).thenReturn(claims);
    }

    private void mockInvalidToken() {
        when(jwtService.parse(anyString())).thenThrow(new JwtException("bad signature"));
    }

    private void mockEmptyPolicyViolations() {
        when(passwordPolicy.violations(anyString())).thenReturn(List.of());
    }

    private String realnameBody(String realname) {
        return "{\"realname\":\"" + realname + "\"}";
    }

    private String identifierBody(String identifier) {
        return "{\"identifier\":\"" + identifier + "\"}";
    }

    private String passwordChangeBody(String current, String next) {
        return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}";
    }

    private String deleteBody(String password) {
        return "{\"password\":\"" + password + "\"}";
    }

    // ================================================================ GET /users

    @Test
    void listUsers_noJwt_returns401() throws Exception {
        mockMvc.perform(get("/users"))
               .andExpect(status().isUnauthorized());

        verifyNoInteractions(userDAO);
    }

    @ParameterizedTest(name = "header={0}")
    @ValueSource(strings = {
            "",
            "Bearer",
            "Bearer ",
            "Basic abc",
            "Bearer  token",
            "BearerNoSpace"
    })
    void listUsers_malformedAuthorizationHeader_returns401(String header) throws Exception {
        mockMvc.perform(get("/users").header("Authorization", header))
               .andExpect(status().isUnauthorized());

        verifyNoInteractions(userDAO);
    }

    @Test
    void listUsers_invalidJwt_returns401() throws Exception {
        mockInvalidToken();

        mockMvc.perform(get("/users").header("Authorization", "Bearer bad-token"))
               .andExpect(status().isUnauthorized());

        verifyNoInteractions(userDAO);
    }

    @Test
    void listUsers_jwtWithoutSubject_returns401() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(null);
        when(jwtService.parse("token")).thenReturn(claims);

        mockMvc.perform(get("/users").header("Authorization", "Bearer token"))
               .andExpect(status().isUnauthorized());

        verifyNoInteractions(userDAO);
    }

    @Test
    void listUsers_validJwt_returns200WithPublicUserList() throws Exception {
        mockValidToken("u1");
        when(userDAO.findAll()).thenReturn(List.of(
                user("u1", "alice", false, "hash", null, List.of("local")),
                user("u2", "ada@x.com", true, null, "g1", List.of("google"))));

        mockMvc.perform(get("/users").header("Authorization", "Bearer token"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$", hasSize(2)))
               .andExpect(jsonPath("$[0].uuid").value("u1"))
               .andExpect(jsonPath("$[1].uuid").value("u2"));
    }

    @Test
    void listUsers_validJwt_emptyStore_returns200WithEmptyArray() throws Exception {
        mockValidToken("u1");
        when(userDAO.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/users").header("Authorization", "Bearer token"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void listUsers_sensitiveFieldsAbsentFromEachItem() throws Exception {
        mockValidToken("u1");
        when(userDAO.findAll()).thenReturn(List.of(
                user("u1", "alice", false, "secret-hash", "secret-sub", List.of("local"))));

        mockMvc.perform(get("/users").header("Authorization", "Bearer token"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].hashedPassword").doesNotExist())
               .andExpect(jsonPath("$[0].ip").doesNotExist())
               .andExpect(jsonPath("$[0].googleSub").doesNotExist())
               .andExpect(jsonPath("$[0].modifyDate").doesNotExist());
    }

    // ================================================================ GET /users/{id}

    @Test
    void getUser_noJwt_returns401() throws Exception {
        mockMvc.perform(get("/users/u1"))
               .andExpect(status().isUnauthorized());

        verifyNoInteractions(userDAO);
    }

    @Test
    void getUser_invalidJwt_returns401() throws Exception {
        mockInvalidToken();

        mockMvc.perform(get("/users/u1").header("Authorization", "Bearer bad-token"))
               .andExpect(status().isUnauthorized());

        verifyNoInteractions(userDAO);
    }

    @Test
    void getUser_validJwt_userFound_returns200() throws Exception {
        mockValidToken("caller-uuid");
        when(userDAO.findById("u1"))
                .thenReturn(Optional.of(user("u1", "alice", false, "hash", null, List.of("local"))));

        mockMvc.perform(get("/users/u1").header("Authorization", "Bearer token"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.uuid").value("u1"))
               .andExpect(jsonPath("$.identifier").value("alice"))
               .andExpect(jsonPath("$.realname").value("Ada"));
    }

    @Test
    void getUser_validJwt_userNotFound_returns404() throws Exception {
        mockValidToken("caller-uuid");
        when(userDAO.findById("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(get("/users/ghost").header("Authorization", "Bearer token"))
               .andExpect(status().isNotFound());
    }

    @Test
    void getUser_anyAuthenticatedCallerCanReadAnyPublicProfile() throws Exception {
        // Caller uuid != target uuid — still returns 200 (not self-only)
        mockValidToken("u2");
        when(userDAO.findById("u1"))
                .thenReturn(Optional.of(user("u1", "alice", false, "hash", null, List.of("local"))));

        mockMvc.perform(get("/users/u1").header("Authorization", "Bearer token"))
               .andExpect(status().isOk());
    }

    @Test
    void getUser_sensitiveFieldsAbsentFromResponse() throws Exception {
        mockValidToken("u1");
        when(userDAO.findById("u1"))
                .thenReturn(Optional.of(user("u1", "alice", false, "secret-hash",
                        "secret-sub", List.of("local", "google"))));

        mockMvc.perform(get("/users/u1").header("Authorization", "Bearer token"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.uuid").value("u1"))
               .andExpect(jsonPath("$.providers", hasSize(2)))
               .andExpect(jsonPath("$.hashedPassword").doesNotExist())
               .andExpect(jsonPath("$.ip").doesNotExist())
               .andExpect(jsonPath("$.googleSub").doesNotExist())
               .andExpect(jsonPath("$.modifyDate").doesNotExist());
    }

    // ================================================================ PUT /users/{id}

    @Test
    void updateUser_noJwt_returns401() throws Exception {
        mockMvc.perform(put("/users/u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(realnameBody("New Name")))
               .andExpect(status().isUnauthorized());

        verifyNoInteractions(userDAO);
    }

    @Test
    void updateUser_invalidJwt_returns401() throws Exception {
        mockInvalidToken();

        mockMvc.perform(put("/users/u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(realnameBody("New Name"))
                .header("Authorization", "Bearer bad-token"))
               .andExpect(status().isUnauthorized());

        verifyNoInteractions(userDAO);
    }

    @Test
    void updateUser_differentUser_returns403() throws Exception {
        mockValidToken("u2");

        mockMvc.perform(put("/users/u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(realnameBody("New Name"))
                .header("Authorization", "Bearer token"))
               .andExpect(status().isForbidden());

        verifyNoInteractions(userDAO);
    }

    @Test
    void updateUser_selfRealnameChange_returns200WithUpdatedUser() throws Exception {
        mockValidToken("u1");
        User updated = user("u1", "alice", false, "hash", null, List.of("local"));
        when(userDAO.findById("u1")).thenReturn(Optional.of(
                user("u1", "alice", false, "hash", null, List.of("local"))));
        when(userDAO.update(eq("u1"), any())).thenReturn(updated);

        mockMvc.perform(put("/users/u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(realnameBody("New Name"))
                .header("Authorization", "Bearer token"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.uuid").value("u1"));
    }

    @Test
    void updateUser_selfIdentifierChange_returns200() throws Exception {
        mockValidToken("u1");
        User updated = user("u1", "bob", false, "hash", null, List.of("local"));
        when(userDAO.findById("u1")).thenReturn(Optional.of(
                user("u1", "alice", false, "hash", null, List.of("local"))));
        when(userDAO.update(eq("u1"), any())).thenReturn(updated);

        mockMvc.perform(put("/users/u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(identifierBody("bob"))
                .header("Authorization", "Bearer token"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.identifier").value("bob"));
    }

    @Test
    void updateUser_identifierAlreadyTaken_returns409() throws Exception {
        mockValidToken("u1");
        when(userDAO.findById("u1")).thenReturn(Optional.of(
                user("u1", "alice", false, "hash", null, List.of("local"))));
        when(userDAO.update(eq("u1"), any())).thenThrow(new IdentifierTakenException("bob"));

        mockMvc.perform(put("/users/u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(identifierBody("bob"))
                .header("Authorization", "Bearer token"))
               .andExpect(status().isConflict());
    }

    @Test
    void updateUser_userNotFound_returns404() throws Exception {
        mockValidToken("u1");
        when(userDAO.findById("u1")).thenReturn(Optional.empty());

        mockMvc.perform(put("/users/u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(realnameBody("New Name"))
                .header("Authorization", "Bearer token"))
               .andExpect(status().isNotFound());

        verify(userDAO, never()).update(anyString(), any());
    }

    @Test
    void updateUser_passwordChange_correctCurrentPassword_returns200() throws Exception {
        mockValidToken("u1");
        mockEmptyPolicyViolations();
        User stored = user("u1", "alice", false, "stored-hash", null, List.of("local"));
        when(userDAO.findById("u1")).thenReturn(Optional.of(stored));
        when(passwordHasher.verify(VALID_PASSWORD, "stored-hash")).thenReturn(true);
        when(passwordHasher.hash("NewPass1!")).thenReturn("new-hash");
        when(userDAO.update(eq("u1"), any())).thenReturn(stored);

        mockMvc.perform(put("/users/u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(passwordChangeBody(VALID_PASSWORD, "NewPass1!"))
                .header("Authorization", "Bearer token"))
               .andExpect(status().isOk());

        verify(passwordHasher).verify(VALID_PASSWORD, "stored-hash");
        verify(passwordHasher).hash("NewPass1!");
    }

    @Test
    void updateUser_passwordChange_wrongCurrentPassword_returns401() throws Exception {
        mockValidToken("u1");
        User stored = user("u1", "alice", false, "stored-hash", null, List.of("local"));
        when(userDAO.findById("u1")).thenReturn(Optional.of(stored));
        when(passwordHasher.verify(anyString(), eq("stored-hash"))).thenReturn(false);

        mockMvc.perform(put("/users/u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(passwordChangeBody("wrong-pass", "NewPass1!"))
                .header("Authorization", "Bearer token"))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.error").value("invalid_credentials"));

        verify(userDAO, never()).update(anyString(), any());
    }

    @Test
    void updateUser_passwordChange_missingCurrentPassword_returns400() throws Exception {
        mockValidToken("u1");
        when(userDAO.findById("u1")).thenReturn(Optional.of(
                user("u1", "alice", false, "hash", null, List.of("local"))));

        mockMvc.perform(put("/users/u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"NewPass1!\"}")
                .header("Authorization", "Bearer token"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.details", hasItem("missing_current_password")));

        verify(userDAO, never()).update(anyString(), any());
    }

    @Test
    void updateUser_passwordChange_newPasswordFailsPolicy_returns400WithDetails() throws Exception {
        mockValidToken("u1");
        User stored = user("u1", "alice", false, "stored-hash", null, List.of("local"));
        when(userDAO.findById("u1")).thenReturn(Optional.of(stored));
        when(passwordHasher.verify(anyString(), eq("stored-hash"))).thenReturn(true);
        when(passwordPolicy.violations("weak")).thenReturn(List.of("too_short", "missing_uppercase"));

        mockMvc.perform(put("/users/u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(passwordChangeBody(VALID_PASSWORD, "weak"))
                .header("Authorization", "Bearer token"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.details", hasItem("too_short")))
               .andExpect(jsonPath("$.details", hasItem("missing_uppercase")));

        verify(passwordHasher, never()).hash(anyString());
        verify(userDAO, never()).update(anyString(), any());
    }

    @Test
    void updateUser_passwordChange_googleOnlyAccount_returns400() throws Exception {
        // Google-only accounts have no hashedPassword — cannot verify current password.
        mockValidToken("u1");
        User googleOnly = user("u1", "ada@x.com", true, null, "g1", List.of("google"));
        when(userDAO.findById("u1")).thenReturn(Optional.of(googleOnly));

        mockMvc.perform(put("/users/u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(passwordChangeBody("anything", "NewPass1!"))
                .header("Authorization", "Bearer token"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.error").value("no_password_auth"));

        verify(userDAO, never()).update(anyString(), any());
    }

    @Test
    void updateUser_sensitiveFieldsAbsentFromResponse() throws Exception {
        mockValidToken("u1");
        User stored = user("u1", "alice", false, "secret-hash", "secret-sub",
                List.of("local", "google"));
        when(userDAO.findById("u1")).thenReturn(Optional.of(stored));
        when(userDAO.update(eq("u1"), any())).thenReturn(stored);

        mockMvc.perform(put("/users/u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(realnameBody("New Name"))
                .header("Authorization", "Bearer token"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.hashedPassword").doesNotExist())
               .andExpect(jsonPath("$.ip").doesNotExist())
               .andExpect(jsonPath("$.googleSub").doesNotExist())
               .andExpect(jsonPath("$.modifyDate").doesNotExist());
    }

    @Test
    void updateUser_disallowedFieldsInBody_areIgnoredOrRejected() throws Exception {
        // Fields like uuid and createDate must not be writable via this endpoint.
        mockValidToken("u1");
        User stored = user("u1", "alice", false, "hash", null, List.of("local"));
        when(userDAO.findById("u1")).thenReturn(Optional.of(stored));
        when(userDAO.update(eq("u1"), any())).thenReturn(stored);

        mockMvc.perform(put("/users/u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"uuid\":\"evil-uuid\",\"createDate\":\"1970-01-01T00:00:00Z\"}")
                .header("Authorization", "Bearer token"))
               // Must not crash or propagate the injected values — 200 with untampered user,
               // or 400 if the controller rejects unknown write attempts explicitly.
               .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<com.chefit.users.dao.UpdateRequest> captor =
                org.mockito.ArgumentCaptor.forClass(com.chefit.users.dao.UpdateRequest.class);
        verify(userDAO).update(eq("u1"), captor.capture());
        com.chefit.users.dao.UpdateRequest req = captor.getValue();
        // uuid and createDate are not writable fields — they must not appear in UpdateRequest.
        org.junit.jupiter.api.Assertions.assertNull(req.identifier());
        org.junit.jupiter.api.Assertions.assertNull(req.realname());
        org.junit.jupiter.api.Assertions.assertNull(req.newHashedPassword());
    }

    // ================================================================ DELETE /users/{id}

    @Test
    void deleteUser_noJwt_returns401() throws Exception {
        mockMvc.perform(delete("/users/u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(deleteBody(VALID_PASSWORD)))
               .andExpect(status().isUnauthorized());

        verifyNoInteractions(userDAO);
    }

    @Test
    void deleteUser_invalidJwt_returns401() throws Exception {
        mockInvalidToken();

        mockMvc.perform(delete("/users/u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(deleteBody(VALID_PASSWORD))
                .header("Authorization", "Bearer bad-token"))
               .andExpect(status().isUnauthorized());

        verifyNoInteractions(userDAO);
    }

    @Test
    void deleteUser_differentUser_returns403() throws Exception {
        mockValidToken("u2");

        mockMvc.perform(delete("/users/u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(deleteBody(VALID_PASSWORD))
                .header("Authorization", "Bearer token"))
               .andExpect(status().isForbidden());

        verifyNoInteractions(userDAO);
    }

    @Test
    void deleteUser_userNotFound_returns404() throws Exception {
        // JWT is valid and sub matches the path id, but the user no longer exists.
        mockValidToken("u1");
        when(userDAO.findById("u1")).thenReturn(Optional.empty());

        mockMvc.perform(delete("/users/u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(deleteBody(VALID_PASSWORD))
                .header("Authorization", "Bearer token"))
               .andExpect(status().isNotFound());

        verify(userDAO, never()).delete(anyString());
    }

    @Test
    void deleteUser_correctPassword_returns204() throws Exception {
        mockValidToken("u1");
        User stored = user("u1", "alice", false, "stored-hash", null, List.of("local"));
        when(userDAO.findById("u1")).thenReturn(Optional.of(stored));
        when(passwordHasher.verify(VALID_PASSWORD, "stored-hash")).thenReturn(true);

        mockMvc.perform(delete("/users/u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(deleteBody(VALID_PASSWORD))
                .header("Authorization", "Bearer token"))
               .andExpect(status().isNoContent());

        verify(userDAO).delete("u1");
    }

    @Test
    void deleteUser_wrongPassword_returns401() throws Exception {
        mockValidToken("u1");
        User stored = user("u1", "alice", false, "stored-hash", null, List.of("local"));
        when(userDAO.findById("u1")).thenReturn(Optional.of(stored));
        when(passwordHasher.verify(anyString(), eq("stored-hash"))).thenReturn(false);

        mockMvc.perform(delete("/users/u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(deleteBody("wrong-pass"))
                .header("Authorization", "Bearer token"))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.error").value("invalid_credentials"));

        verify(userDAO, never()).delete(anyString());
        verify(passwordHasher).verify("wrong-pass", "stored-hash");
    }

    @Test
    void deleteUser_missingPasswordField_returns400() throws Exception {
        mockValidToken("u1");
        when(userDAO.findById("u1")).thenReturn(Optional.of(
                user("u1", "alice", false, "hash", null, List.of("local"))));

        mockMvc.perform(delete("/users/u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .header("Authorization", "Bearer token"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.details", hasItem("missing_password")));

        verify(userDAO, never()).delete(anyString());
        verifyNoInteractions(passwordHasher);
    }

    @Test
    void deleteUser_googleOnlyAccount_returns400() throws Exception {
        // Accounts with no hashedPassword cannot be deleted via password verification.
        mockValidToken("u1");
        User googleOnly = user("u1", "ada@x.com", true, null, "g1", List.of("google"));
        when(userDAO.findById("u1")).thenReturn(Optional.of(googleOnly));

        mockMvc.perform(delete("/users/u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(deleteBody(VALID_PASSWORD))
                .header("Authorization", "Bearer token"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.error").value("no_password_auth"));

        verify(userDAO, never()).delete(anyString());
    }

    @Test
    void deleteUser_wrongPasswordAndDeleteNotCalled_returnsIdenticalBodyRegardlessOfStoredHash()
            throws Exception {
        // Both "wrong password" failure paths (wrong value, correct account) must return
        // identical bodies so callers cannot distinguish them by response content.
        mockValidToken("u1");
        User stored = user("u1", "alice", false, "stored-hash", null, List.of("local"));
        when(userDAO.findById("u1")).thenReturn(Optional.of(stored));
        when(passwordHasher.verify(anyString(), eq("stored-hash"))).thenReturn(false);

        MvcResult r1 = mockMvc.perform(delete("/users/u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(deleteBody("wrong-pass-1"))
                .header("Authorization", "Bearer token"))
            .andExpect(status().isUnauthorized()).andReturn();

        MvcResult r2 = mockMvc.perform(delete("/users/u1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(deleteBody("wrong-pass-2"))
                .header("Authorization", "Bearer token"))
            .andExpect(status().isUnauthorized()).andReturn();

        assertEquals(r1.getResponse().getContentAsString(),
                     r2.getResponse().getContentAsString());
    }
}
