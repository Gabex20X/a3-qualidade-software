package br.edu.financas;

import br.edu.financas.Modelo.Meta;
import br.edu.financas.Modelo.Transacao;
import br.edu.financas.Modelo.Usuario;
import br.edu.financas.Servico.DadosTransacao;
import br.edu.financas.Servico.ResumoConta;
import br.edu.financas.Servico.ResumoMeta;
import br.edu.financas.Servico.ResumoOrcamento;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** API HTTP/JSON sobre o HttpServer do JDK. O usuario e identificado pelo cabecalho X-Usuario-Id. */
public final class Api {

    private static final String ID = "(\\d{1,18})";

    private final Servico servico;
    private final HttpServer server;
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final List<Rota> rotas = new ArrayList<>();

    @FunctionalInterface
    private interface Acao {
        Resposta executar(Matcher m, Requisicao req);
    }

    private record Rota(String metodo, Pattern padrao, Acao acao) {
    }

    private record Requisicao(Long atorId, JsonObject corpo) {
    }

    private record Resposta(int status, Object corpo) {
    }

    public Api(Servico servico, int porta) throws IOException {
        this.servico = servico;
        this.server = HttpServer.create(new InetSocketAddress(porta), 0);
        registrarRotas();
        server.createContext("/", this::tratar);
    }

    public void iniciar() {
        server.start();
    }

    public void parar() {
        server.stop(0);
    }

    public int porta() {
        return server.getAddress().getPort();
    }

    // ---------- rotas ----------

    private void rota(String metodo, String padrao, Acao acao) {
        rotas.add(new Rota(metodo, Pattern.compile(padrao), acao));
    }

    private static long id(Matcher m) {
        return Long.parseLong(m.group(1));
    }

    private void registrarRotas() {
        rota("GET", "/health", (m, r) -> new Resposta(200, Map.of("status", "ok")));

        // RF-01 usuarios
        rota("POST", "/usuarios", (m, r) -> new Resposta(201,
                json(servico.criarUsuario(texto(r.corpo(), "nome"), inteiro(r.corpo(), "idade")))));
        rota("GET", "/usuarios/" + ID, (m, r) -> new Resposta(200, json(servico.buscarUsuario(id(m)))));

        // RF-02 transacoes (CRUD) e RF-05 alerta
        rota("POST", "/usuarios/" + ID + "/transacoes", (m, r) -> {
            Servico.TransacaoCriada criada = servico.criarTransacao(r.atorId(), id(m), dadosTransacao(r.corpo()));
            Map<String, Object> corpo = json(criada.transacao());
            corpo.put("alertaFamiliar", criada.alertaFamiliar());
            return new Resposta(201, corpo);
        });
        rota("GET", "/usuarios/" + ID + "/transacoes", (m, r) -> new Resposta(200,
                servico.listarTransacoes(r.atorId(), id(m)).stream().map(Api::json).toList()));
        rota("GET", "/transacoes/" + ID, (m, r) -> new Resposta(200,
                json(servico.buscarTransacao(r.atorId(), id(m)))));
        rota("PUT", "/transacoes/" + ID, (m, r) -> new Resposta(200,
                json(servico.atualizarTransacao(r.atorId(), id(m), dadosTransacao(r.corpo())))));
        rota("DELETE", "/transacoes/" + ID, (m, r) -> {
            servico.excluirTransacao(r.atorId(), id(m));
            return new Resposta(204, null);
        });

        // RF-03 orcamentos
        rota("PUT", "/usuarios/" + ID + "/orcamentos", (m, r) -> {
            var o = servico.definirOrcamento(r.atorId(), id(m), texto(r.corpo(), "categoria"),
                    decimal(r.corpo(), "limite"));
            return new Resposta(200, Map.of("categoria", o.categoria(), "limite", o.limite()));
        });
        rota("GET", "/usuarios/" + ID + "/orcamentos", (m, r) -> new Resposta(200,
                servico.statusOrcamentos(r.atorId(), id(m)).stream().map(Api::json).toList()));

        // RF-04 contas
        rota("GET", "/usuarios/" + ID + "/contas", (m, r) -> new Resposta(200,
                servico.contas(r.atorId(), id(m)).stream().map(Api::json).toList()));

        // RF-06 familiares
        rota("POST", "/usuarios/" + ID + "/familiares", (m, r) -> {
            Long familiarId = inteiroLongo(r.corpo(), "familiarId");
            servico.vincularFamiliar(r.atorId(), id(m), familiarId);
            return new Resposta(201, Map.of("idosoId", id(m), "familiarId", familiarId));
        });

        // RF-07 metas
        rota("POST", "/usuarios/" + ID + "/metas", (m, r) -> new Resposta(201, json(servico.criarMeta(
                r.atorId(), id(m), texto(r.corpo(), "descricao"), decimal(r.corpo(), "valorAlvo")))));
        rota("GET", "/usuarios/" + ID + "/metas", (m, r) -> new Resposta(200,
                servico.listarMetas(r.atorId(), id(m)).stream().map(Api::json).toList()));
        rota("POST", "/metas/" + ID + "/aportes", (m, r) -> new Resposta(200,
                json(servico.aportar(r.atorId(), id(m), decimal(r.corpo(), "valor")))));
    }

    // ---------- despacho ----------

    private void tratar(HttpExchange ex) throws IOException {
        Resposta resposta;
        try {
            resposta = despachar(ex);
        } catch (Erros.ErroNegocio e) {
            resposta = erro(e.status(), e.getMessage());
        } catch (RuntimeException e) {
            resposta = erro(500, "erro interno");
        }
        enviar(ex, resposta);
    }

