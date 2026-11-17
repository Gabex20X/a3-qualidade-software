package br.edu.financas;

import br.edu.financas.Modelo.Perfil;
import br.edu.financas.Modelo.StatusConta;
import br.edu.financas.Modelo.StatusMeta;
import br.edu.financas.Modelo.StatusOrcamento;
import br.edu.financas.Modelo.Tipo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Regras de negocio puras (sem banco, sem relogio do sistema).
 * Cada metodo corresponde a uma regra RN-xx do plano de teste.
 */
public final class Regras {

    // RN-01 Perfil por idade
    public static final int IDADE_MAXIMA = 130;
    public static final int IDADE_IDOSO = 60;
    public static final int IDADE_JOVEM_MIN = 15;
    public static final int IDADE_JOVEM_MAX = 29;

    // Validacao de valores monetarios
    public static final BigDecimal VALOR_MINIMO = new BigDecimal("0.01");
    public static final BigDecimal VALOR_MAXIMO = new BigDecimal("1000000.00");

    // RN-02 Alerta de orcamento
    public static final BigDecimal LIMIAR_ATENCAO = new BigDecimal("0.80");

    // RN-03 Conta a vencer
    public static final int DIAS_ALERTA_VENCIMENTO = 3;

    // RN-04 Gasto alto de idoso
    public static final BigDecimal LIMITE_GASTO_ALTO_IDOSO = new BigDecimal("500.00");

    // RN-05 Compartilhamento familiar
    public static final int MAX_FAMILIARES = 2;

    private static final BigDecimal CEM = new BigDecimal("100");

    private Regras() {
    }

    /** RN-01: 60+ e IDOSO; 15 a 29 e JOVEM; demais idades, PADRAO. */
    public static Perfil perfilPorIdade(int idade) {
        if (idade < 0 || idade > IDADE_MAXIMA) {
            throw new Erros.Validacao("idade deve estar entre 0 e " + IDADE_MAXIMA);
        }
        if (idade >= IDADE_IDOSO) {
            return Perfil.IDOSO;
        }
        if (idade >= IDADE_JOVEM_MIN && idade <= IDADE_JOVEM_MAX) {
            return Perfil.JOVEM;
        }
        return Perfil.PADRAO;
    }

    /** Valor monetario: entre 0,01 e 1.000.000,00, com no maximo 2 casas decimais. */
    public static void validarValor(BigDecimal valor) {
        if (valor == null) {
            throw new Erros.Validacao("valor e obrigatorio");
        }
        if (valor.stripTrailingZeros().scale() > 2) {
            throw new Erros.Validacao("valor deve ter no maximo 2 casas decimais");
        }
        if (valor.compareTo(VALOR_MINIMO) < 0 || valor.compareTo(VALOR_MAXIMO) > 0) {
            throw new Erros.Validacao("valor deve estar entre 0.01 e 1000000.00");
        }
    }

    /** RN-02: abaixo de 80% do limite, OK; de 80% a 100%, ATENCAO; acima de 100%, ESTOURADO. */
    public static StatusOrcamento statusOrcamento(BigDecimal gasto, BigDecimal limite) {
        if (limite == null || limite.signum() <= 0) {
            throw new Erros.Validacao("limite do orcamento deve ser maior que zero");
        }
        if (gasto.compareTo(limite) > 0) {
            return StatusOrcamento.ESTOURADO;
        }
        if (gasto.compareTo(limite.multiply(LIMIAR_ATENCAO)) >= 0) {
            return StatusOrcamento.ATENCAO;
        }
        return StatusOrcamento.OK;
    }

    /** RN-03: paga; vencida (depois do vencimento); a vencer (0 a 3 dias); ou em dia. */
    public static StatusConta statusConta(LocalDate vencimento, boolean paga, LocalDate hoje) {
        if (paga) {
            return StatusConta.PAGA;
        }
        if (hoje.isAfter(vencimento)) {
            return StatusConta.ATRASADA;
        }
        long dias = ChronoUnit.DAYS.between(hoje, vencimento);
        return dias <= DIAS_ALERTA_VENCIMENTO ? StatusConta.A_VENCER : StatusConta.EM_DIA;
    }

    /** RN-04: despesa de usuario IDOSO acima de R$ 500,00 gera alerta ao familiar. */
    public static boolean exigeAlertaFamiliar(Perfil perfil, Tipo tipo, BigDecimal valor) {
        return perfil == Perfil.IDOSO
                && tipo == Tipo.DESPESA
                && valor.compareTo(LIMITE_GASTO_ALTO_IDOSO) > 0;
    }

    /** RN-05: um idoso pode ter no maximo 2 familiares vinculados. */
    public static boolean podeAdicionarFamiliar(int familiaresAtuais) {
        return familiaresAtuais < MAX_FAMILIARES;
    }

    /** RN-06: percentual da meta (arredondado para baixo, teto 100). */
    public static int percentualMeta(BigDecimal valorAtual, BigDecimal valorAlvo) {
        if (valorAlvo == null || valorAlvo.signum() <= 0) {
            throw new Erros.Validacao("valor alvo da meta deve ser maior que zero");
        }
        int pct = valorAtual.multiply(CEM).divide(valorAlvo, 0, RoundingMode.DOWN).intValue();
        return Math.min(pct, 100);
    }

    /** RN-06: a meta e CONCLUIDA quando o valor atual alcanca o alvo. */
    public static StatusMeta statusMeta(BigDecimal valorAtual, BigDecimal valorAlvo) {
        return valorAtual.compareTo(valorAlvo) >= 0 ? StatusMeta.CONCLUIDA : StatusMeta.EM_ANDAMENTO;
    }
}
