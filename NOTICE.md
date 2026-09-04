# Third-party notices

Gloaming bundles the following third-party material.

## Fonts

Both fonts are redistributed in this repository under the
**SIL Open Font License, Version 1.1**, whose full text is included as required.

| Font | Where | Copyright | License |
|---|---|---|---|
| Baloo 2 | `app/src/main/res/font/baloo2.ttf` | Copyright 2019 The Baloo 2 Project Authors (https://github.com/EkType/Baloo2) | [licenses/Baloo2-OFL.txt](licenses/Baloo2-OFL.txt) |
| Figtree | `app/src/main/res/font/figtree.ttf` | Copyright 2022 The Figtree Project Authors (https://github.com/erikdkennedy/figtree) | [licenses/Figtree-OFL.txt](licenses/Figtree-OFL.txt) |

Both are variable fonts; the app instances them rather than shipping a file per
weight. Neither is renamed, which the OFL requires of any modified copy — these
are unmodified.

## Icons

The row and effect icons in `app/src/main/res/drawable/ic_*.xml` are path data
from **google/material-design-icons**, licensed under the **Apache License,
Version 2.0**. They are used unmodified apart from being tinted at the call
site.

    https://github.com/google/material-design-icons
    https://www.apache.org/licenses/LICENSE-2.0

The launcher icon (`ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`)
and `ic_gloaming.xml` are original to this project.

## Build tooling

The Gradle wrapper (`gradle/wrapper/`, `gradlew`, `gradlew.bat`) is distributed
with Gradle under the Apache License, Version 2.0.
