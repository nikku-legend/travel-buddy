package com.Travel.Buddy.service.notification;

import com.Travel.Buddy.entity.Notification;
import com.Travel.Buddy.entity.User;

/**
 * Outbound email boundary. (FR-25)
 *
 * <p>Exists so notification logic can be tested and so the
 * transport can change without touching business code. The
 * current implementation only logs, because the project has no
 * SMTP credentials in development; a real provider implements
 * this interface and is picked up by the existing Spring context.
 */
public interface EmailGateway {

    /**
     * @return true when handed off, false when the send failed
     */
    boolean send(User to, Notification notification);
}