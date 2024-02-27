# A specification for models
This is borrowed from [the unofficial Minecraft wiki](https://minecraft.wiki/w/Tutorials/Models) under the CC BY-NC-SA 3.0.

The folder `assets/<namespace>/models/block` holds the model files for all the specified variants. The names of the files can be changed, but must always correspond with the names used in the variant files.

* `[TAG_compound]` The root tag
  * `[TAG_string]` **parent**: Loads a different model from the given path, in form of a [resource location](https://minecraft.wiki/w/Tutorials/Models#File_path). If both `"parent"` and `"elements"` are set, the `"elements"` tag overrides the `"elements"` tag from the previous model.
    * Can be set to `"builtin/generated"` to use a model that is created out of the specified icon. Note that only the first layer is supported, and rotation can be achieved only by using block states files.
  * `[TAG_byte]` **ambientocclusion**: Whether to use [ambient occlusion](http://en.wikipedia.org/wiki/Ambient_occlusion) (`true` - default), or not (`false`). Note:only works on Parent file
  * `[TAG_object]` **display**: Holds the different places where item models are displayed.
    * `[TAG_compound]` ***Position***: Named `thirdperson_righthand`, `thirdperson_lefthand`, `firstperson_righthand`, `firstperson_lefthand`, `gui`, `head`, `ground`, or `fixed`. Place where an item model is displayed. Holds its rotation, translation and scale for the specified situation. '''fixed''' refers to item frames, while the rest are as their name states. Note that translations are applied to the model before rotations.
      * `[TAG_list]` **rotation**: Specifies the rotation of the model according to the scheme `[x, y, z]`.
      * `[TAG_list]` **translation**: Specifies the position of the model according to the scheme `[x, y, z]`. The values are clamped between -80 and 80.
      * `[TAG_list]` **scale**: Specifies the scale of the model according to the scheme `[x, y, z]`. If the value is greater than 4, it is displayed as 4.
  * `[TAG_compound]` **textures**: Holds the textures of the model, in form of a [resource location](https://minecraft.wiki/w/Tutorials/Models#File_path) or can be another texture variable.
    * `[TAG_string]` **particle**: What texture to load particles from. This texture is also used as an overlay if you are in a [nether portal](https://minecraft.wiki/w/Nether_portal), and used for [water](https://minecraft.wiki/w/water) and [lava](https://minecraft.wiki/w/Lava)'s still textures.<sup>[[1]](https://bugs.mojang.com/browse/MC-240042)</sup> This texture is also considered a texture variable that can be referenced as `"#particle"`. Note: All breaking particles from non-model blocks are hard-coded, such as for [barriers](https://minecraft.wiki/w/Barrier).
    * `[TAG_string]` ***Texture variable***: Defines a texture variable with `name` and assigns it to a texture or another variable.
  * `[TAG_list]` **elements**: Contains all the elements of the model. They can have only cubic forms. If both `"parent"` and `"elements"` are set, the `"elements"` tag overrides the `"elements"` tag from the previous model.
    * `[TAG_compound]` An element.
      * `[TAG_list]` **from**: Start point of a cuboid according to the scheme `[x, y, z]`. Values must be between -16 and 32.
      * `[TAG_list]` **to**: Stop point of a cuboid according to the scheme `[x, y, z]`. Values must be between -16 and 32.
      * `[TAG_compound]` **rotation**: Defines the rotation of an element.
        * `[TAG_list]` **origin**: Sets the center of the rotation according to the scheme `[x, y, z]`.
        * `[TAG_string]` **axis**: Specifies the direction of rotation, can be `"x"`, `"y"` or `"z"`.
        * `[TAG_float]` **angle**: Specifies the angle of rotation. Can be 45 through -45 degrees in 22.5 degree increments.
        * `[TAG_byte]` **rescale**: Specifies whether or not to scale the faces across the whole block. Can be true or false. Defaults to false.
      * `[TAG_byte]` **shade**: Defines if shadows are rendered (`true` - default), not (`false`).
      * `[TAG_compound]` **faces**: Holds all the faces of the cuboid. If a face is left out, it does not render.
        * `[TAG_compound]` ***Face***: Named `down`, `up`, `north`, `south`, `west` or `east`. Contains the properties of the specified face.
          * `[TAG_list]` **uv**: Defines the area of the texture to use according to the scheme `[x1, y1, x2, y2]`. The texture behavior is inconsistent if UV extends below 0 or above 16. If the numbers of `x1` and `x2` are swapped (e.g. from `0, 0, 16, 16` to `16, 0, 0, 16`), the texture flips. UV is optional, and if not supplied it automatically generates based on the element's position.
          * `[TAG_string]` **texture**: Specifies the texture in form of the texture variable prepended with a `#`.
          * `[TAG_string]` **cullface**: Specifies whether a face does not need to be rendered when there is a block touching it in the specified position. The position can be: `down`, `up`, `north`, `south`, `west`, or `east`. It also determines the side of the block to use the light level from for lighting the face, and if unset, defaults to the side.
          * `[TAG_int]` **rotation**: Rotates the texture clockwise by the specified number of degrees. Can be 0, 90, 180, or 270. Defaults to 0. Rotation does not affect which part of the texture is used. Instead, it amounts to permutation of the selected texture vertexes (selected implicitly, or explicitly though `uv`).
          * `[TAG_int]` **tintindex**: Determines whether to tint the texture using a hardcoded tint index. The default value, -1, indicates not to use the tint. Any other number is provided to BlockColors to get the tint value corresponding to that index. However, most blocks do not have a tint value defined (in which case white is used). Furthermore, no vanilla block currently uses multiple tint values, and thus the tint index value is ignored (as long as it is set to something other than -1); it could be used for modded blocks that need multiple distinct tint values in the same block though.
