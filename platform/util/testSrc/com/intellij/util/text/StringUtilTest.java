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
package com.intellij.util.text;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.LineSeparator;
import com.intellij.xml.util.XmlStringUtil;
import junit.framework.TestCase;

import java.nio.CharBuffer;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 * @since Dec 22, 2006
 */
public class StringUtilTest extends TestCase {
  public void testToUpperCase() {
    assertEquals('/', StringUtil.toUpperCase('/'));
    assertEquals(':', StringUtil.toUpperCase(':'));
    assertEquals('A', StringUtil.toUpperCase('a'));
    assertEquals('A', StringUtil.toUpperCase('A'));
    assertEquals('K', StringUtil.toUpperCase('k'));
    assertEquals('K', StringUtil.toUpperCase('K'));

    assertEquals('\u2567', StringUtil.toUpperCase(Character.toLowerCase('\u2567')));
  }

  public void testToLowerCase() {
    assertEquals('/', StringUtil.toLowerCase('/'));
    assertEquals(':', StringUtil.toLowerCase(':'));
    assertEquals('a', StringUtil.toLowerCase('a'));
    assertEquals('a', StringUtil.toLowerCase('A'));
    assertEquals('k', StringUtil.toLowerCase('k'));
    assertEquals('k', StringUtil.toLowerCase('K'));

    assertEquals('\u2567', StringUtil.toUpperCase(Character.toLowerCase('\u2567')));
  }

  public void testIsEmptyOrSpaces() throws Exception {
    assertTrue(StringUtil.isEmptyOrSpaces(null));
    assertTrue(StringUtil.isEmptyOrSpaces(""));
    assertTrue(StringUtil.isEmptyOrSpaces("                   "));

    assertFalse(StringUtil.isEmptyOrSpaces("1"));
    assertFalse(StringUtil.isEmptyOrSpaces("         12345          "));
    assertFalse(StringUtil.isEmptyOrSpaces("test"));
  }

  public void testSplitWithQuotes() {
    final List<String> strings = StringUtil.splitHonorQuotes("aaa bbb   ccc \"ddd\" \"e\\\"e\\\"e\"  ", ' ');
    assertEquals(5, strings.size());
    assertEquals("aaa", strings.get(0));
    assertEquals("bbb", strings.get(1));
    assertEquals("ccc", strings.get(2));
    assertEquals("\"ddd\"", strings.get(3));
    assertEquals("\"e\\\"e\\\"e\"", strings.get(4));
  }

  public void testUnpluralize() {
    assertEquals("s", StringUtil.unpluralize("s"));
    assertEquals("z", StringUtil.unpluralize("zs"));
  }

  public void testPluralize() {
    assertEquals("values", StringUtil.pluralize("value"));
    assertEquals("values", StringUtil.pluralize("values"));
  }

  public void testStartsWithConcatenation() {
    assertTrue(StringUtil.startsWithConcatenation("something.with.dot", "something", "."));
    assertTrue(StringUtil.startsWithConcatenation("something.with.dot", "", "something."));
    assertTrue(StringUtil.startsWithConcatenation("something.", "something", "."));
    assertTrue(StringUtil.startsWithConcatenation("something", "something", "", "", ""));
    assertFalse(StringUtil.startsWithConcatenation("something", "something", "", "", "."));
    assertFalse(StringUtil.startsWithConcatenation("some", "something", ""));
  }

