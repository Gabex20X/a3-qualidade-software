package br.edu.financas;

import br.edu.financas.Modelo.Perfil;
import br.edu.financas.Modelo.StatusConta;
import br.edu.financas.Modelo.StatusMeta;
import br.edu.financas.Modelo.StatusOrcamento;
import br.edu.financas.Modelo.Tipo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testes unitarios das regras puras (padrao AAA).
 * Nomes provisorios por regra (rnXX); na Fase 3 passam a citar o caso de teste (ctXX).
 */
class RegrasTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 12, 3);

    // RN-01 Perfil por idade: limites 14|15, 29|30, 59|60
    @ParameterizedTest(name = "idade {0} -> {1}")
    @CsvSource({"0,PADRAO", "14,PADRAO", "15,JOVEM", "29,JOVEM", "30,PADRAO", "59,PADRAO", "60,IDOSO", "130,IDOSO"})
    void rn01_perfilPorIdade_nosLimites(int idade, Perfil esperado) {
        // Arrange: idade vinda do CsvSource
        // Act
        Perfil perfil = Regras.perfilPorIdade(idade);
        // Assert
        assertEquals(esperado, perfil);
    }

    @ParameterizedTest(name = "idade invalida {0}")
    @ValueSource(ints = {-1, 131})
    void rn01_idadeForaDoIntervalo_eRecusada(int idade) {
        // Arrange: idade vinda do ValueSource
        // Act + Assert
        assertThrows(Erros.Validacao.class, () -> Regras.perfilPorIdade(idade));
    }

    // RN-02 Alerta de orcamento: limite 100,00 -> 79,99 | 80,00 | 100,00 | 100,01
    @ParameterizedTest(name = "gasto {0} de 100.00 -> {1}")
    @CsvSource({"0.00,OK", "79.99,OK", "80.00,ATENCAO", "100.00,ATENCAO", "100.01,ESTOURADO"})
    void rn02_statusOrcamento_nosLimites(String gasto, StatusOrcamento esperado) {
        // Arrange
        BigDecimal limite = new BigDecimal("100.00");
        // Act
        StatusOrcamento status = Regras.statusOrcamento(new BigDecimal(gasto), limite);
        // Assert
        assertEquals(esperado, status);
    }

    // RN-03 Conta a vencer: hoje = 03/12; vencimento em 4, 3, 0 e -1 dias
    @ParameterizedTest(name = "vencimento {0} -> {1}")
    @CsvSource({"2026-12-07,EM_DIA", "2026-12-06,A_VENCER", "2026-12-03,A_VENCER", "2026-12-02,ATRASADA"})
    void rn03_statusConta_nosLimites(String vencimento, StatusConta esperado) {
        // Arrange
        LocalDate venc = LocalDate.parse(vencimento);
        // Act
        StatusConta status = Regras.statusConta(venc, false, HOJE);
        // Assert
        assertEquals(esperado, status);
    }

    @Test
    void rn03_contaPaga_ePagaMesmoVencida() {
        // Arrange
        LocalDate vencida = HOJE.minusDays(10);
        // Act
        StatusConta status = Regras.statusConta(vencida, true, HOJE);
        // Assert
        assertEquals(StatusConta.PAGA, status);
    }

    // RN-04 Gasto alto de idoso: 500,00 nao alerta; 500,01 alerta
    @ParameterizedTest(name = "{0} {1} {2} -> alerta {3}")
    @CsvSource({
            "IDOSO,DESPESA,500.00,false",
            "IDOSO,DESPESA,500.01,true",
            "IDOSO,RECEITA,9000.00,false",
            "JOVEM,DESPESA,9000.00,false",
            "PADRAO,DESPESA,9000.00,false"})
    void rn04_alertaFamiliar(Perfil perfil, Tipo tipo, String valor, boolean esperado) {
        // Arrange
        BigDecimal v = new BigDecimal(valor);
        // Act
        boolean alerta = Regras.exigeAlertaFamiliar(perfil, tipo, v);
        // Assert
        assertEquals(esperado, alerta);
    }

    // RN-05 Limite de familiares: 0 e 1 podem; 2 nao
    @ParameterizedTest(name = "{0} familiares -> pode adicionar {1}")
    @CsvSource({"0,true", "1,true", "2,false"})
    void rn05_limiteDeFamiliares(int atuais, boolean esperado) {
        // Arrange: quantidade vinda do CsvSource
        // Act
        boolean pode = Regras.podeAdicionarFamiliar(atuais);
        // Assert
        assertEquals(esperado, pode);
    }

    // RN-06 Metas: 99,99 de 100 -> 99% em andamento; 100 -> concluida; acima -> teto 100%
    @ParameterizedTest(name = "{0} de 100.00 -> {1}% {2}")
    @CsvSource({"0.00,0,EM_ANDAMENTO", "99.99,99,EM_ANDAMENTO", "100.00,100,CONCLUIDA", "150.00,100,CONCLUIDA"})
    void rn06_progressoDaMeta(String atual, int pctEsperado, StatusMeta statusEsperado) {
        // Arrange
        BigDecimal alvo = new BigDecimal("100.00");
        BigDecimal valorAtual = new BigDecimal(atual);
        // Act
        int pct = Regras.percentualMeta(valorAtual, alvo);
        StatusMeta status = Regras.statusMeta(valorAtual, alvo);
        // Assert
        assertEquals(pctEsperado, pct);
        assertEquals(statusEsperado, status);
    }

    // Validacao de valor: 0,01 e 1.000.000,00 aceitos; vizinhos recusados
    @ParameterizedTest(name = "valor valido {0}")
    @ValueSource(strings = {"0.01", "1000000.00", "10.5"})
    void valor_dentroDoIntervalo_eAceito(String valor) {
        // Arrange
        BigDecimal v = new BigDecimal(valor);
        // Act + Assert
        assertDoesNotThrow(() -> Regras.validarValor(v));
    }

    @ParameterizedTest(name = "valor invalido {0}")
    @ValueSource(strings = {"0.00", "-1.00", "1000000.01", "10.001"})
    void valor_foraDoIntervaloOuComMaisDeDuasCasas_eRecusado(String valor) {
        // Arrange
        BigDecimal v = new BigDecimal(valor);
        // Act + Assert
        assertThrows(Erros.Validacao.class, () -> Regras.validarValor(v));
    }
}
