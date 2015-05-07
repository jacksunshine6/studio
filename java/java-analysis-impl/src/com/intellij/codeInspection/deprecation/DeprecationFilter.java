/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.intellij.codeInspection.deprecation;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Filter which allows plugins to customize the inspection results for deprecated elements
 */
public abstract class DeprecationFilter {
  public static final ExtensionPointName<DeprecationFilter> EP_NAME = new ExtensionPointName<DeprecationFilter>("com.intellij.deprecationFilter");

  /**
   * For a deprecated element, returns true to remove the deprecation warnings for this element,
   * or false to not exclude the element (e.g. show it as deprecated).
   * <p/>
   * This is for example used in Android when an API is marked as deprecated in a particular version
   * of Android, but the current project declares that it supports an older version of Android where
   * the API is not yet deprecated.
   *
   * @param deprecatedElement the deprecated element (e.g. the deprecated class, method or field)
   * @param referenceElement  the reference to that deprecated element
   * @param symbolName        the user visible symbol name
   * @return true if we should hide this deprecation
   */
  public boolean isExcluded(@NotNull PsiElement deprecatedElement, @NotNull PsiElement referenceElement, @Nullable String symbolName) {
    return false;
  }

  /**
   * Optionally changes the deprecation message shown for a given element.
   * For example, a plugin can add knowledge it has about the deprecation, such as
   * a suggested replacement.
   *
   * @param deprecatedElement the deprecated element (e.g. the deprecated class, method or field)
   * @param referenceElement  the reference to that deprecated element
   * @param symbolName        the user visible symbol name
   * @param defaultMessage    the default message to be shown for this deprecation
   * @return a suggested replacement message (which is often the default message with
   * some additional details concatenated), or just the passed in original message to leave it alone
   */
  @NotNull
  public String getDeprecationMessage(@NotNull PsiElement deprecatedElement,
                                      @NotNull PsiElement referenceElement,
                                      @Nullable String symbolName,
                                      @NotNull String defaultMessage) {
    return defaultMessage;
  }

  /**
   * Returns optional quick fixes, if any, to add to this deprecation element
   *
   * @param deprecatedElement the deprecated element (e.g. the deprecated class, method or field)
   * @param referenceElement  the reference to that deprecated element
   * @param symbolName        the user visible symbol name
   * @return a (possibly empty) array of quick fixes to register with the deprecation
   */
  @NotNull
  public LocalQuickFix[] getQuickFixes(@NotNull PsiElement deprecatedElement,
                                       @NotNull PsiElement referenceElement,
                                       @Nullable String symbolName) {
    return LocalQuickFix.EMPTY_ARRAY;
  }
}
