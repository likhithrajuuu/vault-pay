package com.vaultpay.wallet;

import com.vaultpay.wallet.model.Wallet;
import com.vaultpay.wallet.model.WalletStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Storage is mocked and Flyway is off: these tests cover the HTTP and security behavior only.
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class WalletServiceApplicationTests {
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @MockitoBean WalletRepository repository;

    @Test
    void firstCreateReturns201AndPendingWalletOwnedByTokenSubject() throws Exception {
        when(repository.insertIfAbsent(any(), eq(USER_ID), eq("INR"))).thenReturn(Optional.of(wallet()));
        mvc.perform(post("/api/v1/wallets").with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.ownerUserId").doesNotExist())
                .andExpect(jsonPath("$.balance").doesNotExist());
    }

    @Test
    void repeatedCreateReturnsExistingWalletWith200() throws Exception {
        when(repository.insertIfAbsent(any(), eq(USER_ID), eq("INR"))).thenReturn(Optional.empty());
        when(repository.findByOwner(USER_ID)).thenReturn(Optional.of(wallet()));
        mvc.perform(post("/api/v1/wallets").with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isOk());
        verify(repository).findByOwner(USER_ID);
    }

    @Test
    void getMineReturns404WhenNoWalletExists() throws Exception {
        when(repository.findByOwner(USER_ID)).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/wallets/me").with(jwt().jwt(token -> token.subject(USER_ID.toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingTokenIs401AndUnlistedRoutesAreDenied() throws Exception {
        mvc.perform(post("/api/v1/wallets")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(get("/api/v1/wallets/" + UUID.randomUUID()).with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    private static Wallet wallet() {
        return new Wallet(UUID.randomUUID(), USER_ID, "INR", WalletStatus.PENDING, Instant.now(), Instant.now(), 0);
    }
}
