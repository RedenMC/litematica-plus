package me.zly2006.rvc;

import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.eclipse.jgit.revwalk.RevCommit;

import fi.dy.masa.litematica.Reference;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.GuiTextFieldInteger;
import fi.dy.masa.malilib.gui.GuiTextInput;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.ITextFieldListener;
import fi.dy.masa.malilib.interfaces.IConfirmationListener;
import fi.dy.masa.malilib.interfaces.IStringConsumerFeedback;
import fi.dy.masa.malilib.interfaces.IStringDualConsumerFeedback;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;

public class GuiRvcProjectEditor extends GuiBase
{
    private static final int MARGIN = 10;
    private static final int BUTTON_HEIGHT = 20;
    private static final int TEXT_FIELD_HEIGHT = 16;
    private static final int COORDINATE_FIELD_WIDTH = 72;
    private static final int REGION_ROW_HEIGHT = 24;
    private static final int REGION_SCROLLBAR_TRACK_RIGHT_OFFSET = 7;
    private static final int REGION_SCROLLBAR_TRACK_WIDTH = 4;
    private static final int REGION_SCROLL_ROWS = 3;
    private static final int TOP_Y = 28;
    private static final int PROJECT_FIELD_Y = 56;
    private static final int TOP_BUTTON_Y = 82;
    private static final int REGION_EDITOR_Y = 108;
    private static final int STACKED_ORIGIN_Y = 112;
    private static final int STACKED_REGION_EDITOR_Y = 190;
    private static final int STATUS_COLOR = 0xFFAAAAAA;
    private static final int ERROR_COLOR = 0xFFFF5555;
    private static final int SUCCESS_COLOR = 0xFF55FF55;
    private static final int PROJECT_FIELD_WIDTH = 202;
    private static final int ORIGIN_PANEL_WIDTH = 128;
    private static final int STACKED_LAYOUT_MAX_WIDTH = 430;
    private static final String PLUS_MINUS_HOVER = "litematica.gui.button.hover.plus_minus_tip_ctrl_alt_shift";

    private final Path repositoryDirectory;
    private String projectName;
    @Nullable private RvcProjectService.ProjectEditorState state;
    @Nullable private String selectedRegionId;
    private int regionScrollOffset;
    private String statusText = "";
    private int statusColor = STATUS_COLOR;

    public GuiRvcProjectEditor(Path repositoryDirectory, String projectName)
    {
        this.repositoryDirectory = repositoryDirectory;
        this.projectName = projectName;
        this.title = StringUtils.translate("litematica.gui.title.rvc_project_editor", Reference.MOD_VERSION, projectName);
    }

    @Override
    public void initGui()
    {
        super.initGui();
        this.refreshState();

        if (this.state == null)
        {
            this.createBottomButtons();
            return;
        }

        this.createProjectNameField();
        this.createOriginFields();
        this.createSelectedRegionFields();
        this.createTopButtons();
        this.createRegionRowButtons();
        this.createBottomButtons();
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        RenderUtils.drawRect(ctx, 0, 0, this.getScreenWidth(), this.getScreenHeight(), 0xD03B424A);
        super.drawContents(ctx, mouseX, mouseY, partialTicks);

        if (this.state == null)
        {
            ctx.drawString(ctx.fontRenderer(), this.statusText, MARGIN, TOP_Y, this.statusColor, false);
            return;
        }

        this.drawProjectFields(ctx);
        this.drawOriginFields(ctx);
        this.drawSelectedRegionFields(ctx);
        this.drawRegionList(ctx, mouseX, mouseY);
        this.drawStatus(ctx);
    }