    private Resposta despachar(HttpExchange ex) throws IOException {
        String caminho = ex.getRequestURI().getPath();
        String metodo = ex.getRequestMethod();
        boolean caminhoExiste = false;
        for (Rota rota : rotas) {
            Matcher m = rota.padrao().matcher(caminho);
            if (m.matches()) {
                caminhoExiste = true;
                if (rota.metodo().equals(metodo)) {
                    return rota.acao().executar(m, lerRequisicao(ex));
                }
            }
        }
        return caminhoExiste ? erro(405, "metodo nao permitido") : erro(404, "rota nao encontrada");
    }

    private static Requisicao lerRequisicao(HttpExchange ex) throws IOException {
        Long atorId = null;
        String cabecalho = ex.getRequestHeaders().getFirst("X-Usuario-Id");
        if (cabecalho != null) {
            try {
                atorId = Long.parseLong(cabecalho.trim());
            } catch (NumberFormatException e) {
                throw new Erros.NaoAutenticado("cabecalho X-Usuario-Id invalido");
            }
        }
        String texto = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (texto.isBlank()) {
            return new Requisicao(atorId, new JsonObject());
        }
        try {
            JsonElement elemento = JsonParser.parseString(texto);
            if (!elemento.isJsonObject()) {
                throw new Erros.Validacao("corpo deve ser um objeto JSON");
            }
            return new Requisicao(atorId, elemento.getAsJsonObject());
        } catch (JsonParseException e) {
            throw new Erros.Validacao("JSON invalido");
        }
    }

    private static Resposta erro(int status, String mensagem) {
        return new Resposta(status, Map.of("erro", mensagem));
    }

    private void enviar(HttpExchange ex, Resposta resposta) throws IOException {
        try (ex) {
            if (resposta.corpo() == null) {
                ex.sendResponseHeaders(resposta.status(), -1);
                return;
            }
            byte[] bytes = gson.toJson(resposta.corpo()).getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(resposta.status(), bytes.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    // ---------- leitura de campos do JSON ----------

    private static JsonElement campo(JsonObject o, String nome) {
        JsonElement e = o.get(nome);
        return e == null || e.isJsonNull() ? null : e;
    }

    private static String texto(JsonObject o, String nome) {
        JsonElement e = campo(o, nome);
        if (e == null) {
            return null;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) {
            throw new Erros.Validacao(nome + " deve ser texto");
        }
        return e.getAsString();
    }

    private static BigDecimal decimal(JsonObject o, String nome) {
        JsonElement e = campo(o, nome);
        if (e == null) {
            return null;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            throw new Erros.Validacao(nome + " deve ser numerico");
        }
        return e.getAsBigDecimal();
    }

    private static Long inteiroLongo(JsonObject o, String nome) {
        BigDecimal d = decimal(o, nome);
        if (d == null) {
            return null;
        }
        try {
            return d.longValueExact();
        } catch (ArithmeticException e) {
            throw new Erros.Validacao(nome + " deve ser inteiro");
        }
    }

    private static Integer inteiro(JsonObject o, String nome) {
        Long l = inteiroLongo(o, nome);
        if (l == null) {
            return null;
        }
        if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
            throw new Erros.Validacao(nome + " fora do intervalo");
        }
        return l.intValue();
    }

    private static LocalDate dataCampo(JsonObject o, String nome) {
        String s = texto(o, nome);
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new Erros.Validacao(nome + " deve estar no formato AAAA-MM-DD");
        }
    }

    private static Boolean booleano(JsonObject o, String nome) {
        JsonElement e = campo(o, nome);
        if (e == null) {
            return null;
        }
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) {
            throw new Erros.Validacao(nome + " deve ser true ou false");
        }
        return e.getAsBoolean();
    }

    private static DadosTransacao dadosTransacao(JsonObject o) {
        return new DadosTransacao(texto(o, "tipo"), decimal(o, "valor"), texto(o, "categoria"),
                dataCampo(o, "data"), dataCampo(o, "vencimento"), booleano(o, "paga"), texto(o, "descricao"));
    }

    // ---------- conversao para JSON ----------

    private static String data(LocalDate d) {
        return d == null ? null : d.toString();
    }

    private static Map<String, Object> json(Usuario u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.id());
        m.put("nome", u.nome());
        m.put("idade", u.idade());
        m.put("perfil", u.perfil().name());
        return m;
    }

    private static Map<String, Object> json(Transacao t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.id());
        m.put("usuarioId", t.usuarioId());
        m.put("tipo", t.tipo().name());
        m.put("valor", t.valor());
        m.put("categoria", t.categoria());
        m.put("data", data(t.data()));
        m.put("vencimento", data(t.vencimento()));
        m.put("paga", t.paga());
        m.put("descricao", t.descricao());
        return m;
    }

    private static Map<String, Object> json(ResumoOrcamento r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("categoria", r.categoria());
        m.put("limite", r.limite());
        m.put("gasto", r.gasto());
        m.put("status", r.status().name());
        return m;
    }

    private static Map<String, Object> json(ResumoConta r) {
        Map<String, Object> m = json(r.transacao());
        m.put("status", r.status().name());
        return m;
    }

    private static Map<String, Object> json(ResumoMeta r) {
        Meta meta = r.meta();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", meta.id());
        m.put("usuarioId", meta.usuarioId());
        m.put("descricao", meta.descricao());
        m.put("valorAlvo", meta.valorAlvo());
        m.put("valorAtual", meta.valorAtual());
        m.put("percentual", r.percentual());
        m.put("status", r.status().name());
        return m;
    }
}
