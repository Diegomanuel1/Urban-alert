const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_ssa_123";
const BOB_UID = "bob_ssa_456";
const ADMIN_UID = "admin_ssa_999";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated visitor: cannot read or write profiles, can read public categories", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Silva",
      email: "alice@example.com",
      homeCityCode: "ssa-ba",
      role: "citizen",
      acceptedPrivacyVersion: "lgpd-v1",
      createdAt: new Date(Date.now() - 5000),
      updatedAt: new Date(Date.now() - 5000),
    });
    await db.collection("categories").doc("cat_pothole").set({
      slug: "buracos-em-vias",
      title: "Buracos em vias",
      description: "Pavimento danificado em via pública.",
      defaultSeverity: "high",
      active: true,
      createdAt: new Date(Date.now() - 5000),
      updatedAt: new Date(Date.now() - 5000),
    });
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("profiles").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("profiles").get());
  await assertFails(
    unauthDb.collection("profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Hacker",
      homeCityCode: "ssa-ba",
      role: "citizen",
      acceptedPrivacyVersion: "lgpd-v1",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(unauthDb.collection("categories").get());
  await assertSucceeds(unauthDb.collection("categories").doc("cat_pothole").get());
});

test("Citizen profile: owner can create as citizen, update allowed fields, and delete (LGPD); cannot escalate role", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertFails(
    aliceDb.collection("profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      homeCityCode: "ssa-ba",
      role: "super_admin",
      acceptedPrivacyVersion: "lgpd-v1",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Silva",
      email: "alice@salvador.ba.gov.br",
      homeCityCode: "ssa-ba",
      role: "citizen",
      acceptedPrivacyVersion: "lgpd-v1",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("profiles").doc(ALICE_UID).update({
      displayName: "Alice S.",
      homeCityCode: "ssa-ba",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(
    aliceDb.collection("profiles").doc(ALICE_UID).update({
      role: "operator",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(aliceDb.collection("profiles").doc(ALICE_UID).delete());
});

test("Cross-user isolation: Bob cannot read, update, or delete Alice's profile", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Silva",
      email: "alice@example.com",
      homeCityCode: "ssa-ba",
      role: "citizen",
      acceptedPrivacyVersion: "lgpd-v1",
      createdAt: new Date(Date.now() - 5000),
      updatedAt: new Date(Date.now() - 5000),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("profiles").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("profiles").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(bobDb.collection("profiles").doc(ALICE_UID).delete());
});

test("Public incidents: visitor can run bounded bounding-box query without login; cannot create or scrape unbounded", async () => {
  const created = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("incidents").doc("inc_barra_1").set({
      authorId: ALICE_UID,
      cityCode: "ssa-ba",
      categorySlug: "buracos-em-vias",
      title: "Buraco profundo na Av. Oceânica",
      description: "Asfalto cedendo próximo à faixa de pedestres na Barra.",
      severity: "high",
      status: "open",
      latitude: -13.0085,
      longitude: -38.5326,
      addressLabel: "Barra, Salvador - BA",
      stillExistsCount: 2,
      suggestResolvedCount: 0,
      idempotencyKey: "11111111-1111-4111-8111-111111111111",
      createdAt: created,
      updatedAt: created,
    });
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  // Consulta paginada por faixa de latitude espelhando IncidentRepository.kt funciona para visitante
  await assertSucceeds(
    unauthDb
      .collection("incidents")
      .where("latitude", ">=", -13.05)
      .where("latitude", "<=", -12.90)
      .limit(150)
      .get()
  );
  await assertSucceeds(unauthDb.collection("incidents").doc("inc_barra_1").get());

  // Consulta sem limite (tentativa de raspagem da cidade inteira) deve falhar
  await assertFails(unauthDb.collection("incidents").get());

  // Visitante não autenticado não pode criar ocorrência
  await assertFails(
    unauthDb.collection("incidents").doc("inc_unauth").set({
      authorId: ALICE_UID,
      cityCode: "ssa-ba",
      categorySlug: "buracos-em-vias",
      title: "Teste sem login",
      description: "Descrição de teste sem autenticação prévia.",
      severity: "low",
      status: "open",
      latitude: -12.97,
      longitude: -38.50,
      stillExistsCount: 0,
      suggestResolvedCount: 0,
      idempotencyKey: "22222222-2222-4222-8222-222222222222",
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("Authenticated incidents: owner creates valid open incident, Bob cannot edit, Admin can transition status", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  // Não pode criar com coordenada nula (0,0) nem status já 'resolved'
  await assertFails(
    aliceDb.collection("incidents").doc("inc_invalid_coord").set({
      authorId: ALICE_UID,
      cityCode: "ssa-ba",
      categorySlug: "alagamentos",
      title: "Ponto inválido",
      description: "Teste de coordenada nula rejeitada.",
      severity: "high",
      status: "open",
      latitude: 0.0,
      longitude: 0.0,
      stillExistsCount: 0,
      suggestResolvedCount: 0,
      idempotencyKey: "33333333-3333-4333-8333-333333333333",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("incidents").doc("inc_pituba_1").set({
      authorId: ALICE_UID,
      cityCode: "ssa-ba",
      categorySlug: "iluminacao-publica",
      title: "Poste apagado na Pituba",
      description: "Luminária queimada há três dias deixando a esquina escura.",
      severity: "medium",
      status: "open",
      latitude: -13.0012,
      longitude: -38.4591,
      addressLabel: "Pituba, Salvador - BA",
      stillExistsCount: 0,
      suggestResolvedCount: 0,
      idempotencyKey: "44444444-4444-4444-8444-444444444444",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Bob não pode editar ocorrência da Alice
  await assertFails(
    bobDb.collection("incidents").doc("inc_pituba_1").update({
      title: "Alterado por outro usuário",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Alice não pode mudar o próprio status para 'resolved'
  await assertFails(
    aliceDb.collection("incidents").doc("inc_pituba_1").update({
      status: "resolved",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Admin pode atualizar status
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("admins").doc(ADMIN_UID).set({
      role: "municipal_manager",
      organizationId: "salvador-ba",
    });
  });
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  await assertSucceeds(
    adminDb.collection("incidents").doc("inc_pituba_1").update({
      status: "in_progress",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Public agencies: visitors can read, only admins can write", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("admins").doc(ADMIN_UID).set({
      role: "municipal_manager",
      organizationId: "salvador-ba",
    });
  });

  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 2000);

  const agencyPayload = {
    cityCode: "ssa-ba",
    acronym: "SEMAN",
    name: "Secretaria Municipal de Manutenção da Cidade",
    competenceSummary: "Conservação de vias, operação tapa-buracos e drenagem urbana.",
    categorySlugs: ["buracos-em-vias", "problemas-de-drenagem"],
    serviceAreaLabel: "Salvador (BA)",
    officialChannelLabel: "Fala Salvador 156",
    officialPortalUrl: "https://salvadordigital.salvador.ba.gov.br",
    officialPhone: "156",
    lastVerifiedAtLabel: "2026-11-17",
    active: true,
    createdAt: ts,
    updatedAt: ts,
  };

  await assertFails(aliceDb.collection("agencies").doc("agency_seman").set(agencyPayload));
  await assertSucceeds(adminDb.collection("agencies").doc("agency_seman").set(agencyPayload));
  await assertSucceeds(unauthDb.collection("agencies").get());
  await assertSucceeds(unauthDb.collection("agencies").doc("agency_seman").get());
});

test("Community confirmations: atomic +1 once per user/type, blocks duplicates, never alters status", async () => {
  const created = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("incidents").doc("inc_rio_vermelho").set({
      authorId: ALICE_UID,
      cityCode: "ssa-ba",
      categorySlug: "acumulo-de-lixo",
      title: "Acúmulo de lixo próximo à praça",
      description: "Resíduos acumulados na esquina há dois dias.",
      severity: "medium",
      status: "open",
      latitude: -13.0135,
      longitude: -38.4912,
      stillExistsCount: 0,
      suggestResolvedCount: 0,
      idempotencyKey: "55555555-5555-4555-8555-555555555555",
      createdAt: created,
      updatedAt: created,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const incRef = bobDb.collection("incidents").doc("inc_rio_vermelho");
  const stillId = `inc_rio_vermelho_${BOB_UID}_still_exists`;
  const resolvedId = `inc_rio_vermelho_${BOB_UID}_suggest_resolved`;

  // Visitante não autenticado não pode confirmar
  await assertFails(
    unauthDb.collection("incident_confirmations").doc(`inc_rio_vermelho_anon_still_exists`).set({
      incidentId: "inc_rio_vermelho",
      userId: "anon",
      confirmationType: "still_exists",
      cityCode: "ssa-ba",
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Incrementar contador sem criar documento de confirmação no mesmo batch/transação deve falhar
  await assertFails(
    incRef.update({
      stillExistsCount: 1,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Batch/transação válida para still_exists (1x)
  const batch1 = bobDb.batch();
  batch1.set(bobDb.collection("incident_confirmations").doc(stillId), {
    incidentId: "inc_rio_vermelho",
    userId: BOB_UID,
    confirmationType: "still_exists",
    cityCode: "ssa-ba",
    createdAt: new Date(Date.now() - 1000),
  });
  batch1.update(incRef, {
    stillExistsCount: 1,
    updatedAt: new Date(Date.now() - 1000),
  });
  await assertSucceeds(batch1.commit());

  // Tentativa duplicada de still_exists pelo mesmo usuário deve falhar
  const batchDup = bobDb.batch();
  batchDup.set(bobDb.collection("incident_confirmations").doc(stillId), {
    incidentId: "inc_rio_vermelho",
    userId: BOB_UID,
    confirmationType: "still_exists",
    cityCode: "ssa-ba",
    createdAt: new Date(Date.now() - 500),
  });
  batchDup.update(incRef, {
    stillExistsCount: 2,
    updatedAt: new Date(Date.now() - 500),
  });
  await assertFails(batchDup.commit());

  // Tentativa de alterar status junto com suggest_resolved deve falhar
  const batchEscalate = bobDb.batch();
  batchEscalate.set(bobDb.collection("incident_confirmations").doc(resolvedId), {
    incidentId: "inc_rio_vermelho",
    userId: BOB_UID,
    confirmationType: "suggest_resolved",
    cityCode: "ssa-ba",
    createdAt: new Date(Date.now() - 400),
  });
  batchEscalate.update(incRef, {
    suggestResolvedCount: 1,
    status: "resolved",
    updatedAt: new Date(Date.now() - 400),
  });
  await assertFails(batchEscalate.commit());

  // Outro tipo (suggest_resolved) sem alterar status funciona uma única vez
  const batch2 = bobDb.batch();
  batch2.set(bobDb.collection("incident_confirmations").doc(resolvedId), {
    incidentId: "inc_rio_vermelho",
    userId: BOB_UID,
    confirmationType: "suggest_resolved",
    cityCode: "ssa-ba",
    createdAt: new Date(Date.now() - 300),
  });
  batch2.update(incRef, {
    suggestResolvedCount: 1,
    updatedAt: new Date(Date.now() - 300),
  });
  await assertSucceeds(batch2.commit());
});

test("Admin operations: citizen cannot read/write audit_logs or status history; admin writes atomically and immutably", async () => {
  const created = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("admins").doc(ADMIN_UID).set({
      role: "municipal_manager",
      organizationId: "salvador-ba",
    });
    await db.collection("incidents").doc("inc_pelourinho_1").set({
      authorId: ALICE_UID,
      cityCode: "ssa-ba",
      categorySlug: "iluminacao-publica",
      title: "Poste apagado no Pelourinho",
      description: "Luminária queimada na escadaria principal.",
      severity: "high",
      status: "open",
      latitude: -12.9718,
      longitude: -38.5096,
      stillExistsCount: 1,
      suggestResolvedCount: 0,
      idempotencyKey: "66666666-6666-4666-8666-666666666666",
      createdAt: created,
      updatedAt: created,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

  // Cidadão comum não pode ler nem gravar audit_logs ou histórico oficial
  await assertFails(aliceDb.collection("audit_logs").limit(10).get());
  await assertFails(
    aliceDb.collection("audit_logs").doc("log_hack").set({
      action: "incident_status_updated",
      actorUid: ALICE_UID,
      targetType: "incident",
      targetId: "inc_pelourinho_1",
      summary: "Tentativa indevida",
      createdAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("incident_status_history").doc("hist_hack").set({
      incidentId: "inc_pelourinho_1",
      previousStatus: "open",
      newStatus: "resolved",
      reason: "Tentativa sem permissão",
      changedByUid: ALICE_UID,
      createdAt: new Date(Date.now() - 1000),
    })
  );

  // Admin executa transição em lote com histórico e log de auditoria
  const batch = adminDb.batch();
  const ts = new Date(Date.now() - 500);
  batch.update(adminDb.collection("incidents").doc("inc_pelourinho_1"), {
    status: "in_progress",
    updatedAt: ts,
  });
  batch.set(adminDb.collection("incident_status_history").doc("hist_1"), {
    incidentId: "inc_pelourinho_1",
    previousStatus: "open",
    newStatus: "in_progress",
    reason: "Equipe da SEMAN encaminhada ao local.",
    changedByUid: ADMIN_UID,
    createdAt: ts,
  });
  batch.set(adminDb.collection("audit_logs").doc("log_1"), {
    action: "incident_status_updated",
    actorUid: ADMIN_UID,
    targetType: "incident",
    targetId: "inc_pelourinho_1",
    summary: "Status alterado de open para in_progress.",
    createdAt: ts,
  });
  await assertSucceeds(batch.commit());

  await assertSucceeds(adminDb.collection("audit_logs").limit(20).get());
  // Logs são imutáveis até mesmo para admins
  await assertFails(adminDb.collection("audit_logs").doc("log_1").delete());

  // Telemetria de IA: cidadão envia, apenas admin lê
  await assertSucceeds(
    aliceDb.collection("ai_telemetry").doc("tel_1").set({
      userId: ALICE_UID,
      mode: "ai_suggested",
      modelName: "gemini-3.8-flash",
      confidence: 0.87,
      latencyMs: 1420,
      createdAt: ts,
    })
  );
  await assertFails(aliceDb.collection("ai_telemetry").limit(10).get());
  await assertSucceeds(adminDb.collection("ai_telemetry").limit(10).get());
});


