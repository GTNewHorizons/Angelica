# Angelica<img src="img/logo-readme.png" align="right">

<sup>**[CurseForge](https://www.curseforge.com/minecraft/mc-mods/angelica) | [Modrinth](https://modrinth.com/mod/angelica)**</sup>

🌐 **Мови:** [English](README.MD) | [Українська](README_uk.MD)

* Маленький Кут [sic], що підтримує шейдери, водночас рятуючи ваші FPS від вірної смерті — boubou
* Він тупий чи гострий? — Caedis
* Це милий кут — mitchej123
* Це янгол, який витягує вас із пекла OptiFine — Roadhog360

## **Бета-якість — Тут водяться дракони**
* Шейдери — Бета
   * Complementary + Euphoria Patches є головною ціллю для сумісності
   * Sildurs, Seus та будь-які інші шейдери можуть працювати

## Базовий профіль
Цільовим є базовий профіль GL 3.3+
* Емуляція FFP через згенеровані GLSL-шейдери
* Емуляція списків відображення
* Програмні стеки матриць та керування станом через GLSM
* Конвертація GL_QUADS → GL_TRIANGLES
* Автоматична трансформація шейдерів з модів, які використовують вбудовані функції GLSL виключно для сумісності (`gl_ModelViewMatrix`, `texture2D` тощо)


# Обов’язкові залежності
* [UniMixins](https://github.com/LegacyModdingMC/UniMixins/releases) >= 0.1.23
* [GTNHLib](https://github.com/GTNewHorizons/GTNHLib/releases) >= 0.11.19
* ПРИМІТКА: Деякі моди не є обов’язковими, але якщо вони встановлені, вимагається їхня конкретна версія — див. розділ `Постійна несумісність` нижче.

# Відома (тимчасова) несумісність
* Forge Relocation — Наразі не є потокобезпечним; безпечно, якщо не використовується
* ProjectRed Frames — Наразі не є потокобезпечним; безпечно, якщо не використовується

# Можлива несумісність
* EndlessIDs — Коміти для сумісності були зроблені, може працювати, а може й ні — не повністю протестовано

# Відома несумісність
* FalseTweaks — Наразі не планується (Шукаємо волонтерів) [Може працювати, якщо фактично вимкнути більшу частину мода]
* RPLE — Наразі не планується (Шукаємо волонтерів)
* LiteLoader — Встановіть [LiteloaderLoader](https://github.com/Midnight145/LiteloaderLoader)

# Постійна несумісність
* OptiFine [Вимкнено, виправлятись не буде]
* FastCraft [Вимкнено, виправлятись не буде]
* BetterFPS [Вимкнено, виправлятись не буде]
* OptimizationsAndTweaks [Вимкнено, виправлятись не буде]
* [Amazing Trophies 1.2.1 і нижче](https://github.com/GTNewHorizons/Amazing-Trophies/releases) — Використовуйте 1.2.2+
* [LWJGL3ify 1.5.4 і нижче](https://github.com/GTNewHorizons/lwjgl3ify/releases/) — Використовуйте найновішу доступну версію, 1.5.7+
* [ArchaicFix 0.6.2 і нижче](https://www.curseforge.com/minecraft/mc-mods/archaicfix/files/all?page=1&pageSize=20) — Використовуйте 0.7.0 або вище
* [Hodgepodge 2.4.3 і нижче](https://github.com/GTNewHorizons/Hodgepodge/releases) — Використовуйте 2.4.5 або вище
* CodeChicken[Core](https://github.com/GTNewHorizons/CodeChickenCore/releases)/[Lib](https://github.com/GTNewHorizons/CodeChickenLib/releases) <1.2.0 — Проблеми з багатопотоковістю, використовуйте 1.3.0+
* Neodymium — Не перевірено, але навряд чи буде сумісним. (Побудова сітки чанків точно несумісна, інші функції можуть працювати залежно від конфігурації)
* SmoothFont — Додано еквівалентний функціонал
* ClientFixer — Використовуйте пакет ресурсів, як-от [GTNH-FTI-Standard-Font](https://github.com/Eldrinn-Elantey/GTNH-FTI-Standard-Font)

# Подяки
* ShadersMod
  * Оригінальні ShadersMods: karyonix, sonic ether, id_miner, daxnitro
  * eigenraven, Caedis, glowredman та mitchej123 за конвертацію ASM у Mixin
  * eigenraven та vlaetansky за те, що змусили оригінальний ShadersMod знову компілюватися
* ArchaicFix
  * Включає графічні покращення з [ArchaicFix](https://github.com/embeddedt/ArchaicFix/tree/4afb943751883b27a1f0aa7c5ba71698bc897e95) від embeddedt за ліцензією [LGPL](https://github.com/embeddedt/ArchaicFix/blob/main/LICENSE.md)
* NotFine
  * Включає модифіковану версію [NotFine](https://github.com/jss2a98aj/NotFine/tree/a2652e24b5654f437fb7138f6d1f5e947a7d7125) від jss2a98aj за ліцензією LGPL
* Iris Shaders
  * Команда [Iris](https://github.com/IrisShaders/Iris): coderbot, IMS212, Justsnoopy30, FoundationGames
  * Asek3 за порт Iris для Forge [Oculus](https://github.com/Asek3/Oculus/tree/839ce8eca8cf0c4f6b7a1322b906e5c99125ca69)
  * Бекпортований код за ліцензією LGPL
* Sodium / Celeritas
  * JellySquid та команді CaffeineMC за створення Sodium
  * Embeddedt за Celeritas, що використовується за ліцензією LGPL 3.0
* Reese's Sodium Options
  * FlashyReese за [Reese's Sodium Options](https://github.com/FlashyReese/reeses-sodium-options/tree/aa4ea67047cf53e54976e93c6bb1ab5ba16b142b) за ліцензією [MIT](https://github.com/FlashyReese/reeses-sodium-options/blob/aa4ea67047cf53e54976e93c6bb1ab5ba16b142b/LICENSE.txt)
* Neodymium
  * Makamys за [Neodymium](https://github.com/makamys/Neodymium) — Різноманітний функціонал Tessellator -> Quad взято за ліцензією LGPL 3
* MCPatcherForge
  * Оригінальний [MCPatcher](https://bitbucket.org/prupe/mcpatcher/src/master/): Paul Rupe
  * [Версія для Forge](https://github.com/mist475/MCPatcherForge): Mist475 за ліцензією LGPL 3.0 з типовими ctm-текстурами для скла та книжкових полиць від DarkBum за ліцензією CC-BY-SA
* HUDCaching
  * Створено [Moulberry](https://github.com/Moulberry/MCHUDCaching), бекпортовано з мода [Patcher](https://github.com/Sk1erLLC/Patcher) від Alexdoru за ліцензією CC-BY-SA-NC 4.0
* Angelica
  * mitchej123, Omni, Makamys, Embeddedt, NanoLive, Caedis, Cardinalstar, Alexdoru, Eigenraven, mist475, Clepto, kurrycat за допомогу в запуску та налаштуванні Angelica!
  * IMS, jss2a98aj, || dbp ||, roadhog360 за поради та моральну підтримку
* Forge 1.12+
  * Деякі файли були взяті з Minecraft Forge за ліцензією LGPL 2.1 і позначені відповідно
* FRAPI та Indigo
  * API для побудови моделей значною мірою натхненний/скопійований з Fabric; автори оригінального коду (upstream) можуть використовувати його за оригінальною ліцензією Apache 2.0, 
    див. LICENSE у `com.gtnewhorizons.angelica.models`
* Dynamic Lights
  * LambdAurora за [LambDynamicLights](https://github.com/LambdAurora/LambDynamicLights) за ліцензією MIT, та anthxnymc за порт для Forge [DynamicLightsReforged](https://github.com/anthxnymc/DynamicLightsReforged), також за ліцензією MIT
* Embeddedt та Ferri_Arnus за [glsl-transformation-lib](https://github.com/TauMC/glsl-transformation-lib)
  * Також за роботу над [Monocle](https://github.com/ferriarnus/Monocle), який використовувався як еталон для реалізації glsl-transformation-lib
  * Код з обох glsl-transformation-lib та Monocle взято за їхніми відповідними ліцензіями LGPL 3.0
* FalseTweaks
  * FalsePattern за [FalseTweaks](https://github.com/falsepattern/falsetweaks) — Оптимізації рендерингу предметів взято з FalseTweaks за ліцензією LGPL 3.0
* Beddium
  * Venn та Falsepattern за [Beddium](https://github.com/vfx-dev/Beddium) — MinimapRenderer, SafeChunkTracker та деякі команди для налагодження адаптовано за ліцензією LGPL 3.0
* [Mesa](https://gitlab.freedesktop.org/mesa/mesa) — Код портований з Mesa та/або натхненний нею — за ліцензією MIT
  * Розподілювач ID списків відображення портовано з [util_idalloc](https://gitlab.freedesktop.org/mesa/mesa/-/blob/main/src/util/u_idalloc.c) (Copyright 2017 Valve Corporation)
  * Генерація вершинних шейдерів FFP натхненна ffvertex_prog.c (Copyright 2007 VMware, Inc.)
  * Генерація фрагментних шейдерів FFP натхненна ff_fragment_shader.c (Copyright 2007, 2009 VMware, Inc. / 2010-2011 Intel Corporation)
  * Оптимізація параметрів туману натхненна st_nir_lower_fog.c (Copyright 2023 Google LLC) та prog_statevars.c (Copyright 1999-2007 Brian Paul)
