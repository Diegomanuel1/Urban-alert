# Arquitetura e Decisões Técnicas — Urban Alert AI

## 1. Contexto e Adaptação de Stack (Opção A)

Originalmente, o Prompt Mestre previa um monorepositório Web/Mobile/Backend em Node.js (`Next.js`, `Expo`, `Express`, `Supabase`, `Ollama`). Como o ambiente de execução disponível é o **Google AI Studio Build para Android**, foi aprovada a **Opção A**:

- **Cliente nativo Android:** Kotlin + Jetpack Compose + Material 3.
- **Persistência offline:** Room (SQLite) para rascunhos (`DRAFT`, `PENDING_SYNC`, `SYNCING`, `SYNCED`, `SYNC_FAILED`), conversas e fila idempotente.
- **Backend gerenciado e segurança:** Firebase Firestore com regras de acesso zero-trust (isolamento por usuário/organização e função) + Firebase Auth com Google Sign-In via Jetpack Credential Manager.
- **IA multimodal:** Firebase AI Logic (Gemini), mantendo a interface `AiAnalysisDraft` desacoplada para futura evolução ou integração externa.

## 2. Separação de Responsabilidades (MVVM + Camadas)

1. **`core/model` (Domínio puro):** Contratos independentes de biblioteca de rede ou UI.
2. **`core/config` (Configuração territorial):** Parâmetros de cidade-piloto (`Salvador, BA`) e taxonomia de categorias urbanas, permitindo expansão para outros municípios sem reescrever regras.
3. **`data` (`auth`, `profile`, `incidents`):** Repositórios conectados ao banco Firestore nomeado (`R.string.firestore_database_id`), protegidos por App Check e regras zero-trust em `firestore.rules`.
4. **`core/security`:** Sanitização local de mídias com remoção de EXIF (`MediaSanitizer`), validação de coordenadas/textos (`InputValidator`) e chaves de idempotência (`IdempotencyKeyGenerator`).
5. **`core/agent` (M5):** Orquestrador conversacional (`AgentRunner`, `ToolRegistry`, `MemoryManager`) e cliente Gemini multimodal (`IncidentAiAnalyzer`) via `Firebase.ai` com saída JSON estruturada e fallback manual.
6. **`data/local` e `data/sync` (M6):** Persistência offline em Room (`UrbanAlertDatabase`) com tabelas `drafts`, `conversations`, `conversation_messages`, `local_media` e `pending_sync`, coordenada por `SyncEngine` e `ConnectivityMonitor`.
7. **`data/community` e `data/agencies` (M7):** Confirmações comunitárias únicas (`/incident_confirmations`) com incremento transacional seguro de contadores e catálogo de órgãos públicos verificados (`/agencies`) para Salvador-BA.
8. **`data/admin` e `feature/admin` (M8):** Painel administrativo condicionado à existência de `/admins/{uid}`, triagem oficial com `WriteBatch` atômico (`/incidents` + `/incident_status_history` + `/audit_logs`), gestão de catálogo e relatórios/telemetria da IA.
9. **`feature/*` (Apresentação):** Composables sem estado direto de banco; comunicam-se com `ViewModel` via `StateFlow`.

## 3. Regras Invariantes do Domínio

- **Status vs. Gravidade vs. Sincronização:**
  - `Severity`: `low`, `medium`, `high`, `critical` (impacto técnico).
  - `IncidentStatus`: `open`, `under_review`, `in_progress`, `resolved`, `rejected` (ciclo de atendimento no servidor).
  - `SyncState`: `draft`, `pending_sync`, `syncing`, `synced`, `sync_failed` (exclusivo do armazenamento local no aparelho).
- **Confirmação comunitária (M7):** `still_exists` e `suggest_resolved` usam ID determinístico `{incidentId}_{userId}_{type}` com transação atômica protegida por `firestore.rules`, garantindo máximo de 1 por tipo/usuário/ocorrência e **nunca** alterando o `IncidentStatus` administrativo.
- **Órgãos públicos (M7):** Direcionamento transparente aos canais oficiais verificados de Salvador (Fala Salvador 156 / Salvador Digital, CODESAL 199, LIMPURB, TRANSALVADOR, SEMAN/SUCOP, SEDUR, EMBASA), sem simular abertura automática de protocolos governamentais.
- **Autorização e Auditoria (M8):** Login social atribui apenas perfil `citizen`. Papéis administrativos dependem de registro em `/admins/{uid}` e toda alteração administrativa grava trilha imutável em `/incident_status_history` e `/audit_logs`.
- **Consultas Geográficas Paginadas (M4):** O mapa consulta ocorrências em `/incidents` por faixa de latitude visível com `.limit(150)` e filtro complementar em memória (longitude, categoria, status), evitando índices compostos frágeis e raspagem total da base.
- **Revisão Humana Obrigatória (M5):** A IA apenas preenche sugestões editáveis (`AiAnalysisDraft`); nenhuma ocorrência é enviada ao Firestore sem confirmação explícita do cidadão na `IncidentReviewScreen` usando chave de idempotência (`inc_<uuid>`).
- **Resiliência Offline e Retenção (M6):** Rascunhos confirmados sem rede ficam enfileirados em `pending_sync` com backoff exponencial e mesma `idempotencyKey`, e mídias locais sincronizadas ou expiradas são removidas por `runRetentionCleanup`.

## 4. Plano para o Próximo Marco (M10 — Publicação)

1. Confirmar prontidão final de produção (identidade, regras publicadas, build sem erros e checklist de qualidade concluído).
2. Entregar ao usuário o link de compartilhamento e instruções passo a passo para exportar ZIP, enviar ao GitHub ou gerar APK/AAB pelo AI Studio.



