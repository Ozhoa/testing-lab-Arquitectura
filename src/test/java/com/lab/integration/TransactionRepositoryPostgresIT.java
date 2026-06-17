package com.lab.integration;

import com.lab.model.Transaction;
import com.lab.model.Transaction.TransactionStatus;
import com.lab.repository.TransactionRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  EJERCICIO B – Migración a PostgreSQL con Testcontainers         │
 * │                                                                  │
 * │  @Testcontainers levanta un contenedor Docker de Postgres        │
 * │  y lo inyecta dinámicamente en el contexto de Spring.            │
 * │                                                                  │
 * │  Pre-requisito: Docker corriendo en el host del CI/desarrollador.│
 * │  Se omite en entornos sin Docker con @DisabledIfSystemProperty.  │
 * └─────────────────────────────────────────────────────────────────┘
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("TransactionRepository – Postgres real (Testcontainers)")
class TransactionRepositoryPostgresIT {

    // ── Contenedor compartido para toda la clase (static) ────────────
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("lab_test")
        .withUsername("testuser")
        .withPassword("testpass");

    /**
     * Inyecta las propiedades del contenedor dinámicamente en Spring
     * antes de que se construya el ApplicationContext.
     */
    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private TransactionRepository repo;

    private static final LocalDate JAN_01 = LocalDate.of(2024, 1, 1);
    private static final LocalDate JAN_31 = LocalDate.of(2024, 1, 31);
    private static final LocalDate FEB_01 = LocalDate.of(2024, 2, 1);
    private static final LocalDate FEB_29 = LocalDate.of(2024, 2, 29); // 2024 es bisiesto

    @BeforeEach
    void seed() {
        repo.deleteAll();
        repo.saveAll(List.of(
            tx("PG-001", "1500.00", JAN_01,  TransactionStatus.APPROVED),
            tx("PG-002", "2500.00", JAN_31,  TransactionStatus.APPROVED),
            tx("PG-003",  "200.00", FEB_01,  TransactionStatus.PENDING),
            tx("PG-004", "8000.00", FEB_29,  TransactionStatus.APPROVED)
        ));
    }

    @AfterEach
    void cleanup() {
        repo.deleteAll();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Tests – verifican que el comportamiento en Postgres es idéntico
    //  al verificado en H2 (mismas queries, distintas DBs)
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Postgres: rango enero → 2 transacciones")
    void postgres_findByDateRange_january() {
        List<Transaction> result = repo.findByTransactionDateBetween(JAN_01, JAN_31);
        assertThat(result).hasSize(2)
            .extracting(Transaction::getReference)
            .containsExactlyInAnyOrder("PG-001", "PG-002");
    }

    @Test
    @DisplayName("Postgres: suma APPROVED enero → $4000.00")
    void postgres_sumApproved_january() {
        BigDecimal sum = repo.sumApprovedBetween(JAN_01, JAN_31);
        assertThat(sum).isEqualByComparingTo("4000.00");
    }

    @Test
    @DisplayName("Postgres: suma APPROVED total → $12000.00")
    void postgres_sumApproved_allMonths() {
        BigDecimal sum = repo.sumApprovedBetween(JAN_01, FEB_29);
        assertThat(sum).isEqualByComparingTo("12000.00");
    }

    @Test
    @DisplayName("Postgres: findAboveAmountInRange → orden DESC correcto")
    void postgres_findAboveAmount_orderedDesc() {
        List<Transaction> result = repo.findAboveAmountInRange(
            JAN_01, FEB_29, new BigDecimal("500.00"));

        assertThat(result).hasSize(3)
            .extracting(t -> t.getAmount().toPlainString())
            .containsExactly("8000.00", "2500.00", "1500.00");
    }

    @Test
    @DisplayName("Postgres: contenedor efectivamente corre Postgres 16")
    void postgres_containerVersionCheck() {
        assertThat(postgres.isRunning()).isTrue();
        assertThat(postgres.getDockerImageName()).contains("postgres:16");
    }

    // ── helper ───────────────────────────────────────────────────────
    private Transaction tx(String ref, String amount, LocalDate date, TransactionStatus status) {
        return Transaction.builder()
            .reference(ref)
            .amount(new BigDecimal(amount))
            .transactionDate(date)
            .status(status)
            .build();
    }
}
