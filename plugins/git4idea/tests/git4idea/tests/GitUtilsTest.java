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
package git4idea.tests;

import com.intellij.openapi.util.io.FileUtil;
import git4idea.GitUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static git4idea.GitUtil.unescapePath;
import static org.junit.Assert.*;

public class GitUtilsTest {

  @Test
  public void format_long_rev() {
    assertEquals("0000000000000000", GitUtil.formatLongRev(0));
    assertEquals("fffffffffffffffe", GitUtil.formatLongRev(-2));
  }

  @Test
  public void unescape_cyrillic() throws Exception {
    assertEquals("Cyrillic folder", "папка/file.txt", unescapePath("\\320\\277\\320\\260\\320\\277\\320\\272\\320\\260/file.txt"));
    assertEquals("Cyrillic folder and filename", "папка/документ", unescapePath(
      "\\320\\277\\320\\260\\320\\277\\320\\272\\320\\260/\\320\\264\\320\\276\\320\\272\\321\\203\\320\\274\\320\\265\\320\\275\\321\\202"));
  }

  @Test
  public void testValidGitRoot() throws IOException {
    File tempFolder = FileUtil.createTempDirectory("gittest", null);
    File dotGit = createTempDirectory(tempFolder, ".git");
    assertFalse("Folder without .git/HEAD identified as valid git repository", GitUtil.isGitRoot(tempFolder));
    createTempFile(dotGit, "HEAD");
    assertTrue(GitUtil.isGitRoot(tempFolder));
  }

  private static File createTempFile(@NotNull File dotGit, @NotNull String fileName) throws IOException {
    File f = new File(dotGit, fileName);
    f.createNewFile();
    f.deleteOnExit();
    return f;
  }

  private static File createTempDirectory(@NotNull File parent, @NotNull String folderName) {
    File f = new File(parent, folderName);
    f.mkdir();
    f.deleteOnExit();
    return f;
  }
}
