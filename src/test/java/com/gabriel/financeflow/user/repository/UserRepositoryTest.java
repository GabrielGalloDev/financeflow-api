package com.gabriel.financeflow.user.repository;

import com.gabriel.financeflow.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void saveFillsIdAndAuditDatesAndDefaults() {
        User saved = userRepository.saveAndFlush(new User("Gabriel", "gabriel@email.com", "hash"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getCurrency()).isEqualTo("BRL");
        assertThat(saved.getTimezone()).isEqualTo("America/Sao_Paulo");
    }

    @Test
    void findByEmailAndExistsByEmail() {
        userRepository.saveAndFlush(new User("Gabriel", "gabriel@email.com", "hash"));

        assertThat(userRepository.findByEmail("gabriel@email.com"))
                .isPresent()
                .get()
                .extracting(User::getName)
                .isEqualTo("Gabriel");
        assertThat(userRepository.existsByEmail("gabriel@email.com")).isTrue();
        assertThat(userRepository.findByEmail("outro@email.com")).isEmpty();
        assertThat(userRepository.existsByEmail("outro@email.com")).isFalse();
    }
}
