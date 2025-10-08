# JAR Packager Tool

A JavaFX desktop application that simplifies the process of packaging JAR files into native executables (EXE/MSI) for Windows using the `jpackage` tool.

## Features

- **Step-by-Step Wizard Interface**: Intuitive three-step process for easy configuration
- **JAR Analysis**: Automatically extracts main class and other information from JAR manifest
- **Multiple Output Formats**: Supports both EXE and MSI packaging
- **Dynamic Java Options**: Add multiple Java runtime options with automatic quoting
- **Icon Support**: Convert PNG/JPG images to ICO format and apply to your application
- **Windows-Specific Options**: Configure shortcuts, menu entries, and upgrade capabilities
- **Real-time Progress Tracking**: View packaging progress and detailed output in a modal dialog

## Prerequisites

- Java Development Kit (JDK) 14 or higher
- `jpackage` tool (included in JDK 14+)

### Step-by-Step Guide

#### Step 1: Basic Information
1. Click "Browse..." next to "Select JAR File" to choose your application JAR file
2. The tool will automatically analyze the JAR and populate:
   - Main Class (from JAR manifest)
   - Application Name (based on JAR filename)
   - Main JAR File path
   - Input Directory (JAR file location)
3. Review and modify the following fields if needed:
   - Application Name
   - App Version (optional)
   - Package Type (EXE or MSI)
   - Destination Directory (where the packaged application will be saved)
4. Click "Next >>" to proceed to Step 2

#### Step 2: Advanced Options
1. Icon File (optional):
   - Click "Browse..." to select an icon
   - Supports PNG, JPG, and ICO formats
   - Non-ICO images will be automatically converted
2. Application Metadata (optional):
   - Vendor
   - Copyright
   - Description
   - License File
3. Java Module System (optional):
   - Add Modules (for modular applications)
   - Module Path (directory containing modules)
4. Click "Next >>" to proceed to Step 3

#### Step 3: Windows Options
1. Windows Configuration:
   - Upgrade UUID (for application updates)
   - Menu Group (Start menu folder name)
2. Windows Options (check/uncheck as needed):
   - Create desktop shortcut
   - Add to Windows Start menu
   - Enable directory chooser in installer
3. Java Options Management:
   - Enter Java runtime options in the text field (e.g., `-Xmx2g`, `-Dfile.encoding=UTF-8`)
   - Click "Add" to add additional Java options
   - Each option will be automatically quoted for proper execution
   - Click "Remove" to delete unwanted options
4. Click "Package" to start the packaging process

### Packaging Process
1. A progress dialog will appear showing real-time output
2. The process may take a few minutes depending on your application size
3. Upon completion, you'll see a success message with the output file location
4. If packaging fails, detailed error information will be displayed in the progress dialog

## Key Functionality

### Java Options Management
- Add multiple Java runtime options using the "Add" button
- Each option is automatically quoted to ensure proper execution
- Examples: `-Xmx2g`, `-Dfile.encoding=UTF-8`

### Icon Conversion
- Supports PNG, JPG, and ICO formats
- Automatically converts non-ICO images to ICO format
- Applies selected icon to the packaged application

### Windows Options
- Create desktop shortcuts
- Add to Windows Start menu
- Enable directory chooser in installers
- Set upgrade UUID for application updates

## Building Executables

To create a native executable of the JAR Packager Tool itself:

```bash
mvn clean package
jpackage --input target --name JAR-Packager --app-version 1.0 --main-class it.saimao.jarpackager.Main --main-jar jar-packager.jar --type exe --dest target
```

## Troubleshooting

### Common Issues

1. **jpackage not found**: Ensure you're using JDK 14 or higher
2. **Icon conversion failed**: Check that your image files are valid PNG or JPG files
3. **Packaging fails**: Verify all paths are correct and accessible

### Warning Messages

You may see this warning when running the application:
```
WARNING: java.lang.System::load has been called by com.sun.glass.utils.NativeLibLoader
```

This is a JavaFX internal warning that can be resolved by adding the JVM argument:
```
--enable-native-access=javafx.graphics
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Built with JavaFX
- Uses the `jpackage` tool included in JDK 14+
