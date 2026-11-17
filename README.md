# Urban Alert AI (Android)

Plataforma cívica geolocalizada para identificação, registro assistido por inteligência artificial e acompanhamento comunitário de problemas urbanos — com lançamento piloto em **Salvador, Bahia, Brasil**.

## Status atual do projeto

- **Marcos concluídos até aqui:**
  - `M1 — Fundação` (identidade, tema M3, modelos de domínio e cidade-piloto Salvador-BA)
  - `M2 — Banco e Identidade` (Firebase Firestore, App Check, regras zero-trust e Login Google via Credential Manager)
  - `M3 — Mídias, EXIF e Segurança` (Photo Picker sem permissão ampla, remoção de metadados EXIF sensíveis, validação de entradas e idempotência)
  - `M4 — Mapas e Consulta Pública` (mapa nativo OSMDroid centrado em Salvador-BA, clusters por zoom, filtros por categoria/status, busca de endereços, GPS opcional, consulta paginada no Firestore e tela de detalhes)
  - `M5 — Agente Conversacional e IA Multimodal` (`AgentRunner`, `ToolRegistry`, `MemoryManager`, Gemini `gemini-3.8-flash` via Firebase AI Logic com saída JSON estruturada, fallback manual e revisão cidadã obrigatória antes de publicar em `/incidents`)
  - `M6 — Offline, SQLite (Room) e Sincronização` (banco local `UrbanAlertDatabase` com `drafts`, `conversations`, `conversation_messages`, `local_media`, `pending_sync`, `SyncEngine` com backoff e idempotência e `OfflineQueueScreen`)
  - `M7 — Comunidade e Órgãos Públicos` (confirmações comunitárias únicas via transação em `/incident_confirmations`, prevenção contra duplicidade sem alterar status oficial e diretório verificado de órgãos públicos de Salvador em `/agencies` e `IncidentDetailScreen`)
  - `M8 — Painel Administrativo` (acesso restrito por `/admins/{uid}`, triagem oficial com histórico imutável em `/incident_status_history` e `/audit_logs`, gestão de categorias/órgãos, relatórios operacionais e monitoramento da IA em `AdminDashboardScreen`)
  - `M9 — Qualidade` (bateria de testes unitários, Compose UI + Robolectric, 8 testes de regras Firestore, otimização de dependências e relatório em `docs/QUALITY_CHECKLIST.md`)
  - `M10 — Publicação` (auditoria final de prontidão, conformidade Google Play e entrega para compartilhamento/exportação APK e AAB)
- **Status do Roadmap:** **100% concluído (M1–M10)**.

## Decisão de Arquitetura (Opção A — Android Nativo)

Este projeto é desenvolvido nativamente para **Android** em **Kotlin + Jetpack Compose** (Material Design 3), adotando a seguinte stack aprovada:

- **Interface:** Jetpack Compose com tema claro/escuro automático (verde `#25A66A`, grafite `#20282B`, fundo cartográfico `#DCE8DC`) e fontes `Space Grotesk` + `Plus Jakarta Sans`.
- **Mapas (M4):** `osmdroid-android` nativo com cache privado em `cacheDir`, clusterização espacial e consulta geográfica paginada.
- **Persistência local e modo offline (M6):** Room (SQLite) para rascunhos locais, fila de sincronização idempotente (`pending_sync`), histórico de conversa com o agente e retenção controlada de mídias locais.
- **Nuvem e identidade (M2–M9):** Firebase Firestore (banco nomeado + regras zero-trust) + Firebase Authentication (Google Sign-In via Credential Manager) + Firebase App Check.
- **Inteligência Artificial multimodal (M5):** Firebase AI Logic (`Firebase.ai` / `gemini-3.8-flash`) com `Schema.obj(...)`, sanitização contra *prompt injection* e revisão obrigatória pelo cidadão antes da publicação.

> Consulte `docs/ARCHITECTURE.md` e `docs/QUALITY_CHECKLIST.md` para detalhes completos de camadas, regras de segurança/LGPD e cobertura de testes.

## Estrutura de Pacotes (`app/src/main/java/com/example`)