  public void testNaturalCompare() {
    assertEquals(1, StringUtil.naturalCompare("test011", "test10"));
    assertEquals(1, StringUtil.naturalCompare("test10a", "test010"));
    final List<String> strings = new ArrayList<String>(Arrays.asList("Test99", "tes0", "test0", "testing", "test", "test99", "test011", "test1",
                                                             "test 3", "test2", "test10a", "test10", "1.2.10.5", "1.2.9.1"));
    final Comparator<String> c = new Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        return StringUtil.naturalCompare(o1, o2);
      }
    };
    Collections.sort(strings, c);
    assertEquals(Arrays.asList("1.2.9.1", "1.2.10.5", "tes0", "test", "test0", "test1", "test2", "test 3", "test10", "test10a",
                               "test011", "Test99", "test99", "testing"), strings);
    final List<String> strings2 = new ArrayList<String>(Arrays.asList("t1", "t001", "T2", "T002", "T1", "t2"));
    Collections.sort(strings2, c);
    assertEquals(Arrays.asList("T1", "t1", "t001", "T2", "t2", "T002"), strings2);
    assertEquals(1 ,StringUtil.naturalCompare("7403515080361171695", "07403515080361171694"));
    assertEquals(-14, StringUtil.naturalCompare("_firstField", "myField1"));
    //idea-80853
    final List<String> strings3 = new ArrayList<String>(
      Arrays.asList("C148A_InsomniaCure", "C148B_Escape", "C148C_TersePrincess", "C148D_BagOfMice", "C148E_Porcelain"));
    Collections.sort(strings3, c);
    assertEquals(Arrays.asList("C148A_InsomniaCure", "C148B_Escape", "C148C_TersePrincess", "C148D_BagOfMice", "C148E_Porcelain"), strings3);
  }

  public void testFormatLinks() {
    assertEquals("<a href=\"http://a-b+c\">http://a-b+c</a>", StringUtil.formatLinks("http://a-b+c"));
  }

  public void testCopyHeapCharBuffer() {
    String s = "abcde";
    CharBuffer buffer = CharBuffer.allocate(s.length());
    buffer.append(s);
    buffer.rewind();

    assertNotNull(CharArrayUtil.fromSequenceWithoutCopying(buffer));
    assertNotNull(CharArrayUtil.fromSequenceWithoutCopying(buffer.subSequence(0, 5)));
    //assertNull(CharArrayUtil.fromSequenceWithoutCopying(buffer.subSequence(0, 4))); // end index is not checked
    assertNull(CharArrayUtil.fromSequenceWithoutCopying(buffer.subSequence(1, 5)));
    assertNull(CharArrayUtil.fromSequenceWithoutCopying(buffer.subSequence(1, 2)));
  }

  public void testTitleCase() {
    assertEquals("Couldn't Connect to Debugger", StringUtil.wordsToBeginFromUpperCase("Couldn't connect to debugger"));
  }

  public void testSentenceCapitalization() {
    assertEquals("couldn't connect to debugger", StringUtil.wordsToBeginFromLowerCase("Couldn't Connect to Debugger"));
  }

  public void testEscapeStringCharacters() {
    assertEquals("\\\"\\n", StringUtil.escapeStringCharacters(3, "\\\"\n", "\"", false, new StringBuilder()).toString());
    assertEquals("\\\"\\n", StringUtil.escapeStringCharacters(2, "\"\n", "\"", false, new StringBuilder()).toString());
    assertEquals("\\\\\\\"\\n", StringUtil.escapeStringCharacters(3, "\\\"\n", "\"", true, new StringBuilder()).toString());
  }

  public void testEscapeSlashes() {
    assertEquals("\\/", StringUtil.escapeSlashes("/"));
    assertEquals("foo\\/bar\\foo\\/", StringUtil.escapeSlashes("foo/bar\\foo/"));

    assertEquals("\\\\\\\\server\\\\share\\\\extension.crx", StringUtil.escapeBackSlashes("\\\\server\\share\\extension.crx"));
  }

  public void testEscapeQuotes() {
    assertEquals("\\\"", StringUtil.escapeQuotes("\""));
    assertEquals("foo\\\"bar'\\\"", StringUtil.escapeQuotes("foo\"bar'\""));
  }

  public void testUnqote() {
    assertEquals("", StringUtil.unquoteString(""));
    assertEquals("\"", StringUtil.unquoteString("\""));
    assertEquals("", StringUtil.unquoteString("\"\""));
    assertEquals("\"", StringUtil.unquoteString("\"\"\""));
    assertEquals("foo", StringUtil.unquoteString("\"foo\""));
    assertEquals("\"foo", StringUtil.unquoteString("\"foo"));
    assertEquals("foo\"", StringUtil.unquoteString("foo\""));
    assertEquals("", StringUtil.unquoteString(""));
    assertEquals("\'", StringUtil.unquoteString("\'"));
    assertEquals("", StringUtil.unquoteString("\'\'"));
    assertEquals("\'", StringUtil.unquoteString("\'\'\'"));
    assertEquals("foo", StringUtil.unquoteString("\'foo\'"));
    assertEquals("\'foo", StringUtil.unquoteString("\'foo"));
    assertEquals("foo\'", StringUtil.unquoteString("foo\'"));

    assertEquals("\'\"", StringUtil.unquoteString("\'\""));
    assertEquals("\"\'", StringUtil.unquoteString("\"\'"));
    assertEquals("\"foo\'", StringUtil.unquoteString("\"foo\'"));
  }

  public void testStripQuotesAroundValue() {
    assertEquals("", StringUtil.stripQuotesAroundValue(""));
    assertEquals("", StringUtil.stripQuotesAroundValue("'"));
    assertEquals("", StringUtil.stripQuotesAroundValue("\""));
    assertEquals("", StringUtil.stripQuotesAroundValue("''"));
    assertEquals("", StringUtil.stripQuotesAroundValue("\"\""));
    assertEquals("", StringUtil.stripQuotesAroundValue("'\""));
    assertEquals("foo", StringUtil.stripQuotesAroundValue("'foo'"));
    assertEquals("foo", StringUtil.stripQuotesAroundValue("'foo"));
    assertEquals("foo", StringUtil.stripQuotesAroundValue("foo'"));
    assertEquals("f'o'o", StringUtil.stripQuotesAroundValue("'f'o'o'"));
    assertEquals("f\"o'o", StringUtil.stripQuotesAroundValue("\"f\"o'o'"));
    assertEquals("f\"o'o", StringUtil.stripQuotesAroundValue("f\"o'o"));
    assertEquals("\"'f\"o'o\"", StringUtil.stripQuotesAroundValue("\"\"'f\"o'o\"\""));
    assertEquals("''f\"o'o''", StringUtil.stripQuotesAroundValue("'''f\"o'o'''"));
    assertEquals("foo' 'bar", StringUtil.stripQuotesAroundValue("foo' 'bar"));
  }

  public void testUnqoteWithQuotationChar() {
    assertEquals("", StringUtil.unquoteString("", '|'));
    assertEquals("|", StringUtil.unquoteString("|", '|'));
    assertEquals("", StringUtil.unquoteString("||", '|'));
    assertEquals("|", StringUtil.unquoteString("|||", '|'));
    assertEquals("foo", StringUtil.unquoteString("|foo|", '|'));
    assertEquals("|foo", StringUtil.unquoteString("|foo", '|'));
    assertEquals("foo|", StringUtil.unquoteString("foo|", '|'));
  }

  public void testIsQuotedString() {
    assertFalse(StringUtil.isQuotedString(""));
    assertFalse(StringUtil.isQuotedString("'"));
    assertFalse(StringUtil.isQuotedString("\""));
    assertTrue(StringUtil.isQuotedString("\"\""));
    assertTrue(StringUtil.isQuotedString("''"));
    assertTrue(StringUtil.isQuotedString("'ab'"));
    assertTrue(StringUtil.isQuotedString("\"foo\""));
  }

  public void testJoin() {
    assertEquals("", StringUtil.join(Collections.<String>emptyList(), ","));
    assertEquals("qqq", StringUtil.join(Collections.singletonList("qqq"), ","));
    assertEquals("", StringUtil.join(Collections.<String>singletonList(null), ","));
    assertEquals("a,b", StringUtil.join(Arrays.asList("a", "b"), ","));
    assertEquals("foo,,bar", StringUtil.join(Arrays.asList("foo", "", "bar"), ","));
    assertEquals("foo,,bar", StringUtil.join(new String[]{"foo", "", "bar"}, ","));
  }

  public void testSplitByLineKeepingSeparators() {
    assertEquals(Arrays.asList(""), Arrays.asList(StringUtil.splitByLinesKeepSeparators("")));
    assertEquals(Arrays.asList("aa"), Arrays.asList(StringUtil.splitByLinesKeepSeparators("aa")));
    assertEquals(Arrays.asList("\n", "\n", "aa\n", "\n", "bb\n", "cc\n", "\n"),
                 Arrays.asList(StringUtil.splitByLinesKeepSeparators("\n\naa\n\nbb\ncc\n\n")));

    assertEquals(Arrays.asList("\r", "\r\n", "\r"), Arrays.asList(StringUtil.splitByLinesKeepSeparators("\r\r\n\r")));
    assertEquals(Arrays.asList("\r\n", "\r", "\r\n"), Arrays.asList(StringUtil.splitByLinesKeepSeparators("\r\n\r\r\n")));

    assertEquals(Arrays.asList("\n", "\r\n", "\n", "\r\n", "\r", "\r", "aa\r", "bb\r\n", "cc\n", "\r", "dd\n", "\n", "\r\n", "\r"),
                 Arrays.asList(StringUtil.splitByLinesKeepSeparators("\n\r\n\n\r\n\r\raa\rbb\r\ncc\n\rdd\n\n\r\n\r")));
  }

  public void testShortened() {
    String[] names = {"AVeryVeeryLongClassName.java", "com.test.SomeJAVAClassName.java", "strangelowercaseclassname.java", "PrefixPostfix.java", "SomeJAVAClassName.java", "qwertyuiopasdghjklzxcvbnm1234567890"};
    for (String name : names) {
      for (int i = name.length() + 1; i > 15; i--) {
        String shortened = StringUtil.getShortened(name, i);
        assertTrue(shortened.length() <= i);
        assertTrue(!shortened.contains("...."));
        int pos = shortened.indexOf("...");
        if (pos != -1) {
          assertTrue(name.startsWith(shortened.substring(0, pos)));
          assertTrue(name.endsWith(shortened.substring(pos + 3)));
        }
        else {
          assertEquals(shortened,  name);
        }
      }
    }
  }

  public void testReplaceReturnReplacementIfTextEqualsToReplacedText() {
    String newS = "/tmp";
    assertSame(StringUtil.replace("$PROJECT_FILE$", "$PROJECT_FILE$".toLowerCase().toUpperCase() /* ensure new String instance */, newS), newS);
  }

  public void testReplace() {
    assertEquals(StringUtil.replace("$PROJECT_FILE$/filename", "$PROJECT_FILE$", "/tmp"), "/tmp/filename");
  }

  public void testEqualsIgnoreWhitespaces() {
    assertTrue(StringUtil.equalsIgnoreWhitespaces(null, null));
    assertFalse(StringUtil.equalsIgnoreWhitespaces("", null));

    assertTrue(StringUtil.equalsIgnoreWhitespaces("", ""));
    assertTrue(StringUtil.equalsIgnoreWhitespaces("\n\t ", ""));
    assertTrue(StringUtil.equalsIgnoreWhitespaces("", "\t\n \n\t"));
    assertTrue(StringUtil.equalsIgnoreWhitespaces("\t", "\n"));

    assertTrue(StringUtil.equalsIgnoreWhitespaces("x", " x"));
    assertTrue(StringUtil.equalsIgnoreWhitespaces("x", "x "));
    assertTrue(StringUtil.equalsIgnoreWhitespaces("x\n", "x"));

    assertTrue(StringUtil.equalsIgnoreWhitespaces("abcd", "a\nb\nc\nd\n"));
    assertTrue(StringUtil.equalsIgnoreWhitespaces("x y x", "x y x"));
    assertTrue(StringUtil.equalsIgnoreWhitespaces("xyx", "x y x"));

    assertFalse(StringUtil.equalsIgnoreWhitespaces("x", "\t\n "));
    assertFalse(StringUtil.equalsIgnoreWhitespaces("", " x "));
    assertFalse(StringUtil.equalsIgnoreWhitespaces("", "x "));
    assertFalse(StringUtil.equalsIgnoreWhitespaces("", " x"));
    assertFalse(StringUtil.equalsIgnoreWhitespaces("xyx", "xxx"));
    assertFalse(StringUtil.equalsIgnoreWhitespaces("xyx", "xYx"));
  }

  public void testStringHashCodeIgnoreWhitespaces() {
    assertTrue(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces(""), StringUtil.stringHashCodeIgnoreWhitespaces("")));
    assertTrue(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces("\n\t "), StringUtil.stringHashCodeIgnoreWhitespaces("")));
    assertTrue(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces(""), StringUtil.stringHashCodeIgnoreWhitespaces("\t\n \n\t")));
    assertTrue(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces("\t"), StringUtil.stringHashCodeIgnoreWhitespaces("\n")));

    assertTrue(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces("x"), StringUtil.stringHashCodeIgnoreWhitespaces(" x")));
    assertTrue(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces("x"), StringUtil.stringHashCodeIgnoreWhitespaces("x ")));
    assertTrue(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces("x\n"), StringUtil.stringHashCodeIgnoreWhitespaces("x")));

    assertTrue(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces("abcd"), StringUtil.stringHashCodeIgnoreWhitespaces("a\nb\nc\nd\n")));
    assertTrue(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces("x y x"), StringUtil.stringHashCodeIgnoreWhitespaces("x y x")));
    assertTrue(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces("xyx"), StringUtil.stringHashCodeIgnoreWhitespaces("x y x")));

    assertFalse(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces("x"), StringUtil.stringHashCodeIgnoreWhitespaces("\t\n ")));
    assertFalse(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces(""), StringUtil.stringHashCodeIgnoreWhitespaces(" x ")));
    assertFalse(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces(""), StringUtil.stringHashCodeIgnoreWhitespaces("x ")));
    assertFalse(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces(""), StringUtil.stringHashCodeIgnoreWhitespaces(" x")));
    assertFalse(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces("xyx"), StringUtil.stringHashCodeIgnoreWhitespaces("xxx")));
    assertFalse(Comparing.equal(StringUtil.stringHashCodeIgnoreWhitespaces("xyx"), StringUtil.stringHashCodeIgnoreWhitespaces("xYx")));
  }

  public void testContains() {
    assertTrue(StringUtil.contains("1", "1"));
    assertFalse(StringUtil.contains("1", "12"));
    assertTrue(StringUtil.contains("12", "1"));
    assertTrue(StringUtil.contains("12", "2"));
  }

  public void testDetectSeparators() {
    assertEquals(null, StringUtil.detectSeparators(""));
    assertEquals(null, StringUtil.detectSeparators("asd"));
    assertEquals(null, StringUtil.detectSeparators("asd\t"));

    assertEquals(LineSeparator.LF, StringUtil.detectSeparators("asd\n"));
    assertEquals(LineSeparator.LF, StringUtil.detectSeparators("asd\nads\r"));
    assertEquals(LineSeparator.LF, StringUtil.detectSeparators("asd\nads\n"));

    assertEquals(LineSeparator.CR, StringUtil.detectSeparators("asd\r"));
    assertEquals(LineSeparator.CR, StringUtil.detectSeparators("asd\rads\r"));
    assertEquals(LineSeparator.CR, StringUtil.detectSeparators("asd\rads\n"));

    assertEquals(LineSeparator.CRLF, StringUtil.detectSeparators("asd\r\n"));
    assertEquals(LineSeparator.CRLF, StringUtil.detectSeparators("asd\r\nads\r"));
    assertEquals(LineSeparator.CRLF, StringUtil.detectSeparators("asd\r\nads\n"));
  }

  public void testFormatFileSize() {
    assertEquals("0B", StringUtil.formatFileSize(0));
    assertEquals("1B", StringUtil.formatFileSize(1));
    assertEquals("2.15G", StringUtil.formatFileSize(Integer.MAX_VALUE));
    assertEquals("9.22E", StringUtil.formatFileSize(Long.MAX_VALUE));

    assertEquals("60.10K", StringUtil.formatFileSize(60100));

    assertEquals("1.23K", StringUtil.formatFileSize(1234));
    assertEquals("12.35K", StringUtil.formatFileSize(12345));
    assertEquals("123.46K", StringUtil.formatFileSize(123456));
    assertEquals("1.23M", StringUtil.formatFileSize(1234567));
    assertEquals("12.35M", StringUtil.formatFileSize(12345678));
    assertEquals("123.46M", StringUtil.formatFileSize(123456789));
    assertEquals("1.23G", StringUtil.formatFileSize(1234567890));
  }

  public void testFormatDuration() {
    assertEquals("0ms", StringUtil.formatDuration(0));
    assertEquals("1ms", StringUtil.formatDuration(1));
    assertEquals("3w 3d 20h 31m 23s 647ms", StringUtil.formatDuration(Integer.MAX_VALUE));
    assertEquals("31ep 7714ml 2c 59yr 5mo 0w 3d 7h 12m 55s 807ms", StringUtil.formatDuration(Long.MAX_VALUE));

    assertEquals("1m 0s 100ms", StringUtil.formatDuration(60100));

    assertEquals("1s 234ms", StringUtil.formatDuration(1234));
    assertEquals("12s 345ms", StringUtil.formatDuration(12345));
    assertEquals("2m 3s 456ms", StringUtil.formatDuration(123456));
    assertEquals("20m 34s 567ms", StringUtil.formatDuration(1234567));
    assertEquals("3h 25m 45s 678ms", StringUtil.formatDuration(12345678));
    assertEquals("1d 10h 17m 36s 789ms", StringUtil.formatDuration(123456789));
    assertEquals("2w 0d 6h 56m 7s 890ms", StringUtil.formatDuration(1234567890));
  }

  public void testXmlWrapInCDATA() {
    assertEquals("<![CDATA[abcd]]>", XmlStringUtil.wrapInCDATA("abcd"));
    assertEquals("<![CDATA[abcd]]]><![CDATA[]>]]>", XmlStringUtil.wrapInCDATA("abcd]]>"));
    assertEquals("<![CDATA[abcd]]]><![CDATA[]>efgh]]>", XmlStringUtil.wrapInCDATA("abcd]]>efgh"));
    assertEquals("<![CDATA[123<![CDATA[wow<&>]]]><![CDATA[]>]]]><![CDATA[]><![CDATA[123]]>", XmlStringUtil.wrapInCDATA(
      "123<![CDATA[wow<&>]]>]]><![CDATA[123"));
  }

  public void testFormatVersionToMajorMinorPatchString() {
    assertEquals("15.0.0", StringUtil.formatVersionToMajorMinorPatchString("15"));
    assertEquals("15.1.0", StringUtil.formatVersionToMajorMinorPatchString("15.1"));
    assertEquals("15.1.3", StringUtil.formatVersionToMajorMinorPatchString("15.1.3"));

    assertEquals("15.1.3.25", StringUtil.formatVersionToMajorMinorPatchString("15.1.3.25"));

    assertEquals("15.1 EAP", StringUtil.formatVersionToMajorMinorPatchString("15.1 EAP"));
    assertEquals("15 EAP", StringUtil.formatVersionToMajorMinorPatchString("15 EAP"));
    assertEquals("Not Parsable 15.1.3.25", StringUtil.formatVersionToMajorMinorPatchString("Not Parsable 15.1.3.25"));
  }
}
