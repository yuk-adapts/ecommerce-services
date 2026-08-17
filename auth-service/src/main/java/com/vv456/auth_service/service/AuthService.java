package com.vv456.auth_service.service;

import com.vv456.auth_service.dto.AuthResponse;
import com.vv456.auth_service.dto.LoginRequest;
import com.vv456.auth_service.dto.RegisterRequest;
import com.vv456.auth_service.entities.User;
import com.vv456.auth_service.repository.UserRepository;
import com.vv456.auth_service.security.UserDetailsHelper;
import com.vv456.auth_service.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication Service
 * Contains business logic for user registration and authentication
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse registerUser(RegisterRequest registerRequest) {
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new RuntimeException("Email is already taken!");
        }

        User user = User.builder()
                .email(registerRequest.getEmail())
                .passwordHash(passwordEncoder.encode(registerRequest.getPassword()))
                .role(registerRequest.getRole() != null ? registerRequest.getRole() : "USER")
                .status("ACTIVE")
                .build();

        User savedUser = userRepository.save(user);

        UserDetailsHelper userDetails = UserDetailsHelper.build(savedUser);
        String token = jwtUtil.generateToken(userDetails);

        return AuthResponse.builder()
                .token(token)
                .id(savedUser.getId())
                .email(savedUser.getEmail())
                .role(savedUser.getRole())
                .message("User registered successfully!")
                .build();
    }

    public AuthResponse loginUser(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetailsHelper userDetails = (UserDetailsHelper) authentication.getPrincipal();

        String token = jwtUtil.generateToken(userDetails);

        return AuthResponse.builder()
                .token(token)
                .id(userDetails.getId())
                .email(userDetails.getEmail())
                .role(userDetails.getRole())
                .message("Login successful!")
                .build();
    }

    public AuthResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsHelper userDetails = (UserDetailsHelper) authentication.getPrincipal();

        return AuthResponse.builder()
                .id(userDetails.getId())
                .email(userDetails.getEmail())
                .role(userDetails.getRole())
                .message("User details retrieved successfully!")
                .build();
    }
}
