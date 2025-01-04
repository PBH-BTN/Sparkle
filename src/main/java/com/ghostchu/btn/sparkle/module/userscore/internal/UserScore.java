package com.ghostchu.btn.sparkle.module.userscore.internal;

import com.ghostchu.btn.sparkle.module.user.internal.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_score",
        uniqueConstraints = {@UniqueConstraint(columnNames = "user")},
        indexes = {@Index(columnList = "user")})
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UserScore {
    @Id
    @GeneratedValue
    @Column(nullable = false, unique = true)
    private Long id;
    @JoinColumn(nullable = false, name = "user")
    @ManyToOne(fetch = FetchType.EAGER)
    private User user;
    @Column(nullable = false)
    private Long scoreBytes;
    @Version
    private Long version;
}