- `core/model/`: Modelos de domínio (`Severity`, `IncidentStatus`, `SyncState`, `ConfirmationType`, `UrbanCategory`, `PublicAgency`, `AiAnalysisDraft`, `UserRole`, `AdminAccountInfo`, `CityOperationsReport`).
- `core/config/`: Configurações territoriais e catálogo inicial da cidade-piloto (`PilotCityConfig` — Salvador, BA).
- `core/firebase/`: Inicialização de App Check (`AppCheckHelper`) e tratamento estruturado de erros do Firestore (`FirestoreErrorHandler`).
- `core/security/`: Sanitizador de mídia e removedor de EXIF (`MediaSanitizer`), validador de entradas (`InputValidator`) e gerador de idempotência (`IdempotencyKeyGenerator`).
- `core/agent/`: Orquestrador conversacional (`AgentRunner`), registro de ferramentas (`ToolRegistry`), memória da sessão (`MemoryManager`) e analisador Gemini (`IncidentAiAnalyzer`).
- `data/auth/`, `data/profile/`, `data/incidents/`, `data/community/`, `data/agencies/`, `data/admin/`: Repositórios de autenticação Google, perfil, ocorrências, comunidade, órgãos públicos e operações administrativas auditáveis.
- `data/local/`, `data/sync/`: Banco Room (`UrbanAlertDatabase`), `LocalOfflineRepository`, `ConnectivityMonitor` e `SyncEngine`.
- `feature/map/`, `feature/incidents/`: Mapa público nativo (`PublicMapScreen`), `MapViewModel`, `IncidentClusterer` e detalhes da ocorrência (`IncidentDetailScreen`).
- `feature/report/`, `feature/offline/`: Fluxo conversacional (`ReportChatScreen`), revisão cidadã (`IncidentReviewScreen`), fila offline (`OfflineQueueScreen`) e `ReportViewModel`.
- `feature/auth/`, `feature/admin/`, `feature/privacy/`, `feature/foundation/`: Fluxo de conta/LGPD, painel administrativo restrito (`AdminDashboardScreen`), privacidade/EXIF e diagnóstico de marcos.
- `ui/theme/`, `ui/navigation/`: Tokens visuais M3 e navegação principal (`UrbanAlertApp`).

## Comandos de Build e Testes

```bash
# Compilar APK de depuração
gradle assembleDebug

# Executar testes unitários e Robolectric
gradle :app:testDebugUnitTest

# Executar testes das regras do Firestore contra o emulador local (porta 8085)
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```

## Princípios de Segurança e LGPD

- A IA nunca publica ocorrências sem confirmação explícita do cidadão.
- Gravidade (`Severity`), status no servidor (`IncidentStatus`) e estado de sincronização local (`SyncState`) são sempre campos independentes.
- Status e gravidade são sempre comunicados por **ícone + texto**, nunca apenas por cor.
- Imagens enviadas pelo cidadão passam por remoção obrigatória de EXIF sensível (`GPS`, modelo da câmera, data original) antes de qualquer uso público (`MediaSanitizer`).
- Consultas públicas de ocorrências no mapa exigem limite explícito (`limit <= 200`) nas regras do Firestore para impedir raspagem irrestrita da base.

## Exportação (ZIP, GitHub, APK / AAB) e Acesso Administrativo

1. **Compartilhar ou baixar o projeto pelo AI Studio**:
   - Use o menu de **Configurações** do AI Studio para **baixar o código-fonte em ZIP**, **enviar para o GitHub** ou **gerar os binários Android (`APK` para instalação direta / `AAB` para Google Play Console)**.
2. **Habilitar o primeiro Administrador no Firebase**:
   - Por segurança zero-trust, nenhum usuário comum pode promover a si mesmo.
   - Após fazer login no aplicativo com sua conta Google, copie seu `UID` em **Firebase Console → Authentication → Users** e crie um documento em `/admins/{UID}` no banco Firestore `ai-studio-android-urbanale-a04628a1-a243-4916-872d-5a08297c9f87` com:
     - `role`: `"superadmin"` (ou `"city_manager"`, `"operator"`)
     - `cityCode`: `"ssa-ba"`
     - `active`: `true`
     - `displayName`: `"Seu Nome"`
   - Ao reabrir a aba **Conta**, o card **Gestão Municipal Autorizada** aparecerá automaticamente.
