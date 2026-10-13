# JAR Packager

A JavaFX desktop app for turning Java applications into native packages with
`jpackage`. A three-step workspace guides you through the source, identity/runtime,
and build options, with a package summary before building.

## Build and run

Use JDK 25 or newer. JavaFX is supplied by Maven; a JavaFX-bundled JDK is not required.

```sh
mvn javafx:run
mvn test
mvn package
```

## Package an application

1. Choose the output: **EXE/MSI** on Windows, **DMG/PKG** on macOS, or **app-image**.
2. Select a JAR. The app reads its manifest and fills in the main class, application
   name, input folder, and main JAR. Installers can also be built from an existing
   application image (a `.app` bundle on macOS).
3. Review the destination, name, and version. Add an icon, metadata, modules, or
   custom runtime if needed.
4. Review the package summary, set Java/Windows options, and click **Build package**.
   The progress dialog shows the actual command, output, and any errors.

Packages must be built on their target platform. Windows-only options are hidden
and omitted on macOS. App-image and prebuilt-image modes omit incompatible options.
Signing and notarization are not configured by this app.

### Destination

The destination starts at `<working-directory>/dist` and follows the source folder.
For a JAR in `/home/example`, it defaults to `/home/example/dist`. For an existing
app image, it defaults to a `dist` folder beside that image. A destination you
enter or browse to explicitly is preserved when changing sources. The output
folder need not exist yet; it is created during packaging.

### Icons and automatic conversion

- macOS: select **PNG or ICNS**. PNG is automatically converted into a multi-size
  `.icns` file using macOS's built-in `iconutil`, directly from the app.
- Windows: select **PNG, JPG, or ICO**. PNG/JPG is converted into `.ico`.
- Paste an image path and click **Convert**, or let the app convert it when building.
- Successful conversion updates the icon field to the new native icon. The original
  selected **PNG is deleted only after the icon is successfully saved**. JPG files
  are kept. Other PNG files in the folder are never removed.
- Failed conversions keep the original image. If the source changes while conversion
  is running, it is kept. If deletion fails, the app reports that the icon was saved
  but the source could not be removed.
- Existing icons are not overwritten: a numeric suffix is used when necessary.
  Temporary iconset images are cleaned up after conversion.
- The macOS converter preserves aspect ratio and transparency, creating standard
  and Retina sizes up to 1024×1024. Use a high-resolution PNG for the best result.

### Automatic Java modules

**Add modules** is filled automatically using `jdeps` from the same installed JDK
used for packaging. Selecting a JAR or changing its input folder, main JAR, or
module path refreshes the analysis in the background. The app scans the main JAR
and dependency JARs in the input folder, including `lib/`, while excluding the
output folder. Named modules on **Module path** are included in resolution.

You can edit the detected list or click **Detect** to scan again after replacing
files on disk. Manual additions are preserved, and results from an old selection
cannot overwrite a newer one. Multi-release JARs are analyzed for the packaging
JDK's version. Detection is skipped when using a prebuilt app image or runtime.

If libraries are missing or analysis fails, **Analysis details** explains the issue;
no incomplete defaults are inserted. Nested-JAR launchers (such as Spring Boot
archives) require extracting libraries and appropriate packaging configuration.
Static analysis cannot reliably discover modules loaded only through reflection,
service providers, or other dynamic loading: add those manually if your app needs
them, and test the packaged application. See the [jdeps documentation](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jdeps.html).

## Environment check

Click **Check** to inspect installed tools. Green rows mean the tool ran successfully;
red rows mean setup needs attention. Click a red row to open a custom dialog with
installation steps and a link to the official guide. Green rows also show details.
The Check button turns green/red to summarize the result.

| Platform | Checks |
| --- | --- |
| All | Java, JDK compiler, jpackage, jlink, and jdeps from the same JDK |
| Windows | WiX for EXE/MSI: candle + light for WiX 3, or modern WiX with JDK 24+ |
| macOS | hdiutil, pkgbuild, productbuild, iconutil, and Xcode Command Line Tools / SetFile |

WiX is not needed for app-image output. Xcode tools enable custom DMG icons and
signing setup; plain unsigned packages can work without them. Signing certificates
are separate. Checks cover tool availability, not whether a particular application
or installer configuration is valid.

### Automatic JDK discovery

There is no manual JDK selector. The app discovers a full JDK from `JAVA_HOME`, the
running JVM, resolved Java executables on PATH, and standard vendor installation
folders. Check and packaging use the same discovery logic. Commands are invoked
by their full paths, avoiding dependence on Oracle's limited Windows `javapath`
shortcuts. Standard WiX and global .NET tool folders are also discovered and shared
with the packaging subprocess.

If a tool is missing, follow its red-row instructions. After installing tools or
changing environment variables, restart the app and check again. To make Oracle
JDK tools callable in Command Prompt too, set `JAVA_HOME` to the JDK home (for example
`C:\Program Files\Java\jdk-27`), add `%JAVA_HOME%\bin` to PATH, and open a new prompt.

References: [Oracle packaging prerequisites](https://docs.oracle.com/en/java/javase/25/jpackage/packaging-overview.html),
[WiX installation](https://wixtoolset.org/docs/intro/),
[Apple Command Line Tools](https://developer.apple.com/documentation/xcode/installing-the-command-line-tools/).
