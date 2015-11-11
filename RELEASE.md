# Android Studio Release Process

These changes (except for step 1) are typically only done in a
*release* branch (e.g. studio-1.4-release), and only for release
candidate and stable builds (not the release branch for milestones and
betas.)

 1. Make sure the version number is correct in
    ../adt/idea/adt-branding/src/idea/AndroidStudioApplicationInfo.xml

    Example:

    ```
      <version major="1" minor="4" micro="0" patch="7" full="{0}.{1} RC 1" eap="false" />
                     ~~~       ~~~       ~~~       ~~~      ~~~~~~~~~~~~~~
    ```

 2. Also make sure that the eap= flag in the same file is correct.

    ```
     <version major="1" minor="4" micro="0" patch="7" full="{0}.{1} RC 1" eap="false" />
                                                                          ~~~~~~~~~~~
    ```

 3. Update the "selector"; the directory name used in the user's home
    directory (Windows/Linux) or in ~/Library/Preferences (OSX).

    For preview builds we set it to
        `AndroidStudioPreviewX.Y`
    and for release builds to
        `AndroidStudioX.Y`.

    The selector is specified in `build/scripts/studio_properties.gant`:

    ```
    def selector = "AndroidStudioPreview1.4"
                                 ~~~~~~~~~~
    ```

 4. Update the settings importer.

    Edit
    `platform/platform-impl/src/com/intellij/openapi/application/ConfigImportHelper.java`
    such that it imports from the previous few versions (e.g. previous stable
    versions, as well as most recent preview.)

    Example CL: https://android-review.googlesource.com/#/c/161783/


 5. Make sure assertions are turned off.

    This is controlled by `build/scripts/utils.gant`:

    ```
    binding.setVariable("common_vmoptions", "-XX:+UseConcMarkSweepGC -XX:SoftRefLRUPolicyMSPerMB=50 -da " + ...
                                                                                                   ~~~~~
    ```

    This should be set to -da in production builds.

 6. Make sure -OmitStackTraceInFastThrow is removed.

    This is controlled by `build/scripts/utils.gant`:

    ```
    binding.setVariable("common_vmoptions", "... -XX:SoftRefLRUPolicyMSPerMB=50 -ea -XX:-OmitStackTraceInFastThrow " + ...
                                                                                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    ```

 7. Turn off null checking.

    Edit .idea/compiler.xml and make sure null assertions are disabled by
    adding the following line:

    ```
         <option name="DEFAULT_COMPILER" value="Javac" />
    +    <addNotNullAssertions enabled="false" />
         <excludeFromCompile>
    ```

    (We don't leave it in with enabled="true" because the IDE will automatically
    remove it and then leave the .idea/compiler.xml file in an edited state
    for all developers who open the project.)

 8. Turn off CLASS retention in
    platform/annotations/src/org/jetbrains/annotations

    ```
    --- a/platform/annotations/src/org/jetbrains/annotations/NotNull.java
    +++ b/platform/annotations/src/org/jetbrains/annotations/NotNull.java
    @@ -27,7 +27,7 @@ import java.lang.annotation.*;
      * @author max
      */
     @Documented
    -@Retention(RetentionPolicy.CLASS)
    +@Retention(RetentionPolicy.SOURCE)
     @Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
     public @interface NotNull {
       /**
    ```

    etc.
