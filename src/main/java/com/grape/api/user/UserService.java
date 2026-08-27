package com.grape.api.user;

import com.grape.api.common.ApiException;
import com.grape.api.common.ErrorCode;
import com.grape.api.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
    }

    /**
     * §3-2: immediate hard delete. The DB {@code ON DELETE CASCADE} removes the user's bunches,
     * bunch_fill_events, harvests, user_settings and refresh_tokens. No soft delete / grace period.
     */
    @Transactional
    public void deleteMe(UUID userId) {
        userRepository.deleteById(userId);
    }
}
