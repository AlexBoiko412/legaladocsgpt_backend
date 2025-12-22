package com.legaldocsgpt.authservice.security;

import com.legaldocsgpt.authservice.entity.User;
import com.legaldocsgpt.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String email = oAuth2User.getAttribute("email");
        String providerId = oAuth2User.getAttribute("sub");
        String provider = userRequest.getClientRegistration().getRegistrationId().toUpperCase();

        userRepository.findByEmail(email)
                .orElseGet(() -> {
                    String username = generateUniqueUsername(oAuth2User.getAttributes());
                    User newUser = User.builder()
                            .username(username)
                            .email(email)
                            .password("")
                            .provider(provider)
                            .providerId(providerId)
                            .role("ROLE_USER")
                            .build();
                    return userRepository.save(newUser);
                });

        return oAuth2User;
    }

    private String generateUniqueUsername(Map<String, Object> attributes) {
        String baseUsername = getBaseUsername(attributes);
        String username = baseUsername;
        int counter = 1;
        while (userRepository.existsByUsername(username)) {
            username = baseUsername + counter;
            counter++;
        }
        return username;
    }

    private String getBaseUsername(Map<String, Object> attributes) {
        String name = Optional.ofNullable(attributes.get("name"))
                .map(String::valueOf)
                .filter(StringUtils::hasText)
                .orElseGet(() -> Optional.ofNullable(attributes.get("given_name"))
                        .map(String::valueOf)
                        .filter(StringUtils::hasText)
                        .orElse(""));

        if (StringUtils.hasText(name)) {
            return name.replaceAll("\\s+", "").toLowerCase();
        }

        String email = (String) attributes.get("email");
        return email.split("@")[0];
    }
}
