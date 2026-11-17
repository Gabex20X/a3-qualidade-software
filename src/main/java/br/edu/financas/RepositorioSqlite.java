package br.edu.financas;

import br.edu.financas.Modelo.Meta;
import br.edu.financas.Modelo.Orcamento;
import br.edu.financas.Modelo.Perfil;
import br.edu.financas.Modelo.Tipo;
import br.edu.financas.Modelo.Transacao;
import br.edu.financas.Modelo.Usuario;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Repositorio em SQLite via JDBC. Valores monetarios e datas sao gravados como texto (sem perda de precisao). */
public final class RepositorioSqlite implements Repositorio, AutoCloseable {

    private final Connection conn;

    @FunctionalInterface
    private interface Sql<T> {
        T executar() throws SQLException;
    }

    public RepositorioSqlite(String url) {
        try {
            this.conn = DriverManager.getConnection(url);
            criarTabelas();
        } catch (SQLException e) {
            throw new IllegalStateException("falha ao abrir o banco " + url, e);
        }
    }

    private void criarTabelas() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS usuarios (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      nome TEXT NOT NULL, idade INTEGER NOT NULL, perfil TEXT NOT NULL)""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS transacoes (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
                      tipo TEXT NOT NULL, valor TEXT NOT NULL, categoria TEXT NOT NULL,
                      data TEXT NOT NULL, vencimento TEXT, paga INTEGER NOT NULL, descricao TEXT)""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS orcamentos (
                      usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
                      categoria TEXT NOT NULL, limite TEXT NOT NULL,
                      PRIMARY KEY (usuario_id, categoria))""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS vinculos (
                      idoso_id INTEGER NOT NULL REFERENCES usuarios(id),
                      familiar_id INTEGER NOT NULL REFERENCES usuarios(id),
                      PRIMARY KEY (idoso_id, familiar_id))""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS metas (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
                      descricao TEXT NOT NULL, valor_alvo TEXT NOT NULL, valor_atual TEXT NOT NULL)""");
        }
    }

    private synchronized <T> T sql(Sql<T> acao) {
        try {
            return acao.executar();
        } catch (SQLException e) {
            throw new IllegalStateException("erro de banco: " + e.getMessage(), e);
        }
    }

    private long ultimoId() throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static String data(LocalDate d) {
        return d == null ? null : d.toString();
    }

    private static LocalDate data(String s) {
        return s == null ? null : LocalDate.parse(s);
    }

    // ---------- usuarios ----------

    @Override
    public Usuario salvarUsuario(Usuario u) {
        return sql(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO usuarios (nome, idade, perfil) VALUES (?, ?, ?)")) {
                ps.setString(1, u.nome());
                ps.setInt(2, u.idade());
                ps.setString(3, u.perfil().name());
                ps.executeUpdate();
            }
            return new Usuario(ultimoId(), u.nome(), u.idade(), u.perfil());
        });
    }

    @Override
    public Optional<Usuario> buscarUsuario(long id) {
        return sql(() -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM usuarios WHERE id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new Usuario(rs.getLong("id"), rs.getString("nome"),
                            rs.getInt("idade"), Perfil.valueOf(rs.getString("perfil"))));
                }
            }
        });
    }

    // ---------- transacoes ----------

    @Override
    public Transacao salvarTransacao(Transacao t) {
        return sql(() -> {
            boolean novo = t.id() == 0;
            String comando = novo
                    ? "INSERT INTO transacoes (usuario_id, tipo, valor, categoria, data, vencimento, paga, descricao)"
                      + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                    : "UPDATE transacoes SET usuario_id = ?, tipo = ?, valor = ?, categoria = ?, data = ?,"
                      + " vencimento = ?, paga = ?, descricao = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(comando)) {
                ps.setLong(1, t.usuarioId());
                ps.setString(2, t.tipo().name());
                ps.setString(3, t.valor().toPlainString());
                ps.setString(4, t.categoria());
                ps.setString(5, data(t.data()));
                ps.setString(6, data(t.vencimento()));
                ps.setInt(7, t.paga() ? 1 : 0);
                ps.setString(8, t.descricao());
                if (!novo) {
                    ps.setLong(9, t.id());
                }
                ps.executeUpdate();
            }
            long id = novo ? ultimoId() : t.id();
            return new Transacao(id, t.usuarioId(), t.tipo(), t.valor(), t.categoria(),
                    t.data(), t.vencimento(), t.paga(), t.descricao());
        });
    }

    private static Transacao transacao(ResultSet rs) throws SQLException {
        return new Transacao(rs.getLong("id"), rs.getLong("usuario_id"), Tipo.valueOf(rs.getString("tipo")),
                new BigDecimal(rs.getString("valor")), rs.getString("categoria"), data(rs.getString("data")),
                data(rs.getString("vencimento")), rs.getInt("paga") == 1, rs.getString("descricao"));
    }

    @Override
    public Optional<Transacao> buscarTransacao(long id) {
        return sql(() -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM transacoes WHERE id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(transacao(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public List<Transacao> listarTransacoes(long usuarioId) {
        return sql(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM transacoes WHERE usuario_id = ? ORDER BY data, id")) {
                ps.setLong(1, usuarioId);
                List<Transacao> lista = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lista.add(transacao(rs));
                    }
                }
                return lista;
            }
        });
    }

    @Override
    public boolean excluirTransacao(long id) {
        return sql(() -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM transacoes WHERE id = ?")) {
                ps.setLong(1, id);
                return ps.executeUpdate() > 0;
            }
        });
    }

    // ---------- orcamentos ----------

    @Override
    public void salvarOrcamento(Orcamento o) {
        sql(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO orcamentos (usuario_id, categoria, limite) VALUES (?, ?, ?)")) {
                ps.setLong(1, o.usuarioId());
                ps.setString(2, o.categoria());
                ps.setString(3, o.limite().toPlainString());
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public List<Orcamento> listarOrcamentos(long usuarioId) {
        return sql(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM orcamentos WHERE usuario_id = ? ORDER BY categoria")) {
                ps.setLong(1, usuarioId);
                List<Orcamento> lista = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lista.add(new Orcamento(rs.getLong("usuario_id"), rs.getString("categoria"),
                                new BigDecimal(rs.getString("limite"))));
                    }
                }
                return lista;
            }
        });
    }

    // ---------- vinculos familiares ----------

    @Override
    public void vincularFamiliar(long idosoId, long familiarId) {
        sql(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO vinculos (idoso_id, familiar_id) VALUES (?, ?)")) {
                ps.setLong(1, idosoId);
                ps.setLong(2, familiarId);
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public List<Long> familiaresDe(long idosoId) {
        return sql(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT familiar_id FROM vinculos WHERE idoso_id = ? ORDER BY familiar_id")) {
                ps.setLong(1, idosoId);
                List<Long> lista = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lista.add(rs.getLong(1));
                    }
                }
                return lista;
            }
        });
    }

    // ---------- metas ----------

    @Override
    public Meta salvarMeta(Meta m) {
        return sql(() -> {
            boolean novo = m.id() == 0;
            String comando = novo
                    ? "INSERT INTO metas (usuario_id, descricao, valor_alvo, valor_atual) VALUES (?, ?, ?, ?)"
                    : "UPDATE metas SET usuario_id = ?, descricao = ?, valor_alvo = ?, valor_atual = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(comando)) {
                ps.setLong(1, m.usuarioId());
                ps.setString(2, m.descricao());
                ps.setString(3, m.valorAlvo().toPlainString());
                ps.setString(4, m.valorAtual().toPlainString());
                if (!novo) {
                    ps.setLong(5, m.id());
                }
                ps.executeUpdate();
            }
            long id = novo ? ultimoId() : m.id();
            return new Meta(id, m.usuarioId(), m.descricao(), m.valorAlvo(), m.valorAtual());
        });
    }

    private static Meta meta(ResultSet rs) throws SQLException {
        return new Meta(rs.getLong("id"), rs.getLong("usuario_id"), rs.getString("descricao"),
                new BigDecimal(rs.getString("valor_alvo")), new BigDecimal(rs.getString("valor_atual")));
    }

    @Override
    public Optional<Meta> buscarMeta(long id) {
        return sql(() -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM metas WHERE id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(meta(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public List<Meta> listarMetas(long usuarioId) {
        return sql(() -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM metas WHERE usuario_id = ? ORDER BY id")) {
                ps.setLong(1, usuarioId);
                List<Meta> lista = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lista.add(meta(rs));
                    }
                }
                return lista;
            }
        });
    }

    @Override
    public void close() {
        sql(() -> {
            conn.close();
            return null;
        });
    }
}
