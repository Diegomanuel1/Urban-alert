package com.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.IncidentStatus
import com.example.core.model.Severity
import com.example.feature.foundation.FoundationShellScreen
import com.example.feature.incidents.DetailBadge
import com.example.feature.incidents.severityAccent
import com.example.feature.incidents.severityVector
import com.example.feature.incidents.statusAccent
import com.example.feature.incidents.statusVector
import com.example.feature.privacy.PrivacyAndMediaSafetyScreen
import com.example.ui.theme.UrbanAlertTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ComposeUiRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `foundation shell screen renders Salvador pilot badge and categories`() {
        composeRule.setContent {
            UrbanAlertTheme {
                FoundationShellScreen()
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Urban Alert AI").assertIsDisplayed()
        composeRule.onNodeWithTag("pilot_city_badge").assertIsDisplayed()
        composeRule.onNodeWithText("Salvador, BA").assertIsDisplayed()
        composeRule.onNodeWithText("Mapa público de Salvador ativo").assertIsDisplayed()
        composeRule.onNodeWithTag("foundation_scroll_list")
            .performScrollToNode(hasText("Categorias urbanas previstas"))
        composeRule.onNodeWithText("Categorias urbanas previstas").assertIsDisplayed()
    }

    @Test
    fun `privacy screen renders Photo Picker action and LGPD pillars`() {
        composeRule.setContent {
            UrbanAlertTheme {
                PrivacyAndMediaSafetyScreen()
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Privacidade e Mídias (LGPD)").assertIsDisplayed()
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Selecionar foto para sanitizar"))
        composeRule.onNodeWithText("Selecionar foto para sanitizar").assertIsDisplayed()
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("1. Minimização de dados e EXIF"))
        composeRule.onNodeWithText("1. Minimização de dados e EXIF").assertIsDisplayed()
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun `severity and status badges always render accessible icon description and visible text`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        composeRule.setContent {
            UrbanAlertTheme {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Severity.entries.forEach { severity ->
                            DetailBadge(
                                label = context.getString(severity.labelRes),
                                icon = severityVector(severity),
                                accent = severityAccent(severity),
                                contentDescription = "Gravidade ${severity.wireValue}",
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IncidentStatus.entries.forEach { status ->
                            DetailBadge(
                                label = context.getString(status.labelRes),
                                icon = statusVector(status),
                                accent = statusAccent(status),
                                contentDescription = "Status ${status.wireValue}",
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        Severity.entries.forEach { severity ->
            composeRule.onNodeWithText(context.getString(severity.labelRes)).assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Gravidade ${severity.wireValue}").assertIsDisplayed()
        }
        IncidentStatus.entries.forEach { status ->
            composeRule.onNodeWithText(context.getString(status.labelRes)).assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Status ${status.wireValue}").assertIsDisplayed()
        }
    }
}
