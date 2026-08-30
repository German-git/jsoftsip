# GraalVM Native Image Attempt for JSoftSIP

## Date
August 2026

## Context
This document records the attempt to generate a native image of the JSoftSIP project using GraalVM, with the goal of producing a standalone executable without a JVM dependency on the target system.

## Closest Approach

The approach that came closest to working was using `native-maven-plugin` (org.graalvm.buildtools) configured in the `packager` module, with:

- GraalVM CE 22.0.2 as the build JDK
- `native-maven-plugin` version 1.1.9
- Manual extraction of native `.so` libraries from the JAR `javafx-graphics-21-linux.jar`
- Reflection configuration via JSON files in `META-INF/native-image/`
- Delayed initialization (`--initialize-at-run-time`) for JavaFX classes

## Steps Performed

### 1. Plugin Configuration in packager/pom.xml

The Maven `native` profile was added with the following plugin configuration:

```xml
<plugin>
    <groupId>org.graalvm.buildtools</groupId>
    <artifactId>native-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>create-native-image</id>
            <phase>package</phase>
            <goals>
                <goal>compile-no-fork</goal>
            </goals>
            <configuration>
                <imageName>jsoftsip-${project.version}-linux-x64</imageName>
                <mainClass>com.jsoftsip.launcher.JSoftSipApplication</mainClass>
                <buildArgs>
                    <buildArg>--no-fallback</buildArg>
                    <buildArg>--module-path ${project.build.directory}/mods</buildArg>
                    <buildArg>--add-modules java.sql,javafx.graphics,javafx.controls,javafx.fxml</buildArg>
                    <buildArg>-H:ConfigurationFileDirectories=...</buildArg>
                    <buildArg>--initialize-at-run-time=...</buildArg>
                </buildArgs>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### 2. Native Library Extraction

`maven-antrun-plugin` was configured to automatically extract `.so` files from the JAR `javafx-graphics-21-linux.jar`:

```xml
<unzip src="${project.build.directory}/mods/javafx-graphics-21-linux.jar"
       dest="${project.build.directory}/native-libs">
    <patternset>
        <include name="*.so"/>
    </patternset>
