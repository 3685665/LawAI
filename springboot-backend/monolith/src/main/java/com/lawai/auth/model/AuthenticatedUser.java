package com.lawai.auth.model;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

public record AuthenticatedUser(String id, String name, String email, String role) {
  public Collection<? extends GrantedAuthority> authorities() {
    return List.of(new SimpleGrantedAuthority(normalizedAuthority()));
  }

  public String role() {
    return role == null || role.isBlank() ? "USER" : role;
  }

  public boolean isAdmin() {
    return "ADMIN".equalsIgnoreCase(role());
  }

  private String normalizedAuthority() {
    return "ROLE_" + role().toUpperCase();
  }
}
