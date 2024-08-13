package com.ghostchu.btn.sparkle.module.user.internal;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

@Entity
@Table(name = "user",
        uniqueConstraints = {@UniqueConstraint(columnNames = "githubLogin"), @UniqueConstraint(columnNames = "githubUserId"),@UniqueConstraint(columnNames = "email")},
        indexes = {@Index(columnList = "githubLogin"),@Index(columnList = "githubUserId")})
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, unique = true)
    private Long id;
    @Column(nullable = false)
    private String avatar;
    @Column(unique = true, nullable = false)
    private String email;
    @Column(nullable = false)
    private String nickname;
    @Column(nullable = false)
    private Timestamp registerAt;
    @Column(nullable = false)
    private Timestamp lastSeenAt;
    @Column(nullable = false)
    private Timestamp lastAccessAt;
    @Column(nullable = false)
    private String githubLogin;
    @Column(nullable = false)
    private Long githubUserId;
}
