# MyDelivery Admin API

Painel administrativo do MyDelivery — **projeto separado** do backend dos restaurantes.

## Stack

- **Java 21** + **Spring Boot 3.5** (Maven)
- **MySQL** (2 datasources: admin write + main read-only)
- **Spring Security + JWT** (a implementar — esqueleto livre por enquanto)

## Arquitetura

```
admin-mydelivery-api  ──writes──▶  admin_mydelivery  (DB próprio)
                      ──reads───▶  mydelivery_db     (DB do projeto principal, read-only)
```

Por que separado:
- Atualizar admin não derruba restaurantes
- Atualizar app dos restaurantes não derruba admin
- Falha de schema no main não corrompe dados do admin

## Rodando localmente

### 1. Pré-requisitos
- Java 21
- MySQL local com 2 databases:
  - `admin_mydelivery` (criar vazio, JPA gera tabelas com ddl-auto=update)
  - `mydelivery_db` (deve existir; é o mesmo banco do projeto principal)

### 2. Configurar env
```bash
cp .env.example .env
# editar .env com sua senha do MySQL
```

### 3. Subir
```bash
./mvnw spring-boot:run
```

Sobe em `http://localhost:8090` (porta 8090 pra não bater com o 8080 do main).

### 4. Testar
```bash
curl http://localhost:8090/api/admin/health
```

Resposta esperada:
```json
{"status":"ok","servico":"admin-mydelivery-api","dbAdmin":"ok","dbMain":"ok",...}
```

## Estrutura de pastas

```
src/main/java/com/mydelivery/admin/
├── config/                 # 2 DataSources, Security, CORS
├── security/               # Filtros JWT (a implementar)
├── modulos/
│   ├── auth/               # Login do admin (a implementar)
│   ├── dashboard/          # Endpoints agregados (KPIs)
│   ├── restaurantes/       # Listar/filtrar restaurantes (lê do main)
│   ├── monitoramento/      # Health de serviços externos
│   ├── alertas/            # Detecção e gestão de alertas
│   ├── tickets/            # Suporte / fila de tickets
│   ├── autocorrecao/       # Engine de auto-fix
│   ├── financeiro/         # Cobrança mensal dos restaurantes
│   ├── pagamentos/         # Config MP do admin (eu receber)
│   ├── relatorios/         # Crescimento da plataforma
│   ├── logs/               # Logs do sistema visíveis no painel
│   └── auditoria/          # Audit log do admin
└── shared/
    ├── dto/                # DTOs compartilhados
    ├── exception/          # Global exception handler
    └── main/               # Acesso ao DB do MyDelivery principal
        ├── entity/         # Entidades espelho (read-only)
        └── repository/     # Repositories do main
```

## Deploy no Railway

Mesmo projeto Railway do MyDelivery principal — novo service.

### Env vars necessárias

```bash
# DB próprio do admin (mesmo plugin MySQL, database diferente)
DB_URL_ADMIN=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/admin_mydelivery?useSSL=false&serverTimezone=America/Sao_Paulo&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true
DB_USERNAME_ADMIN=${{MySQL.MYSQLUSER}}
DB_PASSWORD_ADMIN=${{MySQL.MYSQLPASSWORD}}

# DB do MyDelivery principal (LEITURA only)
DB_URL_MAIN=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useSSL=false&serverTimezone=America/Sao_Paulo&allowPublicKeyRetrieval=true
DB_USERNAME_MAIN=${{MySQL.MYSQLUSER}}
DB_PASSWORD_MAIN=${{MySQL.MYSQLPASSWORD}}

# JWT
ADMIN_JWT_SECRET=<gerar com: openssl rand -base64 64>

# CORS
ADMIN_CORS_ORIGINS=https://admin.mydeliveryfood.com.br

# Cloudinary (anexos de ticket — reusa as mesmas chaves do main)
CLOUDINARY_CLOUD_NAME=...
CLOUDINARY_API_KEY=...
CLOUDINARY_API_SECRET=...

# Timezone
TZ=America/Sao_Paulo
```
