# Angelica
* The little Angle [sic] that supports shaders while saving your fps from a certain death -boubou
* Is it obtuse, or accute? -Caedis
* It's a cute angle -mitchej123

**Alpha Quality - Here there be Dragons**


# Required Dependencies
* [UniMixins](https://github.com/LegacyModdingMC/UniMixins/releases) >= 0.1.14
* [GTNHLib](https://github.com/GTNewHorizons/GTNHLib/releases) >= 0.2.1
* NOTE: Some mods are not required, but a specific version is required if present - see: `Permanent Incompatibilities` below.

# Known (temporary) Incompatibilities
* Forge Relocation - Currently non Thread Safe; safe if not in use
* ProjectRed Frames - Currently non Thread Safe; safe if not in use
* EndlessIDs - Future compat planned
* FalseTweaks - Future compat planned
* SmoothFont - Future compat planned
* RPLE - Future compat planned

# Permanent Incompatibilities
* Optifine [Disabled, won't fix]
* Fastcraft [Disable, won't fix]
* BetterFPS [Won't fix]
* LWJGL3ify 1.5.4 and below - Use the latest version available, 1.5.7+
* ArchaicFix 0.6.2 and below - Use 0.7.0 or above
* Hodgepodge 2.4.3 and below - [Use 2.4.4 or above](https://github.com/GTNewHorizons/Hodgepodge/releases)
* CodeChicken[Core](https://github.com/GTNewHorizons/CodeChickenCore/releases)/[Lib](https://github.com/GTNewHorizons/CodeChickenLib/releases) <1.2.0 - Threading issues, use 1.2.0+
* Neodymium - Untested, but unlikely to be compatible.  (Chunk Meshing is definitely incompatible, other features might work depending on config)

# Credits
* ShadersMod
  * Original ShadersMods: karyonix, sonic ether, id_miner, daxnitro
  * eigenraven, Caedis, glowredman, and mitchej123 for the conversion of ASM to Mixin
  * eigenraven and vlaetansky for getting the original ShadersMod compiling again
* ArchaicFix
  * Includes graphic enhancements from [ArchaicFix](https://github.com/embeddedt/ArchaicFix/tree/4afb943751883b27a1f0aa7c5ba71698bc897e95) by embeddedt under the [LGPL](https://github.com/embeddedt/ArchaicFix/blob/main/LICENSE.md)
* NotFine
  * Includes modified version of [NotFine](https://github.com/jss2a98aj/NotFine/tree/a2652e24b5654f437fb7138f6d1f5e947a7d7125) by jss2a98aj under the LGPL
* Iris Shaders
  * The [Iris](https://github.com/IrisShaders/Iris) Team: coderbot, IMS212, Justsnoopy30, FoundationGames
  * Asek3 for the Iris Forge port [Oculus](https://github.com/Asek3/Oculus/tree/839ce8eca8cf0c4f6b7a1322b906e5c99125ca69)
  * Backported code under the LGPL
* Sodium
  * JellySquid & the CaffeineMC team, for making Sodium in the first place
  * Embeddedt for [Embeddium](https://github.com/embeddedt/embeddium/tree/dc59ca357c25beefd6288f0d1d40b4cd8e670ab8) under the [LGPL 3.0](https://github.com/embeddedt/embeddium/blob/16.x/forge/LICENSE)
* Reese's Sodium Options
  * FlashyReese for [Reese's Sodium Options](https://github.com/FlashyReese/reeses-sodium-options/tree/aa4ea67047cf53e54976e93c6bb1ab5ba16b142b) under the [MIT license](https://github.com/FlashyReese/reeses-sodium-options/blob/aa4ea67047cf53e54976e93c6bb1ab5ba16b142b/LICENSE.txt)
* Neodymium
  * Makamys for [Neodymium](https://github.com/makamys/Neodymium) - Various Tessellator -> Quad functionality taken under the LGPL 3
* MCPatcherForge
  * Original [MCPatcher](https://bitbucket.org/prupe/mcpatcher/src/master/): Paul Rupe
  * [Forge version](https://bitbucket.org/prupe/mcpatcher/src/master/): Mist475 under LGPL 3.0 with default ctm textures for glass and bookshelves by DarkBum under CC-BY-SA
* HUDCaching
  * Created by [Moulberry](https://github.com/Moulberry/MCHUDCaching), backported from [Patcher](https://github.com/Sk1erLLC/Patcher) mod by Alexdoru under the CC-BY-SA-NC 4.0 license
* Angelica
  * mitchej123, Omni, Makamys, Embeddedt, NanoLive, Caedis, Cardinalstar, Alexdoru, Eigenraven, mist475, Clepto for helping get Angelica up and running!
  * IMS, jss2a98aj, || dbp ||, roadhog360 for advice and moral support
* Forge 1.12+
  * Some files have been taken from Minecraft Forge under the LGPL 2.1 license and are marked as such