    @Override
    public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick)
    {
        if (super.onMouseClicked(click, doubleClick))
        {
            return true;
        }

        RvcManifest.Region region = this.getRegionAt((int) click.x(), (int) click.y());

        if (region != null)
        {
            this.selectedRegionId = region.id();
            this.initGui();
            return true;
        }

        return false;
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)
    {
        if (this.isMouseOverRegionList((int) mouseX, (int) mouseY))
        {
            int oldOffset = this.regionScrollOffset;
            this.scrollRegions(verticalAmount);

            if (oldOffset != this.regionScrollOffset)
            {
                this.initGui();
                return true;
            }
        }

        return super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void refreshState()
    {
        try
        {
            this.state = RvcProjectService.readSemanticProjectEditorState(this.repositoryDirectory);
            this.projectName = this.state.projectName();
            this.title = StringUtils.translate("litematica.gui.title.rvc_project_editor", Reference.MOD_VERSION, this.projectName);
            this.ensureSelectedRegion();
            this.clampRegionScroll();
        }
        catch (Exception e)
        {
            this.state = null;
            this.setErrorStatus(e.getMessage());
        }
    }

    private void ensureSelectedRegion()
    {
        if (this.state == null || this.state.regions().isEmpty())
        {
            this.selectedRegionId = null;
            this.regionScrollOffset = 0;
            return;
        }

        if (this.getSelectedRegion() != null)
        {
            return;
        }

        this.selectedRegionId = this.state.regions().get(0).id();
    }

    private void createProjectNameField()
    {
        GuiTextFieldGeneric textField = new GuiTextFieldGeneric(MARGIN, PROJECT_FIELD_Y, this.getProjectFieldWidth(), TEXT_FIELD_HEIGHT, this.font);
        textField.setMaxLength(128);
        textField.setValueWrapper(this.state.projectName());
        this.addTextField(textField, new ProjectNameListener(this));
    }

    private void createTopButtons()
    {
        int x = MARGIN - 1;
        x += this.createButton(x, TOP_BUTTON_Y, ButtonType.NEW_SUB_REGION) + 4;
        this.createButton(x, TOP_BUTTON_Y, ButtonType.SAVE_VERSION);
    }

    private void createOriginFields()
    {
        int x = this.getOriginX();
        int y = this.getOriginY() + 24;
        BlockPos origin = this.state.localOrigin();

        this.createCoordinateField(x, y, FieldKind.ORIGIN_X, origin.getX());
        y += 20;
        this.createCoordinateField(x, y, FieldKind.ORIGIN_Y, origin.getY());
        y += 20;
        this.createCoordinateField(x, y, FieldKind.ORIGIN_Z, origin.getZ());
        this.createButton(x, y + 22, ButtonType.SET_ORIGIN_TO_PLAYER);
    }

    private void createSelectedRegionFields()
    {
        RvcManifest.Region region = this.getSelectedRegion();

        if (region == null)
        {
            return;
        }

        int x = MARGIN;
        int y = this.getSelectedRegionEditorY() + 24;
        BlockPos min = this.blockPosFromList(region.min());
        BlockPos size = this.blockPosFromList(region.size());

        this.createCoordinateField(x, y, FieldKind.REGION_MIN_X, min.getX());
        this.createCoordinateField(x + 118, y, FieldKind.REGION_MIN_Y, min.getY());
        this.createCoordinateField(x + 236, y, FieldKind.REGION_MIN_Z, min.getZ());

        int sizeX = this.isRegionEditorStacked() ? x : x + 382;
        int sizeY = this.isRegionEditorStacked() ? y + 32 : y;
        this.createCoordinateField(sizeX, sizeY, FieldKind.REGION_SIZE_X, size.getX());
        this.createCoordinateField(sizeX + 118, sizeY, FieldKind.REGION_SIZE_Y, size.getY());
        this.createCoordinateField(sizeX + 236, sizeY, FieldKind.REGION_SIZE_Z, size.getZ());
    }

    private void createCoordinateField(int x, int y, FieldKind kind, int value)
    {
        GuiTextFieldInteger textField = new GuiTextFieldInteger(x + 16, y + 2, COORDINATE_FIELD_WIDTH, TEXT_FIELD_HEIGHT, this.font);
        textField.setValueWrapper(String.valueOf(value));
        this.addTextField(textField, new IntegerFieldListener(this, kind));
        this.addButton(new ButtonGeneric(x + 16 + COORDINATE_FIELD_WIDTH + 4, y + 2, Icons.BUTTON_PLUS_MINUS_16, StringUtils.translate(PLUS_MINUS_HOVER)), new CoordinateButtonListener(this, kind));
    }

    private void createRegionRowButtons()
    {
        int y = this.getRegionRowsY() + 2;
        int endIndex = Math.min(this.state.regions().size(), this.regionScrollOffset + this.getVisibleRegionRows());

        for (int index = this.regionScrollOffset; index < endIndex; index++)
        {
            RvcManifest.Region region = this.state.regions().get(index);
            int buttonRightPadding = this.getRegionMaxScroll() > 0 ? 18 : 6;
            int rightX = this.getRegionListX() + this.getRegionListWidth() - buttonRightPadding;

            rightX = this.createRightRegionButton(rightX, y, RegionButtonType.REMOVE, region);
            rightX = this.createRightRegionButton(rightX, y, RegionButtonType.RENAME, region);
            this.createRightRegionButton(rightX, y, RegionButtonType.CONFIGURE, region);
            y += REGION_ROW_HEIGHT;
        }
    }

    private int createRightRegionButton(int rightX, int y, RegionButtonType type, RvcManifest.Region region)
    {
        ButtonGeneric button = new ButtonGeneric(rightX, y, -1, true, type.getDisplayName());
        this.addButton(button, new RegionButtonListener(this, type, region.id()));
        return button.getX() - 2;
    }

    private void createBottomButtons()
    {
        String label = StringUtils.translate("litematica.gui.button.rvc_project.project_page");
        int width = this.getStringWidth(label) + 20;
        this.addButton(new ButtonGeneric(this.getScreenWidth() - width - MARGIN, this.getBottomButtonY(), width, BUTTON_HEIGHT, label), (button, mouseButton) -> this.openProjectPage());
    }

    private int createButton(int x, int y, ButtonType type)
    {
        ButtonGeneric button = new ButtonGeneric(x, y, -1, BUTTON_HEIGHT, type.getDisplayName());
        this.addButton(button, new ButtonListener(this, type));
        return button.getWidth();
    }

    private void drawProjectFields(GuiContext ctx)
    {
        ctx.drawString(ctx.fontRenderer(), StringUtils.translate("litematica.gui.label.rvc_project_editor.project_name"), MARGIN, TOP_Y + 16, 0xFFFFFFFF, false);
    }

    private void drawOriginFields(GuiContext ctx)
    {
        int x = this.getOriginX();
        int y = this.getOriginY();

        ctx.drawString(ctx.fontRenderer(), StringUtils.translate("litematica.gui.label.rvc_project_editor.local_site_origin"), x, y, 0xFFFFFFFF, false);
        ctx.drawString(ctx.fontRenderer(), StringUtils.translate("litematica.gui.label.rvc_project_editor.dimension", this.state.localDimension()), x, y + 12, 0xFFAAAAAA, false);

        this.drawCoordinateLabel(ctx, x, y + 24, "X:");
        this.drawCoordinateLabel(ctx, x, y + 44, "Y:");
        this.drawCoordinateLabel(ctx, x, y + 64, "Z:");

        if (!this.state.localDimension().equals(this.state.siteDimension()))
        {
            ctx.drawString(ctx.fontRenderer(), StringUtils.translate("litematica.gui.label.rvc_project_editor.dimension_mismatch", this.state.siteDimension()), x, y + 108, ERROR_COLOR, false);
        }
    }

    private void drawSelectedRegionFields(GuiContext ctx)
    {
        int y = this.getSelectedRegionEditorY();
        RvcManifest.Region region = this.getSelectedRegion();

        if (region == null)
        {
            ctx.drawString(ctx.fontRenderer(), StringUtils.translate("litematica.gui.label.rvc_project_editor.no_region_selected"), MARGIN, y, 0xFFAAAAAA, false);
            return;
        }

        ctx.drawString(ctx.fontRenderer(), StringUtils.translate("litematica.gui.label.rvc_project_editor.selected_region", region.name()), MARGIN, y, 0xFFFFFFFF, false);
        ctx.drawString(ctx.fontRenderer(), StringUtils.translate("litematica.gui.label.rvc_project_editor.region_min"), MARGIN, y + 13, 0xFFAAAAAA, false);
        int sizeLabelX = this.isRegionEditorStacked() ? MARGIN : MARGIN + 382;
        int sizeLabelY = this.isRegionEditorStacked() ? y + 45 : y + 13;
        ctx.drawString(ctx.fontRenderer(), StringUtils.translate("litematica.gui.label.rvc_project_editor.region_size"), sizeLabelX, sizeLabelY, 0xFFAAAAAA, false);

        int fieldY = y + 24;
        this.drawCoordinateLabel(ctx, MARGIN, fieldY, "X:");
        this.drawCoordinateLabel(ctx, MARGIN + 118, fieldY, "Y:");
        this.drawCoordinateLabel(ctx, MARGIN + 236, fieldY, "Z:");
        int sizeFieldX = this.isRegionEditorStacked() ? MARGIN : MARGIN + 382;
        int sizeFieldY = this.isRegionEditorStacked() ? fieldY + 32 : fieldY;
        this.drawCoordinateLabel(ctx, sizeFieldX, sizeFieldY, "X:");
        this.drawCoordinateLabel(ctx, sizeFieldX + 118, sizeFieldY, "Y:");
        this.drawCoordinateLabel(ctx, sizeFieldX + 236, sizeFieldY, "Z:");
    }

    private void drawCoordinateLabel(GuiContext ctx, int x, int y, String label)
    {
        ctx.drawString(ctx.fontRenderer(), label, x, y + 5, 0xFFFFFFFF, false);
    }

    private void drawRegionList(GuiContext ctx, int mouseX, int mouseY)
    {
        int x = this.getRegionListX();
        int y = this.getRegionListY();
        int width = this.getRegionListWidth();
        int height = this.getRegionListHeight();

        RenderUtils.drawOutlinedBox(ctx, x, y, width, height, 0xA0000000, COLOR_HORIZONTAL_BAR);
        ctx.drawString(ctx.fontRenderer(), StringUtils.translate("litematica.gui.label.rvc_project_editor.sub_regions", this.state.regions().size()), x + 4, y + 5, 0xFFFFFFFF, false);

        if (this.state.regions().isEmpty())
        {
            ctx.drawString(ctx.fontRenderer(), StringUtils.translate("litematica.gui.label.rvc_project_editor.no_sub_regions"), x + 6, this.getRegionRowsY() + 6, 0xFFAAAAAA, false);
            return;
        }

        this.clampRegionScroll();
        ctx.pushScissor(new ScreenRectangle(x + 2, this.getRegionRowsY(), width - 4, this.getRegionRowsHeight()));
        this.drawVisibleRegionRows(ctx, mouseX, mouseY);
        ctx.popScissor();
        this.drawRegionScrollbar(ctx);
    }

    private void drawVisibleRegionRows(GuiContext ctx, int mouseX, int mouseY)
    {
        int x = this.getRegionListX() + 2;
        int width = this.getRegionListWidth() - 4;
        int y = this.getRegionRowsY();
        int endIndex = Math.min(this.state.regions().size(), this.regionScrollOffset + this.getVisibleRegionRows());

        for (int index = this.regionScrollOffset; index < endIndex; index++)
        {
            RvcManifest.Region region = this.state.regions().get(index);
            boolean selected = region.id().equals(this.selectedRegionId);
            int rowColor = index % 2 == 0 ? 0xA0303030 : 0xA0101010;

            if (selected || GuiBase.isMouseOver(mouseX, mouseY, x, y, width, REGION_ROW_HEIGHT))
            {
                rowColor = 0xA0707070;
            }

            RenderUtils.drawRect(ctx, x, y, width, REGION_ROW_HEIGHT, rowColor);

            if (selected)
            {
                RenderUtils.drawOutline(ctx, x, y, width, REGION_ROW_HEIGHT, 0xFFE0E0E0);
            }

            this.drawRegionRowText(ctx, region, x + 6, y + 5);
            y += REGION_ROW_HEIGHT;
        }
    }

    private void drawRegionRowText(GuiContext ctx, RvcManifest.Region region, int x, int y)
    {
        String name = this.ellipsizeToWidth(region.name(), Math.max(40, this.getRegionListWidth() / 3));
        String details = StringUtils.translate(
                "litematica.gui.label.rvc_project_editor.region_details",
                this.vectorText(region.min()),
                this.vectorText(region.size())
        );
        int detailX = x + Math.min(220, Math.max(120, this.getRegionListWidth() / 4));
        int detailsMaxWidth = Math.max(40, this.getRegionListX() + this.getRegionListWidth() - detailX - 280);

        ctx.drawString(ctx.fontRenderer(), name, x, y, 0xFFFFFFFF, false);
        ctx.drawString(ctx.fontRenderer(), this.ellipsizeToWidth(details, detailsMaxWidth), detailX, y, 0xFFAAAAAA, false);
    }

    private void drawRegionScrollbar(GuiContext ctx)
    {
        int maxScroll = this.getRegionMaxScroll();

        if (maxScroll <= 0)
        {
            return;
        }

        int trackX = this.getRegionListX() + this.getRegionListWidth() - REGION_SCROLLBAR_TRACK_RIGHT_OFFSET;
        int trackY = this.getRegionRowsY();
        int trackHeight = this.getRegionRowsHeight();
        int visibleRows = this.getVisibleRegionRows();
        int thumbHeight = Math.max(14, trackHeight * visibleRows / Math.max(visibleRows, this.state.regions().size()));
        int thumbY = trackY + (trackHeight - thumbHeight) * this.regionScrollOffset / maxScroll;

        RenderUtils.drawRect(ctx, trackX, trackY, REGION_SCROLLBAR_TRACK_WIDTH, trackHeight, 0xA0202020);
        RenderUtils.drawRect(ctx, trackX, thumbY, REGION_SCROLLBAR_TRACK_WIDTH, thumbHeight, 0xFFE0E0E0);
    }

    private void drawStatus(GuiContext ctx)
    {
        if (!this.statusText.isBlank())
        {
            ctx.drawString(ctx.fontRenderer(), this.statusText, MARGIN, this.getBottomButtonY() + 6, this.statusColor, false);
        }
    }

    private void updateProjectName(String value)
    {
        if (value == null || value.isBlank())
        {
            this.setErrorStatus(StringUtils.translate("litematica.error.rvc_project_editor.project_name_required"));
            return;
        }

        try
        {
            RvcProjectService.updateSemanticProjectName(this.repositoryDirectory, value);
            this.refreshState();
            this.setSavedStatus("litematica.message.rvc_project_editor.project_name_updated");
        }
        catch (Exception e)
        {
            this.setErrorStatus(e.getMessage());
        }
    }

    private void updateIntegerField(FieldKind kind, String value)
    {
        try
        {
            this.applyIntegerValue(kind, Integer.parseInt(value.trim()));
        }
        catch (NumberFormatException e)
        {
            this.setErrorStatus(StringUtils.translate("litematica.error.rvc_project_editor.invalid_integer"));
        }
    }

    private void applyIntegerValue(FieldKind kind, int value)
    {
        if (this.state == null)
        {
            return;
        }

        try
        {
            if (kind.isOrigin())
            {
                RvcProjectService.updateSemanticLocalOrigin(this.repositoryDirectory, this.updatedOrigin(kind, value));
                this.refreshState();
                this.setSavedStatus("litematica.message.rvc_project_editor.local_origin_updated");
                return;
            }

            RvcManifest.Region region = this.getSelectedRegion();

            if (region == null)
            {
                return;
            }

            BlockPos min = this.blockPosFromList(region.min());
            BlockPos size = this.blockPosFromList(region.size());

            if (kind.isRegionMin())
            {
                min = this.updatedVector(min, kind, value);
            }
            else
            {
                size = this.updatedVector(size, kind, Math.max(1, value));
            }

            RvcProjectService.updateSemanticRegion(this.repositoryDirectory, region.id(), region.name(), min, size);
            this.refreshState();
            this.setSavedStatus("litematica.message.rvc_project_editor.region_updated");
        }
        catch (Exception e)
        {
            this.setErrorStatus(e.getMessage());
        }
    }

    private BlockPos updatedOrigin(FieldKind kind, int value)
    {
        BlockPos origin = this.state.localOrigin();

        return switch (kind)
        {
            case ORIGIN_X -> new BlockPos(value, origin.getY(), origin.getZ());
            case ORIGIN_Y -> new BlockPos(origin.getX(), value, origin.getZ());
            case ORIGIN_Z -> new BlockPos(origin.getX(), origin.getY(), value);
            default -> origin;
        };
    }

    private BlockPos updatedVector(BlockPos current, FieldKind kind, int value)
    {
        return switch (kind)
        {
            case REGION_MIN_X, REGION_SIZE_X -> new BlockPos(value, current.getY(), current.getZ());
            case REGION_MIN_Y, REGION_SIZE_Y -> new BlockPos(current.getX(), value, current.getZ());
            case REGION_MIN_Z, REGION_SIZE_Z -> new BlockPos(current.getX(), current.getY(), value);
            default -> current;
        };
    }

    private void nudgeIntegerField(FieldKind kind, int mouseButton)
    {
        int amount = mouseButton == 1 ? -1 : 1;

        if (GuiBase.isCtrlDown())
        {
            amount *= 100;
        }

        if (GuiBase.isShiftDown())
        {
            amount *= 10;
        }

        if (GuiBase.isAltDown())
        {
            amount *= 5;
        }

        Integer currentValue = this.currentValue(kind);

        if (currentValue != null)
        {
            int newValue = kind.isRegionSize() ? Math.max(1, currentValue + amount) : currentValue + amount;
            this.applyIntegerValue(kind, newValue);
            this.initGui();
        }
    }

    @Nullable
    private Integer currentValue(FieldKind kind)
    {
        if (this.state == null)
        {
            return null;
        }

        if (kind.isOrigin())
        {
            BlockPos origin = this.state.localOrigin();
            return switch (kind)
            {
                case ORIGIN_X -> origin.getX();
                case ORIGIN_Y -> origin.getY();
                case ORIGIN_Z -> origin.getZ();
                default -> null;
            };
        }

        RvcManifest.Region region = this.getSelectedRegion();

        if (region == null)
        {
            return null;
        }

        BlockPos value = kind.isRegionMin() ? this.blockPosFromList(region.min()) : this.blockPosFromList(region.size());

        return switch (kind)
        {
            case REGION_MIN_X, REGION_SIZE_X -> value.getX();
            case REGION_MIN_Y, REGION_SIZE_Y -> value.getY();
            case REGION_MIN_Z, REGION_SIZE_Z -> value.getZ();
            default -> null;
        };
    }

    private void setOriginToPlayer()
    {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        Level world = minecraft.level;

        if (player == null)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.no_player");
            return;
        }

        if (world == null)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.no_world");
            return;
        }

        String dimension = RvcMinecraftWorldReader.dimensionId(world);

        if (this.state != null && !dimension.equals(this.state.localDimension()))
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project_editor.dimension_mismatch", this.state.localDimension(), dimension);
            return;
        }

        try
        {
            RvcProjectService.updateSemanticLocalOrigin(this.repositoryDirectory, fi.dy.masa.malilib.util.position.PositionUtils.getEntityBlockPos(player));
            this.initGui();
            this.addMessage(MessageType.SUCCESS, "litematica.message.rvc_project_editor.local_origin_updated");
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project_editor.save_failed", e.getMessage());
        }
    }

    private void promptNewRegion()
    {
        GuiBase.openGui(new GuiTextInput(128, "litematica.gui.title.rvc_project_editor.new_sub_region", "", this, new NewRegionCreator(this)));
    }

    private void createRegion(String name)
    {
        try
        {
            RvcManifest.Region region = RvcProjectService.createSemanticRegion(this.repositoryDirectory, name, this.suggestedNewRegionMin(), new BlockPos(1, 1, 1));
            this.selectedRegionId = region.id();
            this.initGui();
            this.addMessage(MessageType.SUCCESS, "litematica.message.rvc_project_editor.region_created", region.name());
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project_editor.save_failed", e.getMessage());
        }
    }

    private BlockPos suggestedNewRegionMin()
    {
        if (this.state == null || this.state.regions().isEmpty())
        {
            return BlockPos.ZERO;
        }

        int nextX = 0;

        for (RvcManifest.Region region : this.state.regions())
        {
            BlockPos min = this.blockPosFromList(region.min());
            BlockPos size = this.blockPosFromList(region.size());
            nextX = Math.max(nextX, min.getX() + size.getX() + 1);
        }

        return new BlockPos(nextX, 0, 0);
    }

    private void promptRenameRegion(String regionId)
    {
        RvcManifest.Region region = this.regionById(regionId);

        if (region != null)
        {
            GuiBase.openGui(new GuiTextInput(128, "litematica.gui.title.rvc_project_editor.rename_sub_region", region.name(), this, new RegionRenamer(this, region.id())));
        }
    }

    private void renameRegion(String regionId, String name)
    {
        RvcManifest.Region region = this.regionById(regionId);

        if (region == null)
        {
            return;
        }

        try
        {
            RvcProjectService.updateSemanticRegion(this.repositoryDirectory, region.id(), name, this.blockPosFromList(region.min()), this.blockPosFromList(region.size()));
            this.initGui();
            this.addMessage(MessageType.SUCCESS, "litematica.message.rvc_project_editor.region_renamed", name.trim());
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project_editor.save_failed", e.getMessage());
        }
    }

    private void confirmDeleteRegion(String regionId)
    {
        RvcManifest.Region region = this.regionById(regionId);

        if (region != null)
        {
            GuiBase.openGui(new GuiConfirmAction(
                    420,
                    "litematica.gui.title.rvc_project_editor.confirm_delete_region",
                    new DeleteRegionConfirmListener(this, region.id()),
                    this,
                    "litematica.gui.message.rvc_project_editor.confirm_delete_region",
                    region.name()
            ));
        }
    }

    private void deleteRegion(String regionId)
    {
        try
        {
            RvcProjectService.deleteSemanticRegion(this.repositoryDirectory, regionId);
            this.selectedRegionId = null;
            this.initGui();
            this.addMessage(MessageType.SUCCESS, "litematica.message.rvc_project_editor.region_deleted");
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project_editor.save_failed", e.getMessage());
        }
    }

    private void promptCommitMessage()
    {
        if (this.state == null || this.state.regions().isEmpty())
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.no_tracking_areas");
            return;
        }

        try
        {
            if (RvcProjectService.isDetachedHead(this.repositoryDirectory))
            {
                this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.commit_failed", StringUtils.translate("litematica.error.rvc_project.detached_head_commit"));
                return;
            }
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.commit_failed", e.getMessage());
            return;
        }

        GuiBase.openGui(new GuiRvcProject.CommitMessageDialog(
                256,
                4,
                8,
                "litematica.gui.title.rvc_project.commit_message",
                "",
                "",
                this,
                new CommitMessageSetter(this)
        ));
    }

    private void commitCurrentProject(String message)
    {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        Level world = minecraft.level;

        if (player == null)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.no_player");
            return;
        }

        if (world == null)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.no_world");
            return;
        }

        AreaSelection selectionFallback = DataManager.getSelectionManager().getCurrentSelection();

        try
        {
            RvcPlayerIdentity identity = new RvcPlayerIdentity(player.getName().getString(), player.getUUID());
            RevCommit commit = RvcProjectService.gitCommit(this.repositoryDirectory, this.projectName, identity, world, selectionFallback, false, message);

            if (commit == null)
            {
                this.initGui();
                this.addMessage(MessageType.INFO, "litematica.message.rvc_project.nothing_to_commit");
                return;
            }

            this.initGui();
            this.addMessage(MessageType.SUCCESS, "litematica.message.rvc_project.committed");
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.commit_failed", e.getMessage());
        }
    }

    private void openProjectPage()
    {
        GuiBase.openGui(new GuiRvcProject(this.repositoryDirectory, this.projectName));
    }

    private void scrollRegions(double verticalAmount)
    {
        if (verticalAmount == 0 || this.state == null)
        {
            return;
        }

        int rows = Math.max(1, (int) Math.ceil(Math.abs(verticalAmount))) * REGION_SCROLL_ROWS;
        this.regionScrollOffset += verticalAmount > 0 ? -rows : rows;
        this.clampRegionScroll();
    }

    private void clampRegionScroll()
    {
        this.regionScrollOffset = Math.clamp(this.regionScrollOffset, 0, this.getRegionMaxScroll());
    }

    private int getRegionMaxScroll()
    {
        if (this.state == null)
        {
            return 0;
        }

        return Math.max(0, this.state.regions().size() - this.getVisibleRegionRows());
    }

    private int getVisibleRegionRows()
    {
        return Math.max(1, this.getRegionRowsHeight() / REGION_ROW_HEIGHT);
    }

    private int getRegionRowsHeight()
    {
        return Math.max(REGION_ROW_HEIGHT, this.getRegionListHeight() - 24);
    }

    private int getRegionRowsY()
    {
        return this.getRegionListY() + 22;
    }

    private boolean isMouseOverRegionList(int mouseX, int mouseY)
    {
        return GuiBase.isMouseOver(mouseX, mouseY, this.getRegionListX(), this.getRegionRowsY(), this.getRegionListWidth(), this.getRegionRowsHeight());
    }

    @Nullable
    private RvcManifest.Region getRegionAt(int mouseX, int mouseY)
    {
        if (this.state == null || !this.isMouseOverRegionList(mouseX, mouseY))
        {
            return null;
        }

        int index = this.regionScrollOffset + (mouseY - this.getRegionRowsY()) / REGION_ROW_HEIGHT;
        return index >= 0 && index < this.state.regions().size() ? this.state.regions().get(index) : null;
    }

    @Nullable
    private RvcManifest.Region getSelectedRegion()
    {
        return this.selectedRegionId == null ? null : this.regionById(this.selectedRegionId);
    }

    @Nullable
    private RvcManifest.Region regionById(String regionId)
    {
        if (this.state == null)
        {
            return null;
        }

        for (RvcManifest.Region region : this.state.regions())
        {
            if (region.id().equals(regionId))
            {
                return region;
            }
        }

        return null;
    }

    private BlockPos blockPosFromList(List<Integer> values)
    {
        return new BlockPos(values.get(0), values.get(1), values.get(2));
    }

    private String vectorText(List<Integer> vector)
    {
        return vector.get(0) + ", " + vector.get(1) + ", " + vector.get(2);
    }

    private String ellipsizeToWidth(String text, int maxWidth)
    {
        if (this.getStringWidth(text) <= maxWidth)
        {
            return text;
        }

        String suffix = "...";
        int suffixWidth = this.getStringWidth(suffix);

        for (int length = text.length(); length > 0; length--)
        {
            String candidate = text.substring(0, length);

            if (this.getStringWidth(candidate) + suffixWidth <= maxWidth)
            {
                return candidate + suffix;
            }
        }

        return suffix;
    }

    private void setSavedStatus(String key)
    {
        this.statusText = StringUtils.translate(key);
        this.statusColor = SUCCESS_COLOR;
    }

    private void setErrorStatus(@Nullable String message)
    {
        this.statusText = message == null || message.isBlank() ? StringUtils.translate("litematica.error.rvc_project_editor.unknown_error") : message;
        this.statusColor = ERROR_COLOR;
    }

    private boolean isStackedLayout()
    {
        return this.getScreenWidth() < STACKED_LAYOUT_MAX_WIDTH;
    }

    private boolean isRegionEditorStacked()
    {
        return this.getScreenWidth() < 760;
    }

    private int getProjectFieldWidth()
    {
        if (this.isStackedLayout())
        {
            return Math.min(PROJECT_FIELD_WIDTH, this.getScreenWidth() - MARGIN * 2);
        }

        return Math.min(PROJECT_FIELD_WIDTH, Math.max(160, this.getOriginX() - MARGIN - 24));
    }

    private int getOriginX()
    {
        return this.isStackedLayout() ? MARGIN : Math.max(MARGIN + PROJECT_FIELD_WIDTH + 120, this.getScreenWidth() - ORIGIN_PANEL_WIDTH - MARGIN);
    }

    private int getOriginY()
    {
        return this.isStackedLayout() ? STACKED_ORIGIN_Y : TOP_Y;
    }

    private int getSelectedRegionEditorY()
    {
        if (this.isStackedLayout())
        {
            return STACKED_REGION_EDITOR_Y;
        }

        int buttonBottom = TOP_BUTTON_Y + BUTTON_HEIGHT;
        int originBottom = this.getOriginY() + 112;
        return Math.max(REGION_EDITOR_Y, Math.max(buttonBottom, originBottom) + 8);
    }

    private int getRegionListX()
    {
        return MARGIN;
    }

    private int getRegionListY()
    {
        return this.getSelectedRegionEditorY() + (this.isRegionEditorStacked() ? 90 : 58);
    }

    private int getRegionListWidth()
    {
        return this.getScreenWidth() - MARGIN * 2;
    }

    private int getRegionListHeight()
    {
        return Math.max(0, this.getBottomButtonY() - this.getRegionListY() - 8);
    }

    private int getBottomButtonY()
    {
        return this.getScreenHeight() - 24;
    }

    private enum ButtonType
    {
        NEW_SUB_REGION("litematica.gui.button.rvc_project_editor.new_sub_region"),
        SAVE_VERSION("litematica.gui.button.rvc_project.save_version"),
        SET_ORIGIN_TO_PLAYER("litematica.gui.button.move_to_player");

        private final String translationKey;

        ButtonType(String translationKey)
        {
            this.translationKey = translationKey;
        }

        private String getDisplayName()
        {
            return StringUtils.translate(this.translationKey);
        }
    }

    private enum RegionButtonType
    {
        CONFIGURE("litematica.gui.button.configure"),
        RENAME("litematica.gui.button.rename"),
        REMOVE(GuiBase.TXT_RED + "-");

        private final String translationKey;

        RegionButtonType(String translationKey)
        {
            this.translationKey = translationKey;
        }

        private String getDisplayName()
        {
            return StringUtils.translate(this.translationKey);
        }
    }

    private enum FieldKind
    {
        ORIGIN_X,
        ORIGIN_Y,
        ORIGIN_Z,
        REGION_MIN_X,
        REGION_MIN_Y,
        REGION_MIN_Z,
        REGION_SIZE_X,
        REGION_SIZE_Y,
        REGION_SIZE_Z;

        private boolean isOrigin()
        {
            return this == ORIGIN_X || this == ORIGIN_Y || this == ORIGIN_Z;
        }

        private boolean isRegionMin()
        {
            return this == REGION_MIN_X || this == REGION_MIN_Y || this == REGION_MIN_Z;
        }

        private boolean isRegionSize()
        {
            return this == REGION_SIZE_X || this == REGION_SIZE_Y || this == REGION_SIZE_Z;
        }
    }

    private record ButtonListener(GuiRvcProjectEditor gui, ButtonType type) implements IButtonActionListener
    {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton)
        {
            switch (this.type)
            {
                case NEW_SUB_REGION -> this.gui.promptNewRegion();
                case SAVE_VERSION -> this.gui.promptCommitMessage();
                case SET_ORIGIN_TO_PLAYER -> this.gui.setOriginToPlayer();
            }
        }
    }

    private record RegionButtonListener(GuiRvcProjectEditor gui, RegionButtonType type, String regionId) implements IButtonActionListener
    {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton)
        {
            switch (this.type)
            {
                case CONFIGURE ->
                {
                    this.gui.selectedRegionId = this.regionId;
                    this.gui.initGui();
                }
                case RENAME -> this.gui.promptRenameRegion(this.regionId);
                case REMOVE -> this.gui.confirmDeleteRegion(this.regionId);
            }
        }
    }

    private record CoordinateButtonListener(GuiRvcProjectEditor gui, FieldKind kind) implements IButtonActionListener
    {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton)
        {
            this.gui.nudgeIntegerField(this.kind, mouseButton);
        }
    }

    private record ProjectNameListener(GuiRvcProjectEditor gui) implements ITextFieldListener<GuiTextFieldGeneric>
    {
        @Override
        public boolean onTextChange(GuiTextFieldGeneric textField)
        {
            this.gui.updateProjectName(textField.getValueWrapper());
            return false;
        }
    }

    private record IntegerFieldListener(GuiRvcProjectEditor gui, FieldKind kind) implements ITextFieldListener<GuiTextFieldGeneric>
    {
        @Override
        public boolean onTextChange(GuiTextFieldGeneric textField)
        {
            this.gui.updateIntegerField(this.kind, textField.getValueWrapper());
            return false;
        }
    }

    private record NewRegionCreator(GuiRvcProjectEditor gui) implements IStringConsumerFeedback
    {
        @Override
        public boolean setString(String string)
        {
            if (string == null || string.isBlank())
            {
                this.gui.addMessage(MessageType.ERROR, "litematica.error.rvc_project_editor.region_name_required");
                return false;
            }

            this.gui.createRegion(string);
            return true;
        }
    }

    private record RegionRenamer(GuiRvcProjectEditor gui, String regionId) implements IStringConsumerFeedback
    {
        @Override
        public boolean setString(String string)
        {
            if (string == null || string.isBlank())
            {
                this.gui.addMessage(MessageType.ERROR, "litematica.error.rvc_project_editor.region_name_required");
                return false;
            }

            this.gui.renameRegion(this.regionId, string);
            return true;
        }
    }

    private record DeleteRegionConfirmListener(GuiRvcProjectEditor gui, String regionId) implements IConfirmationListener
    {
        @Override
        public boolean onActionConfirmed()
        {
            this.gui.deleteRegion(this.regionId);
            return true;
        }

        @Override
        public boolean onActionCancelled()
        {
            return true;
        }
    }

    private record CommitMessageSetter(GuiRvcProjectEditor gui) implements IStringDualConsumerFeedback
    {
        @Override
        public boolean setStrings(String title, String description)
        {
            if (title == null || title.isBlank())
            {
                this.gui.addMessage(MessageType.ERROR, "litematica.error.rvc_project.commit_failed", "Commit message must not be blank");
                return false;
            }

            this.gui.commitCurrentProject(this.fullCommitMessage(title, description));
            return true;
        }

        private String fullCommitMessage(String title, String description)
        {
            String trimmedTitle = title.strip();
            String trimmedDescription = description == null ? "" : description.strip();

            if (trimmedDescription.isBlank())
            {
                return trimmedTitle;
            }

            return trimmedTitle + "\n\n" + trimmedDescription;
        }
    }
}
