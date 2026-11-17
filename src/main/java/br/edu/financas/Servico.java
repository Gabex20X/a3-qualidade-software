package br.edu.financas;

import br.edu.financas.Modelo.Meta;
import br.edu.financas.Modelo.Orcamento;
import br.edu.financas.Modelo.Perfil;
import br.edu.financas.Modelo.StatusConta;
import br.edu.financas.Modelo.StatusMeta;
import br.edu.financas.Modelo.StatusOrcamento;
import br.edu.financas.Modelo.Tipo;
import br.edu.financas.Modelo.Transacao;
import br.edu.financas.Modelo.Usuario;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

/**
 * Casos de uso. Ordem das verificacoes em cada operacao:
 * autenticacao (401) -> existencia do recurso (404) -> permissao (403) -> dados e regras (400/409).
 */
public final class Servico {

    private final Repositorio repo;
    private final Clock relogio;

    public record DadosTransacao(String tipo, BigDecimal valor, String categoria, LocalDate data,
                                 LocalDate vencimento, Boolean paga, String descricao) {
    }

    public record TransacaoCriada(Transacao transacao, boolean alertaFamiliar) {
    }

    public record ResumoOrcamento(String categoria, BigDecimal limite, BigDecimal gasto, StatusOrcamento status) {
    }

    public record ResumoConta(Transacao transacao, StatusConta status) {
    }

    public record ResumoMeta(Meta meta, int percentual, StatusMeta status) {
    }

    public Servico(Repositorio repo, Clock relogio) {
        this.repo = repo;
        this.relogio = relogio;
    }

    public LocalDate hoje() {
        return LocalDate.now(relogio);
    }

    // ---------- usuarios (RF-01, RN-01) ----------

    public Usuario criarUsuario(String nome, Integer idade) {
        if (nome == null || nome.isBlank()) {
            throw new Erros.Validacao("nome e obrigatorio");
        }
        if (idade == null) {
            throw new Erros.Validacao("idade e obrigatoria");
        }
        Perfil perfil = Regras.perfilPorIdade(idade);
        return repo.salvarUsuario(new Usuario(0, nome.trim(), idade, perfil));
    }

    public Usuario buscarUsuario(long id) {
        return repo.buscarUsuario(id)
                .orElseThrow(() -> new Erros.NaoEncontrado("usuario " + id + " nao encontrado"));
    }

    // ---------- autenticacao e permissoes (RN-05) ----------

    private Usuario autenticar(Long atorId) {
        if (atorId == null) {
            throw new Erros.NaoAutenticado("informe o cabecalho X-Usuario-Id");
        }
        return repo.buscarUsuario(atorId)
                .orElseThrow(() -> new Erros.NaoAutenticado("usuario " + atorId + " nao existe"));
    }

    /** Leitura: o proprio dono ou um familiar vinculado. */
    private void exigirLeitura(Usuario ator, long donoId) {
        if (ator.id() != donoId && !repo.familiaresDe(donoId).contains(ator.id())) {
            throw new Erros.Proibido("sem permissao para ver os dados do usuario " + donoId);
        }
    }

    /** Escrita: so o dono. Familiar vinculado tem acesso somente leitura. */
    private void exigirEscrita(Usuario ator, long donoId) {
        if (ator.id() == donoId) {
            return;
        }
        if (repo.familiaresDe(donoId).contains(ator.id())) {
            throw new Erros.Proibido("familiar tem acesso somente leitura");
        }
        throw new Erros.Proibido("sem permissao para alterar os dados do usuario " + donoId);
    }

    // ---------- transacoes (RF-02, RF-05, RN-04) ----------

