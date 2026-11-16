# Relatório Técnico — Desafio Prático Desenvolvedor Back-end Java (BRy Tecnologia)

## 1. Visão Geral
Este relatório documenta a implementação do desafio técnico para a vaga de Desenvolvedor Back-end Java. O projeto consiste em uma aplicação desenvolvida em **Java 17** com **Spring Boot 3**, **Maven** e **BouncyCastle** (`bcprov` e `bcpkix`), contemplando todas as etapas propostas: geração de resumo criptográfico (SHA-512), assinatura digital no padrão CMS (*Cryptographic Message Syntax*) do tipo *attached*, verificação de integridade e cadeia de confiança X.509, exposição de uma API REST, suíte de testes automatizados com JUnit 5 e pipeline de distribuição automatizada via GitHub Actions.

---

## 2. Estrutura do Projeto

O projeto segue a arquitetura padrão Maven/Spring Boot, organizado por responsabilidades:

```text
desafio-bry/
├── .github/
│   └── workflows/
│       └── release.yml                  # Pipeline CI/CD para build, testes e publicação de Release
├── src/
│   ├── main/
│   │   ├── java/com/github/sandros22/desafio_bry/
│   │   │   ├── controller/
│   │   │   │   └── Controller.java      # Endpoints REST (/signature/ e /verify/)
│   │   │   ├── crypto/
│   │   │   │   ├── CmsSigner.java       # Carregamento de KeyStore e assinatura CMS attached
│   │   │   │   └── CmsVerifier.java     # Decodificação, verificação de integridade, PKIX e extração de metadados
│   │   │   ├── dto/
│   │   │   │   ├── SignatureInfos.java  # DTO com metadados extraídos da assinatura (CN, data, hash, algoritmo)
│   │   │   │   └── VerifyResponse.java  # DTO de resposta padronizada (Status + Infos)
│   │   │   ├── exception/
│   │   │   │   └── GlobalExceptionHandler.java # Tratamento global de exceções HTTP (400, 200 INVALIDO, 500)
│   │   │   └── DesafioBryApplication.java
│   │   └── resources/
│   │       ├── arquivos/
│   │       │   └── doc.txt              # Documento padrão utilizado nas etapas 1 e 2
│   │       ├── cadeia/                  # Certificados da cadeia de confiança para validação PKIX
│   │       ├── pkcs12/                  # Repositório PKCS#12 (.pfx) com certificado e chave privada
│   │       └── application.properties   # Configurações de alias e senha do KeyStore
│   └── test/
│       └── java/com/github/sandros22/desafio_bry/
│           ├── controller/
│           │   ├── ControllerTest.java            # Testes unitários da camada web (WebMvcTest + MockMvc)
│           │   └── ControllerIntegrationTest.java # Testes ponta a ponta (SpringBootTest)
│           └── crypto/
│               ├── CmsSignerTest.java             # Testes unitários do componente de assinatura
│               └── CmsVerifierTest.java           # Testes unitários do componente de verificação
└── pom.xml
```

---

## 3. Descrição da Implementação por Etapa

### Etapa 1 — Obtenção do Resumo Criptográfico (SHA-512)
* Implementada a leitura do documento `resources/arquivos/doc.txt` e o cálculo do resumo criptográfico utilizando `java.security.MessageDigest` com o algoritmo **SHA-512**.
* A conversão do array de bytes resultante para representação hexadecimal utiliza `java.util.HexFormat.of().formatHex(...)` (recurso nativo do Java 17), gerando o arquivo entregue com o hash correspondente.

### Etapa 2 — Geração de Assinatura Digital (CMS Attached)
* Encapsulada na classe `CmsSigner`.
* **Carregamento do KeyStore:** Utiliza `java.security.KeyStore` do tipo `PKCS12` para carregar o artefato em `resources/pkcs12/` (ou bytes enviados dinamicamente via API), extraindo a chave privada e o certificado correspondente ao alias `e2618a8b-20de-4dd2-b209-70912e3177f4` com a senha `bry123456`.
* **Assinatura CMS:** Registra o provedor `BouncyCastleProvider` (`BC`) e utiliza `CMSSignedDataGenerator` com `JcaContentSignerBuilder("SHA512withRSA")`. O método `generator.generate(new CMSProcessableByteArray(content), true)` é invocado com o parâmetro `encapsulate = true` para garantir que o conteúdo original fique embutido na estrutura ASN.1 (*attached*).
* A cadeia completa de certificados (combinando a cadeia do `KeyStore` e os certificados de `resources/cadeia/`) é anexada ao pacote via `JcaCertStore`, e o resultado binário é gravado em disco como `assinatura.p7s`.

### Etapa 3 — Verificação da Assinatura Digital e Cadeia de Confiança
* Encapsulada na classe `CmsVerifier`.
* **Integridade:** Extrai o `SignerInformation` e o certificado do signatário (`JcaX509CertificateConverter`) contidos no CMS, validando a assinatura matemática através de `JcaSimpleSignerInfoVerifierBuilder`.
* **Cadeia de Confiança (PKIX):** Carrega os certificados de `resources/cadeia/`, identifica os certificados raiz autoassinados como `TrustAnchor` e executa a validação do caminho de certificação utilizando `CertPathBuilder` / `CertPathValidator` com o algoritmo `PKIX`.
* **Extração de Metadados:** Extrai o *Common Name* (`CN`) via `X500Name` e `BCStyle.CN`, a data de assinatura (`CMSAttributes.signingTime`), o algoritmo de hash (`DefaultAlgorithmNameFinder`) e recalcula o hash hexadecimal do conteúdo encapsulado (`encapContentInfo`).

