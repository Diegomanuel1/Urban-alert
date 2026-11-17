package com.example.core.config

import com.example.core.model.CityPilotConfig
import com.example.core.model.GeoBoundingBox
import com.example.core.model.GeoCoordinate
import com.example.core.model.PublicAgency
import com.example.core.model.RoadmapMilestone
import com.example.core.model.Severity
import com.example.core.model.UrbanCategory

/**
 * Configuração inicial da cidade-piloto (Salvador, BA) e taxonomia base de categorias.
 * A arquitetura permite adicionar novos municípios sem alterar regras de negócio.
 */
object PilotCityConfig {

    val salvador = CityPilotConfig(
        cityCode = "ssa-ba",
        displayName = "Salvador",
        stateCode = "BA",
        countryCode = "BR",
        center = GeoCoordinate(latitude = -12.9714, longitude = -38.5014),
        initialBounds = GeoBoundingBox(
            north = -12.7800,
            south = -13.0200,
            east = -38.3000,
            west = -38.5500,
        ),
        defaultZoom = 13f,
    )

    val initialCategories: List<UrbanCategory> = listOf(
        UrbanCategory(
            id = "cat_pothole",
            slug = "buracos-em-vias",
            title = "Buracos em vias",
            description = "Afundamentos, cratera no asfalto ou pavimento danificado com risco a veículos e pedestres.",
            defaultSeverityHint = Severity.HIGH,
        ),
        UrbanCategory(
            id = "cat_waste",
            slug = "acumulo-de-lixo",
            title = "Acúmulo de lixo",
            description = "Descarte irregular de resíduos sólidos, entulho ou pontos críticos de coleta.",
            defaultSeverityHint = Severity.MEDIUM,
        ),
        UrbanCategory(
            id = "cat_lighting",
            slug = "iluminacao-publica",
            title = "Iluminação pública",
            description = "Poste apagado, luminária quebrada ou trecho escuro que compromete a segurança.",
            defaultSeverityHint = Severity.MEDIUM,
        ),
        UrbanCategory(
            id = "cat_leak",
            slug = "vazamentos",
            title = "Vazamentos",
            description = "Vazamento de água tratada ou esgoto exposto em via pública.",
            defaultSeverityHint = Severity.HIGH,
        ),
        UrbanCategory(
            id = "cat_flooding",
            slug = "alagamentos",
            title = "Alagamentos",
            description = "Acúmulo severo de água impedindo circulação de pedestres ou transporte.",
            defaultSeverityHint = Severity.CRITICAL,
        ),
        UrbanCategory(
            id = "cat_drainage",
            slug = "problemas-de-drenagem",
            title = "Drenagem",
            description = "Bueiro entupido, boca de lobo sem tampa ou galeria pluvial danificada.",
            defaultSeverityHint = Severity.HIGH,
        ),
        UrbanCategory(
            id = "cat_signage",
            slug = "sinalizacao-danificada",
            title = "Sinalização",
            description = "Placa caída, ilegível, vandalizada ou faixa de pedestres apagada.",
            defaultSeverityHint = Severity.MEDIUM,
        ),
        UrbanCategory(
            id = "cat_traffic_light",
            slug = "semaforos-quebrados",
            title = "Semáforos",
            description = "Semáforo desligado, piscante irregular ou com lâmpadas queimadas.",
            defaultSeverityHint = Severity.CRITICAL,
        ),
        UrbanCategory(
            id = "cat_accessibility",
            slug = "barreiras-de-acessibilidade",
            title = "Acessibilidade",
            description = "Calçada obstruída, ausência de rampa ou piso tátil danificado.",
            defaultSeverityHint = Severity.MEDIUM,
        ),
    )

