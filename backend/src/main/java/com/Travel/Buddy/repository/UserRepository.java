package com.Travel.Buddy.repository;

import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.entity.UserStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByStatus(UserStatus status);

    /**
     * Admin console search. (FR-31)
     *
     * <p>Matches name or email, case-insensitively; an empty or
     * null query is the whole list, because a console that shows
     * nothing until you type hides the users who need attention
     * most.
     */
    @Query("""
            SELECT u
            FROM User u
            WHERE :query IS NULL
               OR LOWER(u.fullName)
                    LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(u.email)
                    LIKE LOWER(CONCAT('%', :query, '%'))
            ORDER BY u.createdAt DESC
            """)
    List<User> search(
            @Param("query") String query,
            Pageable pageable
    );
}