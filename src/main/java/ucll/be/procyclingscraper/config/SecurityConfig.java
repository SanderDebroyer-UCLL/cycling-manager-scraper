package ucll.be.procyclingscraper.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import ucll.be.procyclingscraper.security.JwtAuthEntryPoint;
import ucll.be.procyclingscraper.security.JwtAuthFilter;

@Configuration
public class SecurityConfig {

        private JwtAuthFilter filter;

        private JwtAuthEntryPoint jwtAuthEntryPoint;


        public SecurityConfig(JwtAuthFilter filter, JwtAuthEntryPoint jwtAuthEntryPoint,
                        UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
                this.jwtAuthEntryPoint = jwtAuthEntryPoint;
                this.filter = filter;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http,
                        AuthenticationProvider authenticationProvider) throws Exception {
                http
                                .csrf(csrf -> csrf.disable())
                                .headers(headers -> headers
                                                .frameOptions(frameOptions -> frameOptions.disable()))
                                .authorizeHttpRequests(req -> req
                                                .anyRequest().permitAll())
                                .exceptionHandling(exception -> exception
                                                .authenticationEntryPoint(jwtAuthEntryPoint))
                                .sessionManagement(management -> management
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authenticationProvider(authenticationProvider)
                                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        public AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
                        PasswordEncoder passwordEncoder) {
                DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
                provider.setPasswordEncoder(passwordEncoder);
                return provider;
        }
}