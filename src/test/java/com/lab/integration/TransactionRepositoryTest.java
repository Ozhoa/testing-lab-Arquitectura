package com.lab.integration;

import com.lab.model.Transaction;
import com.lab.model.Transaction.TransactionStatus;
import com.lab.repository.TransactionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  EJERCICIO B – Integración + DB con @DataJpaTest (H2)           │
 * │                                                                  │
 * │  Levanta solo la capa JPA sin el servidor web completo.          │
 * │  Usa H2 en memoria. Para Postgres real → ver la clase            │
 * │  TransactionRepositoryPostgresIT (con Testcontainers).           │
 * └─────────────────────────────────────────────────────────────────┘
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("TransactionRepository – Integración H2 (@DataJpaTest)")
class TransactionRepositoryTest {

    @Autowired
    private TransactionRepository repo;

    // ── fixture ──────────────────────────────────────────────────────
    private static final LocalDate D_JAN_01 = LocalDate.of(2024, 1, 1);
    private static final LocalDate D_JAN_15 = LocalDate.of(2024, 1, 15);
    private static final LocalDate D_JAN_31 = LocalDate.of(2024, 1, 31);
    private static final LocalDate D_FEB_01 = LocalDate.of(2024, 2, 1);
    private static final LocalDate D_FEB_28 = LocalDate.of(2024, 2, 28);

    @BeforeEach
    void seed() {
        repo.deleteAll();
        repo.saveAll(List.of(
            tx("TX-001", "100.00",   D_JAN_01,  TransactionStatus.APPROVED),
            tx("TX-002", "250.00",   D_JAN_15,  TransactionStatus.APPROVED),
            tx("TX-003", "75.00",    D_JAN_31,  TransactionStatus.PENDING),
            tx("TX-004", "5000.00",  D_FEB_01,  TransactionStatus.APPROVED),
            tx("TX-005", "3000.00",  D_FEB_28,  TransactionStatus.REJECTED),
            tx("TX-006", "800.00",   D_FEB_28,  TransactionStatus.REVERSED)
        ));
    }

    // ═══════════════════════════════════════════════════════════════
    //  1. findByTransactionDateBetween
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("1 · findByTransactionDateBetween")
    class FindByDateRange {

        @Test
        @DisplayName("Rango completo de enero → 3 transacciones")
        void january_returnsThree() {
            List<Transaction> result = repo.findByTransactionDateBetween(D_JAN_01, D_JAN_31);
            assertThat(result).hasSize(3)
                .extracting(Transaction::getReference)
                .containsExactlyInAnyOrder("TX-001", "TX-002", "TX-003");
        }

        @Test
        @DisplayName("Rango completo de febrero → 3 transacciones")
        void february_returnsThree() {
            List<Transaction> result = repo.findByTransactionDateBetween(D_FEB_01, D_FEB_28);
            assertThat(result).hasSize(3)
                .extracting(Transaction::getReference)
                .containsExactlyInAnyOrder("TX-004", "TX-005", "TX-006");
        }

        @Test
        @DisplayName("Fecha inicio = fecha fin (un solo día) → solo esa transacción")
        void singleDay_jan01() {
            List<Transaction> result = repo.findByTransactionDateBetween(D_JAN_01, D_JAN_01);
            assertThat(result).hasSize(1)
                .first()
                .extracting(Transaction::getReference)
                .isEqualTo("TX-001");
        }

        @Test
        @DisplayName("Rango cruzado enero-febrero → todas las transacciones")
        void crossMonth_allTransactions() {
            List<Transaction> result = repo.findByTransactionDateBetween(D_JAN_01, D_FEB_28);
            assertThat(result).hasSize(6);
        }

