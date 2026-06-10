package com.lawai.common.security;

import com.lawai.common.client.AuthSessionClient;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@EnableMethodSecurity
public abstract class MicroserviceSecurityConfig {

  protected abstract List<String> publicPaths();

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthSessionClient authSessionClient) throws Exception {
    RemoteSessionAuthenticationFilter sessionFilter = new RemoteSessionAuthenticationFilter(authSessionClient);
    http
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> {
          auth.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll();
          for (String path : publicPaths()) {
            auth.requestMatchers(path).permitAll();
          }
          auth.anyRequest().authenticated();
        })
        .exceptionHandling(exception -> exception.authenticationEntryPoint((request, response, authException) -> {
          response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
          response.setContentType("application/json");
          response.getWriter().write("{\"detail\":\"Oturum gerekli.\"}");
        }))
        .addFilterBefore(sessionFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