    /**
     * Catálogo inicial de órgãos e canais públicos verificados para Salvador (BA).
     * Utilizado como base informativa transparente (sem simular abertura de protocolo oficial).
     */
    val salvadorAgencies: List<PublicAgency> = listOf(
        PublicAgency(
            id = "agency_seman_salvador",
            cityCode = "ssa-ba",
            acronym = "SEMAN / SUCOP",
            name = "Secretaria Municipal de Manutenção da Cidade e Obras Públicas",
            competenceSummary = "Operação tapa-buracos, pavimentação, recuperação de passeios, manutenção de galerias de drenagem e iluminação pública em vias de Salvador.",
            categorySlugs = listOf("buracos-em-vias", "problemas-de-drenagem", "iluminacao-publica"),
            serviceAreaLabel = "Salvador (BA)",
            officialChannelLabel = "Fala Salvador 156 / Salvador Digital",
            officialPortalUrl = "https://salvadordigital.salvador.ba.gov.br",
            officialPhone = "156",
            lastVerifiedAtLabel = "2026-11-17",
        ),
        PublicAgency(
            id = "agency_limpurb_salvador",
            cityCode = "ssa-ba",
            acronym = "LIMPURB",
            name = "Empresa de Limpeza Urbana de Salvador",
            competenceSummary = "Coleta de resíduos sólidos, varrição, remoção de entulho e atendimento a pontos de descarte irregular em áreas públicas.",
            categorySlugs = listOf("acumulo-de-lixo"),
            serviceAreaLabel = "Salvador (BA)",
            officialChannelLabel = "Fala Salvador 156 / Salvador Digital",
            officialPortalUrl = "https://salvadordigital.salvador.ba.gov.br",
            officialPhone = "156",
            lastVerifiedAtLabel = "2026-11-17",
        ),
        PublicAgency(
            id = "agency_codesal_salvador",
            cityCode = "ssa-ba",
            acronym = "CODESAL",
            name = "Defesa Civil de Salvador",
            competenceSummary = "Monitoramento e atendimento a emergências por alagamentos, deslizamentos, desabamentos e risco geológico.",
            categorySlugs = listOf("alagamentos", "problemas-de-drenagem"),
            serviceAreaLabel = "Salvador (BA)",
            officialChannelLabel = "Defesa Civil 199 / Fala Salvador 156",
            officialPortalUrl = "https://salvadordigital.salvador.ba.gov.br",
            officialPhone = "199",
            lastVerifiedAtLabel = "2026-11-17",
        ),
        PublicAgency(
            id = "agency_transalvador",
            cityCode = "ssa-ba",
            acronym = "TRANSALVADOR",
            name = "Superintendência de Trânsito de Salvador",
            competenceSummary = "Gestão de semáforos, sinalização viária vertical/horizontal e ordenamento do tráfego urbano.",
            categorySlugs = listOf("sinalizacao-danificada", "semaforos-quebrados"),
            serviceAreaLabel = "Salvador (BA)",
            officialChannelLabel = "Fala Salvador 156 / Salvador Digital",
            officialPortalUrl = "https://salvadordigital.salvador.ba.gov.br",
            officialPhone = "156",
            lastVerifiedAtLabel = "2026-11-17",
        ),
        PublicAgency(
            id = "agency_sedur_salvador",
            cityCode = "ssa-ba",
            acronym = "SEDUR",
            name = "Secretaria Municipal de Desenvolvimento e Urbanismo",
            competenceSummary = "Fiscalização de posturas municipais, obstruções e padrões de acessibilidade em calçadas e espaços urbanos.",
            categorySlugs = listOf("barreiras-de-acessibilidade"),
            serviceAreaLabel = "Salvador (BA)",
            officialChannelLabel = "Fala Salvador 156 / Salvador Digital",
            officialPortalUrl = "https://salvadordigital.salvador.ba.gov.br",
            officialPhone = "156",
            lastVerifiedAtLabel = "2026-11-17",
        ),
        PublicAgency(
            id = "agency_embasa_ba",
            cityCode = "ssa-ba",
            acronym = "EMBASA",
            name = "Empresa Baiana de Águas e Saneamento",
            competenceSummary = "Concessionária estadual responsável por redes de abastecimento de água tratada, esgotamento sanitário e reparos de vazamentos na via.",
            categorySlugs = listOf("vazamentos"),
            serviceAreaLabel = "Salvador e Região Metropolitana (BA)",
            officialChannelLabel = "Portal oficial EMBASA",
            officialPortalUrl = "https://www.embasa.ba.gov.br",
            officialPhone = null,
            lastVerifiedAtLabel = "2026-11-17",
        ),
    )

    fun findAgenciesForIncident(
        cityCode: String,
        categorySlug: String,
        agencies: List<PublicAgency> = salvadorAgencies,
    ): List<PublicAgency> {
        val activeForCity = agencies.filter { it.active && it.cityCode == cityCode }
        val exact = activeForCity.filter { categorySlug in it.categorySlugs }
        if (exact.isNotEmpty()) return exact
        // Fallback institucional da cidade-piloto (Canal central 156) se nenhuma categoria bater
        return activeForCity.take(1)
    }

    val roadmapPreview: List<RoadmapMilestone> = listOf(
        RoadmapMilestone(
            code = "M1",
            title = "Fundação",
            summary = "Identidade, tema acessível, modelos de domínio e documentação.",
            isCurrent = false,
            isCompleted = true,
        ),
        RoadmapMilestone(
            code = "M2",
            title = "Banco e Identidade",
            summary = "Firebase Firestore, regras zero-trust e Login Google via Credential Manager.",
            isCurrent = false,
            isCompleted = true,
        ),
        RoadmapMilestone(
            code = "M3",
            title = "Mídias e Segurança",
            summary = "Photo Picker seguro, remoção de EXIF, validação e LGPD.",
            isCurrent = false,
            isCompleted = true,
        ),
        RoadmapMilestone(
            code = "M4",
            title = "Mapas",
            summary = "Mapa nativo centrado em Salvador, marcadores e consulta geográfica.",
            isCurrent = false,
            isCompleted = true,
        ),
        RoadmapMilestone(
            code = "M5",
            title = "Agente e IA",
            summary = "Fluxo conversacional com Gemini multimodal e revisão cidadã obrigatória.",
            isCurrent = false,
            isCompleted = true,
        ),
        RoadmapMilestone(
            code = "M6",
            title = "Offline",
            summary = "Room SQLite, rascunhos locais, fila de sincronização e idempotência.",
            isCurrent = false,
            isCompleted = true,
        ),
        RoadmapMilestone(
            code = "M7",
            title = "Comunidade e Órgãos",
            summary = "Confirmações comunitárias únicas, canais públicos verificados e zero alteração automática de status.",
            isCurrent = false,
            isCompleted = true,
        ),
        RoadmapMilestone(
            code = "M8",
            title = "Painel Administrativo",
            summary = "Triagem autorizada, trilha de status, categorias, órgãos, métricas da IA e auditoria.",
            isCurrent = false,
            isCompleted = true,
        ),
        RoadmapMilestone(
            code = "M9",
            title = "Qualidade",
            summary = "Bateria final de testes unitários, Compose UI, Robolectric, regras de segurança e Lint.",
            isCurrent = false,
            isCompleted = true,
        ),
        RoadmapMilestone(
            code = "M10",
            title = "Publicação",
            summary = "Checklist final de entrega, instruções de exportação APK/AAB e link de compartilhamento.",
            isCurrent = false,
            isCompleted = true,
        ),
    )
}
