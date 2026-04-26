# Cypher — API REST de Segurança Industrial

API backend da plataforma **Cypher**, desenvolvida com Spring Boot para gestão de alertas gerados pelo motor de visão computacional em ambientes industriais.

## Contexto do Projeto

O Cypher é uma plataforma de segurança industrial proativa que detecta infrações de uso de EPIs (capacetes, coletes, luvas, óculos) e invasões de zonas de risco via câmeras IP. Esta API recebe, armazena e disponibiliza os alertas gerados pelo motor de IA para operadores e gestores.

## Entidade Principal

### Alerta

Representa um evento de segurança detectado pelo sistema de visão computacional.

| Campo      | Tipo          | Descrição                                              |
|------------|---------------|--------------------------------------------------------|
| `id`       | Long          | Identificador único                                    |
| `tipo`     | AlertaTipo    | `EPI_VIOLATION` ou `ZONE_VIOLATION`                    |
| `nivel`    | AlertaNivel   | Severidade: `BAIXO`, `MEDIO` ou `ALTO`                 |
| `descricao`| String        | Descrição do evento detectado                          |
| `zona`     | String        | Zona industrial onde ocorreu (ex: "Linha de Produção A") |
| `cameraId` | String        | Identificador da câmera que detectou o evento          |
| `dataHora` | LocalDateTime | Timestamp do evento                                    |
| `status`   | AlertaStatus  | `PENDENTE`, `REVISADO` ou `RESOLVIDO`                  |

## Endpoints

| Método   | URL             | Descrição                  |
|----------|-----------------|----------------------------|
| `POST`   | `/alertas`      | Criar novo alerta           |
| `GET`    | `/alertas`      | Listar todos os alertas     |
| `GET`    | `/alertas/{id}` | Buscar alerta por ID        |
| `PUT`    | `/alertas/{id}` | Atualizar alerta existente  |
| `DELETE` | `/alertas/{id}` | Remover alerta              |

### Exemplo de Payload (POST/PUT)

```json
{
  "tipo": "EPI_VIOLATION",
  "nivel": "ALTO",
  "descricao": "Trabalhador detectado sem capacete na Linha de Produção A",
  "zona": "Linha de Produção A",
  "cameraId": "CAM-001",
  "dataHora": "2026-04-26T14:30:00",
  "status": "PENDENTE"
}
```

## Banco de Dados

H2 em modo **file** — os dados persistem entre reinicializações.

- Console H2: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:file:./data/cypherdb`
- Usuário: `sa` | Senha: *(vazia)*

## Como Rodar

**Pré-requisitos:** Java 17+ e Maven instalados.

```bash
# Clonar o repositório
git clone <url-do-repositorio>
cd cypher

# Rodar a aplicação
./mvnw spring-boot:run
```

A API ficará disponível em `http://localhost:8080`.

## Estrutura do Projeto

```
src/main/java/com/dev4/cypher/
├── controller/
│   └── AlertaController.java
├── service/
│   └── AlertaService.java
├── repository/
│   └── AlertaRepository.java
├── model/
│   ├── Alerta.java
│   ├── AlertaTipo.java
│   ├── AlertaNivel.java
│   └── AlertaStatus.java
└── CypherApplication.java
```

## Tecnologias

- Java 21
- Spring Boot
- Spring Web MVC
- Spring Data JPA
- H2 Database (modo file)
- Lombok
- Maven