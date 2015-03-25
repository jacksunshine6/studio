/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.javaee;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.Html5SchemaProvider;
import com.intellij.xml.XmlSchemaProvider;
import com.intellij.xml.util.XmlUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.net.URL;
import java.util.*;

@State(
  name = "ExternalResourceManagerImpl",
  storages = {@Storage(file = StoragePathMacros.APP_CONFIG + "/other.xml")}
)
public class ExternalResourceManagerExImpl extends ExternalResourceManagerEx implements PersistentStateComponent<Element> {
  static final Logger LOG = Logger.getInstance(ExternalResourceManagerExImpl.class);

  @NonNls public static final String J2EE_1_3 = "http://java.sun.com/dtd/";
  @NonNls public static final String J2EE_1_2 = "http://java.sun.com/j2ee/dtds/";
  @NonNls public static final String J2EE_NS = "http://java.sun.com/xml/ns/j2ee/";
  @NonNls public static final String JAVAEE_NS = "http://java.sun.com/xml/ns/javaee/";
  private static final String CATALOG_PROPERTIES_ELEMENT = "CATALOG_PROPERTIES";

  private final Map<String, Map<String, String>> myResources = new THashMap<String, Map<String, String>>();
  private final Set<String> myResourceLocations = new THashSet<String>();

  private final Set<String> myIgnoredResources = new TreeSet<String>();
  private final Set<String> myStandardIgnoredResources = new TreeSet<String>();

  private final NotNullLazyValue<Map<String, Map<String, Resource>>> myStandardResources = new AtomicNotNullLazyValue<Map<String, Map<String, Resource>>>() {
    @NotNull
    @Override
    protected Map<String, Map<String, Resource>> compute() {
      return computeStdResources();
    }
  };

  private String myDefaultHtmlDoctype = HTML5_DOCTYPE_ELEMENT;

  private String myCatalogPropertiesFile;
  private XMLCatalogManager myCatalogManager;
  private static final String HTML5_DOCTYPE_ELEMENT = "HTML5";

  protected Map<String, Map<String, Resource>> computeStdResources() {
    ResourceRegistrarImpl registrar = new ResourceRegistrarImpl();
    for (StandardResourceProvider provider : StandardResourceProvider.EP_NAME.getExtensions()) {
      provider.registerResources(registrar);
    }
    for (StandardResourceEP extension : StandardResourceEP.EP_NAME.getExtensions()) {
      registrar.addStdResource(extension.url, extension.version, extension.resourcePath, null, extension.getLoaderForClass());
    }

    myStandardIgnoredResources.addAll(registrar.getIgnored());
    return registrar.getResources();
  }

  private final List<ExternalResourceListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  @NonNls private static final String RESOURCE_ELEMENT = "resource";
  @NonNls private static final String URL_ATTR = "url";
  @NonNls private static final String LOCATION_ATTR = "location";
  @NonNls private static final String IGNORED_RESOURCE_ELEMENT = "ignored-resource";
  @NonNls private static final String HTML_DEFAULT_DOCTYPE_ELEMENT = "default-html-doctype";

  private static final String DEFAULT_VERSION = "";

  @Override
  public boolean isStandardResource(VirtualFile file) {
    VirtualFile parent = file.getParent();
    return parent != null && parent.getName().equals("standardSchemas");
  }

  @Override
  public boolean isUserResource(VirtualFile file) {
    return myResourceLocations.contains(file.getUrl());
  }

  @Nullable
  static <T> Map<String, T> getMap(@NotNull Map<String, Map<String, T>> resources, @Nullable String version, boolean create) {
    version = StringUtil.notNullize(version, DEFAULT_VERSION);
    Map<String, T> map = resources.get(version);
    if (map == null) {
      if (create) {
        map = new THashMap<String, T>();
        resources.put(version, map);
      }
      else if (!version.equals(DEFAULT_VERSION)) {
        map = resources.get(DEFAULT_VERSION);
      }
    }
    return map;
  }

  @Override
  public String getResourceLocation(@NotNull String url) {
    return getResourceLocation(url, DEFAULT_VERSION);
  }

  @Override
  public String getResourceLocation(@NotNull @NonNls String url, @Nullable String version) {
    String result = getUserResource(url, StringUtil.notNullize(version, DEFAULT_VERSION));
    if (result == null) {
      XMLCatalogManager manager = getCatalogManager();
      if (manager != null) {
        result = manager.resolve(url);
      }

      if (result == null) {
        result = getStdResource(url, version);
        if (result == null) {
          return url;
        }
      }
    }
    return result;
  }

  @Override
  @Nullable
  public String getUserResource(Project project, String url, String version) {
    String resource = getProjectResources(project).getUserResource(url, version);
    return resource == null ? getUserResource(url, version) : resource;
  }

