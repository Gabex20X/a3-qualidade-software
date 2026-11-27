package br.edu.financas;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Dados compartilhados pelos testes: o relogio fixo em 03/12/2026 (data de referencia do plano). */
final class Fixtures {

    static final LocalDate HOJE = LocalDate.of(2026, 12, 3);
    static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-12-03T12:00:00Z"), ZoneOffset.UTC);

    private Fixtures() {
    }
}
