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

## 4.3 Checkout (Version Restoration)

This flow allows users to physically revert the world to a specific point in history. It includes a mandatory preview phase to ensure the restoration is accurate before the world is modified.

### 4.3.1 The Checkout Trigger and Safety Check

1. **Select Version**: In the **Project Manager**, click on a specific commit entry to expand the **Context Menu**.
2. **Initiate Checkout**: Click the **[Checkout]** button within the context menu.
3. **Unsaved Changes Prompt**: The system checks the current world state against the last save. If discrepancies exist, the user is prompted to commit or discard changes before proceeding.

### 4.3.2 Target Version Preview (Ghost Overlay)

Before the physical swap occurs, the system enters a Preview Mode:

1. **Ghost Placement**: The selected target version is loaded as a Ghost Overlay in the world, mapped to the current Project Origin.
2. **Visual Verification**: The user can walk around the build to see exactly where the blocks will land.
3. **Real-Time Comparison**: The system highlights mismatches between the current physical world and the target ghost overlay using the standard color palette (Red/Orange/Magenta/Cyan). This shows the user exactly what will be added, removed, or changed if they proceed.

### 4.3.3 The Final Confirmation and Restoration

1. **Confirmation Prompt**: The user is presented with a final choice: [Confirm] or [Cancel].
2. **Union Volume Clear**: Upon confirmation, the system calculates the Union Volume (the combined bounding boxes of the Current State and the Target State) and clears everything within this volume, including:
   - All blocks and their states.
   - All entities (Armor stands, Minecarts, Item frames, etc.).
   - All Tile Entity data (inventories, signs, etc.).

3. **Physical World Swap**: The system populates the cleared volume with the exact block states, entities, and NBT data from the target commit.
4. **Overlay Transition**: The ghost overlay from the preview is dismissed, and a new persistent ghost overlay of the now-active version is applied.

---

### Checkout Workflow Summary

| Stage            | Action                 | Result                                          |
| ---------------- | ---------------------- | ----------------------------------------------- |
| **Selection**    | Pick version from list | Initiates the restoration sequence.             |
| **Preview**      | Inspect Ghost Overlay  | Visualizes changes before world modification.   |
| **Confirmation** | Click [Confirm]        | Triggers the physical block swap.               |
| **Result**       | World Update           | Physical blocks now match the selected version. |

---

### Context Menu

| Option                 | Function                                                  |
| ---------------------- | --------------------------------------------------------- |
| **Checkout**           | Physically swaps the world to this commit's state.        |
| **Open Changes**       | Enters the dual-ghost visualizer mode (See 4.4).          |
| **Create Branch From** | Spawns a new independent timeline from this anchor point. |

---

## 4.4 History Diff Inspection (Visual Comparison)

This feature is an advanced diagnostic mode modeled after Litematica and TechUtils verifiers. It allows users to see exactly what changed within a specific commit by comparing it against its predecessor in a non-interactable, "view-only" environment.

### 4.4.1 The Inspection Trigger (Action Hub)

1. **Select Commit**: In the **Project Manager**, click on the commit entry you wish to audit to expand the **Context Menu**.
2. **Initiate Inspection**: Click the **[Open Changes]** button.
3. **Safety Check**: The system prompts the user to ensure the current physical work is saved.
4. **The "Visualizer" Clear**: Upon confirmation, the system calculates the combined volume of the **Current State**, the **Parent State**, and the **Target State**. It clears all physical blocks and entities from this volume to provide a clean slate for the visualizer.
5. **Full-Build Ghost Loading**: Unlike a traditional text-based Git diff that only shows lines changed, this mode loads **two complete versions of the entire build**. Both layers are aligned to the **Project Origin**:

- **Layer A (The Parent State)**: A ghost representation of the entire build as it existed in the previous version. This layer uses **Solid-Style Transparency**, making it look like real blocks while remaining non-interactable.
- **Layer B (The Target State)**: A translucent ghost representation of the entire build as it exists in the selected version, layered directly over the Parent.

### 4.4.2 Standardized Color Palette

The system compares the two complete build states and applies tints based on the differences found between the two full-volume layers:

| Color              | Status Type      | Description                                                                      |
| ------------------ | ---------------- | -------------------------------------------------------------------------------- |
| **Light Blue**     | Added            | Block exists in the Target version but was absent in the Parent.                 |
| **Pink / Magenta** | Removed          | Block was in the Parent but is missing from the Target.                          |
| **Orange**         | Mismatched State | Block type is the same, but properties (delay, rotation) or inventories changed. |
| **Red**            | Wrong Block      | The block type in the Target is different from the Parent at that coordinate.    |

### 4.4.3 The Verifier GUI and Toggle View

The user can open the Verifier GUI to control the visualization of these two full-build layers:

- **Filter by Comparison State**: Instantly toggle the visibility of blocks based on their relationship between the two versions. This includes:
- **Wrong Blocks (Red)**, **Wrong States (Orange)**, **Extra Blocks (Pink)**, **Missing Blocks (Blue)**, and **Correct State (Unchanged)**.
- **Verification Range**: Use the **Range:** setting to define the scope of the comparison. This allows users to restrict the verification check to specific layers or sub-regions rather than the entire volume.
- **Layer Visibility**: Toggle the Parent (Solid Ghost) or Target (Translucent Ghost) layer on/off to see the "Before" and "After" versions of the entire project.

### 4.4.4 The Information HUD

A real-time HUD provides data on the ghost blocks currently under the crosshair:

- **Property Mismatches**: Displays the exact state change (e.g., Parent: delay=1 | Target: delay=3).
- **Inventory Mismatches**: Lists specific item changes in containers (e.g., Target added 1x Diamond).

### 4.4.5 Component Clustering

- **Glow Boundaries**: Groups of touching mismatched blocks are wrapped in a glowing edge to highlight the "zone" of change within the full build.
- **Unit Logic**: Individual block changes are grouped into logical "component updates," allowing the user to see a complex circuit change as a single unit of history.

---

## 4.5 Branching (Parallel Timelines)

This feature enables non-linear development, allowing both the project structure (Sub-Regions) and the block data to diverge into separate, independent paths.

### 4.5.1 Contextual Branch Creation

In the **Project Manager**, branching is handled as a contextual action tied to specific commits in your history.

1. **Select a Base**: Click on any commit entry within the version history list. This expands a **Context Menu** for that specific point in time.
2. **Branch Setup**: Upon clicking **[Create Branch From]**, you are prompted to **name** the new timeline (e.g., `feature`).
3. **Structural Inheritance**: The new branch initially inherits the exact **Sub-Region definitions** (box sizes and positions) of the commit it was spawned from.
4. **Divergent Layouts**: From this point forward, any changes made to Sub-Regions (adding, resizing, or moving boxes) are recorded **only** within the active branch. This allows one branch to have a compact footprint while another expands to include new modules.

## TODO

Remote: host project on server
discard all changes hotkey/button
