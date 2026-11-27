package br.edu.financas;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nivel de integracao: Api + Servico + SQLite real, via HTTP de verdade (HttpServer do JDK
 * numa porta livre, HttpClient do JDK). Relogio fixo em 03/12/2026.
 */
class ApiTest {

    @TempDir
    Path pasta;

    private RepositorioSqlite repo;
    private Api api;
    private String base;
    private final HttpClient http = HttpClient.newHttpClient();

    private record Resp(int status, String corpo) {
        JsonObject json() {
            return JsonParser.parseString(corpo).getAsJsonObject();
        }

        JsonArray lista() {
            return JsonParser.parseString(corpo).getAsJsonArray();
        }
    }

    @BeforeEach
    void subir() throws IOException {
        repo = new RepositorioSqlite("jdbc:sqlite:" + pasta.resolve("api.db"));
        api = new Api(new Servico(repo, Fixtures.RELOGIO), 0);
        api.iniciar();
        base = "http://localhost:" + api.porta();
    }

    @AfterEach
    void derrubar() {
        api.parar();
        repo.close();
    }

    // ---------- auxiliares HTTP ----------

    private Resp chamar(String metodo, String caminho, Long ator, String corpo) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(base + caminho))
                .method(metodo, corpo == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(corpo));
        if (ator != null) {
            req.header("X-Usuario-Id", String.valueOf(ator));
        }
        HttpResponse<String> r = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(r.statusCode(), r.body());
    }

    private long criarUsuario(String nome, int idade) throws Exception {
        Resp r = chamar("POST", "/usuarios", null, "{\"nome\":\"" + nome + "\",\"idade\":" + idade + "}");
        assertEquals(201, r.status(), r.corpo());
        return r.json().get("id").getAsLong();
    }

    private Resp criarDespesa(long u, String valor, String extra) throws Exception {
        return chamar("POST", "/usuarios/" + u + "/transacoes", u,
                "{\"tipo\":\"DESPESA\",\"valor\":" + valor + ",\"categoria\":\"saude\"" + extra + "}");
    }

    // ---------- casos ----------

    @Test
    void ct01_cadastroNoLimiteDe60Anos() throws Exception {
        // Arrange: API vazia
        // Act
        Resp r59 = chamar("POST", "/usuarios", null, "{\"nome\":\"A\",\"idade\":59}");
        Resp r60 = chamar("POST", "/usuarios", null, "{\"nome\":\"B\",\"idade\":60}");
        Resp lido = chamar("GET", "/usuarios/" + r60.json().get("id").getAsLong(), null, null);
        // Assert
        assertEquals(201, r59.status());
        assertEquals("PADRAO", r59.json().get("perfil").getAsString());
        assertEquals(201, r60.status());
        assertEquals("IDOSO", r60.json().get("perfil").getAsString());
        assertEquals(200, lido.status());
        assertEquals("IDOSO", lido.json().get("perfil").getAsString());
    }

    @Test
    void ct03_ct04_cadastroInvalido_eRecusadoCom400() throws Exception {
        // Arrange
        List<String> corpos = List.of("{\"nome\":\"X\",\"idade\":131}", "{\"nome\":\"X\",\"idade\":-1}",
                "{\"idade\":30}", "{\"nome\":\"X\",\"idade\":\"sessenta\"}", "{\"nome\":\"X\",\"idade\":60.5}");
        // Act + Assert
        for (String corpo : corpos) {
            Resp r = chamar("POST", "/usuarios", null, corpo);
            assertEquals(400, r.status(), corpo);
            assertTrue(r.json().has("erro"), corpo);
        }
        assertEquals(404, chamar("GET", "/usuarios/999999", null, null).status());
    }

    @Test
    void ct05_ct10_cicloDeVidaDaTransacao() throws Exception {
        // Arrange
        long u = criarUsuario("Carlos", 40);
        // Act + Assert: criada
        Resp criada = chamar("POST", "/usuarios/" + u + "/transacoes", u,
                "{\"tipo\":\"DESPESA\",\"valor\":120.50,\"categoria\":\"Mercado\",\"descricao\":\"compras\"}");
        assertEquals(201, criada.status(), criada.corpo());
        JsonObject t = criada.json();
        long id = t.get("id").getAsLong();
        assertEquals("120.50", t.get("valor").getAsBigDecimal().toPlainString());
        assertEquals("mercado", t.get("categoria").getAsString());
        assertEquals("2026-12-03", t.get("data").getAsString());
        assertFalse(t.get("paga").getAsBoolean());
        assertFalse(t.get("alertaFamiliar").getAsBoolean());
        assertEquals(1, chamar("GET", "/usuarios/" + u + "/transacoes", u, null).lista().size());
        // alterada
        Resp alterada = chamar("PUT", "/transacoes/" + id, u,
                "{\"tipo\":\"DESPESA\",\"valor\":80.00,\"categoria\":\"lazer\"}");
        assertEquals(200, alterada.status());
        assertEquals("lazer", chamar("GET", "/transacoes/" + id, u, null).json().get("categoria").getAsString());
        // excluida
        Resp excluida = chamar("DELETE", "/transacoes/" + id, u, null);
        assertEquals(204, excluida.status());
        assertEquals("", excluida.corpo());
        // nao volta a nenhum estado
        assertEquals(404, chamar("GET", "/transacoes/" + id, u, null).status());
        assertEquals(404, chamar("DELETE", "/transacoes/" + id, u, null).status());
        assertEquals(404, chamar("PUT", "/transacoes/" + id, u,
                "{\"tipo\":\"DESPESA\",\"valor\":1,\"categoria\":\"x\"}").status());
    }

    @Test
    void ct06_ct08_limitesDeValorEData_viaApi() throws Exception {
        // Arrange
        long u = criarUsuario("Carlos", 40);
        // Act + Assert
        assertAll(
                () -> assertEquals(400, criarDespesa(u, "0.00", "").status()),
                () -> assertEquals(201, criarDespesa(u, "0.01", "").status()),
                () -> assertEquals(201, criarDespesa(u, "1000000.00", "").status()),
                () -> assertEquals(400, criarDespesa(u, "1000000.01", "").status()),
                () -> assertEquals(201, criarDespesa(u, "10", ",\"data\":\"2026-12-03\"").status()),
                () -> assertEquals(400, criarDespesa(u, "10", ",\"data\":\"2026-12-04\"").status()),
                () -> assertEquals(400, criarDespesa(u, "10", ",\"data\":\"03/12/2026\"").status()));
    }

    @Test
    void ct11_orcamentoEmAtencao_viaApi() throws Exception {
        // Arrange
        long u = criarUsuario("Carlos", 40);
        assertEquals(200, chamar("PUT", "/usuarios/" + u + "/orcamentos", u,
                "{\"categoria\":\"saude\",\"limite\":100.00}").status());
        criarDespesa(u, "80.00", "");
        // Act
        JsonObject orc = chamar("GET", "/usuarios/" + u + "/orcamentos", u, null).lista().get(0).getAsJsonObject();
        // Assert
        assertEquals("ATENCAO", orc.get("status").getAsString());
        assertEquals("80.00", orc.get("gasto").getAsBigDecimal().toPlainString());
    }

    @Test
    void ct13_contasComSituacao_viaApi() throws Exception {
        // Arrange
        long u = criarUsuario("Carlos", 40);
        criarDespesa(u, "10", ",\"vencimento\":\"2026-12-06\"");
        criarDespesa(u, "10", ",\"vencimento\":\"2026-12-02\"");
        criarDespesa(u, "10", "");
        // Act
        JsonArray contas = chamar("GET", "/usuarios/" + u + "/contas", u, null).lista();
        // Assert
        assertEquals(2, contas.size());
        assertEquals("ATRASADA", contas.get(0).getAsJsonObject().get("status").getAsString());
        assertEquals("A_VENCER", contas.get(1).getAsJsonObject().get("status").getAsString());
    }

    @Test
    void ct15_alertaFamiliar_viaApi() throws Exception {
        // Arrange
        long idoso = criarUsuario("Dona Maria", 60);
        // Act
        Resp r500 = criarDespesa(idoso, "500.00", "");
        Resp r50001 = criarDespesa(idoso, "500.01", "");
        // Assert
        assertFalse(r500.json().get("alertaFamiliar").getAsBoolean());
        assertTrue(r50001.json().get("alertaFamiliar").getAsBoolean());
    }

    @Test
    void ct17_ct18_familiarSoLeELimiteDeVinculos_viaApi() throws Exception {
        // Arrange
        long idoso = criarUsuario("Dona Maria", 60);
        long f1 = criarUsuario("F1", 35);
        long f2 = criarUsuario("F2", 35);
        long f3 = criarUsuario("F3", 35);
        String vinculos = "/usuarios/" + idoso + "/familiares";
        // Act
        Resp v1 = chamar("POST", vinculos, idoso, "{\"familiarId\":" + f1 + "}");
        Resp v2 = chamar("POST", vinculos, idoso, "{\"familiarId\":" + f2 + "}");
        Resp v3 = chamar("POST", vinculos, idoso, "{\"familiarId\":" + f3 + "}");
        Resp repetido = chamar("POST", vinculos, idoso, "{\"familiarId\":" + f1 + "}");
        Resp leitura = chamar("GET", "/usuarios/" + idoso + "/transacoes", f1, null);
        Resp escrita = chamar("POST", "/usuarios/" + idoso + "/transacoes", f1,
                "{\"tipo\":\"DESPESA\",\"valor\":10,\"categoria\":\"x\"}");
        Resp leituraDeF3 = chamar("GET", "/usuarios/" + idoso + "/transacoes", f3, null);
        // Assert
        assertEquals(201, v1.status());
        assertEquals(201, v2.status());
        assertEquals(409, v3.status());
        assertEquals("limite de 2 familiares atingido", v3.json().get("erro").getAsString());
        assertEquals(409, repetido.status());
        assertEquals(200, leitura.status());
        assertEquals(403, escrita.status());
        assertEquals("familiar tem acesso somente leitura", escrita.json().get("erro").getAsString());
        assertEquals(403, leituraDeF3.status());
    }

    @Test
    void ct19_vinculoInvalido_viaApi() throws Exception {
        // Arrange
        long idoso = criarUsuario("Dona Maria", 60);
        long jovem = criarUsuario("Neto", 20);
        // Act + Assert
        assertEquals(400, chamar("POST", "/usuarios/" + jovem + "/familiares", jovem,
                "{\"familiarId\":" + idoso + "}").status());
        assertEquals(400, chamar("POST", "/usuarios/" + idoso + "/familiares", idoso,
                "{\"familiarId\":" + idoso + "}").status());
        assertEquals(404, chamar("POST", "/usuarios/" + idoso + "/familiares", idoso,
                "{\"familiarId\":999999}").status());
        assertEquals(400, chamar("POST", "/usuarios/" + idoso + "/familiares", idoso, "{}").status());
        assertEquals(403, chamar("POST", "/usuarios/" + idoso + "/familiares", jovem,
                "{\"familiarId\":" + jovem + "}").status());
    }

    @Test
    void ct20_identificacaoPeloCabecalho() throws Exception {
        // Arrange
        long u = criarUsuario("Carlos", 40);
        String lista = "/usuarios/" + u + "/transacoes";
        // Act
        Resp semCabecalho = chamar("GET", lista, null, null);
        HttpResponse<String> invalido = http.send(HttpRequest.newBuilder(URI.create(base + lista))
                .header("X-Usuario-Id", "abc").GET().build(), HttpResponse.BodyHandlers.ofString());
        Resp inexistente = chamar("GET", lista, 999999L, null);
        Resp valido = chamar("GET", lista, u, null);
        Resp donoInexistente = chamar("GET", "/usuarios/999999/transacoes", u, null);
        // Assert
        assertEquals(401, semCabecalho.status());
        assertEquals("informe o cabecalho X-Usuario-Id", semCabecalho.json().get("erro").getAsString());
        assertEquals(401, invalido.statusCode());
        assertEquals(401, inexistente.status());
        assertEquals(200, valido.status());
        assertEquals(404, donoInexistente.status());
    }

    @Test
    void ct21_metaEAportes_viaApi() throws Exception {
        // Arrange
        long u = criarUsuario("Carlos", 40);
        Resp meta = chamar("POST", "/usuarios/" + u + "/metas", u, "{\"descricao\":\"Reserva\",\"valorAlvo\":100.00}");
        long m = meta.json().get("id").getAsLong();
        // Act
        Resp a1 = chamar("POST", "/metas/" + m + "/aportes", u, "{\"valor\":99.99}");
        Resp a2 = chamar("POST", "/metas/" + m + "/aportes", u, "{\"valor\":0.01}");
        Resp a3 = chamar("POST", "/metas/" + m + "/aportes", u, "{\"valor\":50.00}");
        // Assert
        assertEquals(201, meta.status());
        assertEquals(99, a1.json().get("percentual").getAsInt());
        assertEquals("EM_ANDAMENTO", a1.json().get("status").getAsString());
        assertEquals(100, a2.json().get("percentual").getAsInt());
        assertEquals("CONCLUIDA", a2.json().get("status").getAsString());
        assertEquals("150.00", a3.json().get("valorAtual").getAsBigDecimal().toPlainString());
        assertEquals(100, a3.json().get("percentual").getAsInt());
        assertEquals(1, chamar("GET", "/usuarios/" + u + "/metas", u, null).lista().size());
        assertEquals(400, chamar("POST", "/usuarios/" + u + "/metas", u,
                "{\"descricao\":\"X\",\"valorAlvo\":0}").status());
        assertEquals(404, chamar("POST", "/metas/999999/aportes", u, "{\"valor\":1}").status());
    }

    @Test
    void ct22_entradasMalformadas_nuncaGeram500() throws Exception {
        // Arrange
        long u = criarUsuario("Carlos", 40);
        String tr = "/usuarios/" + u + "/transacoes";
        // Act
        List<Resp> respostas = List.of(
                chamar("POST", "/usuarios", null, "{nao json"),
                chamar("POST", "/usuarios", null, "[1,2]"),
                chamar("POST", tr, u, "{\"tipo\":\"DESPESA\",\"valor\":\"abc\",\"categoria\":\"t\"}"),
                chamar("POST", tr, u, "{\"tipo\":\"DESPESA\",\"valor\":1,\"categoria\":\"t\",\"paga\":\"sim\"}"),
                chamar("POST", tr, u, "{\"tipo\":1,\"valor\":1,\"categoria\":\"t\"}"),
                chamar("PATCH", "/usuarios/" + u, null, null),
                chamar("GET", "/rota-inexistente", null, null),
                chamar("GET", "/transacoes/99999999999999999999", u, null));
        // Assert
        List<Integer> esperados = List.of(400, 400, 400, 400, 400, 405, 404, 404);
        assertEquals(esperados, respostas.stream().map(Resp::status).toList());
        for (Resp r : respostas) {
            JsonElement corpo = JsonParser.parseString(r.corpo());
            assertTrue(corpo.getAsJsonObject().has("erro"), r.corpo());
        }
    }

    @Test
    void saude_respondeOk() throws Exception {
        // Arrange: API no ar
        // Act
        Resp r = chamar("GET", "/health", null, null);
        // Assert
        assertEquals(200, r.status());
        assertEquals("ok", r.json().get("status").getAsString());
    }
}
