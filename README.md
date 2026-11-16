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

### 1. Assinar Arquivo (`POST /signature/`)

Recebe a requisição via `multipart/form-data` e gera a assinatura digital CMS com o conteúdo anexado utilizando a chave privada do arquivo PKCS#12 (.pfx) com algoritmo `SHA512withRSA`.

- **Parâmetros suportados:**
  - `file`: o arquivo a ser assinado (se omitido, usa o arquivo padrão `src/main/resources/arquivos/doc.txt`);
  - `pkcs12`: o arquivo PKCS#12 (.pfx) enviado no request (se omitido, carrega o certificado padrão de `resources/pkcs12/`);
  - `password`: a senha do arquivo PKCS#12 (se omitida, usa a senha configurada no `application.yaml`).
- **Gravação em disco:** Grava a assinatura em disco em notação binária ASN.1 como **`assinatura.p7s`** (`Files.write(Path.of("assinatura.p7s"), encoded)`).
- **Retorno:** Retorna o conteúdo da assinatura CMS attached codificado em Base64 no corpo da resposta.

**Chamada completa com upload de arquivo, pkcs12 e senha:**
```bash
curl -X POST http://localhost:8080/signature/ \
  -F "file=@caminho/para/documento.txt" \
  -F "pkcs12=@caminho/para/certificado.pfx" \
  -F "password=sua_senha"
```

**Chamada simplificada (utiliza doc.txt e certificado padrão de resources):**
```bash
curl -X POST http://localhost:8080/signature/
```

**Resposta (200 OK):**
```text
MIIQ... (conteúdo da assinatura em Base64)
```

---

### 2. Verificar Assinatura (`POST /verify/`)

Recebe a requisição via `multipart/form-data` com o parâmetro `file` (arquivo assinado em Base64 no padrão CMS attached, aceitando também DER binário ou PEM com delimitadores).

- **Validação de Cadeia Híbrida:** Carrega primeiro os certificados presentes em `classpath*:cadeia/*`. Caso a pasta `resources/cadeia/` esteja vazia ou não exista, realiza fallback extraindo a cadeia diretamente do KeyStore PKCS#12.
- **Fallback de Arquivo:** Se chamado sem upload, busca e valida automaticamente o arquivo **`assinatura.p7s`** gravado em disco.

**Chamada enviando arquivo Base64, PEM ou binário .p7s:**
```bash
curl -X POST http://localhost:8080/verify/ \
  -F "file=@assinatura.p7s"
```

**Exemplo de Resposta de Sucesso (200 OK com chaves exatas `Status` e `Infos`):**
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
│   │   │   ├── controller/      # Endpoints REST (/signature/, /verify/)
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
