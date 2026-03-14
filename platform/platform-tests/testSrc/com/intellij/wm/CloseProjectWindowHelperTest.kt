// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.wm

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.CloseProjectWindowHelper
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.runInEdtAndWait
import org.junit.ClassRule
import org.junit.Test

class CloseProjectWindowHelperTest {

  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  @Test
  fun `closing app window without project leads to exit`() {
    val helper = TestCloseProjectWindowHelper()

    runInEdtAndWait {
      helper.windowClosing(null)
    }

    assertThat(helper.wasQuitAppCalled).isTrue()
    assertThat(helper.wasCloseProjectAndShowNoProjectFrameIfNeededCalled).isFalse()
  }

  @Test
  fun `closing project window closes project using no-project flow`() {
    val helper = TestCloseProjectWindowHelper()
    val project = projectRule.project

    runInEdtAndWait {
      helper.windowClosing(project)
    }

    assertThat(helper.wasQuitAppCalled).isFalse()
    assertThat(helper.wasCloseProjectAndShowNoProjectFrameIfNeededCalled).isTrue()
  }
}

open class TestCloseProjectWindowHelper : CloseProjectWindowHelper() {
  var wasQuitAppCalled = false
    private set

  var wasCloseProjectAndShowNoProjectFrameIfNeededCalled = false
    private set

  override fun quitApp() {
    assertThat(wasQuitAppCalled).isFalse()
    wasQuitAppCalled = true
  }

  override fun closeProjectAndShowNoProjectFrameIfNeeded(project: Project?) {
    assertThat(wasCloseProjectAndShowNoProjectFrameIfNeededCalled).isFalse()
    wasCloseProjectAndShowNoProjectFrameIfNeededCalled = true
  }
}
