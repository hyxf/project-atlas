package com.github.hyxf.projectmanager

import com.github.hyxf.projectmanager.feature.ui.ProjectManagerPanel
import com.github.hyxf.projectmanager.settings.ProjectManagerConfigurable
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

class ProjectManagerUiTest {
    private lateinit var fixture: CodeInsightTestFixture

    @BeforeTest
    fun setUp() {
        val factory = IdeaTestFixtureFactory.getFixtureFactory()
        fixture = factory.createCodeInsightFixture(factory.createFixtureBuilder("ProjectManagerUiTest").fixture)
        fixture.setUp()
    }

    @AfterTest
    fun tearDown() = fixture.tearDown()

    @Test
    fun `tool window panel and settings can be created`() {
        val panel = ProjectManagerPanel(fixture.project)
        val configurable = ProjectManagerConfigurable()

        assertNotNull(panel)
        assertNotNull(configurable.createComponent())

        configurable.disposeUIResources()
    }
}
