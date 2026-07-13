package io.docflow.api.infrastructure.security;

import io.docflow.api.core.admin.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final AdminUserRepository adminUserRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("Login denemesi yapılıyor: {}", username);

        return adminUserRepository.findByUserName(username)
                .map(admin -> {
                    log.info("Kullanıcı DB'de bulundu {}. Rolü: {}", admin.getUserName(), admin.getRole());
                    return User.builder()
                            .username(admin.getUserName())
                            .password(admin.getPassword())
                            .authorities(admin.getRole())
                            .build();
                })
                .orElseThrow(() -> {
                    log.error("Kullanıcı DB'de BULUNAMADI: {}", username);
                    return new UsernameNotFoundException("Admin not found: " + username);
                });
    }
}
