/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.status;

import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import java.util.Map;

/**
 * @author Konstantin Kolosovsky.
 */
public enum StatusType {

  // currently used to represent some not used status types from SVNKit
  UNUSED("unused"),

  INAPPLICABLE("inapplicable"),
  UNKNOWN("unknown"),
  UNCHANGED("unchanged"),
  MISSING("missing"),
  OBSTRUCTED("obstructed"),
  CHANGED("changed"),
  MERGED("merged"),
  CONFLICTED("conflicted"),

  STATUS_NONE("none"),
  STATUS_NORMAL("normal", ' '),
  STATUS_MODIFIED("modified", 'M'),
  STATUS_ADDED("added", 'A'),
  STATUS_DELETED("deleted", 'D'),
  STATUS_UNVERSIONED("unversioned", '?'),
  STATUS_MISSING("missing", '!'),
  STATUS_REPLACED("replaced", 'R'),
  STATUS_CONFLICTED("conflicted", 'C'),
  STATUS_OBSTRUCTED("obstructed", '~'),
  STATUS_IGNORED("ignored", 'I'),
  // directory is incomplete - checkout or update was interrupted
  STATUS_INCOMPLETE("incomplete", '!'),
  STATUS_EXTERNAL("external", 'X');

  @NotNull private static final Map<String, StatusType> ourAllStatusTypes = ContainerUtil.newHashMap();

  static {
    for (StatusType action : StatusType.values()) {
      register(action);
    }
  }

  private String myName;
  private char myCode;

  StatusType(String name) {
    this(name, ' ');
  }

  StatusType(String name, char code) {
    myName = name;
    myCode = code;
  }

  public char getCode() {
    return myCode;
  }

  public String toString() {
    return myName;
  }

  private static void register(@NotNull StatusType action) {
    ourAllStatusTypes.put(action.myName, action);
  }

  @NotNull
  public static StatusType from(@NotNull SVNStatusType type) {
    return ObjectUtils.notNull(ourAllStatusTypes.get(type.toString()), UNUSED);
  }
}