### Etapa 4 — API REST (Spring Boot 3)
* Implementada na classe `Controller` com suporte a `multipart/form-data`:
  * **`POST /signature/`**: Recebe `file` (documento), `pkcs12` (certificado) e `password` (senha), retornando HTTP `200 OK` com a assinatura CMS codificada em Base64.
  * **`POST /verify/`**: Recebe `file` (assinatura CMS) e retorna um JSON estruturado (`VerifyResponse`) contendo `Status` (`"VALIDO"` ou `"INVALIDO"`) e o objeto `Infos` com os 4 atributos obrigatórios (`nomeSignatario`, `dataAssinatura`, `hashDocumento` e `algoritmoHash`).
* **Tratamento de Erros (`GlobalExceptionHandler`):** Centraliza o tratamento de exceções com `@RestControllerAdvice`, retornando HTTP `400 Bad Request` para entradas inválidas, HTTP `200 OK` com `Status: "INVALIDO"` quando a estrutura CMS está corrompida (`CMSException`) e HTTP `500 Internal Server Error` para falhas inesperadas.

### Etapa 6 (Opcional) — Pipeline de Distribuição
* Configurado workflow do **GitHub Actions** em `.github/workflows/release.yml`. Sempre que uma tag no padrão `v*` (ex: `v1.1.0`) é enviada ao repositório, o pipeline configura o Java 17 (Temurin), executa `mvn clean package` (validando todos os testes unitários e de integração) e publica automaticamente o `.jar` executável na aba **Releases** do GitHub.

---

## 4. Decisões de Projeto e Robustez

1. **Flexibilidade de Formatos na Verificação (`decodeBase64`):** Embora o contrato da API especifique entrada em Base64, o verificador foi projetado para aceitar transparentemente Base64 puro, Base64 com quebras de linha/espaços, formato PEM (`-----BEGIN CMS-----`) e arquivos binários DER `.p7s` puros (identificados pela tag ASN.1 `0x30`).
2. **Fallbacks Inteligentes e Resiliência:**
   * Na ausência de parâmetros opcionais nos endpoints, a aplicação realiza fallback controlado para os artefatos padrão do desafio (`doc.txt`, certificado em `resources/pkcs12/` e `assinatura.p7s` gerada).
   * Na resolução do alias (`resolveAlias`), caso o alias configurado não seja localizado, o sistema busca automaticamente a primeira entrada de chave privada (`isKeyEntry`) disponível no `KeyStore`.
   * No carregamento da cadeia de confiança (`loadChainCertificates`), falhas isoladas de leitura são tratadas sem interromper o fluxo, acionando fallback para a cadeia embutida no `KeyStore`.
3. **Cobertura de Testes Automatizados:** Foram implementadas suítes separadas de testes unitários isolados com Mockito (`CmsSignerTest`, `CmsVerifierTest`, `ControllerTest`) e testes de integração ponta a ponta (`ControllerIntegrationTest`), cobrindo cenários de sucesso, adulteração de conteúdo, certificados não confiáveis, ausência de atributos opcionais (`signingTime` e `CN`) e falhas de entrada.

---

## 5. Como Buildar, Testar e Executar

### Pré-requisitos
* **JDK 17** (update 17.0.3 ou superior)
* **Apache Maven 3.8+** (ou uso do wrapper `./mvnw`)

### Build e Execução dos Testes
Para compilar o projeto, executar todos os testes unitários/integração e gerar o artefato `.jar`:
```bash
mvn clean package
```

### Executar a Aplicação
```bash
mvn spring-boot:run
```
*(Ou diretamente via JAR gerado: `java -jar target/desafio-bry-0.0.1-SNAPSHOT.jar`)*

A API estará disponível em `http://localhost:8080`.

### Exemplos de Requisição (cURL)

**1. Assinar um documento (`POST /signature/`):**
```bash
curl -X POST http://localhost:8080/signature/ \
  -F "file=@src/main/resources/arquivos/doc.txt" \
  -F "pkcs12=@src/main/resources/pkcs12/certificado_teste_hub.pfx" \
  -F "password=bry123456"
```

**2. Verificar uma assinatura (`POST /verify/`):**
```bash
curl -X POST http://localhost:8080/verify/ \
  -F "file=@assinatura.p7s"
```

---

## 6. Dificuldades Encontradas e Soluções

* **Desconhecimento prévio na área de Assinatura Digital e Criptografia:** Por não ter tido contato anterior com o domínio de certificação digital, a principal dificuldade do desafio foi assimilar os conceitos teóricos e práticos necessários para a implementação . Para superar essa barreira, pesquisei exemplos de implementação com a biblioteca BouncyCastle, consultei a documentação oficial (JCA e RFC 5652) e utilizei ferramentas de IA como apoio para aprofundar o entendimento conceitual, esclarecer dúvidas sobre as classes da API e estruturar testes no código.