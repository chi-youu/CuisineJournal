# Third-party notices

This project incorporates and modifies source code and resources from
Farmer's Delight: Meal Mastery (XircTwerk), version 1.0.2, commit
58c6daa726652ba84a3ed2c33eabb8adca8e376f. The original project is
licensed under MIT; its copyright notice and full license are preserved in
MEAL_MASTERY_LICENSE. The single-loader integration and subsequent food
classification changes are modifications to that source.

The original Meal Mastery source is available at
https://github.com/XircTwerk/Meal-Mastery/tree/1.20.1.

## Build Dependencies

Build dependencies are resolved from publisher-hosted Modrinth Maven artifacts,
not redistributed as repository JARs or nested mod dependencies:

- Just Enough Items 15.49.0.188: https://modrinth.com/mod/jei (MIT).
- Pufferfish's Skills 0.19.2 (compile) and 0.18.3 (compatibility regression only):
  https://modrinth.com/mod/skills (custom license).
- Just Enough Effect Descriptions 1.20-2.2.6:
  https://modrinth.com/mod/just-enough-effect-descriptions-jeed (All Rights Reserved).

Youkai's Homecoming compatibility uses optional host names/reflection, not copied
host classes or a required compile dependency: https://modrinth.com/mod/youkaishomecoming.

The Forge MDK's original license is preserved in [docs/third-party/forge-mdk/LICENSE.txt](docs/third-party/forge-mdk/LICENSE.txt).
Its CREDITS.txt is also retained; these notices do not license Cuisine Journal's original additions.
See LICENSE.md for the scope of Cuisine Journal's own rights declaration.

## Nutrition HUD Artwork

The overflow hunger sprites are a recoloring of Minecraft's hunger icons;
the original Minecraft artwork belongs to Mojang / Microsoft. The orange
saturation sprites follow AppleSkin's four-stage outline artwork, recolored
in the user-supplied palette. AppleSkin by squeek502 is published under
the Unlicense: https://github.com/squeek502/AppleSkin/blob/1.20-forge/LICENSE.
The final sprite sheets were extracted from the user's approved reference
image. No Twilight Spark's Delight texture files are included.
