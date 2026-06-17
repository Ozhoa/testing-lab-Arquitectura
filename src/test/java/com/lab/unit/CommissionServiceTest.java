package com.lab.unit;

import com.lab.service.CommissionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  EJERCICIO A – Unit Tests + Mockito + PIT Mutation Testing       │
 * │                                                                  │
 * │  Estrategia de cobertura:                                        │
 * │  1. Casos felices por tramo (tier coverage)                      │
 * │  2. Límites exactos entre tramos (boundary testing)              │
 * │  3. Redondeo HALF_UP en decimales largos                         │
 * │  4. Comisión mínima (floor)                                      │
 * │  5. Validaciones / casos de error                                │
 * │  6. Spy para verificar resolveRate (comportamiento interno)       │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * Ejecutar mutation testing:
 *   mvn test-compile pitest:mutationCoverage
 *
 * Los mutadores configurados en pom.xml son:
 *   CONDITIONALS_BOUNDARY, NEGATE_CONDITIONALS, MATH,
 *   INCREMENTS, RETURN_VALS, VOID_METHOD_CALLS
 */
@DisplayName("CommissionService – Unit Tests")
class CommissionServiceTest {

    private CommissionService service;

    @BeforeEach
    void setUp() {
        service = new CommissionService();
    }

    // ═══════════════════════════════════════════════════════════════
    //  1. TRAMOS CENTRALES (valores lejos de cualquier límite)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("1 · Valores representativos por tramo")
    class TierHappyPath {

        @Test
        @DisplayName("Tier 1 · $500 → 1.5 % → $7.50")
        void tier1_midRange() {
            BigDecimal result = service.calculate(new BigDecimal("500.00"));
            assertThat(result).isEqualByComparingTo("7.50");
        }

        @Test
        @DisplayName("Tier 2 · $2500 → 1.2 % → $30.00")
        void tier2_midRange() {
            BigDecimal result = service.calculate(new BigDecimal("2500.00"));
            assertThat(result).isEqualByComparingTo("30.00");
        }

        @Test
        @DisplayName("Tier 3 · $7500 → 0.9 % → $67.50")
        void tier3_midRange() {
            BigDecimal result = service.calculate(new BigDecimal("7500.00"));
            assertThat(result).isEqualByComparingTo("67.50");
        }

