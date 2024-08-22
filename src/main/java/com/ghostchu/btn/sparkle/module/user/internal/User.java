package com.ghostchu.btn.sparkle.module.user.internal;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.sql.Timestamp;

@Entity
@Table(name = "user",
        uniqueConstraints = {@UniqueConstraint(columnNames = "githubId")},
        indexes = {@Index(columnList = "githubLogin"),@Index(columnList = "githubUserId")})
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@DynamicUpdate
public class User {
    @Id
    @GeneratedValue
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
    @Column(nullable = true)
    private Long githubUserId;
    @Column(nullable = false)
    private Boolean banned;
}
