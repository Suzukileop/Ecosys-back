package com.plateforme.auth.security;

import com.plateforme.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("Chargement de l'utilisateur par email: {}", email);
        return userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> {
                    log.warn("Utilisateur introuvable pour l'email: {}", email);
                    return new UsernameNotFoundException("Utilisateur introuvable: " + email);
                });
    }
}
