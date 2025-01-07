package com.ghostchu.btn.sparkle.module.user.internal;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user",
        uniqueConstraints = {@UniqueConstraint(columnNames = "githubUserId")},
        indexes = {@Index(columnList = "githubLogin"),@Index(columnList = "githubUserId")})
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class User implements Serializable {
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
    @Column()
    private Long githubUserId;
    @Column()
    private OffsetDateTime bannedAt;
    @Column()
    private String bannedReason;
    @Column(nullable = false)
    private Integer randomGroup;
    @Version
    @Column(nullable = false)
    private long version;

    public boolean isSystemAccount() {
        return email.endsWith("@sparkle.system");
    }
}
