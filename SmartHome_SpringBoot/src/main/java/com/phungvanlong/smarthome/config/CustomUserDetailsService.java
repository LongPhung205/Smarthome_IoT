package com.phungvanlong.smarthome.config;

import com.phungvanlong.smarthome.entities.UserAccount;
import com.phungvanlong.smarthome.repositories.UserAccountRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserAccountRepo userAccountRepo;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<UserAccount> userOpt = userAccountRepo.findByUsername(username);
        if (userOpt.isEmpty()) {
            throw new UsernameNotFoundException("Không tìm thấy tài khoản: " + username);
        }

        UserAccount user = userOpt.get();

        return User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .roles(user.getRole().replace("ROLE_", "")) // Spring Security tự thêm tiền tố ROLE_
                .build();
    }
}
