package com.lawai.common.security;

import com.lawai.common.client.AuthSessionClient;
import com.lawai.common.model.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class RemoteSessionAuthenticationFilter extends OncePerRequestFilter {

  public static final String SESSION_COOKIE_NAME = "LAI_SESSION";
  private final AuthSessionClient authSessionClient;

  public RemoteSessionAuthenticationFilter(AuthSessionClient authSessionClient) {
    this.authSessionClient = authSessionClient;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String token = extractCookie(request);
    if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
      authSessionClient.validateSession(token).ifPresent(user -> setAuthentication(request, user));
    }
    filterChain.doFilter(request, response);
  }

  private void setAuthentication(HttpServletRequest request, AuthenticatedUser user) {
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(user, null, user.authorities());
    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private String extractCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (SESSION_COOKIE_NAME.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