    private Transacao validarTransacao(long id, long usuarioId, DadosTransacao d) {
        if (d.tipo() == null) {
            throw new Erros.Validacao("tipo e obrigatorio (RECEITA ou DESPESA)");
        }
        Tipo tipo;
        try {
            tipo = Tipo.valueOf(d.tipo());
        } catch (IllegalArgumentException e) {
            throw new Erros.Validacao("tipo deve ser RECEITA ou DESPESA");
        }
        Regras.validarValor(d.valor());
        if (d.categoria() == null || d.categoria().isBlank()) {
            throw new Erros.Validacao("categoria e obrigatoria");
        }
        LocalDate data = d.data() == null ? hoje() : d.data();
        if (data.isAfter(hoje())) {
            throw new Erros.Validacao("data nao pode ser futura");
        }
        if (d.vencimento() != null && tipo != Tipo.DESPESA) {
            throw new Erros.Validacao("vencimento so se aplica a despesas");
        }
        boolean paga = d.paga() != null && d.paga();
        return new Transacao(id, usuarioId, tipo, d.valor(), d.categoria().trim().toLowerCase(),
                data, d.vencimento(), paga, d.descricao());
    }

    public TransacaoCriada criarTransacao(Long atorId, long usuarioId, DadosTransacao dados) {
        Usuario ator = autenticar(atorId);
        Usuario dono = buscarUsuario(usuarioId);
        exigirEscrita(ator, dono.id());
        Transacao salva = repo.salvarTransacao(validarTransacao(0, dono.id(), dados));
        boolean alerta = Regras.exigeAlertaFamiliar(dono.perfil(), salva.tipo(), salva.valor());
        return new TransacaoCriada(salva, alerta);
    }

    public List<Transacao> listarTransacoes(Long atorId, long usuarioId) {
        Usuario ator = autenticar(atorId);
        buscarUsuario(usuarioId);
        exigirLeitura(ator, usuarioId);
        return repo.listarTransacoes(usuarioId);
    }

    private Transacao transacaoExistente(long id) {
        return repo.buscarTransacao(id)
                .orElseThrow(() -> new Erros.NaoEncontrado("transacao " + id + " nao encontrada"));
    }

    public Transacao buscarTransacao(Long atorId, long id) {
        Usuario ator = autenticar(atorId);
        Transacao t = transacaoExistente(id);
        exigirLeitura(ator, t.usuarioId());
        return t;
    }

    public Transacao atualizarTransacao(Long atorId, long id, DadosTransacao dados) {
        Usuario ator = autenticar(atorId);
        Transacao atual = transacaoExistente(id);
        exigirEscrita(ator, atual.usuarioId());
        return repo.salvarTransacao(validarTransacao(id, atual.usuarioId(), dados));
    }

    public void excluirTransacao(Long atorId, long id) {
        Usuario ator = autenticar(atorId);
        Transacao atual = transacaoExistente(id);
        exigirEscrita(ator, atual.usuarioId());
        repo.excluirTransacao(id);
    }

    // ---------- orcamentos (RF-03, RN-02) ----------

    public Orcamento definirOrcamento(Long atorId, long usuarioId, String categoria, BigDecimal limite) {
        Usuario ator = autenticar(atorId);
        buscarUsuario(usuarioId);
        exigirEscrita(ator, usuarioId);
        if (categoria == null || categoria.isBlank()) {
            throw new Erros.Validacao("categoria e obrigatoria");
        }
        Regras.validarValor(limite);
        Orcamento o = new Orcamento(usuarioId, categoria.trim().toLowerCase(), limite);
        repo.salvarOrcamento(o);
        return o;
    }

