import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {

    // ============================================================
    // CONFIGURAÇÃO
    // ============================================================

    private static final String TOKEN = "Informe seu TOKEN aqui";

    private static final String URL =
            "https://api.arquivo-nfe.com/prod/consulta_cadastro";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();


    // ============================================================
    // CONSULTAS
    //
    // Informe apenas UM dos identificadores:
    // cnpj, cpf ou ie
    //
    // O request_id será preenchido automaticamente após
    // o envio da consulta.
    // ============================================================

    public static void main(String[] args) {

        List<Consulta> consultas = new ArrayList<>();

        consultas.add(new Consulta(
                "SP",
                "XXXXXXXXXXXXXX",
                null,
                null
        ));

        consultas.add(new Consulta(
                "ES",
                null,
                "XXX.XXX.XXX-XX",
                null
        ));

        consultas.add(new Consulta(
                "GO",
                null,
                null,
                "XXXXXXXXX"
        ));


        // ========================================================
        // ETAPA 1
        // ENVIA O LOTE DE CONSULTAS
        // ========================================================

        System.out.println();
        System.out.println("======================================================================");
        System.out.println("ETAPA 1 - ENVIANDO CONSULTAS");
        System.out.println("======================================================================");


        for (int i = 0; i < consultas.size(); i++) {

            Consulta consulta = consultas.get(i);

            try {

                Map<String, String> parametros =
                        criarParametros(consulta);

                System.out.println();
                System.out.println("Consulta " + (i + 1));
                System.out.println("UF: " + consulta.uf);
                System.out.println("Parâmetros: " + parametros);


                HttpResponse<String> resposta =
                        enviarRequisicao(parametros);


                System.out.println("URL enviada:");
                System.out.println(resposta.uri());

                System.out.println(
                        "HTTP Status: " + resposta.statusCode()
                );


                // ------------------------------------------------
                // CONVERTE A RESPOSTA PARA JSON
                // ------------------------------------------------

                JsonNode dados;

                try {

                    dados = MAPPER.readTree(resposta.body());

                } catch (Exception erro) {

                    System.out.println(
                            "A API não retornou um JSON válido."
                    );

                    System.out.println(
                            "Resposta bruta recebida:"
                    );

                    System.out.println(resposta.body());

                    continue;
                }


                // ------------------------------------------------
                // ERRO
                // ------------------------------------------------

                if (dados.has("erro")) {

                    System.out.println(
                            "ERRO: " + dados.get("erro").asText()
                    );


                    // Caso a API retorne request_id junto com o erro

                    if (dados.has("request_id")
                            && !dados.get("request_id").isNull()) {

                        consulta.requestId =
                                dados.get("request_id").asText();

                        System.out.println(
                                "Request ID recebido: "
                                        + consulta.requestId
                        );
                    }

                    continue;
                }


                // ------------------------------------------------
                // REQUEST_ID
                //
                // O request_id pode vir:
                //
                // 1. Diretamente na resposta:
                //    {"request_id": 26989}
                //
                // 2. Dentro de retorno:
                //    {"retorno": [{"request_id": 26988}]}
                // ------------------------------------------------

                JsonNode requestIdNode =
                        dados.get("request_id");


                if (requestIdNode == null
                        || requestIdNode.isNull()) {

                    JsonNode retorno =
                            dados.get("retorno");

                    if (retorno != null
                            && retorno.isArray()
                            && retorno.size() > 0) {

                        requestIdNode =
                                retorno.get(0).get("request_id");
                    }
                }


                if (requestIdNode != null
                        && !requestIdNode.isNull()) {

                    consulta.requestId =
                            requestIdNode.asText();

                    System.out.println(
                            "Request ID recebido: "
                                    + consulta.requestId
                    );

                } else {

                    System.out.println(
                            "A API não retornou request_id "
                                    + "para esta consulta."
                    );
                }


            } catch (IllegalArgumentException erro) {

                System.out.println(
                        "Erro nos parâmetros: "
                                + erro.getMessage()
                );

            } catch (IOException | InterruptedException erro) {

                System.out.println(
                        "Erro de comunicação com a API: "
                                + erro.getMessage()
                );
            }
        }


        // ========================================================
        // ETAPA 2
        // CONSULTA OS REQUEST_ID
        // ========================================================

        System.out.println();
        System.out.println("======================================================================");
        System.out.println("ETAPA 2 - CONSULTANDO OS RESULTADOS");
        System.out.println("======================================================================");


        // Número máximo de tentativas

        final int MAX_TENTATIVAS = 5;

        // Tempo entre as tentativas

        final int INTERVALO = 5;


        for (int i = 0; i < consultas.size(); i++) {

            Consulta consulta = consultas.get(i);

            String requestId = consulta.requestId;


            if (requestId == null || requestId.isBlank()) {

                System.out.println();

                System.out.println(
                        "Consulta " + (i + 1)
                                + ": sem request_id. Ignorada."
                );

                continue;
            }


            System.out.println();

            System.out.println(
                    "Consulta " + (i + 1)
                            + " - Request ID: "
                            + requestId
            );


            boolean resultadoObtido = false;


            for (int tentativa = 1;
                 tentativa <= MAX_TENTATIVAS;
                 tentativa++) {

                try {

                    Map<String, String> parametros =
                            Map.of(
                                    "uf", consulta.uf,
                                    "request_id", requestId
                            );


                    HttpResponse<String> resposta =
                            enviarRequisicao(parametros);


                    System.out.println();

                    System.out.println(
                            "Tentativa "
                                    + tentativa
                                    + "/"
                                    + MAX_TENTATIVAS
                    );


                    // ------------------------------------------------
                    // CONVERTE A RESPOSTA PARA JSON
                    // ------------------------------------------------

                    JsonNode dados;

                    try {

                        dados =
                                MAPPER.readTree(resposta.body());

                    } catch (Exception erro) {

                        System.out.println(
                                "A API retornou uma resposta "
                                        + "que não é um JSON válido."
                        );

                        System.out.println(
                                "Resposta bruta recebida:"
                        );

                        System.out.println(resposta.body());

                        break;
                    }


                    String info = dados.has("info")
                            ? dados.get("info").asText()
                            : "";


                    // ------------------------------------------------
                    // ERRO
                    // ------------------------------------------------

                    if (dados.has("erro")) {

                        System.out.println(
                                "ERRO: "
                                        + dados.get("erro").asText()
                        );

                        resultadoObtido = true;

                        break;
                    }


                    // ------------------------------------------------
                    // AGUARDANDO RETORNO
                    // ------------------------------------------------

                    if (info.contains("Aguardando retorno")
                            || info.contains("status PENDENTE")) {

                        if (tentativa < MAX_TENTATIVAS) {

                            System.out.println(
                                    "Consulta ainda em processamento."
                            );

                            System.out.println(
                                    "Nova tentativa em "
                                            + INTERVALO
                                            + " segundos."
                            );


                            Thread.sleep(
                                    INTERVALO * 1000L
                            );


                            continue;

                        } else {

                            System.out.println(
                                    "Não foi possível obter "
                                            + "o retorno da SEFAZ "
                                            + "dentro do limite "
                                            + "de tentativas."
                            );

                            break;
                        }
                    }


                    // ------------------------------------------------
                    // SUCESSO
                    // ------------------------------------------------

                    if ("sucesso".equals(info)) {

                        System.out.println();

                        System.out.println(
                                "CONSULTA CONCLUÍDA COM SUCESSO"
                        );


                        System.out.println(
                                MAPPER.writerWithDefaultPrettyPrinter()
                                        .writeValueAsString(dados)
                        );


                        resultadoObtido = true;

                        break;
                    }


                    // ------------------------------------------------
                    // RESPOSTA NÃO PREVISTA
                    // ------------------------------------------------

                    System.out.println(
                            "Resposta recebida da API:"
                    );


                    System.out.println(
                            MAPPER.writerWithDefaultPrettyPrinter()
                                    .writeValueAsString(dados)
                    );


                    resultadoObtido = true;

                    break;


                } catch (InterruptedException erro) {

                    Thread.currentThread().interrupt();

                    System.out.println(
                            "Processamento interrompido."
                    );

                    break;

                } catch (IOException erro) {

                    System.out.println(
                            "Erro de comunicação com a API: "
                                    + erro.getMessage()
                    );

                    break;

                } catch (Exception erro) {

                    System.out.println(
                            "Erro inesperado: "
                                    + erro.getMessage()
                    );

                    break;
                }
            }
        }
    }


    // ============================================================
    // FUNÇÃO PARA MONTAR OS PARÂMETROS
    // ============================================================

    private static Map<String, String> criarParametros(
            Consulta consulta) {

        if (consulta.uf == null
                || consulta.uf.isBlank()) {

            throw new IllegalArgumentException(
                    "Informe a UF."
            );
        }


        int quantidade = 0;

        String nome = null;
        String valor = null;


        if (consulta.cnpj != null
                && !consulta.cnpj.isBlank()) {

            quantidade++;
            nome = "cnpj";
            valor = consulta.cnpj;
        }


        if (consulta.cpf != null
                && !consulta.cpf.isBlank()) {

            quantidade++;
            nome = "cpf";
            valor = consulta.cpf;
        }


        if (consulta.ie != null
                && !consulta.ie.isBlank()) {

            quantidade++;
            nome = "ie";
            valor = consulta.ie;
        }


        if (quantidade != 1) {

            throw new IllegalArgumentException(
                    "Informe exatamente um dos parâmetros: "
                            + "cnpj, cpf ou ie."
            );
        }


        return Map.of(
                "uf", consulta.uf,
                nome, valor
        );
    }


    // ============================================================
    // ENVIA REQUISIÇÃO
    // ============================================================

    private static HttpResponse<String> enviarRequisicao(
            Map<String, String> parametros)
            throws IOException, InterruptedException {


        StringBuilder url = new StringBuilder(URL);

        url.append("?");


        boolean primeiro = true;


        for (Map.Entry<String, String> parametro
                : parametros.entrySet()) {

            if (!primeiro) {
                url.append("&");
            }

            url.append(
                    URLEncoder.encode(
                            parametro.getKey(),
                            StandardCharsets.UTF_8
                    )
            );

            url.append("=");

            url.append(
                    URLEncoder.encode(
                            parametro.getValue(),
                            StandardCharsets.UTF_8
                    )
            );

            primeiro = false;
        }


        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(url.toString()))
                        .timeout(Duration.ofSeconds(60))
                        .header(
                                "Authorization",
                                "Bearer " + TOKEN
                        )
                        .GET()
                        .build();


        return CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }


    // ============================================================
    // CLASSE DE CONSULTA
    // ============================================================

    private static class Consulta {

        String uf;
        String cnpj;
        String cpf;
        String ie;
        String requestId;


        Consulta(
                String uf,
                String cnpj,
                String cpf,
                String ie) {

            this.uf = uf;
            this.cnpj = cnpj;
            this.cpf = cpf;
            this.ie = ie;
            this.requestId = null;
        }
    }
}
