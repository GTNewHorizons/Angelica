# Angelica
the little angle that supports shaders while saving your fps from a certain death - boubou

Not yet in an usable state

Original build instructions obtained via minecraftforums archeology:
```
Developing environment
 - Extract forge src to forge folder. Run "gradlew setupDecompWorkspace setupDevWorkspace" until it succeeded, then "gradlew build", then "gradlew eclipse".
 - Delete everything in src\main folder inside forge folder.
 - Extract smc-2.3.18-mc1.7.10-src.7z to src\main folder.
 - Read patch file shadersmod-mcf.patch
 - Find %userprofile%\.gradle\caches\minecraft\net\minecraftforge\forge\*\forgeSrc-*-sources.jar and extract it somewhere.
 -- Select files that need to be patched and copy them to src\main with correct folder structure.
 - Apply patch shadersmod-mcf.patch
 - In eclipse, add java_dev folder as another source folder.
 - In eclipse, exclude InitNames in src folder from build path. Use InitNames in java_dev folder instead.
 - Find %userprofile%\.gradle\caches\modules-2\files-2.1\net.minecraftforge\forge\*\*\forge-*-userdev.jar and extract conf\*.csv and conf\*.srg in it to forge\conf and rename packaged.srg to joined.srg.
```
