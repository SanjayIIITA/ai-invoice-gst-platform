package com.invoice.platform.control;

import com.invoice.platform.boundary.AuthResponse;
import com.invoice.platform.boundary.LoginRequest;
import com.invoice.platform.boundary.RegisterRequest;
import com.invoice.platform.entity.Organization;
import com.invoice.platform.entity.Role;
import com.invoice.platform.entity.User;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       OrganizationRepository organizationRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered: " + request.email());
        }

        Organization org = new Organization();
        org.setName(request.organizationName());
        org = organizationRepository.save(org);

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setRole(Role.ADMIN);
        user.setOrganization(org);
        user = userRepository.save(user);

        return toResponse(jwtService.generate(user), user);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return toResponse(jwtService.generate(user), user);
    }

    private AuthResponse toResponse(String token, User user) {
        return new AuthResponse(token, user.getEmail(), user.getFullName(),
                user.getRole().name(), user.getOrganization().getId());
    }
}
