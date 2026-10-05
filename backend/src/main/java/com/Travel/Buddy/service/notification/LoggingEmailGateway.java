package com.Travel.Buddy.service.notification;

import com.Travel.Buddy.entity.Notification;
import com.Travel.Buddy.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logging email gateway used in development. (FR-25)
 *
 * <p>Records the attempt so a developer can see exactly what
 * would have been sent. Marked as the fallback so a real
 * implementation takes precedence via Spring's normal
 * single-bean-of-a-type resolution once SMTP is configured.
 */
@Component
public class LoggingEmailGateway implements EmailGateway {

    private static final Logger log = LoggerFactory.getLogger(
            LoggingEmailGateway.class
    );

    @Override
    public boolean send(User to, Notification notification) {
        log.info(
                "[email-gateway] to={} subject=\"{}\" type={} related={}:{}",
                to.getEmail(),
                notification.getTitle(),
                notification.getType(),
                notification.getRelatedType(),
                notification.getRelatedId()
        );
        return true;
    }
}