    /** Gasto do mes corrente (pelo relogio injetado) por categoria, comparado ao limite. */
    public List<ResumoOrcamento> statusOrcamentos(Long atorId, long usuarioId) {
        Usuario ator = autenticar(atorId);
        buscarUsuario(usuarioId);
        exigirLeitura(ator, usuarioId);
        YearMonth mes = YearMonth.from(hoje());
        List<Transacao> despesasDoMes = repo.listarTransacoes(usuarioId).stream()
                .filter(t -> t.tipo() == Tipo.DESPESA && YearMonth.from(t.data()).equals(mes))
                .toList();
        return repo.listarOrcamentos(usuarioId).stream().map(o -> {
            BigDecimal gasto = despesasDoMes.stream()
                    .filter(t -> t.categoria().equals(o.categoria()))
                    .map(Transacao::valor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new ResumoOrcamento(o.categoria(), o.limite(), gasto, Regras.statusOrcamento(gasto, o.limite()));
        }).toList();
    }

    // ---------- contas (RF-04, RN-03) ----------

    public List<ResumoConta> contas(Long atorId, long usuarioId) {
        Usuario ator = autenticar(atorId);
        buscarUsuario(usuarioId);
        exigirLeitura(ator, usuarioId);
        LocalDate hoje = hoje();
        return repo.listarTransacoes(usuarioId).stream()
                .filter(t -> t.vencimento() != null)
                .sorted(Comparator.comparing(Transacao::vencimento))
                .map(t -> new ResumoConta(t, Regras.statusConta(t.vencimento(), t.paga(), hoje)))
                .toList();
    }

    // ---------- familiares (RF-06, RN-05) ----------

    public void vincularFamiliar(Long atorId, long idosoId, Long familiarId) {
        Usuario ator = autenticar(atorId);
        Usuario idoso = buscarUsuario(idosoId);
        exigirEscrita(ator, idoso.id());
        if (idoso.perfil() != Perfil.IDOSO) {
            throw new Erros.Validacao("apenas usuarios idosos podem vincular familiares");
        }
        if (familiarId == null) {
            throw new Erros.Validacao("familiarId e obrigatorio");
        }
        if (familiarId == idosoId) {
            throw new Erros.Validacao("usuario nao pode ser familiar de si mesmo");
        }
        buscarUsuario(familiarId);
        List<Long> atuais = repo.familiaresDe(idosoId);
        if (atuais.contains(familiarId)) {
            throw new Erros.Conflito("familiar ja vinculado");
        }
        if (!Regras.podeAdicionarFamiliar(atuais.size())) {
            throw new Erros.Conflito("limite de " + Regras.MAX_FAMILIARES + " familiares atingido");
        }
        repo.vincularFamiliar(idosoId, familiarId);
    }

    // ---------- metas (RF-07, RN-06) ----------

    private ResumoMeta resumo(Meta m) {
        return new ResumoMeta(m, Regras.percentualMeta(m.valorAtual(), m.valorAlvo()),
                Regras.statusMeta(m.valorAtual(), m.valorAlvo()));
    }

    public ResumoMeta criarMeta(Long atorId, long usuarioId, String descricao, BigDecimal valorAlvo) {
        Usuario ator = autenticar(atorId);
        buscarUsuario(usuarioId);
        exigirEscrita(ator, usuarioId);
        if (descricao == null || descricao.isBlank()) {
            throw new Erros.Validacao("descricao e obrigatoria");
        }
        Regras.validarValor(valorAlvo);
        return resumo(repo.salvarMeta(new Meta(0, usuarioId, descricao.trim(), valorAlvo, BigDecimal.ZERO)));
    }

    public ResumoMeta aportar(Long atorId, long metaId, BigDecimal valor) {
        Usuario ator = autenticar(atorId);
        Meta meta = repo.buscarMeta(metaId)
                .orElseThrow(() -> new Erros.NaoEncontrado("meta " + metaId + " nao encontrada"));
        exigirEscrita(ator, meta.usuarioId());
        Regras.validarValor(valor);
        Meta atualizada = new Meta(meta.id(), meta.usuarioId(), meta.descricao(), meta.valorAlvo(),
                meta.valorAtual().add(valor));
        return resumo(repo.salvarMeta(atualizada));
    }

    public List<ResumoMeta> listarMetas(Long atorId, long usuarioId) {
        Usuario ator = autenticar(atorId);
        buscarUsuario(usuarioId);
        exigirLeitura(ator, usuarioId);
        return repo.listarMetas(usuarioId).stream().map(this::resumo).toList();
    }
}
