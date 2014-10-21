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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

class MultiZipFile {
  private final ZipFile myZipFile;

  private final int myVariant;

  private MultiZipFile(ZipFile zipFile, int variant) {
    myZipFile = zipFile;
    myVariant = variant;
  }

  public MultiZipFile(ZipFile zipFile) {
    this(zipFile, -1);
  }

  public MultiZipFile(File file) throws IOException {
    this(new ZipFile(file));
  }

  public ZipEntry getEntry(String path) {
    return myZipFile.getEntry(getVariantPath(path, myVariant));
  }

  private static String getVariantPath(String path, int variant) {
    if (variant >= 0) {
      path = ".variant" + variant + "/" + path;
    }
    return path;
  }

  public InputStream getInputStream(ZipEntry entry) throws IOException {
    return myZipFile.getInputStream(entry);
  }

  public void close() throws IOException {
    myZipFile.close();
  }

  public MultiZipFile getVariant(int variant) {
    return new MultiZipFile(myZipFile, variant);
  }

  static class OutputStream extends java.io.OutputStream {

    private final ZipOutputStream myZipOutputStream;

    private final int myVariant;

    private OutputStream(ZipOutputStream stream, int variant) {
      myZipOutputStream = stream;
      myVariant = variant;
    }

    public OutputStream(ZipOutputStream stream) {
      this(stream, -1);
    }

    public void putNextEntry(String name) throws IOException {
      myZipOutputStream.putNextEntry(new ZipEntry(getVariantPath(name, myVariant)));
    }

    @Override
    public void write(int i) throws IOException {
      myZipOutputStream.write(i);
    }

    @Override
    public void write(byte[] bytes) throws IOException {
      myZipOutputStream.write(bytes);
    }

    public void closeEntry() throws IOException {
      myZipOutputStream.closeEntry();
    }

    public OutputStream getVariant(int i) {
      return new OutputStream(myZipOutputStream, i);
    }
  }
}
