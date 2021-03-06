/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.folding.impl.actions;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.folding.impl.FoldingUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;

public class CollapseRegionAction extends EditorAction {
  public CollapseRegionAction() {
    super(new EditorActionHandler() {
      @Override
      public void execute(final Editor editor, DataContext dataContext) {
        CodeFoldingManager foldingManager = CodeFoldingManager.getInstance(editor.getProject());
        foldingManager.updateFoldRegions(editor);

        final int line = editor.getCaretModel().getLogicalPosition().line;

        Runnable processor = new Runnable() {
          @Override
          public void run() {
            FoldRegion region = FoldingUtil.findFoldRegionStartingAtLine(editor, line);
            if (region != null && region.isExpanded()){
              region.setExpanded(false);
            }
            else {
              int offset = editor.getCaretModel().getOffset();
              FoldRegion[] regions = FoldingUtil.getFoldRegionsAtOffset(editor, offset);
              for (FoldRegion region1 : regions) {
                if (region1.isExpanded()) {
                  region1.setExpanded(false);
                  break;
                }
              }
            }
          }
        };
        editor.getFoldingModel().runBatchFoldingOperation(processor);
      }

      @Override
      public boolean isEnabled(Editor editor, DataContext dataContext) {
        return super.isEnabled(editor, dataContext) && editor.getProject() != null;
      }

    });
  }
}
