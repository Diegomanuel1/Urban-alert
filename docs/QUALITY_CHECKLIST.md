# Relatório de Qualidade e Validação — Urban Alert AI (M9)

Este documento registra com transparência o que foi **verificado automaticamente** neste ambiente de desenvolvimento e o que exige **validação interativa manual** no emulador em streaming ou dispositivo físico.

---

## 1. Verificações Automatizadas Executadas

| Área | Escopo Validado | Ferramenta / Suíte |
| --- | --- | --- |
| **Compilação & KSP** | Kotlin, Jetpack Compose, Material 3, Room KSP codegen e recursos Android | `compile_applet` / Gradle |
| **Análise Estática (Lint)** | Manifest, permissões, recursos não traduzidos/ausentes e acessibilidade básica | `lint_applet` |
| **Domínio & Segurança** | Remoção de EXIF/GPS (`MediaSanitizer`), validação de coordenadas/textos e sanitização contra *prompt injection* (`InputValidator`), chaves UUID (`IdempotencyKeyGenerator`) | `ExampleRobolectricTest` |
| **Mapas & Clusterização** | Agrupamento espacial por zoom (`IncidentClusterer`) e payload de criação seguro (`IncidentDocument`) | `ExampleRobolectricTest` |
| **Agente IA & Offline** | Parser JSON estruturado (`IncidentAiAnalyzer`), fallback manual (`AgentRunner`), persistência SQLite Room + `SyncEngine` com *backoff* e limpeza de mídia local | `ExampleRobolectricTest` |
| **Comunidade, Órgãos & Admin** | Mapeamento de órgãos públicos de Salvador por categoria, unicidade de confirmações comunitárias e cálculo de relatórios/métricas (`AdminRepository.buildReport`) | `ExampleRobolectricTest` |
| **Interface Compose & Acessibilidade** | Renderização real de telas (`FoundationShellScreen`, `PrivacyAndMediaSafetyScreen`) e verificação de ícone + texto para todos os valores de `Severity` e `IncidentStatus` | `ComposeUiRobolectricTest` |
| **Regras do Firestore (Zero-Trust)** | 8 cenários no emulador local: isolamento de `/profiles`, consulta paginada pública (`limit <= 200`), bloqueio de escalonamento de papel, órgãos públicos, transações de confirmação sem duplicidade e sem alteração de `status`, imutabilidade de `/audit_logs` e `/incident_status_history`, isolamento de `/ai_telemetry` | `firestore.test.js` |
| **Auditoria de Segurança & Google Play** | Zero ocorrências de `GEMINI_API_KEY`, `signInAnonymously`, `signInWithEmailAndPassword` ou `FirebaseFirestore.getInstance()` sem ID; `AndroidManifest.xml` sem `CAMERA`, `READ_EXTERNAL_STORAGE` ou `READ_MEDIA_*` | `grep` + revisão de Manifest |

---

## 2. Roteiro de Validação Manual (Emulador em Streaming / Dispositivo Real)

Como o ambiente do agente não possui acesso direto ao `adb` nem a hardware físico, recomenda-se conferir manualmente os seguintes fluxos interativos na prévia do aplicativo:

1. **Mapa público como visitante:** abrir o mapa de Salvador sem login, mover/aproximar para verificar agrupamento de pinos, aplicar filtros de categoria/status e buscar um bairro na barra de pesquisa.
2. **Captura de mídia sem permissões invasivas:** testar **Galeria** (Android Photo Picker) e **Câmera** (via `FileProvider`) na tela de privacidade ou no assistente de ocorrência, confirmando que o EXIF/GPS é removido.
3. **Análise multimodal com Gemini:** enviar foto + descrição no assistente para conferir sugestão de categoria, gravidade, confiança e revisão editável antes de publicar.
4. **Login Google real (Credential Manager):** entrar com sua conta Google na aba **Conta**, publicar uma ocorrência e testar a confirmação única *"Confirmar que ainda existe"* na tela de detalhes.
5. **Canais públicos e modo offline:** testar abertura do portal oficial / discador `156` (ou `199` na Defesa Civil), além de simular modo avião para confirmar salvamento na fila SQLite e envio automático ao reconectar.
