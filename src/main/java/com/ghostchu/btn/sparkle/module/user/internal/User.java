package com.ghostchu.btn.sparkle.module.user.internal;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.OffsetDateTime;

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
    private OffsetDateTime registerAt;
    @Column(nullable = false)
    private OffsetDateTime lastSeenAt;
    @Column(nullable = false)
    private OffsetDateTime lastAccessAt;
    @Column(nullable = false)
    private String githubLogin;
    @Column(nullable = true)
    private Long githubUserId;
    @Column(nullable = false)
    private Boolean banned;
    @Column(nullable = false)
    private Integer randomGroup;

    public boolean isSystemAccount() {
        return email.endsWith("@sparkle.system");
    }
}
