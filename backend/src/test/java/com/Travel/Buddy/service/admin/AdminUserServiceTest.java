package com.Travel.Buddy.service.admin;

import com.Travel.Buddy.dto.admin.AdminSuspendRequest;
import com.Travel.Buddy.dto.admin.AdminUserResponse;
import com.Travel.Buddy.entity.Role;
import com.Travel.Buddy.entity.User;
import com.Travel.Buddy.entity.UserStatus;
import com.Travel.Buddy.exception.PartnerApplicationException;
import com.Travel.Buddy.repository.BookingRepository;
import com.Travel.Buddy.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * User management rules (FR-31): suspension is a flag plus an
 * audit entry, and the console can never lock itself out.
 */
class AdminUserServiceTest {

    private final UserRepository userRepository =
            mock(UserRepository.class);
    private final BookingRepository bookingRepository =
            mock(BookingRepository.class);
    private final AdminAuditService auditService =
            mock(AdminAuditService.class);

    private AdminUserService adminUserService;
    private User target;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(
                userRepository,
                bookingRepository,
                auditService
        );

        target = new User();
        target.setUserId(5L);
        target.setFullName("Sample Traveler");
        target.setEmail("traveler@example.com");
        target.setRole(Role.ROLE_USER);
        target.setStatus(UserStatus.ACTIVE);

        when(userRepository.findById(5L))
                .thenReturn(Optional.of(target));
        when(bookingRepository.countBookingsPerUser())
                .thenReturn(List.of());
    }

    @Test
    void suspensionFlagsTheAccountAndWritesAnAuditEntry() {
        AdminUserResponse response = adminUserService.suspend(
                1L,
                5L,
                new AdminSuspendRequest("fraud investigation")
        );

        assertEquals(UserStatus.SUSPENDED, target.getStatus());
        assertEquals(UserStatus.SUSPENDED, response.status());
        verify(auditService).record(
                1L,
                "USER_SUSPENDED",
                "User",
                5L,
                "fraud investigation"
        );
    }

    @Test
    void anAdminCannotSuspendTheirOwnAccount() {
        PartnerApplicationException exception = assertThrows(
                PartnerApplicationException.class,
                () -> adminUserService.suspend(
                        5L,
                        5L,
                        new AdminSuspendRequest("slip of the finger")
                )
        );

        assertEquals(UserStatus.ACTIVE, target.getStatus());
        assertEquals(400, exception.getStatus().value());
        verifyNoInteractions(auditService);
    }

    @Test
    void superAdminAccountsCannotBeSuspendedFromTheConsole() {
        target.setRole(Role.ROLE_SUPER_ADMIN);

        PartnerApplicationException exception = assertThrows(
                PartnerApplicationException.class,
                () -> adminUserService.suspend(
                        1L,
                        5L,
                        new AdminSuspendRequest("reviewer dispute")
                )
        );

        assertEquals(400, exception.getStatus().value());
        assertEquals(UserStatus.ACTIVE, target.getStatus());
        verifyNoInteractions(auditService);
    }

    @Test
    void suspendingTwiceIsAConflictNotASilentSecondEntry() {
        target.setStatus(UserStatus.SUSPENDED);

        PartnerApplicationException exception = assertThrows(
                PartnerApplicationException.class,
                () -> adminUserService.suspend(
                        1L,
                        5L,
                        new AdminSuspendRequest("again")
                )
        );

        assertEquals(409, exception.getStatus().value());
        verifyNoInteractions(auditService);
    }

    @Test
    void reactivationRestoresActiveAndAuditsTheRestore() {
        target.setStatus(UserStatus.SUSPENDED);

        AdminUserResponse response =
                adminUserService.reactivate(1L, 5L);

        assertEquals(UserStatus.ACTIVE, target.getStatus());
        assertEquals(UserStatus.ACTIVE, response.status());
        verify(auditService).record(
                1L,
                "USER_REACTIVATED",
                "User",
                5L,
                "Account restored to ACTIVE"
        );
    }

    @Test
    void reactivatingAnActiveAccountIsAConflict() {
        PartnerApplicationException exception = assertThrows(
                PartnerApplicationException.class,
                () -> adminUserService.reactivate(1L, 5L)
        );

        assertEquals(409, exception.getStatus().value());
        verifyNoInteractions(auditService);
    }

    @Test
    void listCarriesThePerUserBookingCount() {
        when(userRepository.search(isNull(), any()))
                .thenReturn(List.of(target));
        when(bookingRepository.countBookingsPerUser())
                .thenReturn(List.<Object[]>of(new Object[]{5L, 3L}));

        List<AdminUserResponse> users = adminUserService.list(null);

        assertEquals(1, users.size());
        assertEquals(3L, users.get(0).bookingCount());
        assertEquals("traveler@example.com", users.get(0).email());
    }

    @Test
    void searchForwardsTheTrimmedQuery() {
        when(userRepository.search(eq("ada"), any()))
                .thenReturn(List.of());

        adminUserService.list("  ada  ");

        verify(userRepository).search(eq("ada"), any());
    }
}