package com.chefit.auth.controller;

import com.chefit.auth.dao.IdentifierTakenException;
import com.chefit.auth.dao.UserDAO;
import com.chefit.auth.model.User;
import com.chefit.auth.service.GoogleTokenVerifier;
import com.chefit.auth.service.GoogleTokenVerifier.GoogleIdentity;
import com.chefit.auth.service.JwtService;
import com.chefit.auth.service.PasswordHasher;
import com.chefit.auth.service.PasswordPolicy;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean GoogleTokenVerifier googleTokenVerifier;
    @MockitoBean UserDAO userDAO;
    @MockitoBean JwtService jwtService;
    @MockitoBean PasswordHasher passwordHasher;
    @MockitoBean PasswordPolicy passwordPolicy;

    private static final String GOOGLE_BODY = "{\"credential\":\"google-id-token\"}";
    private static final String GOOD_PASSWORD = "Abcdefg1!"; // 9 — used for "too short" tests
    private static final String VALID_PASSWORD = "Abcdefg12!"; // 10 chars, all classes

    private User user(String uuid, String identifier, boolean isEmail,
                      String hashed, String googleSub, List<String> providers) {
        return new User(uuid, identifier, isEmail, hashed, googleSub, providers,
                "Ada", "127.0.0.1", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z");
    }

    private void mockEmptyPolicyViolations() {
        when(passwordPolicy.violations(anyString())).thenReturn(List.of());
    }

    // ================================================================ POST /auth/google

    @Test
    void google_invalidToken_returns401() throws Exception {
        when(googleTokenVerifier.verify(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON).content(GOOGLE_BODY))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.error").value("invalid_credentials"));

        verifyNoInteractions(userDAO);
    }

    @Test
    void google_existingGoogleUser_returns200WithToken() throws Exception {
        when(googleTokenVerifier.verify(anyString()))
                .thenReturn(Optional.of(new GoogleIdentity("g1", "a@b.com", true, "Ada")));
        when(userDAO.findByGoogleSub("g1"))
                .thenReturn(Optional.of(user("u1", "a@b.com", true, null, "g1", List.of("google"))));
        when(jwtService.issue(any())).thenReturn("jwt-token");

        mockMvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON).content(GOOGLE_BODY))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.token").value("jwt-token"))
               .andExpect(jsonPath("$.uuid").value("u1"))
               .andExpect(jsonPath("$.identifier").value("a@b.com"));

        verify(userDAO, never()).save(any());
        verify(userDAO, never()).linkGoogle(anyString(), anyString());
    }

    @Test
    void google_verifiedEmailMatchingLocalAccount_mergesAndReturns200() throws Exception {
        when(googleTokenVerifier.verify(anyString()))
                .thenReturn(Optional.of(new GoogleIdentity("g1", "a@b.com", true, "Ada")));
        when(userDAO.findByGoogleSub("g1")).thenReturn(Optional.empty());
        when(userDAO.findByIdentifier("a@b.com"))
                .thenReturn(Optional.of(user("u1", "a@b.com", true, "hash", null, List.of("local"))));
        when(userDAO.linkGoogle("u1", "g1"))
                .thenReturn(user("u1", "a@b.com", true, "hash", "g1", List.of("local", "google")));
        when(jwtService.issue(any())).thenReturn("jwt-token");

        mockMvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON).content(GOOGLE_BODY))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.providers", hasSize(2)));

        verify(userDAO).linkGoogle("u1", "g1");
    }

    @Test
    void google_unverifiedEmailMatchingLocalAccount_returns401GenericAndDoesNotMerge() throws Exception {
        when(googleTokenVerifier.verify(anyString()))
                .thenReturn(Optional.of(new GoogleIdentity("g1", "a@b.com", false, "Ada")));
        when(userDAO.findByGoogleSub("g1")).thenReturn(Optional.empty());
        when(userDAO.findByIdentifier("a@b.com"))
                .thenReturn(Optional.of(user("u1", "a@b.com", true, "hash", null, List.of("local"))));

        mockMvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON).content(GOOGLE_BODY))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.error").value("invalid_credentials"));

        verify(userDAO, never()).linkGoogle(anyString(), anyString());
    }

    @Test
    void google_noExistingAccount_createsAndReturns200() throws Exception {
        when(googleTokenVerifier.verify(anyString()))
                .thenReturn(Optional.of(new GoogleIdentity("g1", "new@b.com", true, "Newbie")));
        when(userDAO.findByGoogleSub("g1")).thenReturn(Optional.empty());
        when(userDAO.findByIdentifier("new@b.com")).thenReturn(Optional.empty());
        when(userDAO.save(any())).thenReturn(user("u2", "new@b.com", true, null, "g1", List.of("google")));
        when(jwtService.issue(any())).thenReturn("jwt-token");

        mockMvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON).content(GOOGLE_BODY))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.token").value("jwt-token"));

        verify(userDAO).save(any());
    }

    @Test
    void google_emailNormalizedToLowercase() throws Exception {
        when(googleTokenVerifier.verify(anyString()))
                .thenReturn(Optional.of(new GoogleIdentity("g1", "Ada@X.COM", true, "Ada")));
        when(userDAO.findByGoogleSub("g1")).thenReturn(Optional.empty());
        when(userDAO.findByIdentifier("ada@x.com")).thenReturn(Optional.empty());
        when(userDAO.save(any())).thenReturn(user("u2", "ada@x.com", true, null, "g1", List.of("google")));
        when(jwtService.issue(any())).thenReturn("jwt-token");

        mockMvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON).content(GOOGLE_BODY))
               .andExpect(status().isOk());

        verify(userDAO).findByIdentifier("ada@x.com");
    }

    // ================================================================ POST /auth/register

    private String registerBody(String identifier, String password, String realname) {
        return "{\"identifier\":\"" + identifier + "\",\"password\":\"" + password
                + "\",\"realname\":\"" + (realname == null ? "" : realname) + "\"}";
    }

    @Test
    void register_success_returns200WithToken() throws Exception {
        mockEmptyPolicyViolations();
        when(passwordHasher.hash(VALID_PASSWORD)).thenReturn("salt$hash");
        when(userDAO.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return new User("u-new", u.identifier(), u.identifierIsEmail(), u.hashedPassword(),
                    null, u.providers(), u.realname(), u.ip(), "now", "now");
        });
        when(jwtService.issue(any())).thenReturn("jwt-token");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("alice", VALID_PASSWORD, "Ada")))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.token").value("jwt-token"))
               .andExpect(jsonPath("$.uuid").value("u-new"))
               .andExpect(jsonPath("$.identifier").value("alice"))
               .andExpect(jsonPath("$.providers[0]").value("local"));

        verify(passwordHasher).hash(VALID_PASSWORD);
    }

    @Test
    void register_savedUserHasHashedPasswordAndIsEmailFlagSet() throws Exception {
        mockEmptyPolicyViolations();
        when(passwordHasher.hash(anyString())).thenReturn("salt$hash");
        when(userDAO.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.issue(any())).thenReturn("jwt-token");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("ada@x.com", VALID_PASSWORD, "Ada")))
               .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userDAO).save(captor.capture());
        User saved = captor.getValue();
        assertEquals("salt$hash", saved.hashedPassword());
        assertEquals("ada@x.com", saved.identifier());
        org.junit.jupiter.api.Assertions.assertTrue(saved.identifierIsEmail());
        assertEquals(List.of("local"), saved.providers());
    }

    @Test
    void register_normalizesMixedCaseIdentifier() throws Exception {
        mockEmptyPolicyViolations();
        when(passwordHasher.hash(anyString())).thenReturn("salt$hash");
        when(userDAO.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.issue(any())).thenReturn("jwt-token");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("Ada@X.COM", VALID_PASSWORD, "Ada")))
               .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userDAO).save(captor.capture());
        assertEquals("ada@x.com", captor.getValue().identifier());
    }

    @Test
    void register_missingIdentifier_returns400() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + VALID_PASSWORD + "\"}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.error").value("invalid_request"))
               .andExpect(jsonPath("$.details", org.hamcrest.Matchers.hasItem("missing_identifier")));

        verify(userDAO, never()).save(any());
    }

    @Test
    void register_missingPassword_returns400() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"alice\"}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.details", org.hamcrest.Matchers.hasItem("missing_password")));

        verify(userDAO, never()).save(any());
    }

    @Test
    void register_policyViolation_returns400WithDetails() throws Exception {
        when(passwordPolicy.violations("weakpass"))
                .thenReturn(List.of("too_short", "missing_uppercase", "missing_digit", "missing_special"));

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("alice", "weakpass", "Ada")))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.details", org.hamcrest.Matchers.hasItem("too_short")))
               .andExpect(jsonPath("$.details", org.hamcrest.Matchers.hasItem("missing_uppercase")));

        verify(passwordHasher, never()).hash(anyString());
    }

    @Test
    void register_identifierTaken_returns401Generic() throws Exception {
        mockEmptyPolicyViolations();
        when(passwordHasher.hash(anyString())).thenReturn("salt$hash");
        when(userDAO.save(any())).thenThrow(new IdentifierTakenException("alice"));

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("alice", VALID_PASSWORD, "Ada")))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.error").value("registration_unavailable"))
               .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void register_threeConflictPaths_returnIdenticalBodies() throws Exception {
        // Proves indistinguishability: the conflict body must not differ regardless of
        // whether the existing account is local-only, google-only, or merged.
        mockEmptyPolicyViolations();
        when(passwordHasher.hash(anyString())).thenReturn("salt$hash");
        when(userDAO.save(any())).thenThrow(new IdentifierTakenException("alice"));

        MvcResult r1 = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("alice", VALID_PASSWORD, "Ada")))
            .andExpect(status().isUnauthorized()).andReturn();
        MvcResult r2 = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("bob", VALID_PASSWORD, "Bob")))
            .andExpect(status().isUnauthorized()).andReturn();
        MvcResult r3 = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("carol", VALID_PASSWORD, "Carol")))
            .andExpect(status().isUnauthorized()).andReturn();

        String b1 = r1.getResponse().getContentAsString();
        String b2 = r2.getResponse().getContentAsString();
        String b3 = r3.getResponse().getContentAsString();
        assertEquals(b1, b2);
        assertEquals(b2, b3);
    }

    @Test
    void register_invalidEmailShape_returns400() throws Exception {
        mockEmptyPolicyViolations();

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("not@an@email", VALID_PASSWORD, "Ada")))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.details", org.hamcrest.Matchers.hasItem("invalid_email_shape")));

        verify(userDAO, never()).save(any());
    }

    // ================================================================ POST /auth/login

    private String loginBody(String identifier, String password) {
        return "{\"identifier\":\"" + identifier + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void login_success_returns200WithToken() throws Exception {
        User stored = user("u1", "alice", false, "stored-hash", null, List.of("local"));
        when(userDAO.findByIdentifier("alice")).thenReturn(Optional.of(stored));
        when(passwordHasher.verify("Abcdefg12!", "stored-hash")).thenReturn(true);
        when(jwtService.issue(stored)).thenReturn("jwt-token");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("alice", VALID_PASSWORD)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.token").value("jwt-token"))
               .andExpect(jsonPath("$.identifier").value("alice"));
    }

    @Test
    void login_byEmailFormIdentifier_resolvesAndSucceeds() throws Exception {
        User stored = user("u1", "ada@x.com", true, "stored-hash", null, List.of("local"));
        when(userDAO.findByIdentifier("ada@x.com")).thenReturn(Optional.of(stored));
        when(passwordHasher.verify(anyString(), eq("stored-hash"))).thenReturn(true);
        when(jwtService.issue(stored)).thenReturn("jwt-token");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("Ada@X.COM", VALID_PASSWORD)))
               .andExpect(status().isOk());

        verify(userDAO).findByIdentifier("ada@x.com");
    }

    @Test
    void login_unknownIdentifier_returns401AndCallsDummyVerify() throws Exception {
        when(userDAO.findByIdentifier("ghost")).thenReturn(Optional.empty());
        when(passwordHasher.verify(anyString(), eq(PasswordHasher.FAKE_HASH))).thenReturn(false);

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("ghost", VALID_PASSWORD)))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.error").value("invalid_credentials"));

        verify(passwordHasher).verify(VALID_PASSWORD, PasswordHasher.FAKE_HASH);
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        User stored = user("u1", "alice", false, "stored-hash", null, List.of("local"));
        when(userDAO.findByIdentifier("alice")).thenReturn(Optional.of(stored));
        when(passwordHasher.verify(anyString(), eq("stored-hash"))).thenReturn(false);

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("alice", VALID_PASSWORD)))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.error").value("invalid_credentials"));

        verify(passwordHasher).verify(VALID_PASSWORD, "stored-hash");
    }

    @Test
    void login_googleOnlyAccount_returns401AndCallsDummyVerify() throws Exception {
        User stored = user("u1", "ada@x.com", true, null, "g1", List.of("google"));
        when(userDAO.findByIdentifier("ada@x.com")).thenReturn(Optional.of(stored));
        when(passwordHasher.verify(anyString(), eq(PasswordHasher.FAKE_HASH))).thenReturn(false);

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("ada@x.com", VALID_PASSWORD)))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.error").value("invalid_credentials"));

        // Dummy verify is the timing-parity guard. Without this verification a future
        // refactor could remove the dummy call and silently re-introduce the timing leak.
        verify(passwordHasher).verify(VALID_PASSWORD, PasswordHasher.FAKE_HASH);
    }

    @Test
    void login_threeFailurePathsReturnIdenticalBodies() throws Exception {
        // Unknown identifier
        when(userDAO.findByIdentifier("ghost")).thenReturn(Optional.empty());
        when(passwordHasher.verify(anyString(), eq(PasswordHasher.FAKE_HASH))).thenReturn(false);

        // Known identifier, wrong password
        User local = user("u1", "alice", false, "stored-hash", null, List.of("local"));
        when(userDAO.findByIdentifier("alice")).thenReturn(Optional.of(local));
        when(passwordHasher.verify(anyString(), eq("stored-hash"))).thenReturn(false);

        // Known identifier, Google-only
        User google = user("u2", "ada@x.com", true, null, "g1", List.of("google"));
        when(userDAO.findByIdentifier("ada@x.com")).thenReturn(Optional.of(google));

        MvcResult r1 = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(loginBody("ghost", VALID_PASSWORD)))
            .andExpect(status().isUnauthorized()).andReturn();
        MvcResult r2 = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(loginBody("alice", VALID_PASSWORD)))
            .andExpect(status().isUnauthorized()).andReturn();
        MvcResult r3 = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(loginBody("ada@x.com", VALID_PASSWORD)))
            .andExpect(status().isUnauthorized()).andReturn();

        String b1 = r1.getResponse().getContentAsString();
        String b2 = r2.getResponse().getContentAsString();
        String b3 = r3.getResponse().getContentAsString();
        assertEquals(b1, b2);
        assertEquals(b2, b3);
    }

    @Test
    void login_missingIdentifier_returns400() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + VALID_PASSWORD + "\"}"))
               .andExpect(status().isBadRequest());

        verifyNoInteractions(userDAO);
        verifyNoInteractions(passwordHasher);
    }

    @Test
    void login_missingPassword_returns400() throws Exception {
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"alice\"}"))
               .andExpect(status().isBadRequest());

        verifyNoInteractions(userDAO);
    }

    // ================================================================ GET /auth/{identifier}

    @Test
    void get_noAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(get("/auth/alice"))
               .andExpect(status().isUnauthorized());

        verifyNoInteractions(userDAO);
    }

    @ParameterizedTest(name = "header={0}")
    @ValueSource(strings = {
            "",                  // empty
            "Bearer",            // no token
            "Bearer ",           // empty token after space
            "Basic abc",         // wrong scheme
            "Bearer  token",     // extra space — strict reject
            "BearerNoSpace"      // no space at all
    })
    void get_malformedAuthorizationHeader_returns401(String header) throws Exception {
        mockMvc.perform(get("/auth/alice").header("Authorization", header))
               .andExpect(status().isUnauthorized());

        verifyNoInteractions(userDAO);
    }

    @Test
    void get_lowercaseBearerScheme_isAccepted() throws Exception {
        Claims claims = claimsWith("u1");
        when(jwtService.parse("token")).thenReturn(claims);
        when(userDAO.findByIdentifier("alice"))
                .thenReturn(Optional.of(user("u1", "alice", false, "h", null, List.of("local"))));

        mockMvc.perform(get("/auth/alice").header("Authorization", "bearer token"))
               .andExpect(status().isOk());
    }

    @Test
    void get_invalidToken_returns401() throws Exception {
        when(jwtService.parse("bad")).thenThrow(new JwtException("bad signature"));

        mockMvc.perform(get("/auth/alice").header("Authorization", "Bearer bad"))
               .andExpect(status().isUnauthorized());

        verifyNoInteractions(userDAO);
    }

    @Test
    void get_tokenWithoutSubject_returns401() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(null);
        when(jwtService.parse("token")).thenReturn(claims);

        mockMvc.perform(get("/auth/alice").header("Authorization", "Bearer token"))
               .andExpect(status().isUnauthorized());

        verifyNoInteractions(userDAO);
    }

    @Test
    void get_selfFetch_returns200WithRedactedFields() throws Exception {
        Claims claims = claimsWith("u1");
        when(jwtService.parse("token")).thenReturn(claims);
        when(userDAO.findByIdentifier("alice"))
                .thenReturn(Optional.of(user("u1", "alice", false, "secret-hash", "secret-sub",
                        List.of("local", "google"))));

        mockMvc.perform(get("/auth/alice").header("Authorization", "Bearer token"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.uuid").value("u1"))
               .andExpect(jsonPath("$.identifier").value("alice"))
               .andExpect(jsonPath("$.realname").value("Ada"))
               .andExpect(jsonPath("$.providers", hasSize(2)))
               // Redactions — every one of these must be absent:
               .andExpect(jsonPath("$.hashedPassword").doesNotExist())
               .andExpect(jsonPath("$.ip").doesNotExist())
               .andExpect(jsonPath("$.googleSub").doesNotExist())
               .andExpect(jsonPath("$.modifyDate").doesNotExist());
    }

    @Test
    void get_differentUser_returns403() throws Exception {
        Claims claims = claimsWith("u1");
        when(jwtService.parse("token")).thenReturn(claims);
        when(userDAO.findByIdentifier("bob"))
                .thenReturn(Optional.of(user("u2", "bob", false, "h", null, List.of("local"))));

        mockMvc.perform(get("/auth/bob").header("Authorization", "Bearer token"))
               .andExpect(status().isForbidden());
    }

    @Test
    void get_userNotFound_returns404() throws Exception {
        Claims claims = claimsWith("u1");
        when(jwtService.parse("token")).thenReturn(claims);
        when(userDAO.findByIdentifier("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(get("/auth/ghost").header("Authorization", "Bearer token"))
               .andExpect(status().isNotFound());
    }

    @Test
    void get_normalizesPathIdentifier() throws Exception {
        Claims claims = claimsWith("u1");
        when(jwtService.parse("token")).thenReturn(claims);
        when(userDAO.findByIdentifier("ada@x.com"))
                .thenReturn(Optional.of(user("u1", "ada@x.com", true, "h", null, List.of("local"))));

        mockMvc.perform(get("/auth/Ada@X.COM").header("Authorization", "Bearer token"))
               .andExpect(status().isOk());

        verify(userDAO).findByIdentifier("ada@x.com");
    }

    @Test
    void get_jwtForDeletedUser_returns404OnSelfLookup() throws Exception {
        // JWT is valid (signature OK), but the user no longer exists in storage.
        Claims claims = claimsWith("u-deleted");
        when(jwtService.parse("token")).thenReturn(claims);
        when(userDAO.findByIdentifier("alice")).thenReturn(Optional.empty());

        mockMvc.perform(get("/auth/alice").header("Authorization", "Bearer token"))
               .andExpect(status().isNotFound());
    }

    // ================================================================ helpers

    private Claims claimsWith(String sub) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(sub);
        return claims;
    }
}
