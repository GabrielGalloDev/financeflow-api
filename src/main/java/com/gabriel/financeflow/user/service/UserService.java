package com.gabriel.financeflow.user.service;

import com.gabriel.financeflow.shared.exception.ConflictException;
import com.gabriel.financeflow.shared.exception.ResourceNotFoundException;
import com.gabriel.financeflow.shared.security.CurrentUser;
import com.gabriel.financeflow.shared.text.EmailNormalizer;
import com.gabriel.financeflow.user.dto.UpdateUserRequest;
import com.gabriel.financeflow.user.dto.UserResponse;
import com.gabriel.financeflow.user.entity.User;
import com.gabriel.financeflow.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    public UserService(UserRepository userRepository, CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        return UserResponse.from(findCurrentUser());
    }

    @Transactional
    public UserResponse updateCurrentUser(UpdateUserRequest request) {
        User user = findCurrentUser();
        String email = EmailNormalizer.normalize(request.email());

        if (userRepository.existsByEmailAndIdNot(email, user.getId())) {
            throw new ConflictException("E-mail já cadastrado");
        }

        user.updateProfile(
                request.name().trim(),
                email,
                request.currency().trim().toUpperCase(Locale.ROOT),
                request.timezone().trim()
        );

        return UserResponse.from(user);
    }

    private User findCurrentUser() {
        return userRepository.findById(currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }
}