  @Override
  @Nullable
  public String getStdResource(@NotNull String url, @Nullable String version) {
    Map<String, Resource> map = getMap(myStandardResources.getValue(), version, false);
    if (map != null) {
      Resource resource = map.get(url);
      return resource == null ? null : resource.getResourceUrl();
    }
    else {
      return null;
    }
  }

  @Nullable
  private String getUserResource(@NotNull String url, @Nullable String version) {
    Map<String, String> map = getMap(myResources, version, false);
    return map != null ? map.get(url) : null;
  }

  @Override
  public String getResourceLocation(@NotNull @NonNls String url, @NotNull Project project) {
    String location = getProjectResources(project).getResourceLocation(url);
    return location == null || location.equals(url) ? getResourceLocation(url) : location;
  }

  public String getResourceLocation(@NonNls String url, String version, @NotNull Project project) {
    String location = getProjectResources(project).getResourceLocation(url, version);
    return location == null || location.equals(url) ? getResourceLocation(url, version) : location;
  }

  @Override
  @Nullable
  public PsiFile getResourceLocation(@NotNull @NonNls final String url, @NotNull final PsiFile baseFile, final String version) {
    final XmlFile schema = XmlSchemaProvider.findSchema(url, baseFile);
    if (schema != null) {
      return schema;
    }
    final String location = getResourceLocation(url, version, baseFile.getProject());
    return XmlUtil.findXmlFile(baseFile, location);
  }

  @Override
  public String[] getResourceUrls(FileType fileType, boolean includeStandard) {
    return getResourceUrls(fileType, DEFAULT_VERSION, includeStandard);
  }

  @Override
  public String[] getResourceUrls(@Nullable FileType fileType, @Nullable @NonNls String version, boolean includeStandard) {
    List<String> result = new LinkedList<String>();
    addResourcesFromMap(result, version, myResources);

    if (includeStandard) {
      addResourcesFromMap(result, version, myStandardResources.getValue());
    }

    return ArrayUtil.toStringArray(result);
  }

  private static <T> void addResourcesFromMap(@NotNull List<String> result, @Nullable String version, @NotNull Map<String, Map<String, T>> resourcesMap) {
    Map<String, T> resources = getMap(resourcesMap, version, false);
    if (resources != null) {
      result.addAll(resources.keySet());
    }
  }

