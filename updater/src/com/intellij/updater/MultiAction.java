/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.updater;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class MultiAction extends PatchAction {

  // Only used on patch creation
  protected transient File myOlderDir;
  List<PatchAction> myActions;

  private transient PatchAction mySelectedAction;

  public MultiAction(Patch patch, String path, long checksum) {
    super(patch, path, checksum);
    myActions = new ArrayList<PatchAction>();
  }

  public MultiAction(Patch patch, DataInputStream in) throws IOException {
    super(patch, in);
    myActions = patch.readActions(in);
  }

  @Override
  public void write(DataOutputStream out) throws IOException {
    super.write(out);
    myPatch.writeActions(out, myActions);
  }

  @Override
  protected void doBuildPatchFile(File toFile, MultiZipFile.OutputStream patchOutput) throws IOException {
    for (int i = 0; i < myActions.size(); i++) {
      PatchAction action = myActions.get(i);
      action.doBuildPatchFile(toFile, patchOutput.getVariant(i));
    }
  }

  @Override
  public ValidationResult validate(File toDir) throws IOException {
    // A MultiAction chooses at validation time which one of its actions will be used.
    // If one of its actions passes validation, that means that in can safely be applied,
    // we select it in mySelectedAction, and use it later on in the process (backup, apply, revert).
    ValidationResult result = null;
    for (PatchAction action : myActions) {
      result = action.validate(toDir);
      if (result == null) {
        mySelectedAction = action;
        break;
      }
    }
    return result;
  }

  @Override
  protected void doApply(MultiZipFile patchFile, File backupDir, File toFile) throws IOException {
    int index = myActions.indexOf(mySelectedAction);
    if (index == -1) throw new IllegalStateException("Invalid action selected");
    mySelectedAction.doApply(patchFile.getVariant(index), backupDir, toFile);
  }

  @Override
  protected void doBackup(File toFile, File backupFile) throws IOException {
    if (mySelectedAction == null) throw new IllegalStateException("No action selected");
    mySelectedAction.doBackup(toFile, backupFile);

  }

  @Override
  protected void doRevert(File toFile, File backupFile) throws IOException {
    if (mySelectedAction == null) throw new IllegalStateException("No action selected");
    mySelectedAction.doRevert(toFile, backupFile);
  }

  @Override
  public PatchAction merge(PatchAction other) {
    addAction(other);
    return this;
  }

  public void addAction(PatchAction action) {
    myActions.add(action);
  }

}
