package cn.edu.campus.kb;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import java.util.Locale;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    UserDetailsService users(@Value("${app.admin.username}") String username,
                             @Value("${app.admin.password}") String password,
                             StudentRepository students, PasswordEncoder encoder) {
        String adminHash = encoder.encode(password);
        return login -> {
            if (username.equalsIgnoreCase(login)) return User.withUsername(username).password(adminHash).roles("ADMIN").build();
            var student = students.findByUsername(login.toLowerCase(Locale.ROOT))
                    .orElseThrow(() -> new UsernameNotFoundException("用户名或密码错误"));
            return User.withUsername(student.username).password(student.passwordHash).roles("STUDENT").build();
        };
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/documents", "/api/documents/**", "/api/status").hasRole("ADMIN")
                .anyRequest().permitAll())
            .formLogin(form -> form.loginProcessingUrl("/api/auth/login")
                .successHandler((req, res, auth) -> {
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"message\":\"登录成功\"}");
                })
                .failureHandler((req, res, ex) -> {
                    res.setStatus(401);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"message\":\"用户名或密码错误\"}");
                }))
            .logout(logout -> logout.logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"message\":\"请先登录管理员账号\"}");
                })
                .accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(403);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"message\":\"会话已过期或没有操作权限，请刷新后重试\"}");
                }));
        return http.build();
    }
}
