package com.novabank.customer.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Second layer of the ownership check (APIM only validates the token itself, not which
 * customer it belongs to). The {customerId} claim in the token must equal {customerId} in
 * the path, unless the token carries the staff role. A mismatch returns 404, not 403, so a
 * caller can't use the status code to confirm another customer's resource even exists.
 */
@Component
public class OwnershipCheckFilter extends OncePerRequestFilter {

    private static final Pattern CUSTOMER_ID_PATH = Pattern.compile("^/api/v1/customers/([^/]+)(?:/.*)?$");

    private final NovaBankSecurityProperties properties;

    public OwnershipCheckFilter(NovaBankSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Matcher matcher = CUSTOMER_ID_PATH.matcher(request.getRequestURI());
        if (!matcher.matches()) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            filterChain.doFilter(request, response);
            return;
        }

        Jwt jwt = jwtAuth.getToken();
        List<String> roles = jwt.getClaimAsStringList("roles");
        boolean isStaff = roles != null && roles.contains(properties.staffRole());
        if (isStaff) {
            filterChain.doFilter(request, response);
            return;
        }

        // Entra ID emits directory extension attributes as a single-element array (e.g.
        // "extn.customerId": ["<uuid>"]) in access tokens, not a plain string claim.
        String tokenCustomerId = firstValue(jwt, properties.customerIdClaim());
        String pathCustomerId = matcher.group(1);
        if (tokenCustomerId == null || !tokenCustomerId.equalsIgnoreCase(pathCustomerId)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static String firstValue(Jwt jwt, String claimName) {
        List<String> values = jwt.getClaimAsStringList(claimName);
        if (values != null && !values.isEmpty()) {
            return values.get(0);
        }
        return jwt.getClaimAsString(claimName);
    }
}
