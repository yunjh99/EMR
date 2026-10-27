package com.example.emr.global.filter;

import com.example.emr.global.security.JwtProvider;
import com.example.emr.users.dto.LoginUser;
import com.example.emr.users.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final ApiAuthenticationEntryPoint entryPoint;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(jwtProvider.getAccessHeader());
        if (header == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = jwtProvider.extractBearerToken(request)
                    .orElseThrow(() -> new BadCredentialsException("Bearer token is required"));
            var authentication = jwtProvider.getAuthentication(token);
            var user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new BadCredentialsException("Account is unavailable"));
            if (!user.isActive()) {
                throw new BadCredentialsException("Account is inactive");
            }
            var loginUser = new LoginUser(user.getId(), user.getRole());
            var verifiedAuthentication = new UsernamePasswordAuthenticationToken(loginUser, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(verifiedAuthentication);
            SecurityContextHolder.setContext(context);
        } catch (JwtException | IllegalArgumentException | BadCredentialsException e) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, new BadCredentialsException("Invalid access token", e));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
