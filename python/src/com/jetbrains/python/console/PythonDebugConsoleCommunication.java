package com.jetbrains.python.console;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.jetbrains.python.console.pydev.AbstractConsoleCommunication;
import com.jetbrains.python.console.pydev.ICallback;
import com.jetbrains.python.console.pydev.InterpreterResponse;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.PyDebugProcess;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.pydev.ProcessDebugger;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author traff
 */
public class PythonDebugConsoleCommunication extends AbstractConsoleCommunication {
  private final PyDebugProcess myDebugProcess;

  private final StringBuilder myExpression = new StringBuilder();


  public PythonDebugConsoleCommunication(Project project, PyDebugProcess debugProcess) {
    super(project);
    myDebugProcess = debugProcess;
  }

  @NotNull
  @Override
  public List<PydevCompletionVariant> getCompletions(String text, String actualToken) throws Exception {
    return myDebugProcess.getCompletions(actualToken);
  }

  @Override
  public String getDescription(String text) {
    return null;
  }

  @Override
  public boolean isWaitingForInput() {
    return false;
  }

  @Override
  public boolean isExecuting() {
    return false;
  }

  protected void exec(final String command, final ProcessDebugger.DebugCallback<Pair<String, Boolean>> callback) {
    myDebugProcess.consoleExec(command, new ProcessDebugger.DebugCallback<String>() {
      @Override
      public void ok(String value) {
        callback.ok(parseExecResponseString(value));
      }

      @Override
      public void error(PyDebuggerException exception) {
        callback.error(exception);
      }
    });
  }

  public void execInterpreter(String s, final ICallback<Object, InterpreterResponse> callback) {
    myExpression.append(s);
    exec(myExpression.toString(), new ProcessDebugger.DebugCallback<Pair<String, Boolean>>() {
      @Override
      public void ok(Pair<String, Boolean> executed) {
        boolean more = executed.second;

        if (!more) {
          myExpression.setLength(0);
        }
        callback.call(new InterpreterResponse(more, isWaitingForInput()));
      }

      @Override
      public void error(PyDebuggerException exception) {
        myExpression.setLength(0);
        callback.call(new InterpreterResponse(false, isWaitingForInput()));
      }
    });
  }

  @Override
  public void interrupt() {
    throw new UnsupportedOperationException();
  }
}
