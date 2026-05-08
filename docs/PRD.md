## 4.1 Project Initialization and Origin Management

The initialization process converts a standard Litematica selection into a managed VCS project. This flow handles naming, area definition, and the first version save in one seamless sequence.

### 4.1.1 Project Creation and Automatic Commit

1. **Define Areas**: Use Litematica in "Normal" selection mode to define your build. This can include one or **multiple sub-regions**.
2. **The Entry Point**: Open the Litematica menu and navigate to:
   **Area Selection** -> **Save Schematic**
3. **Initiate VCS**: Click the **[Create VCS Project]** button located at the bottom of the page.
4. **Naming and Finalizing**: A prompt appears for you to name your project. Upon clicking confirm:
   - The project is created.
   - All active sub-regions are imported into the project.
   - **The Root Version (Version 1)** is automatically captured and saved.
5. **Automatic Manager Entry**: The system immediately transitions you into the **Project Manager** dashboard for that project.

### 4.1.2 Setting and Adjusting the Project Origin

The **Project Origin** is the universal zero-point anchor for the project.

1. **Default Placement**: Upon project creation, the Project Origin is automatically set to **Position 1 (Pos1)** of your Litematica selection.
2. **Integrated Origin Controls**: In the top right of the Project Manager, you can find the origin management tools to refine this anchor:

- **Coordinate Fields**: Manually type X, Y, and Z values to set a specific world coordinate.
- **Nudge Buttons**: Use the **[plus/minus]** buttons to shift the origin block-by-block.
- **Move to Player**: Click this to snap the Project Origin to your current stance.

3. **Visual Representation**: The Project Origin is visually based on Litematica's **Manual Origin** indicator. It renders in the world as a **transparent cyan box**. This distinct color differentiates the project's master anchor from the standard orange manual origin, while maintaining the familiar look and feel of the base mod.

### 4.1.3 Workspace Focus Mode

To maintain project integrity, the system enforces a strict focus mode once a project is active.

1. **Interface Locking**: The standard **Area Selection Browser** in the main Litematica menu becomes **grayed out** and non-interactive. You cannot create new standalone selections while the project is active.
2. **Unified Control**: You retain full control of your areas through the **Area Editor** button in Litematica menu and the Project Manager. This allows you to modify the sub-regions specifically for this project.
3. **Restoring Functionality**: Normal Litematica selection functionality is restored only when you exit the Project Manager or unload the active project.

---

### UI Component Summary

| Component              | Location            | Function                                 | In-World Visual          |
| ---------------------- | ------------------- | ---------------------------------------- | ------------------------ |
| **Create VCS Project** | Save Schematic Page | Initializes project and saves Version 1. | N/A                      |
| **Origin Fields**      | Top Right (Manager) | Manual X, Y, Z entry and nudging.        | **Cyan Transparent Box** |
| **Move to Player**     | Top Right (Manager) | Snaps project anchor to stance.          | **Cyan Transparent Box** |
| **Area Editor**        | Project Manager     | Modifies project tracking volumes.       | Standard Selection Boxes |
| **Litematica Menu**    | Main Menu           | Area Selection Browser                   | **Grayed Out (Locked)**  |

## 4.2 Commit (The State Saving Flow)

This flow captures the current state of the blocks, entities, and NBT data within the defined sub-regions and records them as a permanent version in the project history.

### 4.2.1 The Save Version Trigger

1. **Initiate Save**: Inside the **Project Manager**, click the **[Save Version]** button.
2. **Version Description**: A prompt appears for you to enter a description (e.g., "Optimized piston timings").
3. **Project Update**: Once confirmed, the dashboard updates the version count (e.g., "Version 2 of 2"). The project now recognizes this specific world state as a historical milestone.

### 4.2.2 Sub-Region and Origin Hierarchy

The system uses a layered coordinate system to ensure your build remains consistent even if parts of it are moved.

1. **Local Anchoring**: Every block and entity is recorded relative to the **origin of the specific sub-region** it belongs to.
2. **Global Anchoring**: Each sub-region's position is, in turn, tracked relative to the **Project Origin** (the cyan box).
3. **Structural Integrity**: This hierarchical approach ensures that the internal layout of your build is preserved within its boundaries, while the entire project remains locked to your master zero-point.

### 4.2.3 Post-Save Feedback: Persistent Ghost Overlay

Immediately after saving, the system engages "Tracking Mode" to help you visualize future changes.

1. **Automatic Overlay**: The version you just saved is projected back into the world as a **Ghost Overlay**.
2. **Real-Time Comparison**: As you continue working, the system constantly compares the physical blocks in the world to the saved ghost state.
3. **Change Highlighting**: Any deviations from the saved version are instantly highlighted with color tints:

- **Red**: The wrong block is in this position.
- **Orange**: The block is correct, but its state is wrong (e.g., a repeater is on the wrong delay).
- **Magenta**: A block from the save is missing in the world.
- **Light Blue/Cyan**: An extra block exists in the world that was not in the save.

4. **The Clean State**: If the physical build matches the save exactly, no highlights appear. This acts as a visual confirmation that there are no "unsaved changes."
5. **Visibility Control**: You can toggle the ghost overlay and the highlights on or off at any time using standard Litematica rendering hotkeys.

---

### Commit Lifecycle Summary

| Stage            | Action               | Result                                                  |
| ---------------- | -------------------- | ------------------------------------------------------- |
| **Trigger**      | Click [Save Version] | Opens the description prompt.                           |
| **Capture**      | Hierarchical Scan    | Records blocks to Sub-Region Origin -> Project Origin.  |
| **Tracking**     | Active Ghost Overlay | Real-time comparison between world and save begins.     |
| **Verification** | Color Highlighting   | Visual cues appear if the build deviates from the save. |
