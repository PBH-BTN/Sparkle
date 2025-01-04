package com.ghostchu.btn.sparkle.module.userscore.internal;

import com.ghostchu.btn.sparkle.module.user.internal.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "user_score_history",
        uniqueConstraints = {@UniqueConstraint(columnNames = "user")},
        indexes = {@Index(columnList = "user")})
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UserScoreHistory {
    @Id
    @GeneratedValue
    @Column(nullable = false, unique = true)
    private Long id;
    @Column(nullable = false)
    private OffsetDateTime time;
    @JoinColumn(nullable = false, name = "user")
    @ManyToOne(fetch = FetchType.EAGER)
    private User user;
    @Column(nullable = false)
    private Long scoreBytesChanges;
    @Column(nullable = false)
    private Long scoreBytesNow;
    @Column()
    private String reason;
}
