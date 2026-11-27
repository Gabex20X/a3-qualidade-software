package br.edu.financas;

import br.edu.financas.Modelo.StatusConta;
import br.edu.financas.Modelo.StatusOrcamento;
import br.edu.financas.Modelo.Usuario;
import br.edu.financas.Servico.DadosTransacao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static br.edu.financas.Fixtures.HOJE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Nivel de integracao: Servico + RepositorioSqlite real, relogio fixo em 03/12/2026. */
class ServicoSqliteTest {

    @TempDir
    Path pasta;

    private RepositorioSqlite repo;
    private Servico servico;

    @BeforeEach
    void abrir() {
        repo = new RepositorioSqlite("jdbc:sqlite:" + pasta.resolve("servico.db"));
        servico = new Servico(repo, Fixtures.RELOGIO);
    }

    @AfterEach
    void fechar() {
        repo.close();
    }

    private Usuario usuario(String nome, int idade) {
        return servico.criarUsuario(nome, idade);
    }

    private void despesa(Usuario u, String valor, String categoria, LocalDate vencimento) {
        servico.criarTransacao(u.id(), u.id(),
                new DadosTransacao("DESPESA", new BigDecimal(valor), categoria, null, vencimento, null, null));
    }

    @ParameterizedTest(name = "gasto {0} de 100.00 -> {1}")
    @CsvSource({"79.99,OK", "80.00,ATENCAO", "100.00,ATENCAO", "100.01,ESTOURADO"})
    void ct11_statusOrcamento_nosLimites_comBancoReal(String gasto, StatusOrcamento esperado) {
        // Arrange
        Usuario u = usuario("Carlos", 40);
        servico.definirOrcamento(u.id(), u.id(), "Saude", new BigDecimal("100.00"));
        despesa(u, gasto, "saude", null);
        // Act
        List<Servico.ResumoOrcamento> resumo = servico.statusOrcamentos(u.id(), u.id());
        // Assert
        assertEquals(1, resumo.size());
        assertEquals(esperado, resumo.get(0).status());
        assertEquals(0, new BigDecimal(gasto).compareTo(resumo.get(0).gasto()));
    }

    @Test
    void ct24_somaDeCentavos_eExata() {
        // Arrange
        Usuario u = usuario("Carlos", 40);
        servico.definirOrcamento(u.id(), u.id(), "cafe", new BigDecimal("1.00"));
        despesa(u, "0.10", "cafe", null);
        despesa(u, "0.20", "cafe", null);
        // Act
        BigDecimal gasto = servico.statusOrcamentos(u.id(), u.id()).get(0).gasto();
        // Assert: com double daria 0.30000000000000004
        assertEquals("0.30", gasto.toPlainString());
    }

    @Test
    void ct13_contas_nosLimites_comBancoReal() {
        // Arrange
        Usuario u = usuario("Carlos", 40);
        despesa(u, "1.00", "luz", HOJE.plusDays(4));
        despesa(u, "1.00", "agua", HOJE.plusDays(3));
        despesa(u, "1.00", "gas", HOJE);
        despesa(u, "1.00", "internet", HOJE.minusDays(1));
        // Act
        List<Servico.ResumoConta> contas = servico.contas(u.id(), u.id());
        // Assert
        assertEquals(List.of(StatusConta.ATRASADA, StatusConta.A_VENCER, StatusConta.A_VENCER, StatusConta.EM_DIA),
                contas.stream().map(Servico.ResumoConta::status).toList());
    }

    @Test
    void ct17_familiarLeMasNaoEscreve_comBancoReal() {
        // Arrange
        Usuario idoso = usuario("Dona Maria", 60);
        Usuario familiar = usuario("Familiar", 35);
        Usuario terceiro = usuario("Terceiro", 35);
        servico.vincularFamiliar(idoso.id(), idoso.id(), familiar.id());
        despesa(idoso, "10.00", "mercado", null);
        DadosTransacao dados = new DadosTransacao("DESPESA", BigDecimal.TEN, "x", null, null, null, null);
        // Act
        int lidasPeloFamiliar = servico.listarTransacoes(familiar.id(), idoso.id()).size();
        // Assert
        assertEquals(1, lidasPeloFamiliar);
        assertThrows(Erros.Proibido.class, () -> servico.criarTransacao(familiar.id(), idoso.id(), dados));
        assertThrows(Erros.Proibido.class, () -> servico.listarTransacoes(terceiro.id(), idoso.id()));
        assertEquals(1, servico.listarTransacoes(idoso.id(), idoso.id()).size());
    }

    @Test
    void ct18_limiteDeFamiliares_comBancoReal() {
        // Arrange
        Usuario idoso = usuario("Dona Maria", 60);
        Usuario f1 = usuario("F1", 35);
        Usuario f2 = usuario("F2", 35);
        Usuario f3 = usuario("F3", 35);
        // Act
        servico.vincularFamiliar(idoso.id(), idoso.id(), f1.id());
        servico.vincularFamiliar(idoso.id(), idoso.id(), f2.id());
        // Assert
        assertThrows(Erros.Conflito.class, () -> servico.vincularFamiliar(idoso.id(), idoso.id(), f3.id()));
        assertEquals(List.of(f1.id(), f2.id()), repo.familiaresDe(idoso.id()));
        assertThrows(Erros.Proibido.class, () -> servico.listarTransacoes(f3.id(), idoso.id()));
    }
}
