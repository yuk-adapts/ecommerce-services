package com.vv456.order_service.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Data
@AllArgsConstructor
public class UserDetailsHelper implements UserDetails {

    private Long id;
    private String email;
    private String password;
    private String role;
    private String status;

    public static UserDetailsHelper fromJwtClaims(Long userId, String email, String role) {
        return new UserDetailsHelper(
                userId,
                email,
                null, // password not needed for JWT authentication
                role,
                "ACTIVE" // JWT authenticated users are considered active
        );
    }

    /**
     * Extract user ID from Authentication object
     */
    public static Long getUserIdFromAuthentication(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalArgumentException("Authentication is required");
        }

        Object principal = authentication.getPrincipal();
        
        if (principal instanceof UserDetailsHelper) {
            return ((UserDetailsHelper) principal).getId();
        }
        
        throw new IllegalArgumentException("Invalid authentication principal type");
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return "ACTIVE".equals(status);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "ACTIVE".equals(status);
    }
}
