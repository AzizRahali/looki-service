package com.loomea.auth.service;

import com.loomea.auth.entity.Role;
import com.loomea.auth.entity.User;
import com.loomea.auth.repository.UserRepository;
import com.loomea.owner.entity.StaffStatus;
import com.loomea.owner.repository.StaffMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final StaffMemberRepository staffMemberRepository;

    /** Called by Spring Security during email/password login. */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        return buildUserDetails(user);
    }

    /** Called by the JWT filter on every authenticated request. */
    public Optional<UserDetails> loadUserById(UUID id) {
        return userRepository.findById(id).map(this::buildUserDetails);
    }

    /**
     * Builds Spring Security's UserDetails.
     * For STAFF accounts, the `enabled` flag reflects the staff member's
     * current StaffStatus — SUSPENDED or REMOVED staff are immediately
     * rejected by Spring Security without needing a token invalidation step.
     */
    private UserDetails buildUserDetails(User user) {
        boolean enabled = true;

        if (user.getRole() == Role.STAFF) {
            enabled = staffMemberRepository
                    .findByLinkedUserId(user.getId())
                    .map(m -> m.getStatus() == StaffStatus.ACTIVE)
                    .orElse(false); // no linked staff record → deny
        }

        return new org.springframework.security.core.userdetails.User(
                user.getId().toString(),
                user.getPassword(),
                enabled,   // enabled — toggled for suspended/removed staff
                true,      // accountNonExpired
                true,      // credentialsNonExpired
                true,      // accountNonLocked
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }
}