  @TestOnly
  public static void addTestResource(final String url, final String location, Disposable parentDisposable) {
    final ExternalResourceManagerExImpl instance = (ExternalResourceManagerExImpl)getInstance();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        instance.addResource(url, location);
      }
    });
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            instance.removeResource(url);
          }
        });
      }
    });
  }
  @Override
  public void addResource(@NotNull String url, String location) {
    addResource(url, DEFAULT_VERSION, location);
  }

  @Override
  public void addResource(@NotNull @NonNls String url, @NonNls String version, @NonNls String location) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    addSilently(url, version, location);
    fireExternalResourceChanged();
  }

  private void addSilently(@NotNull String url, @Nullable String version, String location) {
    Map<String, String> map = getMap(myResources, version, true);
    assert map != null;
    map.put(url, location);
    myResourceLocations.add(location);
    incModificationCount();
  }

  @Override
  public void removeResource(@NotNull String url) {
    removeResource(url, DEFAULT_VERSION);
  }

  @Override
  public void removeResource(@NotNull String url, @Nullable String version) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    Map<String, String> map = getMap(myResources, version, false);
    if (map != null) {
      String location = map.remove(url);
      if (location != null) {
        myResourceLocations.remove(location);
      }
      incModificationCount();
      fireExternalResourceChanged();
    }
  }

  @Override
  public void removeResource(String url, @NotNull Project project) {
    getProjectResources(project).removeResource(url);
  }

  @Override
  public void addResource(@NonNls String url, @NonNls String location, @NotNull Project project) {
    getProjectResources(project).addResource(url, location);
  }

  @Override
  public String[] getAvailableUrls() {
    Set<String> urls = new THashSet<String>();
    for (Map<String, String> map : myResources.values()) {
      urls.addAll(map.keySet());
    }
    return ArrayUtil.toStringArray(urls);
  }

  @Override
  public String[] getAvailableUrls(Project project) {
    return getProjectResources(project).getAvailableUrls();
  }

  @Override
  public void clearAllResources() {
    myResources.clear();
    myIgnoredResources.clear();
  }

  @Override
  public void clearAllResources(Project project) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    clearAllResources();
    getProjectResources(project).clearAllResources();
    incModificationCount();
    fireExternalResourceChanged();
  }

  @Override
  public void addIgnoredResource(@NotNull String url) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (addIgnoredSilently(url)) {
      fireExternalResourceChanged();
    }
  }

  private boolean addIgnoredSilently(@NotNull String url) {
    if (myStandardIgnoredResources.contains(url)) {
      return false;
    }

    if (myIgnoredResources.add(url)) {
      incModificationCount();
      return true;
    }
    else {
      return false;
    }
  }

  @Override
  public void removeIgnoredResource(@NotNull String url) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (myIgnoredResources.remove(url)) {
      incModificationCount();
      fireExternalResourceChanged();
    }
  }

  @Override
  public boolean isIgnoredResource(@NotNull String url) {
    if (myIgnoredResources.contains(url)) {
      return true;
    }

    // ensure ignored resources are loaded
    myStandardResources.getValue();
    return myStandardIgnoredResources.contains(url) || isImplicitNamespaceDescriptor(url);
  }

  private static boolean isImplicitNamespaceDescriptor(@NotNull String url) {
    for (ImplicitNamespaceDescriptorProvider provider : ImplicitNamespaceDescriptorProvider.EP_NAME.getExtensions()) {
      if (provider.getNamespaceDescriptor(null, url, null) != null) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String[] getIgnoredResources() {
    // ensure ignored resources are loaded
    myStandardResources.getValue();

    if (myIgnoredResources.isEmpty()) {
      return ArrayUtil.toStringArray(myStandardIgnoredResources);
    }

    Set<String> set = new THashSet<String>(myIgnoredResources.size() + myStandardIgnoredResources.size());
    set.addAll(myIgnoredResources);
    set.addAll(myStandardIgnoredResources);
    return ArrayUtil.toStringArray(set);
  }

  @Override
  public long getModificationCount(@NotNull Project project) {
    return getProjectResources(project).getModificationCount();
  }

  @Nullable
  @Override
  public Element getState() {
    Element element = new Element("state");

    Set<String> urls = new TreeSet<String>();
    for (Map<String, String> map : myResources.values()) {
      urls.addAll(map.keySet());
    }

    for (String url : urls) {
      if (url == null) {
        continue;
      }

      String location = getResourceLocation(url);
      if (location == null) {
        continue;
      }

      Element e = new Element(RESOURCE_ELEMENT);
      e.setAttribute(URL_ATTR, url);
      e.setAttribute(LOCATION_ATTR, location.replace(File.separatorChar, '/'));
      element.addContent(e);
    }

    myIgnoredResources.removeAll(myStandardIgnoredResources);
    for (String ignoredResource : myIgnoredResources) {
      Element e = new Element(IGNORED_RESOURCE_ELEMENT);
      e.setAttribute(URL_ATTR, ignoredResource);
      element.addContent(e);
    }

    if (myDefaultHtmlDoctype != null && !HTML5_DOCTYPE_ELEMENT.equals(myDefaultHtmlDoctype)) {
      Element e = new Element(HTML_DEFAULT_DOCTYPE_ELEMENT);
      e.setText(myDefaultHtmlDoctype);
      element.addContent(e);
    }
    if (myCatalogPropertiesFile != null) {
      Element properties = new Element(CATALOG_PROPERTIES_ELEMENT);
      properties.setText(myCatalogPropertiesFile);
      element.addContent(properties);
    }

    ReplacePathToMacroMap macroReplacements = new ReplacePathToMacroMap();
    PathMacrosImpl.getInstanceEx().addMacroReplacements(macroReplacements);
    macroReplacements.substitute(element, SystemInfo.isFileSystemCaseSensitive);
    return element;
  }

  @Override
  public void loadState(Element state) {
    ExpandMacroToPathMap macroExpands = new ExpandMacroToPathMap();
    PathMacrosImpl.getInstanceEx().addMacroExpands(macroExpands);
    macroExpands.substitute(state, SystemInfo.isFileSystemCaseSensitive);

    incModificationCount();
    for (Element element : state.getChildren(RESOURCE_ELEMENT)) {
      String url = element.getAttributeValue(URL_ATTR);
      if (!StringUtil.isEmpty(url)) {
        addSilently(url, DEFAULT_VERSION, element.getAttributeValue(LOCATION_ATTR).replace('/', File.separatorChar));
      }
    }

    myIgnoredResources.clear();
    for (Element element : state.getChildren(IGNORED_RESOURCE_ELEMENT)) {
      addIgnoredSilently(element.getAttributeValue(URL_ATTR));
    }

    Element child = state.getChild(HTML_DEFAULT_DOCTYPE_ELEMENT);
    if (child != null) {
      String text = child.getText();
      if (FileUtil.toSystemIndependentName(text).endsWith(".jar!/resources/html5-schema/html5.rnc")) {
        text = HTML5_DOCTYPE_ELEMENT;
      }
      myDefaultHtmlDoctype = text;
    }
    Element catalogElement = state.getChild(CATALOG_PROPERTIES_ELEMENT);
    if (catalogElement != null) {
      myCatalogPropertiesFile = catalogElement.getTextTrim();
    }
  }

  @Override
  public void addExternalResourceListener(ExternalResourceListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeExternalResourceListener(ExternalResourceListener listener) {
    myListeners.remove(listener);
  }

  private void fireExternalResourceChanged() {
    for (ExternalResourceListener listener : myListeners) {
      listener.externalResourceChanged();
    }
  }

  Collection<Map<String, Resource>> getStandardResources() {
    return myStandardResources.getValue().values();
  }

  private static final NotNullLazyKey<ExternalResourceManagerExImpl, Project> INSTANCE_CACHE = ServiceManager.createLazyKey(ExternalResourceManagerExImpl.class);

  private static ExternalResourceManagerExImpl getProjectResources(Project project) {
    return INSTANCE_CACHE.getValue(project);
  }

  @Override
  @NotNull
  public String getDefaultHtmlDoctype(@NotNull Project project) {
    final String doctype = getProjectResources(project).myDefaultHtmlDoctype;
    if (XmlUtil.XHTML_URI.equals(doctype)) {
      return XmlUtil.XHTML4_SCHEMA_LOCATION;
    }
    else if (HTML5_DOCTYPE_ELEMENT.equals(doctype)) {
      return Html5SchemaProvider.getHtml5SchemaLocation();
    }
    else {
      return doctype;
    }
  }

  @Override
  public void setDefaultHtmlDoctype(@NotNull String defaultHtmlDoctype, @NotNull Project project) {
    getProjectResources(project).setDefaultHtmlDoctype(defaultHtmlDoctype);
  }

  @Override
  public String getCatalogPropertiesFile() {
    return myCatalogPropertiesFile;
  }

  @Override
  public void setCatalogPropertiesFile(String filePath) {
    myCatalogManager = null;
    myCatalogPropertiesFile = filePath;
    incModificationCount();
  }

  @Nullable
  private XMLCatalogManager getCatalogManager() {
    if (myCatalogManager == null && myCatalogPropertiesFile != null) {
      myCatalogManager = new XMLCatalogManager(myCatalogPropertiesFile);
    }
    return myCatalogManager;
  }

  private void setDefaultHtmlDoctype(String defaultHtmlDoctype) {
    incModificationCount();

    if (Html5SchemaProvider.getHtml5SchemaLocation().equals(defaultHtmlDoctype)) {
      myDefaultHtmlDoctype = HTML5_DOCTYPE_ELEMENT;
    }
    else {
      myDefaultHtmlDoctype = defaultHtmlDoctype;
    }
    fireExternalResourceChanged();
  }

  @TestOnly
  public static void registerResourceTemporarily(final String url, final String location, Disposable disposable) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        getInstance().addResource(url, location);
      }
    });

    Disposer.register(disposable, new Disposable() {
      @Override
      public void dispose() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            getInstance().removeResource(url);
          }
        });
      }
    });
  }

  static class Resource {
    private final String myFile;
    private final ClassLoader myClassLoader;
    private final Class myClass;
    private volatile String myResolvedResourcePath;

    Resource(String _file, Class _class, ClassLoader _classLoader) {
      myFile = _file;
      myClass = _class;
      myClassLoader = _classLoader;
    }

    Resource(String _file, Resource baseResource) {
      this(_file, baseResource.myClass, baseResource.myClassLoader);
    }

    String directoryName() {
      int i = myFile.lastIndexOf('/');
      return i > 0 ? myFile.substring(0, i) : myFile;
    }

    @Nullable
    String getResourceUrl() {
      String resolvedResourcePath = myResolvedResourcePath;
      if (resolvedResourcePath != null) return resolvedResourcePath;

      final URL resource = myClass == null ? myClassLoader.getResource(myFile) : myClass.getResource(myFile);

      if (resource == null) {
        String message = "Cannot find standard resource. filename:" + myFile + " class=" + myClass + ", classLoader:" + myClassLoader;
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          LOG.error(message);
        }
        else {
          LOG.warn(message);
        }

        myResolvedResourcePath = null;
        return null;
      }

      String path = FileUtil.unquote(resource.toString());
      // this is done by FileUtil for windows
      path = path.replace('\\','/');
      myResolvedResourcePath = path;
      return path;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Resource resource = (Resource)o;

      if (myClassLoader != resource.myClassLoader) return false;
      if (myClass != resource.myClass) return false;
      if (myFile != null ? !myFile.equals(resource.myFile) : resource.myFile != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myFile.hashCode();
    }

    @Override
    public String toString() {
      return myFile + " for " + myClassLoader;
    }
  }
}
