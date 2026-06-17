package com.lab.repository;

import com.lab.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Transacciones dentro de un rango de fechas (ambas inclusive).
     */
    List<Transaction> findByTransactionDateBetween(LocalDate from, LocalDate to);

    /**
     * Transacciones dentro de un rango de fechas CON un estado específico.
     */
    List<Transaction> findByTransactionDateBetweenAndStatus(
            LocalDate from,
            LocalDate to,
            Transaction.TransactionStatus status);

    /**
     * Suma de montos aprobados en un rango de fechas.
     * Devuelve 0 si no hay registros (COALESCE).
     */
    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.transactionDate BETWEEN :from AND :to
          AND t.status = 'APPROVED'
        """)
    BigDecimal sumApprovedBetween(
            @Param("from") LocalDate from,
            @Param("to")   LocalDate to);

    /**
     * Transacciones por encima de un monto mínimo en el rango.
     */
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.transactionDate BETWEEN :from AND :to
          AND t.amount >= :minAmount
        ORDER BY t.amount DESC
        """)
    List<Transaction> findAboveAmountInRange(
            @Param("from")       LocalDate from,
            @Param("to")         LocalDate to,
            @Param("minAmount")  BigDecimal minAmount);
}