        @Test
        @DisplayName("Rango sin transacciones → lista vacía")
        void noTransactionsInRange_returnsEmpty() {
            LocalDate mar = LocalDate.of(2024, 3, 1);
            List<Transaction> result = repo.findByTransactionDateBetween(mar, mar.plusDays(30));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Límite inferior igual al último día del rango → incluye ese día")
        void boundary_fromEqualsToFeb28() {
            List<Transaction> result = repo.findByTransactionDateBetween(D_FEB_28, D_FEB_28);
            assertThat(result).hasSize(2)
                .extracting(Transaction::getReference)
                .containsExactlyInAnyOrder("TX-005", "TX-006");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  2. findByTransactionDateBetweenAndStatus
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("2 · findByTransactionDateBetweenAndStatus")
    class FindByDateRangeAndStatus {

        @Test
        @DisplayName("Enero APPROVED → 2 transacciones")
        void january_approved_returnsTwo() {
            List<Transaction> result = repo.findByTransactionDateBetweenAndStatus(
                D_JAN_01, D_JAN_31, TransactionStatus.APPROVED);
            assertThat(result)
                .hasSize(2)
                .extracting(Transaction::getReference)
                .containsExactlyInAnyOrder("TX-001", "TX-002");
        }

        @Test
        @DisplayName("Enero PENDING → 1 transacción")
        void january_pending_returnsOne() {
            List<Transaction> result = repo.findByTransactionDateBetweenAndStatus(
                D_JAN_01, D_JAN_31, TransactionStatus.PENDING);
            assertThat(result).hasSize(1)
                .first().extracting(Transaction::getReference).isEqualTo("TX-003");
        }

        @Test
        @DisplayName("Rango completo REJECTED → 1 transacción")
        void fullRange_rejected_returnsOne() {
            List<Transaction> result = repo.findByTransactionDateBetweenAndStatus(
                D_JAN_01, D_FEB_28, TransactionStatus.REJECTED);
            assertThat(result).hasSize(1)
                .first().extracting(Transaction::getReference).isEqualTo("TX-005");
        }

        @Test
        @DisplayName("Estado sin transacciones en el rango → lista vacía")
        void noMatchingStatus_returnsEmpty() {
            List<Transaction> result = repo.findByTransactionDateBetweenAndStatus(
                D_JAN_01, D_JAN_31, TransactionStatus.REVERSED);
            assertThat(result).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  3. sumApprovedBetween (JPQL personalizado)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("3 · sumApprovedBetween (JPQL)")
    class SumApprovedBetween {

        @Test
        @DisplayName("Enero → suma APPROVED = 100 + 250 = 350.00")
        void january_sumApproved() {
            BigDecimal sum = repo.sumApprovedBetween(D_JAN_01, D_JAN_31);
            assertThat(sum).isEqualByComparingTo("350.00");
        }

        @Test
        @DisplayName("Febrero → suma APPROVED = 5000.00")
        void february_sumApproved() {
            BigDecimal sum = repo.sumApprovedBetween(D_FEB_01, D_FEB_28);
            assertThat(sum).isEqualByComparingTo("5000.00");
        }

        @Test
        @DisplayName("Rango sin APPROVED → devuelve 0 (COALESCE)")
        void noApprovedInRange_returnsZero() {
            LocalDate mar = LocalDate.of(2024, 3, 1);
            BigDecimal sum = repo.sumApprovedBetween(mar, mar.plusDays(30));
            assertThat(sum).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Rango cruzado → suma total APPROVED = 5350.00")
        void crossMonth_sumApproved() {
            BigDecimal sum = repo.sumApprovedBetween(D_JAN_01, D_FEB_28);
            assertThat(sum).isEqualByComparingTo("5350.00");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  4. findAboveAmountInRange (JPQL con ordenamiento)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("4 · findAboveAmountInRange (JPQL con ORDER BY amount DESC)")
    class FindAboveAmountInRange {

        @Test
        @DisplayName("Febrero, monto >= 800 → TX-004 (5000) y TX-005 (3000) y TX-006 (800)")
        void february_above800() {
            List<Transaction> result = repo.findAboveAmountInRange(
                D_FEB_01, D_FEB_28, new BigDecimal("800.00"));
            assertThat(result).hasSize(3)
                .extracting(Transaction::getReference)
                .containsExactly("TX-004", "TX-005", "TX-006"); // ordenado DESC
        }

        @Test
        @DisplayName("Febrero, monto > 3000 → solo TX-004")
        void february_above3000() {
            List<Transaction> result = repo.findAboveAmountInRange(
                D_FEB_01, D_FEB_28, new BigDecimal("3000.01"));
            assertThat(result).hasSize(1)
                .first().extracting(Transaction::getReference).isEqualTo("TX-004");
        }

        @Test
        @DisplayName("Enero, monto >= 10000 → vacío")
        void january_aboveAll_returnsEmpty() {
            List<Transaction> result = repo.findAboveAmountInRange(
                D_JAN_01, D_JAN_31, new BigDecimal("10000.00"));
            assertThat(result).isEmpty();
        }

        @ParameterizedTest(name = "minAmount={0} → expected count={1}")
        @CsvSource({
            "0.01,   6",
            "75.00,  6",
            "100.00, 5",
            "250.01, 4",
            "800.00, 4",
            "800.01, 3"
        })
        @DisplayName("Rango total – conteo por umbral de monto")
        void fullRange_countByThreshold(String minAmount, int expectedCount) {
            List<Transaction> result = repo.findAboveAmountInRange(
                D_JAN_01, D_FEB_28, new BigDecimal(minAmount));
            assertThat(result).hasSize(expectedCount);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────
    private Transaction tx(String ref, String amount, LocalDate date, TransactionStatus status) {
        return Transaction.builder()
            .reference(ref)
            .amount(new BigDecimal(amount))
            .transactionDate(date)
            .status(status)
            .build();
    }
}
