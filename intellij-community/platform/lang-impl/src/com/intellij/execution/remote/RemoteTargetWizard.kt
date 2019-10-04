// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.remote

import com.intellij.ide.wizard.AbstractWizardEx
import com.intellij.ide.wizard.AbstractWizardStepEx
import com.intellij.openapi.project.Project

class RemoteTargetWizard(project: Project, title: String, val subject: RemoteTargetConfiguration, steps: List<AbstractWizardStepEx>)
  : AbstractWizardEx(title, project, steps) {
  override fun getHelpId(): String? = "reference.remote.target.wizard.${subject.typeId}"
}