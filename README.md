## Overview
The Workspace Plugin was built for IntelliJ and Android Studio to support working on source code that spans multiple repositories in a single Gradle workspace and IDE instance.  This way you can build and test against source for libraries you may need to make changes in instead of relying on clumsy SNAPSHOT workflow that requires multiple IDE instances and artifact deployments.

## Features
- Supports adding and removing modules as source from an IntelliJ or Android Studio workspace with a single click (currently Gradle only)
- When projects are added or removed from a workspace, dependencies of all modules in the workspace (that the plugin knows about) are automatically adjusted to account for state of the dependency so they build against the appropriate artifact/source
- When opening a project in IntelliJ, dependencies are adjusted to match the modules included in the workspace.
- Multiple window support so you can work in multiple workspaces and IDE instances at the same time.

## General Implementation Details

- The plugin relies on a set of regular expressions to parse dependencies for both included projects (`settings.gradle`) and module's dependencies (`build.gradle`).  
  - If dependencies aren't matched by the regex, they're ignored (they're not lost, just not auto-adjusted if workspace that match are a part of the workspace).
- Included modules are discovered during refresh and on initialization via settings.gradle parsing.  
  - This allows the plugin to also discover newly added workspace if they're already a part of your workspace. 
- File "lines" are determined differently before run through regular expressions
  - `build.gradle` - Split on `\n` only
  - `settings.gradle` - Split on `,` and by `\n`
- Supported modules are determined by modules present in your `workspace.manfiest` file.

### workspace.manifest format

```json
{  
   "settingsPath": "<relative path to settings.gradle from project root (can be empty)>",
   "name": "<workspace_name>",
   "modules": [
       {
           "name": "<artifact_name>",
           "path": "<relative_path_to_module_source>",
           "group": "<artifact_group>"
       },
       {
           "name": "<artifact_name>",
           "path": "<relative_path_to_module_source>",
           "group": "<artifact_group>"
       },
       {
           "name": "<artifact_name>",
           "path": "<relative_path_to_module_source>",
           "group": "<artifact_group>"
       }
   ]
}
```

### Example workspace.manifest
```json
{  
    "settingsPath": "",
    "name": "example-workspace",
    "modules": [
        {
            "name": "example-1",
            "path": "../example-1/example",
            "group": "com.example.group"
        },
        {
            "name": "random-library",
            "path": "../random-library/random",
            "group": "com.example.random"
        },
        {
            "name": "example-4",
            "path": "../../../../../example-4/example",
            "group": "com.foo.bar"
        }
    ]
}
```

## Workspace Refresh Flow 

#### Plugin Algo
Upon a user triggered workspace refresh, the plugin performs the following actions.
1. Determine included modules (read `settings.gradle`)
1. Gather Dependencies for ALL potentially supported modules (current dependencies per module)
1. Rebuild module info graph (included modules that depend on eachother)
1. Update included modules from bottom up (Modify `build.gradle` filess for included modules)
1. Updated included modules so we know which modules to modify (included)
1. Ensure included modules' dependencies match include (modify `build.gradle` to account for source/remote)

## Module Swap Flow

#### Plugin Algo
Upon a user triggered module swap (source -> artifact or artifact -> source), the plugin performs the following actions.
1. Add/Remove module to/from include
1. Updated `settings.gradle` to add/remove module from workspace
1. Ensure included modules' dependencies match include (modify `build.gradle` to account for source/remote)

## Limitations

- Sub-projects names must contain no more than 2 colons (limitation of the regex used)
- If IntelliJ/Android Studio is opened and a library is included as source already, conversion to artifact (exluding source from workspace) will result in an unknown version number in the `build.gradle` because there's no way to know the desired version if there isn't one to read initially (should be restorable easily by using VCS tools to see the diffs).
- Manual changes to `build.gradle` are not automatically picked up by the plugin so manual refresh might be needed if changes result in the plugin window being out of sync with the `build.gradle` files.
- Currently, modules can only be included as the same name as their artifact name (IE - `com.example.group:artifact-name:1.2.34` would have to be included as `artifact-name`).  Down the road, we could provide more information in the manifest to support this if desired. 
- settings.gradle formatting will be overwritten despite functionality remaining in tact.
- Workspace manifest file is currently hardcoded to live at `<project_root>/workspace.manifest`
- `workspace.manifest` currently needs to be hand edited.

## Feature Roadmap

- Expose file chooser to allow user to point to `workspace.manifest` file
- Expose file chooser to allow user to point to directory or `build.gradle` and add a project to the `workspace.manifest`
- Allow plugin to work with multiple workspaces and hotswap between them.
- Maven support (stretch)
- Possibly use gradle tasks to determine dependencies (requires a lot of work and might result in worse performance)
- Publish to Jetbrains plugin repository

## Development

#### Building and Testing

- IntelliJ Sandbox (Fastest) - see [Running and Debugging](https://www.jetbrains.com/help/idea/running-and-debugging-plugins.html)
- Android Studio
  - Build the plugin into a zip file
  - Find the zip
  - Uninstall the current plugin from Android Studio if installed
  - Install the plugin from the zip file you built
  - Restart Android Studio

### Plugin Dev Nuances
- IntelliJ uses virtual files (see [VFS](https://www.jetbrains.org/intellij/sdk/docs/basics/virtual_file_system.html)) which are potentially stale
  - To account for this, files are forcefully refreshed before attempting any reads or writes on them. This ensures the plugin is always using the most up to date info on the files and prevents us from having to watch files for changes.
- To get multi-window support to work, I had to create a Project Component which starts a [Tool Window](https://www.jetbrains.org/intellij/sdk/docs/user_interface_components/tool_windows.html) manually.
  - [Components](https://www.jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_components.html)
  - [Tool Window and Component](https://intellij-support.jetbrains.com/hc/en-us/community/posts/115000133510-Custom-ToolWindow-implementation-questions)
- When trying to build/run with a normal JDK, I was getting random hangs while indexing.  To fix, you must download and build the Jetbrains JDK and have the IntelliJ sandbox target this for debug builds
  - See ([Selecting JDK for IntelliJ To Run Under](https://intellij-support.jetbrains.com/hc/en-us/articles/206544879-Selecting-the-JDK-version-the-IDE-will-run-under))
  - [Running and Debugging](https://www.jetbrains.com/help/idea/running-and-debugging-plugins.html)
  - [Wrong JDK Type For Plugin](https://intellij-support.jetbrains.com/hc/en-us/community/posts/206146489-getting-started-with-plugin-development-wrong-jdk-type-for-plugin-)
- General IDEA configs and such
  - [Configuration, Caches, Plugin, and Log Paths](https://intellij-support.jetbrains.com/hc/en-us/articles/206544519-Directories-used-by-the-IDE-to-store-settings-caches-plugins-and-logs)
- IDEA has different thread pools for reads and writes and will throw exceptions if you try to write from a read only thread
  - To deal with this, we simply use Write threads for all our async stuff.  It's relatively fast enough for what we're doing.
  - See [General Threading](https://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/general_threading_rules.html)

### Useful Links/Tools

- Java regular expression tester with multiple inputs and java string representation
  - http://www.regexplanet.com/advanced/java/index.html
- Plugin Samples
  - https://github.com/JetBrains/intellij-sdk-docs
- IntelliJ Log File Locations
  - https://intellij-support.jetbrains.com/hc/en-us/articles/206544519