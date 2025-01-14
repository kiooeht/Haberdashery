# Contributing

## Console Command
`haberdashery <subcommand>`

### Subcommands
* `add <relicId>`
  * Attach a new relic to the current character.
  * Will first have you select what bone to relic will be attached too, then activate the Attachment Editor for the relic.
* `edit <relicId>`
  * Edit the attachment of the given relic for the current character.
* `test`
  * Gives the player all relics that have attachment info defined for the current character.
* `debug`
  * Enables debug visuals for player and relic skeletons.
* `saveall`
  * Saves all attachment info for all characters.
* `reload`
  * Reloads all attachment info from file. Resets current attachments to their saved values.
  
## Attachment Editor Controls

* **P** *(while selecting bone)* - Pause player animation.
* **F** - Saves all attachment info for the current character to a json file in the `haberdashery` subdirectory of the current working directory (game's directory).
* **T** - Translate (x, y position). Hold and move mouse. Right-click while active to cancel changes.
* **R** - Rotation. Hold and move mouse around center of screen. Right-click while active to cancel changes.
* **S** - Scale. Hold and move mouse. Right-click while active to cancel changes.
* **C** - Shear along x-axis. Hold and move mouse. Right-click while active to cancel changes.
* **V** - Shear along y-axis. Hold and move mouse. Right-click while active to cancel changes.
* **X** - Flip horizontally.
* **Y** - Flip vertically.
* **J** - Move one slot down the draw order.
* **K** - Move one slot up the draw order.
* **Shift+J** - Increase z-index draw order.
* **Shift+K** - Decrease z-index draw order.
* **Q** - Toggle large relic art (if it exists).
* **M** - Enter Mask Edit mode.

##### Draw Order
Draw order is defined first by one of the original slots of the character animation, then by a z-index. Relics will draw directly in front of whatever original slot they are assigned to and z-index is used to order multiple relics assigned to the same original slot.
**J/K** control which original slot the relic should be assigned to. **Shift+J/K** control the z-index.

##### Notes
Be careful when selecting the attachment bone after adding a new relic, some bones may be positioned on top of each other, making it hard to pick one of them instead of the other. The display in the top-left will tell you the name of the bone you are currently hovering over to help with this. (For example, on the Ironclad, the `root` and `shadow` bones are located on top of each other but you almost always want to pick the `root` bone instead of the `shadow`).

## Mask Edit Controls

* **M** - Save mask to attachment, exit Mask Edit mode.
* **R** - Reset mask to last saved.
* **N** - Toggle mask view mode (views the raw mask instead of relic with mask applied).
* **Left click** - Draw black on mask (removes parts of relic image).
* **Right click** - Draw white on mask (adds parts of relic image).
* **Scroll Up** - Increase brush size.
* **Scroll Down** - Decrease brush size.
* **Ctrl+Scroll Up** - Zoom in.
* **Ctrl+Scroll Down** - Zoom out.
