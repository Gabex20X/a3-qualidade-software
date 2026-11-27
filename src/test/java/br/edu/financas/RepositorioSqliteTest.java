package br.edu.financas;

import br.edu.financas.Modelo.Meta;
import br.edu.financas.Modelo.Orcamento;
import br.edu.financas.Modelo.Perfil;
import br.edu.financas.Modelo.Tipo;
import br.edu.financas.Modelo.Transacao;
import br.edu.financas.Modelo.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static br.edu.financas.Fixtures.HOJE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Nivel de integracao: RepositorioSqlite contra um arquivo SQLite real (um banco novo por teste). */
class RepositorioSqliteTest {

    @TempDir
    Path pasta;

    private String url;
    private RepositorioSqlite repo;
    private Usuario dono;

    @BeforeEach
    void abrirBanco() {
        url = "jdbc:sqlite:" + pasta.resolve("teste.db");
        repo = new RepositorioSqlite(url);
        dono = repo.salvarUsuario(new Usuario(0, "Carlos", 40, Perfil.PADRAO));
    }

    @AfterEach
    void fecharBanco() {
        repo.close();
    }

    private Transacao nova(String valor) {
        return new Transacao(0, dono.id(), Tipo.DESPESA, new BigDecimal(valor), "mercado", HOJE, null, false, "x");
    }

    @Test
    void ct24_valoresMonetarios_sobrevivemAoReaberturaDoBanco() {
        // Arrange
        long idMinimo = repo.salvarTransacao(nova("0.01")).id();
        long idMaximo = repo.salvarTransacao(nova("1000000.00")).id();
        repo.close();
        // Act: reabre o mesmo arquivo, como se a aplicacao reiniciasse
        repo = new RepositorioSqlite(url);
        BigDecimal minimo = repo.buscarTransacao(idMinimo).orElseThrow().valor();
        BigDecimal maximo = repo.buscarTransacao(idMaximo).orElseThrow().valor();
        // Assert: mesmo valor e mesma escala (texto), sem passar por double
        assertEquals("0.01", minimo.toPlainString());
        assertEquals("1000000.00", maximo.toPlainString());
    }

    @Test
    void ct10_transacao_insereAtualizaExclui() {
        // Arrange
        Transacao criada = repo.salvarTransacao(nova("50.00"));
        Transacao alterada = new Transacao(criada.id(), dono.id(), Tipo.DESPESA, new BigDecimal("80.00"), "lazer",
                HOJE, HOJE.plusDays(5), true, null);
        // Act
        repo.salvarTransacao(alterada);
        Transacao lida = repo.buscarTransacao(criada.id()).orElseThrow();
        boolean excluiu = repo.excluirTransacao(criada.id());
        boolean excluiuDeNovo = repo.excluirTransacao(criada.id());
        // Assert
        assertTrue(criada.id() > 0);
        assertEquals(alterada, lida);
        assertTrue(excluiu);
        assertFalse(excluiuDeNovo);
        assertTrue(repo.buscarTransacao(criada.id()).isEmpty());
        assertTrue(repo.listarTransacoes(dono.id()).isEmpty());
    }

    @Test
    void ct05_transacaoDeUsuarioInexistente_violaChaveEstrangeira() {
        // Arrange
        Transacao orfa = new Transacao(0, 999, Tipo.DESPESA, BigDecimal.ONE, "a", HOJE, null, false, null);
        // Act + Assert
        assertThrows(IllegalStateException.class, () -> repo.salvarTransacao(orfa));
    }

    @Test
    void ct12_orcamento_mesmaCategoriaESubstituida() {
        // Arrange
        repo.salvarOrcamento(new Orcamento(dono.id(), "saude", new BigDecimal("100.00")));
        // Act
        repo.salvarOrcamento(new Orcamento(dono.id(), "saude", new BigDecimal("200.00")));
        List<Orcamento> orcamentos = repo.listarOrcamentos(dono.id());
        // Assert
        assertEquals(1, orcamentos.size());
        assertEquals(new BigDecimal("200.00"), orcamentos.get(0).limite());
    }

    @Test
    void ct18_vinculos_saoGravadosESemDuplicidade() {
        // Arrange
        Usuario idoso = repo.salvarUsuario(new Usuario(0, "Dona Maria", 60, Perfil.IDOSO));
        // Act
        repo.vincularFamiliar(idoso.id(), dono.id());
        // Assert
        assertEquals(List.of(dono.id()), repo.familiaresDe(idoso.id()));
        assertThrows(IllegalStateException.class, () -> repo.vincularFamiliar(idoso.id(), dono.id()));
    }

    @Test
    void ct21_meta_atualizaValorAtual() {
        // Arrange
        Meta meta = repo.salvarMeta(new Meta(0, dono.id(), "Reserva", new BigDecimal("100.00"), BigDecimal.ZERO));
        // Act
        repo.salvarMeta(new Meta(meta.id(), dono.id(), "Reserva", new BigDecimal("100.00"), new BigDecimal("99.99")));
        // Assert
        assertEquals(new BigDecimal("99.99"), repo.buscarMeta(meta.id()).orElseThrow().valorAtual());
        assertEquals(1, repo.listarMetas(dono.id()).size());
        assertTrue(repo.buscarMeta(999).isEmpty());
    }

    @Test
    void ct01_usuario_ePersistidoComPerfil() {
        // Arrange: usuario criado no BeforeEach
        // Act
        Usuario lido = repo.buscarUsuario(dono.id()).orElseThrow();
        // Assert
        assertEquals(dono, lido);
        assertTrue(repo.buscarUsuario(999).isEmpty());
    }
}
