package com.example.cortebe.global.swagger;

import com.example.cortebe.global.error.exception.ErrorProperty;
import com.example.cortebe.global.security.exception.AuthErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.WebInvocationPrivilegeEvaluator;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SecurityFilterChain 의 인가 규칙에 엔드포인트별 가짜 요청을 넣어 보고
 * 비로그인이면 막히는지(401) 판정한다.
 */
@Component
public class SecurityErrorCodeResolver {
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{[^}]+}");
    private static final String PATH_VARIABLE_SAMPLE = "1";

    private static final List<ErrorProperty> AUTH_ERROR_CODES = List.of(
        AuthErrorCode.UNAUTHORIZED,
        AuthErrorCode.INVALID_TOKEN,
        AuthErrorCode.EXPIRED_TOKEN
    );

    private static final Authentication ANONYMOUS = new AnonymousAuthenticationToken(
        "swagger", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    private final WebInvocationPrivilegeEvaluator privilegeEvaluator;
    private final RequestMappingHandlerMapping handlerMapping;

    public SecurityErrorCodeResolver(
        WebInvocationPrivilegeEvaluator privilegeEvaluator,
        @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping
    ) {
        this.privilegeEvaluator = privilegeEvaluator;
        this.handlerMapping = handlerMapping;
    }

    public Set<ErrorProperty> resolve(HandlerMethod handlerMethod) {
        Set<ErrorProperty> result = new LinkedHashSet<>();
        RequestMappingInfo mapping = findMapping(handlerMethod);
        if (mapping == null) {
            return result;
        }

        Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
        for (String pattern : mapping.getPatternValues()) {
            String uri = PATH_VARIABLE.matcher(pattern).replaceAll(PATH_VARIABLE_SAMPLE);
            for (RequestMethod method : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
                if (!isAllowed(uri, method, ANONYMOUS)) {
                    result.addAll(AUTH_ERROR_CODES);
                }
            }
        }
        return result;
    }

    private RequestMappingInfo findMapping(HandlerMethod handlerMethod) {
        return handlerMapping.getHandlerMethods().entrySet().stream()
            .filter(entry -> entry.getValue().getMethod().equals(handlerMethod.getMethod()))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    private boolean isAllowed(String uri, RequestMethod method, Authentication authentication) {
        return privilegeEvaluator.isAllowed(null, uri, method.name(), authentication);
    }
}
