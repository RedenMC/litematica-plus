## 4.1 Project Initialization and Origin Management

The initialization process establishes the project’s administrative workspace and its coordinate anchor point. This setup ensures that all future versions are perfectly aligned to a consistent reference point.

### 4.1.1 Project Creation

1. **Entry Point**: Open the Litematica main menu and select the **[Schematic VCS]** button.
2. **Project Identity**: In the project browser, click **[Create New Project]** and assign a name (e.g., "test 1").
3. **Automatic Workspace Entry**: Upon clicking confirm, the system **automatically opens the Project Manager** for that project. You are immediately presented with the project dashboard, which starts as an empty container (Version: N/A of 0).

### 4.1.2 Setting the Project Origin

The **Project Origin** serves as the universal zero-point anchor for the entire project. All area selections and captured block data are measured relative to this point.

1. **Default Placement**: Upon creation, the Project Origin is automatically set to your current coordinates.
2. **Integrated Origin Controls**: Located at the **top right** of the Project Manager dashboard are the direct origin management tools:

- **Coordinate Fields**: Manually type X, Y, and Z values to set the origin to specific world coordinates.
- **Nudge Buttons**: Use the **[plus/minus]** buttons next to each field to shift the origin block-by-block for precise alignment.
- **Move to Player**: Click the **[Move to player]** button next to the coordinates to instantly snap the Project Origin to your current stance.

3. **Visual Representation**: The Project Origin renders in the world as a **transparent cyan box**. This is identical in style to Litematica’s orange "Manual Origin" box, but the distinct color allows you to differentiate the project’s master anchor from temporary schematic origins.

### 4.1.3 Area Definition and The Root Version

With the anchor established, you define the tracking boundaries.

1. **Define Areas**: Click the **[Area Editor]** button to set the 3D boundaries (sub-regions) around your build. These boundaries define what the project watches.
2. **Capture the State**: Once your origin and areas are set, click **[Save Version]**.
3. **Baseline Establishment**: The system captures every block and entity within your defined areas relative to the Project Origin. This becomes **Version 1**, the baseline from which all future changes are measured.

### 4.1.4 Alternative: Creation from Area Selection

This workflow allows you to convert an existing Litematica selection—including all defined sub-regions—into a managed VCS project workspace.

1. **The Entry Point**: Use Litematica in "Normal" selection mode to define your build. This may include a single box or **multiple sub-regions**. Navigate to **Area Selection** -> **Save Schematic** and click the **[Create VCS Project]** button at the bottom.
2. **Automated Setup**: Input the project name. The system creates the project, enters the **Project Manager**, and automatically imports every sub-region from that selection into the project.
3. **Pre-Commit Refinement**: The Project Origin is set to the player position by default. You can refine the origin or modify the imported sub-regions in the **Area Editor** before clicking **[Save Version]** to lock in Version 1.

## 4.1.5 Workspace Focus Mode

To maintain project integrity, the system enforces a strict focus mode once a project is active.

1. **Interface Locking**: The standard **Area Selection** browser in the main Litematica menu becomes **grayed out** and non-interactive. You are prevented from creating standalone selections or modifying unrelated schematic boxes while a project is loaded.
2. **Unified Control**: While the main menu is locked, you retain full control through the project's own tools. You can still access the **Area Editor** via the button inside the **Project Manager**. This leads to the familiar selection management page, but only for the sub-regions belonging to the active project.
3. **Exclusive Modification**: You can only interact with and modify the sub-regions defined within the active project. This ensures that every block tracked belongs to a specific project sub-region anchored to the cyan Project Origin.
4. **Restoring Functionality**: Normal Litematica selection functionality is restored only when you exit the Project Manager or unload the active project.

---

### UI Component Summary

| Component           | Location        | Function                    | In-World Visual          |
| ------------------- | --------------- | --------------------------- | ------------------------ |
| **Origin Fields**   | Top Right       | Manual X, Y, Z entry.       | **Cyan Transparent Box** |
| **Move to Player**  | Top Right       | Snaps origin to stance.     | **Cyan Transparent Box** |
| **Area Editor**     | Project Manager | Defines tracking volumes.   | Standard Selection Boxes |
| **Save Version**    | Main Panel      | Creates the first snapshot. | Tracking Ghost Activated |
| **Litematica Menu** | Main Menu       | Area Selection Browser      | **Grayed Out (Locked)**  |

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
