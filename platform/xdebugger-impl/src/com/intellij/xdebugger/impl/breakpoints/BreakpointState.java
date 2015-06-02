/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author nik
*/
@Tag("breakpoint")
public class BreakpointState<B extends XBreakpoint<P>, P extends XBreakpointProperties, T extends XBreakpointType<B,P>> {
  private String myTypeId;
  private boolean myEnabled;
  private Element myPropertiesElement;
  private SuspendPolicy mySuspendPolicy = SuspendPolicy.THREAD;
  private boolean myLogMessage;
  private LogExpression myLogExpression;
  private Condition myCondition;
  private XBreakpointDependencyState myDependencyState;
  @Tag("group")
  private String myGroup;

  @Tag("description")
  private String myDescription;

  private long myTimeStamp;

  public BreakpointState() {
  }

  public BreakpointState(final boolean enabled, final String typeId, final long timeStamp) {
    myEnabled = enabled;
    myTypeId = typeId;
    myTimeStamp = timeStamp;
  }

  @Attribute("enabled")
  public boolean isEnabled() {
    return myEnabled;
  }

  public void setEnabled(final boolean enabled) {
    myEnabled = enabled;
  }

  @Attribute("type")
  public String getTypeId() {
    return myTypeId;
  }

  public void setTypeId(final String typeId) {
    myTypeId = typeId;
  }

  @Tag("properties")
  public Element getPropertiesElement() {
    return myPropertiesElement;
  }

  public void setPropertiesElement(final Element propertiesElement) {
    myPropertiesElement = propertiesElement;
  }

  @Attribute("suspend")
  public String getSuspendPolicyString() {
    return mySuspendPolicy.name();
  }

  public void setSuspendPolicyString(final String suspendPolicy) {
    mySuspendPolicy = SuspendPolicy.valueOf(suspendPolicy);
  }

  @Transient
  public SuspendPolicy getSuspendPolicy() {
    return mySuspendPolicy;
  }

  public void setSuspendPolicy(SuspendPolicy suspendPolicy) {
    mySuspendPolicy = suspendPolicy;
  }

  @Attribute("log-message")
  public boolean isLogMessage() {
    return myLogMessage;
  }

  public void setLogMessage(final boolean logMessage) {
    myLogMessage = logMessage;
  }

  public String getGroup() {
    return myGroup;
  }

  public void setGroup(String group) {
    myGroup = group;
  }

  public String getDescription() {
    return myDescription;
  }

  public void setDescription(String description) {
    myDescription = description;
  }

  @Nullable
  @Property(surroundWithTag = false)
  public LogExpression getLogExpression() {
    return myLogExpression;
  }

  public void setLogExpression(@Nullable LogExpression logExpression) {
    if (logExpression != null) {
      logExpression.checkConverted();
    }
    myLogExpression = logExpression;
  }

  @Nullable
  @Property(surroundWithTag = false)
  public Condition getCondition() {
    return myCondition;
  }

  public void setCondition(@Nullable Condition condition) {
    if (condition != null) {
      condition.checkConverted();
    }
    myCondition = condition;
  }

  public boolean isLogExpressionEnabled() {
    return myLogExpression == null || !myLogExpression.myDisabled;
  }

  public boolean isConditionEnabled() {
    return myCondition == null || !myCondition.myDisabled;
  }

  @Property(surroundWithTag = false)
  public XBreakpointDependencyState getDependencyState() {
    return myDependencyState;
  }

  public void setDependencyState(final XBreakpointDependencyState dependencyState) {
    myDependencyState = dependencyState;
  }

  public XBreakpointBase<B,P,?> createBreakpoint(@NotNull T type, @NotNull XBreakpointManagerImpl breakpointManager) {
    return new XBreakpointBase<B, P, BreakpointState<B,P,?>>(type, breakpointManager, this);
  }

  public long getTimeStamp() {
    return myTimeStamp;
  }

  public void setTimeStamp(long timeStamp) {
    myTimeStamp = timeStamp;
  }

  void applyDefaults(BreakpointState state) {
    state.mySuspendPolicy = mySuspendPolicy;
  }

  @Tag("condition")
  public static class Condition extends XExpressionState {
    public Condition() {
    }

    private Condition(boolean disabled, XExpression expression) {
      super(disabled, expression);
    }

    @Nullable
    public static Condition create(boolean disabled, XExpression expression) {
      if (XDebuggerUtilImpl.isEmptyExpression(expression)) {
        return null;
      }
      return new Condition(disabled, expression);
    }
  }

  @Tag("log-expression")
  public static class LogExpression extends XExpressionState {
    public LogExpression() {
    }

    private LogExpression(boolean disabled, XExpression expression) {
      super(disabled, expression);
    }

    @Nullable
    public static LogExpression create(boolean disabled, XExpression expression) {
      if (XDebuggerUtilImpl.isEmptyExpression(expression)) {
        return null;
      }
      return new LogExpression(disabled, expression);
    }
  }
}
