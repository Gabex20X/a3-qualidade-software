package br.edu.financas;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Ponto de entrada. Configuracao por variaveis de ambiente:
 * PORT (padrao 8080), DB_URL (padrao jdbc:sqlite:dados/financas.db) e
 * RELOGIO_FIXO (AAAA-MM-DD, opcional: congela a data para demonstracoes e testes de API).
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) throws IOException {
        String url = env("DB_URL", "jdbc:sqlite:dados/financas.db");
        criarPastaDoBanco(url);
        int porta = Integer.parseInt(env("PORT", "8080"));
        Clock relogio = relogio(System.getenv("RELOGIO_FIXO"));

        Servico servico = new Servico(new RepositorioSqlite(url), relogio);
        Api api = new Api(servico, porta);
        api.iniciar();
        System.out.println("API ouvindo na porta " + api.porta() + " (hoje = " + servico.hoje() + ")");
    }

    static Clock relogio(String fixo) {
        if (fixo == null || fixo.isBlank()) {
            return Clock.systemDefaultZone();
        }
        return Clock.fixed(LocalDate.parse(fixo.trim()).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    }

    private static void criarPastaDoBanco(String url) throws IOException {
        String prefixo = "jdbc:sqlite:";
        if (url.startsWith(prefixo) && !url.contains(":memory:")) {
            Path pasta = Path.of(url.substring(prefixo.length())).toAbsolutePath().getParent();
            if (pasta != null) {
                Files.createDirectories(pasta);
            }
        }
    }

    private static String env(String nome, String padrao) {
        String valor = System.getenv(nome);
        return valor == null || valor.isBlank() ? padrao : valor;
    }
}