        @Test
        @DisplayName("Tier 4 · $50000 → 0.5 % → $250.00")
        void tier4_midRange() {
            BigDecimal result = service.calculate(new BigDecimal("50000.00"));
            assertThat(result).isEqualByComparingTo("250.00");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  2. LÍMITES DE TRAMO  ← más matados por PIT sin estos tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("2 · Boundary Testing – límites exactos entre tramos")
    class BoundaryTests {

        /**
         * Tabla de fronteras:
         *  999.99  → Tier 1 (< 1000)
         *  1000.00 → Tier 2 (>= 1000 y < 5000)
         *  4999.99 → Tier 2
         *  5000.00 → Tier 3 (>= 5000 y < 10000)
         *  9999.99 → Tier 3
         * 10000.00 → Tier 4 (>= 10000)
         */
        @ParameterizedTest(name = "${0} → tasa esperada {1}")
        @CsvSource({
            "999.99,  0.015",
            "1000.00, 0.012",
            "4999.99, 0.012",
            "5000.00, 0.009",
            "9999.99, 0.009",
            "10000.00,0.005"
        })
        @DisplayName("resolveRate devuelve la tasa correcta en los límites")
        void resolveRate_boundaryValues(String amount, String expectedRate) {
            BigDecimal rate = service.resolveRate(new BigDecimal(amount));
            assertThat(rate).isEqualByComparingTo(expectedRate);
        }

        @Test
        @DisplayName("$999.99 calcula con Tier 1 rate (1.5%)")
        void boundary_justBelowTier2() {
            // 999.99 * 0.015 = 14.999850 → round HALF_UP → 15.00
            BigDecimal result = service.calculate(new BigDecimal("999.99"));
            assertThat(result).isEqualByComparingTo("15.00");
        }

        @Test
        @DisplayName("$1000.00 calcula con Tier 2 rate (1.2%)")
        void boundary_exactlyTier2Start() {
            // 1000.00 * 0.012 = 12.00
            BigDecimal result = service.calculate(new BigDecimal("1000.00"));
            assertThat(result).isEqualByComparingTo("12.00");
        }

        @Test
        @DisplayName("$4999.99 calcula con Tier 2 rate (1.2%)")
        void boundary_justBelowTier3() {
            // 4999.99 * 0.012 = 59.9999 → 60.00
            BigDecimal result = service.calculate(new BigDecimal("4999.99"));
            assertThat(result).isEqualByComparingTo("60.00");
        }

        @Test
        @DisplayName("$5000.00 calcula con Tier 3 rate (0.9%)")
        void boundary_exactlyTier3Start() {
            // 5000.00 * 0.009 = 45.00
            BigDecimal result = service.calculate(new BigDecimal("5000.00"));
            assertThat(result).isEqualByComparingTo("45.00");
        }

        @Test
        @DisplayName("$9999.99 calcula con Tier 3 rate (0.9%)")
        void boundary_justBelowTier4() {
            // 9999.99 * 0.009 = 89.9999 → 90.00
            BigDecimal result = service.calculate(new BigDecimal("9999.99"));
            assertThat(result).isEqualByComparingTo("90.00");
        }

        @Test
        @DisplayName("$10000.00 calcula con Tier 4 rate (0.5%)")
        void boundary_exactlyTier4Start() {
            // 10000.00 * 0.005 = 50.00
            BigDecimal result = service.calculate(new BigDecimal("10000.00"));
            assertThat(result).isEqualByComparingTo("50.00");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  3. REDONDEO HALF_UP
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("3 · Redondeo HALF_UP en decimales")
    class RoundingTests {

        @Test
        @DisplayName("$33.34 → 1.5% → $0.5001 → redondea a $0.50 (mínimo)")
        void rounding_verySmallAmount() {
            // 33.34 * 0.015 = 0.5001 → rounded = 0.50 → mínimo = 0.50
            BigDecimal result = service.calculate(new BigDecimal("33.34"));
            assertThat(result).isEqualByComparingTo("0.50");
        }

        @Test
        @DisplayName("$133.34 → 1.5% → $2.0001 → redondea a $2.00")
        void rounding_halfUp_down() {
            // 133.34 * 0.015 = 2.0001 → HALF_UP → 2.00
            BigDecimal result = service.calculate(new BigDecimal("133.34"));
            assertThat(result).isEqualByComparingTo("2.00");
        }

        @Test
        @DisplayName("$133.67 → 1.5% → $2.0051 → redondea a $2.01")
        void rounding_halfUp_up() {
            // 133.67 * 0.015 = 2.0051 → HALF_UP → 2.01 (décima ≥ 5)
            BigDecimal result = service.calculate(new BigDecimal("133.67"));
            assertThat(result).isEqualByComparingTo("2.01");
        }

        @Test
        @DisplayName("$166.67 → 1.5% → $2.50005 → redondea a $2.50")
        void rounding_halfUp_exactHalf() {
            // 166.67 * 0.015 = 2.50005 → HALF_UP → 2.50
            BigDecimal result = service.calculate(new BigDecimal("166.67"));
            assertThat(result).isEqualByComparingTo("2.50");
        }

        @ParameterizedTest(name = "amount={0} → expected={1}")
        @CsvSource({
            "100.00, 1.50",
            "200.00, 3.00",
            "750.00, 11.25"
        })
        @DisplayName("Tabla paramétrica – redondeo consistente en Tier 1")
        void rounding_parametric(String amount, String expected) {
            assertThat(service.calculate(new BigDecimal(amount)))
                .isEqualByComparingTo(expected);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  4. COMISIÓN MÍNIMA
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("4 · Comisión mínima de $0.50")
    class MinimumCommissionTests {

        @Test
        @DisplayName("Monto muy pequeño → comisión mínima $0.50")
        void minimumCommission_verySmallAmount() {
            // 10.00 * 0.015 = 0.15 → por debajo del mínimo → 0.50
            BigDecimal result = service.calculate(new BigDecimal("10.00"));
            assertThat(result).isEqualByComparingTo("0.50");
        }

        @Test
        @DisplayName("$33.33 → 1.5% = 0.4999 → mínimo aplica → $0.50")
        void minimumCommission_justBelow() {
            BigDecimal result = service.calculate(new BigDecimal("33.33"));
            assertThat(result).isEqualByComparingTo("0.50");
        }

        @Test
        @DisplayName("$33.34 → 1.5% = 0.5001 → round → 0.50 → igual al mínimo")
        void minimumCommission_exactlyAtFloor() {
            BigDecimal result = service.calculate(new BigDecimal("33.34"));
            assertThat(result).isEqualByComparingTo("0.50");
        }

        @Test
        @DisplayName("$34.00 → 1.5% = 0.51 → por encima del mínimo")
        void minimumCommission_justAbove() {
            BigDecimal result = service.calculate(new BigDecimal("34.00"));
            assertThat(result).isEqualByComparingTo("0.51");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  5. CASOS DE BORDE Y ERRORES
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("5 · Validaciones y casos de error")
    class ValidationTests {

        @Test
        @DisplayName("Monto = 0 → comisión = 0.00")
        void zeroAmount_returnsZeroCommission() {
            BigDecimal result = service.calculate(BigDecimal.ZERO);
            assertThat(result).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Monto null → IllegalArgumentException")
        void nullAmount_throwsException() {
            assertThatThrownBy(() -> service.calculate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nulo");
        }

        @Test
        @DisplayName("Monto negativo → IllegalArgumentException con el valor")
        void negativeAmount_throwsException() {
            assertThatThrownBy(() -> service.calculate(new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativo");
        }

        @ParameterizedTest(name = "amount={0}")
        @ValueSource(strings = {"-0.01", "-100", "-9999.99"})
        @DisplayName("Cualquier valor negativo lanza excepción")
        void anyNegativeAmount_throwsException(String amount) {
            assertThatThrownBy(() -> service.calculate(new BigDecimal(amount)))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  6. SPY CON MOCKITO para verificar colaboración interna
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("6 · Mockito Spy – verificar resolveRate es invocado")
    class MockitoSpyTests {

        /**
         * Un Spy envuelve un objeto real y permite verificar interacciones
         * sin cambiar el comportamiento por defecto.
         * Útil para confirmar que calculate() delega en resolveRate().
         */
        @Test
        @DisplayName("Spy verifica que resolveRate() es llamado exactamente una vez")
        void spy_verifyResolveRateCalledOnce() {
            CommissionService spy = Mockito.spy(service);

            spy.calculate(new BigDecimal("500.00"));

            verify(spy, times(1)).resolveRate(new BigDecimal("500.00"));
        }

        @Test
        @DisplayName("Spy no llama resolveRate() cuando monto es cero")
        void spy_zeroAmount_noResolveRateCall() {
            CommissionService spy = Mockito.spy(service);

            spy.calculate(BigDecimal.ZERO);

            verify(spy, never()).resolveRate(any());
        }

        @Test
        @DisplayName("Spy con stubbing – forzar rate fijo y verificar resultado")
        void spy_stubResolveRate_returnsFixedCommission() {
            CommissionService spy = Mockito.spy(service);
            // Forzamos que resolveRate devuelva 10% para cualquier monto
            doReturn(new BigDecimal("0.10"))
                .when(spy).resolveRate(any(BigDecimal.class));

            BigDecimal result = spy.calculate(new BigDecimal("200.00"));

            // 200 * 0.10 = 20.00
            assertThat(result).isEqualByComparingTo("20.00");
            verify(spy).resolveRate(new BigDecimal("200.00"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  7. FUENTE DE DATOS EXTERNA (MethodSource) – tabla completa
    // ═══════════════════════════════════════════════════════════════

    @ParameterizedTest(name = "[{index}] ${0} → ${1}")
    @MethodSource("commissionTable")
    @DisplayName("7 · Tabla completa de casos representativos")
    void fullTable_parametric(String amount, String expected) {
        assertThat(service.calculate(new BigDecimal(amount)))
            .isEqualByComparingTo(expected);
    }

    static Stream<Arguments> commissionTable() {
        return Stream.of(
            // Tier 1 (1.5%)
            Arguments.of("50.00",     "0.75"),
            Arguments.of("100.00",    "1.50"),
            Arguments.of("500.00",    "7.50"),
            Arguments.of("999.99",    "15.00"),
            // Tier 2 (1.2%)
            Arguments.of("1000.00",   "12.00"),
            Arguments.of("3000.00",   "36.00"),
            Arguments.of("4999.99",   "60.00"),
            // Tier 3 (0.9%)
            Arguments.of("5000.00",   "45.00"),
            Arguments.of("7500.00",   "67.50"),
            Arguments.of("9999.99",   "90.00"),
            // Tier 4 (0.5%)
            Arguments.of("10000.00",  "50.00"),
            Arguments.of("25000.00",  "125.00"),
            Arguments.of("100000.00", "500.00")
        );
    }
}
