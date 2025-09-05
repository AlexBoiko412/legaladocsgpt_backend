package com.legaldocsgpt.authservice.security;

import com.legaldocsgpt.authservice.entity.User;
import com.legaldocsgpt.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String email = oAuth2User.getAttribute("email");
        String sub = oAuth2User.getAttribute("sub"); // унікальний id Google

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .username(email.split("@")[0]) // тимчасовий username
                            .email(email)
                            .provider("GOOGLE")
                            .providerId(sub)
                            .role("ROLE_USER")
                            .build();
                    return userRepository.save(newUser);
                });

        return oAuth2User;
    }
}
