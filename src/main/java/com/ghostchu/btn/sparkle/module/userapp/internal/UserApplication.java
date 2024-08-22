package com.ghostchu.btn.sparkle.module.userapp.internal;

import com.ghostchu.btn.sparkle.module.user.internal.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

@Entity
@Table(name = "userapp", uniqueConstraints = {@UniqueConstraint(columnNames = "appId")},
        indexes = {@Index(columnList = "appId, appSecret"), @Index(columnList = "user")})
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UserApplication {
    @Id
    @GeneratedValue
    @Column(nullable = false, unique = true)
    private Long id;
    @Column(nullable = false, unique = true)
    private String appId;
    @Column(nullable = false)
    private String appSecret;
    @Column(nullable = false)
    private String comment;
    @Column(nullable = false)
    private Timestamp createdAt;
    @JoinColumn(nullable = false, name = "user")
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;
    @Column(nullable = false)
    private Boolean banned;
}
