package br.edu.financas;

import br.edu.financas.Modelo.Meta;
import br.edu.financas.Modelo.Orcamento;
import br.edu.financas.Modelo.Transacao;
import br.edu.financas.Modelo.Usuario;

import java.util.List;
import java.util.Optional;

/** Persistencia. Interface para permitir dubles (Mockito) nos testes unitarios. */
public interface Repositorio {

    Usuario salvarUsuario(Usuario usuario);

    Optional<Usuario> buscarUsuario(long id);

    /** Insere quando id == 0; atualiza caso contrario. */
    Transacao salvarTransacao(Transacao transacao);

    Optional<Transacao> buscarTransacao(long id);

    List<Transacao> listarTransacoes(long usuarioId);

    boolean excluirTransacao(long id);

    /** Insere ou substitui o orcamento da categoria. */
    void salvarOrcamento(Orcamento orcamento);

    List<Orcamento> listarOrcamentos(long usuarioId);

    void vincularFamiliar(long idosoId, long familiarId);

    List<Long> familiaresDe(long idosoId);

    /** Insere quando id == 0; atualiza caso contrario. */
    Meta salvarMeta(Meta meta);

    Optional<Meta> buscarMeta(long id);

    List<Meta> listarMetas(long usuarioId);
}
