package com.looki.payment.config;

import com.looki.payment.entity.PaymentRecordStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * {@code ddl-auto: update} never changes an existing enum check constraint,
 * so databases created before CANCELLED/REFUNDED existed would reject them.
 * Rebuilds {@code payment_records_status_check} from the enum on startup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentSchemaUpdater {

    private static final String CONSTRAINT = "payment_records_status_check";

    private final JdbcTemplate jdbcTemplate;
    // auto-commit is off: both statements must run (and commit) on one connection
    private final TransactionTemplate transactionTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void syncStatusConstraint() {
        String allowed = Arrays.stream(PaymentRecordStatus.values())
                .map(s -> "'" + s.name() + "'")
                .collect(Collectors.joining(", "));
        try {
            transactionTemplate.executeWithoutResult(tx -> {
                jdbcTemplate.execute("ALTER TABLE payment_records DROP CONSTRAINT IF EXISTS " + CONSTRAINT);
                jdbcTemplate.execute("ALTER TABLE payment_records ADD CONSTRAINT " + CONSTRAINT
                        + " CHECK (status IN (" + allowed + "))");
            });
        } catch (DataAccessException e) {
            log.warn("Could not update {}: {}", CONSTRAINT, e.getMessage());
        }
    }
}
