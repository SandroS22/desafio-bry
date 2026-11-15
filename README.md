# Desafio BRy - Assinatura e Verificação CMS

API REST desenvolvida em Spring Boot para assinatura digital de arquivos no padrão **CMS (Cryptographic Message Syntax)** anexado (*attached*) e verificação de integridade e autenticidade da assinatura com validação de cadeia de certificados (PKIX).

---

## Informações do Projeto e Versões

- **Versão do Projeto:** `0.0.1-SNAPSHOT`
- **Java:** `17` (testado e configurado para Java 17.0.21)
- **Spring Boot:** `3.5.16`
- **Bouncy Castle:** `1.87` (`bcprov-jdk18on` e `bcpkix-jdk18on`)
- **Maven:** `3.9.16` (disponível via wrapper `./mvnw`)
- **Gerenciador de versões (opcional):** [mise](https://mise.jdx.dev/) (`mise.toml`)

---

## Pré-requisitos

- **Java JDK 17** ou superior instalado e configurado na variável de ambiente `JAVA_HOME`.
- Acesso à internet na primeira execução para download das dependências do Maven (caso não estejam em cache local).

---

## Passo a Passo para Execução

### 1. Clonar o repositório

```bash
git clone https://github.com/sandros22/desafio-bry.git
cd desafio-bry
```

### 2. Executar os Testes

Para rodar todos os testes unitários e de integração:

```bash
./mvnw test
```

### 3. Compilar e Gerar o Pacote JAR

Para compilar o código e gerar o artefato executável (`.jar`) no diretório `target/`:

```bash
./mvnw clean package
```

### 4. Executar a Aplicação

Você pode iniciar a aplicação de duas formas:

**Opção A - Via Maven Wrapper:**
```bash
./mvnw spring-boot:run
```

**Opção B - Executando o arquivo JAR diretamente:**
```bash
java -jar target/desafio-bry-0.0.1-SNAPSHOT.jar
```

A aplicação será iniciada na porta **8080** (`http://localhost:8080`).

---

## Utilização dos Endpoints

### 1. Assinar Arquivo (`POST /sign`)

Recebe um arquivo via `multipart/form-data`, gera a assinatura digital CMS com o conteúdo anexado utilizando a chave privada do certificado PKCS#12 (`certificado_teste_hub.pfx`) e algoritmo `SHA512withRSA`, salva a assinatura em `assinatura.txt` e retorna o conteúdo assinado codificado em Base64.

```bash
curl -X POST http://localhost:8080/sign \
  -F "file=@src/main/resources/arquivos/doc.txt"
```

**Resposta (200 OK):**
```text
MIIQ... (conteúdo da assinatura em Base64)
```

---

### 2. Verificar Assinatura (`POST /verify`)

Recebe um arquivo contendo a assinatura digital em Base64 (como o gerado em `assinatura.txt`), decodifica o CMS, valida a integridade da assinatura criptográfica e a cadeia de confiança PKIX do certificado, e extrai os metadados do signatário.

```bash
curl -X POST http://localhost:8080/verify \
  -F "file=@assinatura.txt"
```

**Exemplo de Resposta de Sucesso (200 OK):**
```json
{
  "Status": "VALIDO",
  "Infos": {
    "nomeSignatario": "BRy Tecnologia - Teste Hub",
    "dataAssinatura": "2026-11-15T18:21:48.350Z",
    "hashDocumento": "dc1a7de77c59a29f366a4b154b03ad7d99013e36e08beb50d976358bea7b045884fe72111b27cf7d6302916b2691ac7696c1637e1ab44584d8d6613825149e35",
    "algoritmoHash": "SHA512"
  }
}
```

**Exemplo de Resposta para Assinatura Inválida ou Adulterada (200 OK):**
```json
{
  "Status": "INVALIDO",
  "Infos": null
}
```

---

## Estrutura do Projeto

```text
├── .github/workflows/          # Pipeline de CI/CD para release
├── src/
│   ├── main/
│   │   ├── java/com/github/sandros22/desafio_bry/
│   │   │   ├── controller/      # Endpoints REST (/sign, /verify)
│   │   │   ├── crypto/          # Lógica criptográfica (CmsSigner, CmsVerifier)
│   │   │   ├── dto/             # Records de transferência de dados
│   │   │   └── exception/       # GlobalExceptionHandler
│   │   └── resources/
│   │       ├── application.yaml # Configurações da aplicação (porta, alias, senha)
│   │       ├── arquivos/        # Arquivo doc.txt para testes
│   │       └── pkcs12/          # Certificado digital PKCS#12 (.pfx)
│   └── test/
│       └── java/com/github/sandros22/desafio_bry/
│           ├── controller/      # Testes do Controller (WebMvc e Integração)
│           ├── crypto/          # Testes criptográficos (Signer e Verifier)
│           └── exception/       # Testes do tratamento global de erros
├── pom.xml                      # Dependências Maven e plugins de build
├── mise.toml                    # Configuração de versões das ferramentas (Java/Maven)
└── README.md                    # Documentação do projeto
```
