package br.edu.financas;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Tipos do dominio. Registros imutaveis; id 0 significa "ainda nao salvo". */
public final class Modelo {

    private Modelo() {
    }

    public enum Perfil { IDOSO, JOVEM, PADRAO }

    public enum Tipo { RECEITA, DESPESA }

    public enum StatusOrcamento { OK, ATENCAO, ESTOURADO }

    public enum StatusConta { PAGA, EM_DIA, A_VENCER, ATRASADA }

    public enum StatusMeta { EM_ANDAMENTO, CONCLUIDA }

    public record Usuario(long id, String nome, int idade, Perfil perfil) {
    }

    public record Transacao(long id, long usuarioId, Tipo tipo, BigDecimal valor, String categoria,
                            LocalDate data, LocalDate vencimento, boolean paga, String descricao) {
    }

    public record Orcamento(long usuarioId, String categoria, BigDecimal limite) {
    }

    public record Meta(long id, long usuarioId, String descricao, BigDecimal valorAlvo, BigDecimal valorAtual) {
    }
}