</unzip>
```

The extracted libraries were:
- `libprism_sw.so` - Prism software pipeline
- `libprism_es2.so` - OpenGL ES2 pipeline
- `libprism_common.so` - Common Prism code
- `libglass.so` - Native Glass toolkit
- `libglassgtk3.so` - GTK3 integration
- `libjavafx_font.so` - Font engine
- `libjavafx_font_pango.so` - Pango support
- `libjavafx_font_freetype.so` - FreeType support
- `libjavafx_iio.so` - IIO images
- `libdecora_sse.so` - SSE effects

### 3. Reflection Configuration

The file `packager/src/main/resources/META-INF/native-image/reflect-config.json` was created with more than 100 entries for JavaFX classes loaded via reflection:

- `com.sun.javafx.tk.quantum.QuantumToolkit`
- `com.sun.javafx.tk.Toolkit`
- `com.sun.prism.sw.SWPipeline`
- `com.sun.glass.ui.gtk.GtkPlatformFactory`
- `com.sun.prism.es2.X11GLFactory`
- FXML classes: `FXMLLoader`, `JavaFXBuilderFactory`
- JavaFX controls: `Button`, `Label`, `TextField`, etc.
- Rendering: `NGNode`, `NGGroup`, `NGRegion`, etc.

### 4. JNI Configuration

`jni-config.json` was created for classes that interact with JNI:
- `com.sun.glass.ui.Application`
- `com.sun.glass.ui.Screen`
- `com.sun.glass.ui.Window`
- `com.sun.glass.ui.View`
- `com.sun.prism.es2.GLFactory`

### 5. Packaging

The assembly descriptor (`native-tar.xml`) places:
- The `jsoftsip` executable in the root directory
- All `.so` files in the same directory (not in a `lib/` subdirectory)

This is necessary because native-image does not honor `-Djava.library.path` in the resulting executable.

## Results by Stage

### Stage 1: Initial Configuration
**Plugin used:** `native-maven-plugin:1.1.9`
**Error:** Goal `native-image` does not exist. The correct goal is `compile-no-fork`.
**Solution:** Correct the goal.

### Stage 2: GraalVM Detection
**Error:** `GraalVM installation directory not found`
**Solution:** Configure `JAVA_HOME` pointing to GraalVM.

### Stage 3: Missing JavaFX Modules
**Error:** `Module javafx.fxml not found`
**Solution:** Add `--module-path` pointing to `${project.build.directory}/mods`.

### Stage 4: Missing Graphics Pipeline
**Error:** `No toolkit found` / `Error initializing QuantumRenderer: no suitable pipeline found`
**Diagnosis:** The native `.so` libraries were inside the JavaFX JAR but not available to the native executable.
**Solution:** Extract the `.so` files and place them next to the executable.

### Stage 5: Classes Not Found by Reflection
**Error:** `ClassNotFoundException: com.sun.prism.es2.X11GLFactory`
**Error:** `ClassNotFoundException: com.sun.glass.ui.gtk.GtkPlatformFactory`
**Solution:** Add the missing classes to `reflect-config.json`.

### Stage 6: Final JNI Error (Blocking)
**Error:**
```
java.lang.UnsatisfiedLinkError: Unsupported JNI version 0xffffffff, 
required by .../libglassgtk3.so
```

**Location:** `com.oracle.svm.core.jni.JNILibraryInitializer.checkSupportedJNIVersion`

**Root cause:** The native library `libglassgtk3.so` (compiled for the traditional JVM) has a JNI version incompatible with the GraalVM native-image runtime. Native-image implements its own JNI system that is not fully compatible with all existing native libraries.

## Conclusion

Generating a native image for JSoftSIP **is not viable at this time** due to fundamental incompatibilities between:

1. **JavaFX and GraalVM Native Image:** JavaFX heavily depends on JNI to communicate with the native graphics system (GTK, X11, OpenGL). The JavaFX `.so` libraries are compiled for the traditional Oracle/OpenJDK JVM and contain JNI code that native-image cannot execute correctly.

2. **Incompatible JNI Version:** The error `Unsupported JNI version 0xffffffff` indicates that the native library has expectations about the JNI environment that native-image cannot satisfy. This is a known limitation of GraalVM native-image with complex JNI libraries.

3. **JavaFX Architecture:** JavaFX uses dynamic initialization patterns (Class.forName, ServiceLoader, native library loading at runtime) that are inherently difficult to handle in native-image, which requires complete static knowledge of all classes at compile time.

### Recommended Alternative

To distribute JSoftSIP as a standalone application, it is recommended to use **`jpackage`** (already functional in the project):

```bash
mvn clean package -Plinux
```

This generates an application image with an embedded JVM and trimming via `jlink`, which is the standard approach for JavaFX applications. The packaging profile lives in the `packager` module and is strictly opt-in: activate it explicitly with `-Plinux`; it never activates automatically on Linux.

### Recommendations for the Future

If the native image attempt is resumed in the future:

1. **Evaluate newer GraalVM CE 21+:** Newer versions of GraalVM may have better JNI support.
2. **Consider Gluon SubstrateVM:** Gluon has a specific ecosystem for JavaFX + native image, although it requires significant changes to the project architecture.
3. **Investigate headless JavaFX mode:** If the application can run without a GUI, complexity is drastically reduced.
4. **Evaluate GUI alternatives:** Consider migrating to a GUI library with better native image support (e.g. SWT, TornadoFX with specific configuration).

## Files Created/Modified During This Attempt

The following artifacts were created or modified during the native image attempt, but **they are no longer present in the current repository**. They were removed when the native image approach was abandoned in favor of `jpackage`:

- `packager/pom.xml` - `native` profile with plugin configuration
- `packager/src/assembly/native-tar.xml` - Assembly descriptor
- `packager/src/main/resources/META-INF/native-image/reflect-config.json` - Reflection configuration
- `packager/src/main/resources/META-INF/native-image/jni-config.json` - JNI configuration
- `packager/src/main/resources/META-INF/native-image/resource-config.json` - Resource configuration
- `pom.xml` - `graalvm.maven.plugin.version` property

The current `packager` module only contains the `linux` profile for `jpackage` packaging.

---

**Note:** This document was generated as part of the technical attempt documentation process for future reference by the development team.