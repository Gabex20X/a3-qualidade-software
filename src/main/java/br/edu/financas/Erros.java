package br.edu.financas;

/** Erros de negocio, cada um ligado a um status HTTP. */
public final class Erros {

    private Erros() {
    }

    public abstract static class ErroNegocio extends RuntimeException {
        private final int status;

        protected ErroNegocio(int status, String mensagem) {
            super(mensagem);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    /** 400 - dado invalido ou regra de negocio violada. */
    public static final class Validacao extends ErroNegocio {
        public Validacao(String mensagem) {
            super(400, mensagem);
        }
    }

    /** 401 - requisicao sem usuario identificado. */
    public static final class NaoAutenticado extends ErroNegocio {
        public NaoAutenticado(String mensagem) {
            super(401, mensagem);
        }
    }

    /** 403 - usuario identificado, mas sem permissao. */
    public static final class Proibido extends ErroNegocio {
        public Proibido(String mensagem) {
            super(403, mensagem);
        }
    }

    /** 404 - recurso inexistente. */
    public static final class NaoEncontrado extends ErroNegocio {
        public NaoEncontrado(String mensagem) {
            super(404, mensagem);
        }
    }

    /** 409 - conflito com o estado atual (duplicidade, limite atingido). */
    public static final class Conflito extends ErroNegocio {
        public Conflito(String mensagem) {
            super(409, mensagem);
        }
    }
}
