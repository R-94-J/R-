package cn.edu.campus.kb;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class StudentService {
    public record Registration(
            @NotBlank(message = "请输入用户名")
            @Pattern(regexp = "[a-zA-Z0-9_]{4,32}", message = "用户名需为 4—32 位字母、数字或下划线") String username,
            @NotBlank(message = "请输入密码") @Size(min = 8, max = 64, message = "密码需为 8—64 个字符") String password) {}
    private final StudentRepository students;
    private final PasswordEncoder encoder;
    private final String adminUsername;

    public StudentService(StudentRepository students, PasswordEncoder encoder, @Value("${app.admin.username}") String adminUsername) {
        this.students = students; this.encoder = encoder; this.adminUsername = adminUsername;
    }

    @Transactional
    public String register(Registration request) {
        String username = request.username().toLowerCase(Locale.ROOT);
        if (username.equalsIgnoreCase(adminUsername) || students.existsByUsername(username))
            throw new ApiException(HttpStatus.CONFLICT, "用户名已被使用，请更换用户名");
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw ApiException.badRequest("密码 UTF-8 长度不能超过 72 字节，请减少中文或特殊字符");
        StudentAccount student = new StudentAccount(); student.username = username;
        student.passwordHash = encoder.encode(request.password()); students.saveAndFlush(student);
        return username;
    }
}
