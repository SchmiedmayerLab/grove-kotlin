//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.markdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.grovealliance.testing.screenshot.ScreenshotTest
import org.grovealliance.ui.GroveCard
import org.grovealliance.ui.theme.Spacings
import org.grovealliance.ui.theme.TextStyles
import org.grovealliance.ui.theme.bold
import org.junit.Test

@Suppress("MaxLineLength")
class MarkdownDocumentViewScreenshotTest : ScreenshotTest() {

    @Test
    fun `MarkdownDocumentView rich document screenshot`() {
        screenshot {
            MarkdownDocumentView(
                modifier = Modifier.fillMaxSize(),
                document = MarkdownDocument.process(
                    """
                    ---
                    title: Study Information
                    version: 1.0.0
                    ---
                    # Welcome
                    This paragraph includes **bold emphasis**, *italic emphasis*, and a website: https://example.org.

                    ## What to expect
                    Review each section carefully before continuing.
                    - A bullet item with www.example.org/details
                        - A nested bullet item
                            - A deeply nested bullet item
                    * Another bullet item with support@example.org
                    + A final bullet item with +1 234 567 89

                    ### Ordered steps
                    1. Read the study information
                        a. Review the nested detail
                    2. Ask questions if anything is unclear
                    a. Confirm your choices
                    b. Continue when ready

                    #### Privacy note
                    Your participation is voluntary and you can stop sharing data at any time. The study team only uses
                    the information you choose to provide for approved research activities. If you change your mind,
                    contact the study team or update your preferences in the app.

                    ##### Contact
                    Email support@example.org with questions about the document.
                    """.trimIndent()
                ),
            )
        }
    }

    @Test
    fun `MarkdownDocumentView custom element screenshot`() {
        screenshot {
            MarkdownDocumentView(
                modifier = Modifier.fillMaxSize(),
                document = MarkdownDocument.process(
                    text = """
                        ---
                        title: Study Update
                        ---
                        # Before you continue
                        Please review the notice below before moving to the next section.

                        <notice id="data-sharing" title="Data sharing reminder">
                        You can **pause sharing** at any time. Email support@example.org if you need help changing your preferences.
                        </>

                        ## Next steps
                        - Review your current preferences
                        - Continue when everything looks correct
                    """.trimIndent(),
                    customElementNames = setOf("notice"),
                ),
                elementContent = { element ->
                    NoticeElement(element = element)
                },
            )
        }
    }

    @Test
    fun `MarkdownDocumentView tables screenshot`() {
        screenshot {
            MarkdownDocumentView(
                modifier = Modifier.fillMaxSize(),
                document = MarkdownDocument.process(
                    """
                    Blood pressure is recorded as two numbers:

                    | Category             | Systolic (mmHg) | Diastolic (mmHg) |
                    | :------------------- | :-------------: | :--------------: |
                    | Normal               | <120            | <80              |
                    | Elevated             | 120–129         | <80              |
                    | Hypertension stage 1 | 130–139         | 80–89            |
                    | Hypertension stage 2 | ≥140            | ≥90              |

                    Our scoring system helps you see your risk at a glance:

                    | Range      | Score | What it means                                                    |
                    | :--------: | :---: | :--------------------------------------------------------------- |
                    | <55 mg/dL  | 100   | Excellent; maximal protection, especially for high-risk patients |
                    | 55–69      | 90    | Very good; ideal for heart disease patients                      |
                    | ≥190 mg/dL | 0     | **Severe risk**; urgent action needed                            |

                    Lowering LDL through diet and exercise helps reduce cardiovascular risk.
                    """.trimIndent()
                ),
            )
        }
    }

    @Composable
    private fun NoticeElement(element: MarkdownBlock.Element) {
        GroveCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(Spacings.medium),
                verticalArrangement = Arrangement.spacedBy(Spacings.small),
            ) {
                element.attribute("title")?.let { title ->
                    Text(
                        text = title,
                        style = TextStyles.titleMedium.bold(),
                    )
                }
                val text = element.content.filterIsInstance<MarkdownBlock.Element.Content.Text>()
                    .joinToString(separator = "\n\n") { it.text }
                MarkdownTextBlock(text = text)
            }
        }
    }
}
