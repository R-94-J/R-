package cn.edu.campus.kb;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

interface StudentRepository extends JpaRepository<StudentAccount, String> {
    Optional<StudentAccount> findByUsername(String username);
    boolean existsByUsername(String username);
}
