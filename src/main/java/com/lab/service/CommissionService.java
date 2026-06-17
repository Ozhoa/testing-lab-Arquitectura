package com.lab.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Servicio de cálculo de comisiones por tramos (tiers).
 *
 * Estructura de tramos:
 * ┌─────────────────────────┬──────────────┐
 * │ Monto de transacción    │ Comisión     │
 * ├─────────────────────────┼──────────────┤
 * │ $0.00  –  $999.99       │     1.5 %    │
 * │ $1000  –  $4999.99      │     1.2 %    │
 * │ $5000  –  $9999.99      │     0.9 %    │
 * │ $10000 +                │     0.5 %    │
 * └─────────────────────────┴──────────────┘
 *
 * Reglas adicionales:
 *  - Monto negativo o nulo → excepción.
 *  - Monto = 0 → comisión = 0.
 *  - Resultado redondeado a 2 decimales (HALF_UP).
 *  - Se aplica un mínimo de comisión de $0.50 para montos > 0.
 */
@Service
public class CommissionService {

    // ── Límites de tramo (inclusive en el inferior, exclusivo en el superior) ──
    static final BigDecimal TIER_1_MAX  = new BigDecimal("1000.00");
    static final BigDecimal TIER_2_MAX  = new BigDecimal("5000.00");
    static final BigDecimal TIER_3_MAX  = new BigDecimal("10000.00");

    // ── Tasas ──
    static final BigDecimal RATE_TIER_1 = new BigDecimal("0.015");  // 1.5 %
    static final BigDecimal RATE_TIER_2 = new BigDecimal("0.012");  // 1.2 %
    static final BigDecimal RATE_TIER_3 = new BigDecimal("0.009");  // 0.9 %
    static final BigDecimal RATE_TIER_4 = new BigDecimal("0.005");  // 0.5 %

    // ── Comisión mínima ──
    static final BigDecimal MIN_COMMISSION = new BigDecimal("0.50");

    /**
     * Calcula la comisión para un monto dado.
     *
     * @param amount monto bruto de la transacción (≥ 0)
     * @return comisión redondeada a 2 decimales
     * @throws IllegalArgumentException si el monto es null o negativo
     */
    public BigDecimal calculate(BigDecimal amount) {
        validate(amount);

        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal rate = resolveRate(amount);
        BigDecimal raw  = amount.multiply(rate);
        BigDecimal rounded = raw.setScale(2, RoundingMode.HALF_UP);

        // Aplicar mínimo
        return rounded.compareTo(MIN_COMMISSION) < 0 ? MIN_COMMISSION : rounded;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void validate(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("El monto no puede ser nulo");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                "El monto no puede ser negativo: " + amount);
        }
    }

    BigDecimal resolveRate(BigDecimal amount) {
        if (amount.compareTo(TIER_1_MAX) < 0) return RATE_TIER_1;
        if (amount.compareTo(TIER_2_MAX) < 0) return RATE_TIER_2;
        if (amount.compareTo(TIER_3_MAX) < 0) return RATE_TIER_3;
        return RATE_TIER_4;
    }
}
