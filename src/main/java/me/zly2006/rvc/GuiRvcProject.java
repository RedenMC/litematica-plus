package me.zly2006.rvc;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.eclipse.jgit.revwalk.RevCommit;

import fi.dy.masa.litematica.Reference;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.ButtonIcons;
import fi.dy.masa.litematica.gui.GuiMainMenu;
import fi.dy.masa.litematica.gui.Icons;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.GuiTextInput;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.IGuiIcon;
import fi.dy.masa.malilib.gui.interfaces.ITextFieldListener;
import fi.dy.masa.malilib.interfaces.IConfirmationListener;
import fi.dy.masa.malilib.interfaces.ICompletionListener;
import fi.dy.masa.malilib.interfaces.IStringConsumerFeedback;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;

public class GuiRvcProject extends GuiBase implements ICompletionListener
{
    private static final int HISTORY_ROW_HEIGHT = 18;
    private static final int PANEL_MARGIN = 10;
    private static final int TOP_BUTTON_Y = 28;
    private static final int CONTENT_TOP_Y = 56;
    private static final int SEARCH_HEIGHT = 18;
    private static final int HISTORY_HASH_RIGHT_PADDING = 8;
    private static final int HISTORY_SCROLLBAR_GUTTER_WIDTH = 8;
    private static final int HISTORY_SCROLLBAR_TRACK_RIGHT_OFFSET = 9;
    private static final int HISTORY_SCROLLBAR_TRACK_WIDTH = 4;
    private static final int HISTORY_SCROLL_ROWS = 3;
    private static final int COMMIT_METADATA_SCROLL_STEP = 12;
    private static final int COMMIT_METADATA_TEXT_PADDING = 6;
    private static final int COMMIT_METADATA_SCROLLBAR_GUTTER_WIDTH = 16;
    private static final int COMMIT_METADATA_SCROLLBAR_TRACK_RIGHT_OFFSET = 9;
    private static final int COMMIT_METADATA_SCROLLBAR_TRACK_WIDTH = 4;
    private static final int SIDEBAR_METADATA_BUTTON_GAP = 8;
    private static final int SIDEBAR_BUTTON_HEIGHT = 20;
    private static final int SIDEBAR_BUTTON_STEP = 24;
    private static final int COMMIT_METADATA_TEXT_COLOR = 0xFFB0B0B0;
    private static final int COMMIT_METADATA_VALUE_COLOR = 0xFFFFFFFF;
    private static final String SEMANTIC_CHECKOUT_UNSUPPORTED_KEY = "litematica.error.rvc_project.semantic_checkout_restore_unimplemented";
    private static final String SEMANTIC_PULL_UNSUPPORTED_KEY = "litematica.error.rvc_project.semantic_pull_restore_unimplemented";

    private final Path repositoryDirectory;
    private final String projectName;
    private List<RvcProjectService.CommitInfo> history = List.of();
    @Nullable private RvcProjectService.CommitInfo selectedCommit;
    @Nullable private RvcProjectService.TrackingOverlay trackingOverlay;
    @Nullable private String remoteUrl;
    private String historySearchQuery = "";
    private int historyScrollOffset;
    private int commitMetadataScrollOffset;
    private int commitMetadataContentHeight;
    private String trackingStatus = "";
    private boolean detachedHead;
    private String checkoutBranchName = RvcProjectService.DEFAULT_BRANCH;

    public GuiRvcProject(Path repositoryDirectory, String projectName)
    {
        this.repositoryDirectory = repositoryDirectory;
        this.projectName = projectName;
        this.title = StringUtils.translate("litematica.gui.title.rvc_project", Reference.MOD_VERSION, projectName);
    }

    @Override
    public void initGui()
    {
        super.initGui();
        this.refreshRepositoryState();
        this.refreshHistory();
        this.createHistorySearchField();

        int x = this.getHistoryPanelVisibleLeftX();
        x += this.createButton(x, TOP_BUTTON_Y, ButtonType.SAVE_VERSION);
        x += this.createButton(x, TOP_BUTTON_Y, ButtonType.DISCARD_CHANGES);
        this.createButton(x, TOP_BUTTON_Y, ButtonType.CLEAR_AREA);

        int rightX = this.getScreenWidth() - PANEL_MARGIN;
        rightX -= this.createRightButton(rightX, TOP_BUTTON_Y, ButtonType.BRANCH_SELECTOR);
        rightX -= 4;
        rightX -= this.createRightButton(rightX, TOP_BUTTON_Y, ButtonType.PULL);
        rightX -= 4;
        this.createRightButton(rightX, TOP_BUTTON_Y, ButtonType.PUSH);

        x = this.getHistoryPanelVisibleLeftX();
        int bottomY = this.getBottomButtonY();
        x += this.createButton(x, bottomY, ButtonType.CHECKOUT_VERSION);
        x += this.createButton(x, bottomY, ButtonType.VIEW_CHANGES);
        x += this.createButton(x, bottomY, ButtonType.REVERT_CHANGES);
        this.createButton(x, bottomY, ButtonType.CREATE_BRANCH);
        this.createRightButton(this.getScreenWidth() - PANEL_MARGIN, bottomY, ButtonType.LITEMATICA_MENU);

        this.createSidebarButtons();
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);

        this.drawHistoryPanel(ctx, mouseX, mouseY);
        this.drawCommitInfoPanel(ctx);
    }

    @Override
    public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick)
    {
        if (super.onMouseClicked(click, doubleClick))
        {
            return true;
        }

        RvcProjectService.CommitInfo clickedCommit = this.getCommitAt((int) click.x(), (int) click.y());

        if (clickedCommit != null)
        {
            if (!this.isSelectedCommit(clickedCommit))
            {
                this.commitMetadataScrollOffset = 0;
            }

            this.selectedCommit = clickedCommit;
            return true;
        }

        return false;
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)
    {
        if (this.isMouseOverCommitMetadataPanel((int) mouseX, (int) mouseY))
        {
            int oldOffset = this.commitMetadataScrollOffset;
            this.commitMetadataScrollOffset -= (int) (verticalAmount * COMMIT_METADATA_SCROLL_STEP);
            this.clampCommitMetadataScroll();

            if (oldOffset != this.commitMetadataScrollOffset)
            {
                return true;
            }
        }

        if (this.isMouseOverHistoryList((int) mouseX, (int) mouseY))
        {
            int oldOffset = this.historyScrollOffset;
            this.scrollHistory(verticalAmount);

            if (oldOffset != this.historyScrollOffset)
            {
                return true;
            }
        }

        return super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void drawHistoryPanel(GuiContext ctx, int mouseX, int mouseY)
    {
        int panelX = this.getHistoryPanelX();
        int panelY = CONTENT_TOP_Y;
        int panelWidth = this.getHistoryPanelWidth();
        int panelHeight = this.getContentBottomY() - panelY;
        int searchX = panelX + 4;
        int searchY = panelY + 4;
        int searchWidth = panelWidth - 8;
        int x = panelX + 6;
        int y = this.getHistoryStartY();

        RenderUtils.drawOutlinedBox(ctx, panelX, panelY, panelWidth, panelHeight, 0xB0000000, COLOR_HORIZONTAL_BAR);
        RenderUtils.drawOutlinedBox(ctx, searchX, searchY, searchWidth, SEARCH_HEIGHT, 0xA0000000, COLOR_HORIZONTAL_BAR);
        Icons.FILE_ICON_SEARCH.renderAt(ctx, searchX + 4, searchY + 3, 0, true, false);

        List<RvcProjectService.CommitInfo> visibleHistory = this.filteredHistory();

        if (this.history.isEmpty())
        {
            ctx.drawString(ctx.fontRenderer(), StringUtils.translate("litematica.gui.label.rvc_project.history_empty"), x, y, 0xFFAAAAAA, false);
            return;
        }

        if (visibleHistory.isEmpty())
        {
            ctx.drawString(ctx.fontRenderer(), StringUtils.translate("litematica.gui.label.rvc_project.history_no_matches"), x, y, 0xFFAAAAAA, false);
            return;
        }

        int maxY = this.getContentBottomY() - HISTORY_ROW_HEIGHT;
        this.clampHistoryScroll(visibleHistory);
        int visibleRowCount = this.getHistoryVisibleRowCount();
        int hashRightPadding = this.getHistoryHashRightPadding();
        int endIndex = Math.min(visibleHistory.size(), this.historyScrollOffset + visibleRowCount);

        for (int index = this.historyScrollOffset; index < endIndex; index++)
        {
            if (y > maxY)
            {
                break;
            }

            RvcProjectService.CommitInfo commit = visibleHistory.get(index);
            int rowColor = index % 2 == 0 ? 0xA0303030 : 0xA0101010;
            boolean selected = this.isSelectedCommit(commit);
            int rowX = panelX + 2;
            int rowWidth = panelWidth - 4;

            if (selected || GuiBase.isMouseOver(mouseX, mouseY, rowX, y, rowWidth, HISTORY_ROW_HEIGHT))
            {
                rowColor = 0xA0707070;
            }

            RenderUtils.drawRect(ctx, rowX, y, rowWidth, HISTORY_ROW_HEIGHT, rowColor);

            if (selected)
            {
                RenderUtils.drawOutline(ctx, rowX, y, rowWidth, HISTORY_ROW_HEIGHT, 0xFFE0E0E0);
            }

            this.drawHistoryRowText(ctx, commit, x, y + 5, panelX + panelWidth - hashRightPadding);
            y += HISTORY_ROW_HEIGHT;
        }

        this.drawHistoryScrollbar(ctx, panelX, panelWidth, visibleHistory);
    }

    private void drawHistoryRowText(GuiContext ctx, RvcProjectService.CommitInfo commit, int x, int y, int hashRightX)
    {
        int hashWidth = this.getStringWidth(commit.shortId());
        int hashX = hashRightX - hashWidth;
        int availableTextWidth = Math.max(20, hashX - x - 6);
        String title = commit.message();
        String author = " " + commit.author();
        int titleWidth = this.getStringWidth(title);
        int authorWidth = this.getStringWidth(author);

        if (titleWidth + authorWidth <= availableTextWidth)
        {
            ctx.drawString(ctx.fontRenderer(), title, x, y, 0xFFFFFFFF, false);
            ctx.drawString(ctx.fontRenderer(), author, x + titleWidth, y, 0xFF7A7A7A, false);
        }
        else if (authorWidth + 24 <= availableTextWidth)
        {
            String clippedTitle = this.ellipsizeToWidth(title, availableTextWidth - authorWidth);
            ctx.drawString(ctx.fontRenderer(), clippedTitle, x, y, 0xFFFFFFFF, false);
            ctx.drawString(ctx.fontRenderer(), author, x + this.getStringWidth(clippedTitle), y, 0xFF7A7A7A, false);
        }
        else
        {
            ctx.drawString(ctx.fontRenderer(), this.ellipsizeToWidth(title + author, availableTextWidth), x, y, 0xFFFFFFFF, false);
        }

        ctx.drawString(ctx.fontRenderer(), commit.shortId(), hashX, y, 0xFFFFD36A, false);
    }

    private List<RvcProjectService.CommitInfo> filteredHistory()
    {
        String query = this.historySearchQuery.trim().toLowerCase(Locale.ROOT);

        if (query.isEmpty())
        {
            return this.history;
        }

        String[] tokens = query.split("\\s+");
        List<RvcProjectService.CommitInfo> commits = new ArrayList<>();

        for (RvcProjectService.CommitInfo commit : this.history)
        {
            if (this.commitMatchesSearch(commit, tokens))
            {
                commits.add(commit);
            }
        }

        return commits;
    }

    private boolean commitMatchesSearch(RvcProjectService.CommitInfo commit, String[] tokens)
    {
        String haystack = (commit.message() + "\n" +
                commit.description() + "\n" +
                commit.author() + "\n" +
                commit.id() + "\n" +
                commit.shortId()).toLowerCase(Locale.ROOT);

        for (String token : tokens)
        {
            if (!haystack.contains(token))
            {
                return false;
            }
        }

        return true;
    }

    private void scrollHistory(double verticalAmount)
    {
        if (verticalAmount == 0)
        {
            return;
        }

        int rows = Math.max(1, (int) Math.ceil(Math.abs(verticalAmount))) * HISTORY_SCROLL_ROWS;
        this.historyScrollOffset += verticalAmount > 0 ? -rows : rows;
        this.clampHistoryScroll(this.filteredHistory());
    }

    private void drawHistoryScrollbar(GuiContext ctx, int panelX, int panelWidth, List<RvcProjectService.CommitInfo> visibleHistory)
    {
        int maxScroll = this.getHistoryMaxScroll(visibleHistory);

        if (maxScroll <= 0)
        {
            return;
        }

        int trackX = panelX + panelWidth - HISTORY_SCROLLBAR_TRACK_RIGHT_OFFSET;
        int trackY = this.getHistoryStartY();
        int trackHeight = this.getHistoryViewportHeight();
        int visibleRows = this.getHistoryVisibleRowCount();
        int thumbHeight = Math.max(14, trackHeight * visibleRows / Math.max(visibleRows, visibleHistory.size()));
        int thumbY = trackY + (trackHeight - thumbHeight) * this.historyScrollOffset / maxScroll;

        RenderUtils.drawRect(ctx, trackX, trackY, HISTORY_SCROLLBAR_TRACK_WIDTH, trackHeight, 0xA0202020);
        RenderUtils.drawRect(ctx, trackX, thumbY, HISTORY_SCROLLBAR_TRACK_WIDTH, thumbHeight, 0xFFE0E0E0);
    }

    private int getHistoryHashRightPadding()
    {
        return HISTORY_HASH_RIGHT_PADDING + HISTORY_SCROLLBAR_GUTTER_WIDTH;
    }

    private boolean isMouseOverHistoryList(int mouseX, int mouseY)
    {
        return GuiBase.isMouseOver(mouseX, mouseY, this.getHistoryPanelX() + 2, this.getHistoryStartY(), this.getHistoryPanelWidth() - 4, this.getHistoryViewportHeight());
    }

    private void clampHistoryScroll(List<RvcProjectService.CommitInfo> visibleHistory)
    {
        int maxScroll = this.getHistoryMaxScroll(visibleHistory);
        this.historyScrollOffset = Math.max(0, Math.min(this.historyScrollOffset, maxScroll));
    }

    private int getHistoryMaxScroll(List<RvcProjectService.CommitInfo> visibleHistory)
    {
        int visibleRows = this.getHistoryVisibleRowCount();
        return visibleRows <= 0 ? 0 : Math.max(0, visibleHistory.size() - visibleRows);
    }

    private int getHistoryVisibleRowCount()
    {
        return this.getHistoryViewportHeight() / HISTORY_ROW_HEIGHT;
    }

    private int getHistoryVisibleRowsHeight()
    {
        return this.getHistoryVisibleRowCount() * HISTORY_ROW_HEIGHT;
    }

    private int getHistoryViewportHeight()
    {
        return Math.max(0, this.getContentBottomY() - this.getHistoryStartY());
    }

    private void drawCommitInfoPanel(GuiContext ctx)
    {
        int x = this.getSidebarX();
        int y = CONTENT_TOP_Y;
        int width = this.getSidebarWidth();
        int height = this.getInfoPanelHeight();
        int textX = x + COMMIT_METADATA_TEXT_PADDING;
        int textY = y + COMMIT_METADATA_TEXT_PADDING;
        int textWidth = width - COMMIT_METADATA_TEXT_PADDING - COMMIT_METADATA_SCROLLBAR_GUTTER_WIDTH;
        int viewportHeight = height - COMMIT_METADATA_TEXT_PADDING * 2;

        RenderUtils.drawOutlinedBox(ctx, x, y, width, height, 0xA0000000, COLOR_HORIZONTAL_BAR);

        if (this.selectedCommit == null)
        {
            ctx.drawString(ctx.fontRenderer(), StringUtils.translate("litematica.gui.label.rvc_project.no_commit_selected"), textX, textY, 0xFFAAAAAA, false);
            return;
        }

        List<CommitMetadataLine> lines = this.createCommitMetadataLines(textWidth);
        this.commitMetadataContentHeight = this.getCommitMetadataContentHeight(lines.size());
        this.clampCommitMetadataScroll();

        ctx.pushScissor(new ScreenRectangle(textX, textY, textWidth + 1, viewportHeight));

        int drawY = textY - this.commitMetadataScrollOffset;

        for (CommitMetadataLine line : lines)
        {
            int lineX = textX + line.indent();
            ctx.drawString(ctx.fontRenderer(), line.label(), lineX, drawY, COMMIT_METADATA_TEXT_COLOR, false);

            if (line.value() != null)
            {
                ctx.drawString(ctx.fontRenderer(), line.value(), lineX + line.valueOffset(), drawY, COMMIT_METADATA_VALUE_COLOR, false);
            }

            drawY += COMMIT_METADATA_SCROLL_STEP;
        }

        ctx.popScissor();
        this.drawCommitMetadataScrollbar(ctx, x, y, width, height, viewportHeight);
    }

    private List<CommitMetadataLine> createCommitMetadataLines(int maxWidth)
    {
        List<CommitMetadataLine> lines = new ArrayList<>();

        this.addWrappedInlineMetadataLine(lines, "litematica.gui.label.rvc_project.info_title", this.selectedCommit.message(), maxWidth);
        this.addInlineMetadataLine(lines, "litematica.gui.label.rvc_project.info_author", this.selectedCommit.author(), maxWidth);
        this.addDescriptionMetadataLines(lines, maxWidth);
        this.addInlineMetadataLine(lines, "litematica.gui.label.rvc_project.info_date", this.selectedCommit.time(), maxWidth);
        this.addInlineMetadataLine(lines, "litematica.gui.label.rvc_project.info_version", this.selectedCommit.shortId(), maxWidth);
        this.addBlockMetadataLines(lines, "litematica.gui.label.rvc_project.info_changes", this.getChangesDisplayText(), maxWidth);

        return lines;
    }

    private void addInlineMetadataLine(List<CommitMetadataLine> lines, String labelKey, String value, int maxWidth)
    {
        String label = StringUtils.translate(labelKey) + ": ";
        int labelWidth = this.getStringWidth(label);
        int valueWidth = Math.max(20, maxWidth - labelWidth);
        lines.add(new CommitMetadataLine(label, this.ellipsizeToWidth(value, valueWidth), 0, labelWidth));
    }

    private void addWrappedInlineMetadataLine(List<CommitMetadataLine> lines, String labelKey, String value, int maxWidth)
    {
        String label = StringUtils.translate(labelKey) + ": ";
        int labelWidth = this.getStringWidth(label);
        int valueWidth = Math.max(20, maxWidth - labelWidth);
        List<String> wrappedLines = this.wrapTextToWidth(value, valueWidth);

        if (wrappedLines.isEmpty())
        {
            lines.add(new CommitMetadataLine(label, "", 0, labelWidth));
            return;
        }

        lines.add(new CommitMetadataLine(label, wrappedLines.get(0), 0, labelWidth));

        for (int i = 1; i < wrappedLines.size(); i++)
        {
            lines.add(new CommitMetadataLine("", wrappedLines.get(i), labelWidth, 0));
        }
    }

    private void addBlockMetadataLines(List<CommitMetadataLine> lines, String labelKey, String value, int maxWidth)
    {
        int indent = 12;
        lines.add(new CommitMetadataLine(StringUtils.translate(labelKey) + ":", null, 0, 0));

        for (String line : this.wrapTextToWidth(value, Math.max(20, maxWidth - indent)))
        {
            lines.add(new CommitMetadataLine("", line, indent, 0));
        }
    }

    private void addDescriptionMetadataLines(List<CommitMetadataLine> lines, int maxWidth)
    {
        String description = this.selectedCommit.description();

        if (description != null && !description.isBlank())
        {
            this.addBlockMetadataLines(lines, "litematica.gui.label.rvc_project.info_description", description, maxWidth);
        }
    }

    private String getChangesDisplayText()
    {
        String changes = this.selectedCommit.changes();
        return changes == null || changes.isBlank() ? StringUtils.translate("litematica.gui.label.rvc_project.changes_unavailable") : changes;
    }

    private List<String> wrapTextToWidth(String text, int maxWidth)
    {
        List<String> lines = new ArrayList<>();
        String[] paragraphs = text.split("\\R", -1);

        for (String paragraph : paragraphs)
        {
            this.wrapParagraphToWidth(paragraph, maxWidth, lines);
        }

        return lines.isEmpty() ? List.of("") : lines;
    }

    private void wrapParagraphToWidth(String paragraph, int maxWidth, List<String> lines)
    {
        if (paragraph.isBlank())
        {
            lines.add("");
            return;
        }

        String line = "";

        for (String word : paragraph.trim().split("\\s+"))
        {
            String candidate = line.isEmpty() ? word : line + " " + word;

            if (this.getStringWidth(candidate) <= maxWidth)
            {
                line = candidate;
                continue;
            }

            if (!line.isEmpty())
            {
                lines.add(line);
                line = "";
            }

            if (this.getStringWidth(word) <= maxWidth)
            {
                line = word;
            }
            else
            {
                line = this.wrapLongWordToWidth(word, maxWidth, lines);
            }
        }

        if (!line.isEmpty())
        {
            lines.add(line);
        }
    }

    private String wrapLongWordToWidth(String word, int maxWidth, List<String> lines)
    {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < word.length(); i++)
        {
            String candidate = builder.toString() + word.charAt(i);

            if (this.getStringWidth(candidate) > maxWidth && !builder.isEmpty())
            {
                lines.add(builder.toString());
                builder.setLength(0);
            }

            builder.append(word.charAt(i));
        }

        return builder.toString();
    }

    private void drawCommitMetadataScrollbar(GuiContext ctx, int panelX, int panelY, int panelWidth, int panelHeight, int viewportHeight)
    {
        int maxScroll = this.getCommitMetadataMaxScroll(viewportHeight);

        if (maxScroll <= 0)
        {
            return;
        }

        int trackX = panelX + panelWidth - COMMIT_METADATA_SCROLLBAR_TRACK_RIGHT_OFFSET;
        int trackY = panelY + COMMIT_METADATA_TEXT_PADDING;
        int trackHeight = panelHeight - COMMIT_METADATA_TEXT_PADDING * 2;
        int thumbHeight = Math.max(14, trackHeight * viewportHeight / Math.max(viewportHeight, this.commitMetadataContentHeight));
        int thumbY = trackY + (trackHeight - thumbHeight) * this.commitMetadataScrollOffset / maxScroll;

        RenderUtils.drawRect(ctx, trackX, trackY, COMMIT_METADATA_SCROLLBAR_TRACK_WIDTH, trackHeight, 0xA0202020);
        RenderUtils.drawRect(ctx, trackX, thumbY, COMMIT_METADATA_SCROLLBAR_TRACK_WIDTH, thumbHeight, 0xFFE0E0E0);
    }

    private int getCommitMetadataContentHeight(int lineCount)
    {
        if (lineCount <= 0)
        {
            return 0;
        }

        return (lineCount - 1) * COMMIT_METADATA_SCROLL_STEP + Math.max(0, this.font.lineHeight - 1);
    }

    private boolean isMouseOverCommitMetadataPanel(int mouseX, int mouseY)
    {
        return GuiBase.isMouseOver(mouseX, mouseY, this.getSidebarX(), CONTENT_TOP_Y, this.getSidebarWidth(), this.getInfoPanelHeight());
    }

    private void clampCommitMetadataScroll()
    {
        int maxScroll = this.getCommitMetadataMaxScroll(this.getInfoPanelHeight() - COMMIT_METADATA_TEXT_PADDING * 2);
        this.commitMetadataScrollOffset = Math.clamp(this.commitMetadataScrollOffset, 0, maxScroll);
    }

    private int getCommitMetadataMaxScroll(int viewportHeight)
    {
        return Math.max(0, this.commitMetadataContentHeight - viewportHeight);
    }

    private int createButton(int x, int y, ButtonType type)
    {
        ButtonGeneric button = this.createButtonWidget(x, y, -1, type);
        this.addButton(button, new ButtonListener(type, this));
        return button.getWidth() + 4;
    }

    private int createRightButton(int rightX, int y, ButtonType type)
    {
        ButtonGeneric button = this.createButtonWidget(0, y, -1, type);
        button.setX(rightX - button.getWidth());
        this.addButton(button, new ButtonListener(type, this));
        return button.getWidth();
    }

    private ButtonGeneric createButtonWidget(int x, int y, int width, ButtonType type)
    {
        return new ButtonGeneric(x, y, width, SIDEBAR_BUTTON_HEIGHT, type.getLabel(this), type.icon);
    }

    private void createHistorySearchField()
    {
        int x = this.getHistoryPanelX() + 22;
        int searchY = CONTENT_TOP_Y + 4;
        int y = searchY + (SEARCH_HEIGHT - this.font.lineHeight) / 2;
        int width = this.getHistoryPanelWidth() - 30;
        GuiTextFieldGeneric textField = new GuiTextFieldGeneric(x, y, width, 14, this.font);

        textField.setBordered(false);
        textField.setTextColor(0xFFFFFFFF);
        textField.setTextColorUneditable(0xFFAAAAAA);
        textField.setMaxLength(128);
        textField.setValueWrapper(this.historySearchQuery);
        this.addTextField(textField, new HistorySearchListener(this));
    }

    private void createSidebarButtons()
    {
        int y = this.getSidebarActionsY();
        int width = this.createButtonWidget(0, 0, -1, ButtonType.PROJECT_SETTINGS).getWidth();
        int x = this.getSidebarX() - 1;

        this.addButton(this.createButtonWidget(x, y, width, ButtonType.PROJECT_EDITOR), new ButtonListener(ButtonType.PROJECT_EDITOR, this));
        y += SIDEBAR_BUTTON_STEP;
        this.addButton(this.createButtonWidget(x, y, width, ButtonType.PROJECT_SETTINGS), new ButtonListener(ButtonType.PROJECT_SETTINGS, this));
        y += SIDEBAR_BUTTON_STEP;
        this.addButton(this.createButtonWidget(x, y, width, ButtonType.CLOSE_PROJECT), new ButtonListener(ButtonType.CLOSE_PROJECT, this));
    }

    private int getBottomButtonY()
    {
        return this.getScreenHeight() - 24;
    }

    private int getContentBottomY()
    {
        return this.getBottomButtonY() - 8;
    }

    private int getHistoryPanelX()
    {
        return PANEL_MARGIN;
    }

    private int getHistoryPanelVisibleLeftX()
    {
        return this.getHistoryPanelX() - 1;
    }

    private int getHistoryPanelWidth()
    {
        return Math.max(120, this.getSidebarX() - this.getHistoryPanelX() - 8);
    }

    private int getSidebarWidth()
    {
        return Math.clamp(this.getScreenWidth() / 3, 190, 260);
    }

    private int getSidebarX()
    {
        return this.getScreenWidth() - this.getSidebarWidth() - PANEL_MARGIN;
    }

    private int getSidebarActionsY()
    {
        return this.getContentBottomY() - SIDEBAR_BUTTON_HEIGHT - SIDEBAR_BUTTON_STEP * 2 + 1;
    }

    private int getInfoPanelHeight()
    {
        return Math.max(0, this.getSidebarActionsY() - CONTENT_TOP_Y - SIDEBAR_METADATA_BUTTON_GAP);
    }

    private int getHistoryStartY()
    {
        return CONTENT_TOP_Y + SEARCH_HEIGHT + 9;
    }

    @Nullable
    private RvcProjectService.CommitInfo getCommitAt(int mouseX, int mouseY)
    {
        int panelX = this.getHistoryPanelX();
        int panelWidth = this.getHistoryPanelWidth();
        int y = this.getHistoryStartY();
        List<RvcProjectService.CommitInfo> visibleHistory = this.filteredHistory();
        this.clampHistoryScroll(visibleHistory);

        if (!GuiBase.isMouseOver(mouseX, mouseY, panelX + 2, y, panelWidth - 4, this.getHistoryVisibleRowsHeight()))
        {
            return null;
        }

        int index = this.historyScrollOffset + (mouseY - y) / HISTORY_ROW_HEIGHT;

        if (index < 0 || index >= visibleHistory.size())
        {
            return null;
        }

        return visibleHistory.get(index);
    }

    private boolean isSelectedCommit(RvcProjectService.CommitInfo commit)
    {
        return this.selectedCommit != null && this.selectedCommit.id().equals(commit.id());
    }

    private void refreshRepositoryState()
    {
        this.detachedHead = false;
        this.checkoutBranchName = RvcProjectService.DEFAULT_BRANCH;
        this.remoteUrl = null;

        try
        {
            this.detachedHead = RvcProjectService.isDetachedHead(this.repositoryDirectory);
            this.remoteUrl = RvcProjectService.remoteOriginUrl(this.repositoryDirectory);
            this.checkoutBranchName = RvcProjectService.preferredCheckoutBranchName(this.repositoryDirectory);
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.history_failed", e.getMessage());
        }
    }

    private void refreshHistory()
    {
        try
        {
            this.history = RvcProjectService.listCommits(this.repositoryDirectory);
            this.retainSelectedVisibleCommit();
        }
        catch (Exception e)
        {
            this.history = List.of();
            this.selectedCommit = null;
            this.historyScrollOffset = 0;
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.history_failed", e.getMessage());
        }
    }

    private void retainSelectedVisibleCommit()
    {
        List<RvcProjectService.CommitInfo> visibleHistory = this.filteredHistory();

        if (visibleHistory.isEmpty())
        {
            this.selectedCommit = null;
            this.historyScrollOffset = 0;
            this.commitMetadataScrollOffset = 0;
            return;
        }

        if (this.selectedCommit != null)
        {
            for (int index = 0; index < visibleHistory.size(); index++)
            {
                RvcProjectService.CommitInfo commit = visibleHistory.get(index);

                if (commit.id().equals(this.selectedCommit.id()))
                {
                    this.selectedCommit = commit;
                    this.ensureHistoryRowVisible(index, visibleHistory);
                    this.clampCommitMetadataScroll();
                    return;
                }
            }
        }

        this.selectedCommit = visibleHistory.get(0);
        this.historyScrollOffset = 0;
        this.commitMetadataScrollOffset = 0;
    }

    private void ensureHistoryRowVisible(int rowIndex, List<RvcProjectService.CommitInfo> visibleHistory)
    {
        int visibleRows = this.getHistoryVisibleRowCount();

        if (visibleRows <= 0)
        {
            this.historyScrollOffset = 0;
            return;
        }

        if (rowIndex < this.historyScrollOffset)
        {
            this.historyScrollOffset = rowIndex;
        }
        else if (rowIndex >= this.historyScrollOffset + visibleRows)
        {
            this.historyScrollOffset = rowIndex - visibleRows + 1;
        }

        this.clampHistoryScroll(visibleHistory);
    }

    private void promptCommitMessage()
    {
        try
        {
            if (RvcProjectService.isDetachedHead(this.repositoryDirectory))
            {
                this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.commit_failed", StringUtils.translate("litematica.error.rvc_project.detached_head_commit"));
                GuiConfirmAction gui = new GuiConfirmAction(
                        420,
                        "litematica.gui.title.rvc_project.detached_head_commit",
                        new CheckoutMasterBeforeCommitListener(this),
                        this,
                        "litematica.gui.message.rvc_project.detached_head_commit",
                        RvcProjectService.DEFAULT_BRANCH
                );
                GuiBase.openGui(gui);
                return;
            }
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.commit_failed", e.getMessage());
            return;
        }

        GuiBase.openGui(new GuiTextInput(256, "litematica.gui.title.rvc_project.commit_message", "update schematic", this, new CommitMessageSetter(this)));
    }

    private void promptCheckoutBranch()
    {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.no_world");
            return;
        }

        if (this.reportSemanticUnsupported("litematica.error.rvc_project.checkout_failed", SEMANTIC_CHECKOUT_UNSUPPORTED_KEY))
        {
            return;
        }

        Boolean hasUncommittedChanges = this.hasUncommittedChangesOrReport("litematica.error.rvc_project.checkout_failed");

        if (hasUncommittedChanges == null)
        {
            return;
        }

        if (hasUncommittedChanges)
        {
            GuiConfirmAction gui = new GuiConfirmAction(
                    420,
                    "litematica.gui.title.rvc_project.confirm_reset_checkout_branch",
                    new ResetAndCheckoutBranchConfirmListener(this),
                    this,
                    "litematica.gui.message.rvc_project.confirm_reset_checkout_detached_branch",
                    this.checkoutBranchName
            );
            GuiBase.openGui(gui);
            return;
        }

        this.checkoutBranch();
    }

    private void checkoutBranch()
    {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.no_world");
            return;
        }

        try
        {
            this.removeTrackingOverlay();
            RvcProjectService.SchematicWorldRestore restore = RvcProjectService.checkoutBranchToSchematicWorld(this.repositoryDirectory, this.projectName, this.checkoutBranchName, minecraft.level, this);
            this.trackingOverlay = restore.overlay();
            this.updateTrackingStatusAfterRestore();
            this.initGui();
            this.addMessage(MessageType.SUCCESS, "litematica.message.rvc_project.checked_out_branch", this.checkoutBranchName, restore.boxCount());
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.checkout_failed", e.getMessage());
        }
    }

    private void checkoutMasterAndPromptCommitMessage()
    {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.no_world");
            return;
        }

        Boolean hasUncommittedChanges = this.hasUncommittedChangesOrReport("litematica.error.rvc_project.checkout_failed");

        if (hasUncommittedChanges == null)
        {
            return;
        }

        if (hasUncommittedChanges)
        {
            GuiConfirmAction gui = new GuiConfirmAction(
                    420,
                    "litematica.gui.title.rvc_project.confirm_reset_checkout_branch",
                    new ResetAndCheckoutMasterBeforeCommitListener(this),
                    this,
                    "litematica.gui.message.rvc_project.confirm_reset_checkout_branch",
                    RvcProjectService.DEFAULT_BRANCH
            );
            GuiBase.openGui(gui);
            return;
        }

        try
        {
            RvcProjectService.SchematicWorldRestore restore = RvcProjectService.checkoutBranchToSchematicWorld(this.repositoryDirectory, this.projectName, RvcProjectService.DEFAULT_BRANCH, minecraft.level, this);
            this.trackingOverlay = restore.overlay();
            this.updateTrackingStatusAfterRestore();
            this.initGui();
            this.addMessage(MessageType.SUCCESS, "litematica.message.rvc_project.checked_out_branch", RvcProjectService.DEFAULT_BRANCH, restore.boxCount());
            GuiBase.openGui(new GuiTextInput(256, "litematica.gui.title.rvc_project.commit_message", "update schematic", this, new CommitMessageSetter(this)));
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.checkout_failed", e.getMessage());
        }
    }

    private void commitStoredSelectionWithCurrentSelectionFallback(String message)
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

            if (RvcProjectService.isSemanticProject(this.repositoryDirectory))
            {
                this.trackingStatus = StringUtils.translate("litematica.gui.label.rvc_project.semantic_tracking_unavailable");
            }
            else
            {
                this.loadTrackingOverlay();
            }

            this.initGui();
            this.addMessage(MessageType.SUCCESS, "litematica.message.rvc_project.committed");
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.commit_failed", e.getMessage());
        }
    }

    private void push()
    {
        try
        {
            if (!RvcProjectService.hasRemote(this.repositoryDirectory))
            {
                this.promptRemoteEdit(true);
                return;
            }

            RvcProjectService.push(this.repositoryDirectory);
            this.addMessage(MessageType.SUCCESS, "litematica.message.rvc_project.pushed");
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.push_failed", RvcProjectService.describeRemoteFailure(e));
        }
    }

    private void promptRemoteEdit(boolean pushAfterSave)
    {
        try
        {
            String currentRemoteUrl = RvcProjectService.remoteOriginUrl(this.repositoryDirectory);
            String inputValue = currentRemoteUrl != null ? currentRemoteUrl : "";
            GuiBase.openGui(new GuiTextInput(512, "litematica.gui.title.rvc_project.remote_url", inputValue, this, new RemoteUrlSetter(this, pushAfterSave)));
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.remote_failed", e.getMessage());
        }
    }

    private void promptPull()
    {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.no_world");
            return;
        }

        if (this.reportSemanticUnsupported("litematica.error.rvc_project.pull_failed", SEMANTIC_PULL_UNSUPPORTED_KEY))
        {
            return;
        }

        Boolean hasUncommittedChanges = this.hasUncommittedChangesOrReport("litematica.error.rvc_project.pull_failed");

        if (hasUncommittedChanges == null)
        {
            return;
        }

        if (hasUncommittedChanges)
        {
            GuiConfirmAction gui = new GuiConfirmAction(
                    420,
                    "litematica.gui.title.rvc_project.confirm_reset_pull",
                    new ResetAndPullConfirmListener(this),
                    this,
                    "litematica.gui.message.rvc_project.confirm_reset_pull"
            );
            GuiBase.openGui(gui);
            return;
        }

        GuiConfirmAction gui = new GuiConfirmAction(
                420,
                "litematica.gui.title.rvc_project.confirm_pull",
                new PullConfirmListener(this),
                this,
                "litematica.gui.message.rvc_project.confirm_pull"
        );
        GuiBase.openGui(gui);
    }

    private void pull()
    {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.no_world");
            return;
        }

        try
        {
            String result = RvcProjectService.pull(this.repositoryDirectory);
            this.removeTrackingOverlay();
            this.trackingOverlay = RvcProjectService.restoreWorkingTreeToSchematicWorld(this.repositoryDirectory, this.projectName, minecraft.level, this).overlay();
            this.updateTrackingStatusAfterRestore();
            this.initGui();
            this.addMessage(MessageType.SUCCESS, "litematica.message.rvc_project.pulled", result);
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.pull_failed", RvcProjectService.describeRemoteFailure(e));
        }
    }

    @Nullable
    private Boolean hasUncommittedChangesOrReport(String errorKey)
    {
        try
        {
            return RvcProjectService.hasUncommittedChanges(this.repositoryDirectory);
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, errorKey, e.getMessage());
            return null;
        }
    }

    private boolean reportSemanticUnsupported(String errorKey, String detailKey)
    {
        if (!RvcProjectService.isSemanticProject(this.repositoryDirectory))
        {
            return false;
        }

        this.addMessage(MessageType.ERROR, errorKey, StringUtils.translate(detailKey));
        return true;
    }

    private void resetWorkingTreeThenPull()
    {
        try
        {
            RvcProjectService.resetWorkingTreeToHead(this.repositoryDirectory);
            this.pull();
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.pull_failed", e.getMessage());
        }
    }

    private void updateAreas()
    {
        Minecraft minecraft = Minecraft.getInstance();
        AreaSelection selection = DataManager.getSelectionManager().getCurrentSelection();

        if (!RvcProjectService.isSemanticProject(this.repositoryDirectory))
        {
            this.addMessage(MessageType.INFO, "litematica.message.rvc_project.update_areas_semantic_only");
            return;
        }

        if (minecraft.player == null)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.no_player");
            return;
        }

        if (minecraft.level == null)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.no_world");
            return;
        }

        if (selection == null)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.update_areas_failed", StringUtils.translate("litematica.error.rvc_project.no_selection"));
            return;
        }

        try
        {
            if (RvcProjectService.isDetachedHead(this.repositoryDirectory))
            {
                this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.update_areas_failed", StringUtils.translate("litematica.error.rvc_project.detached_head_commit"));
                return;
            }

            int regionCount = RvcProjectService.countValidSelectionRegions(selection);

            if (regionCount <= 0)
            {
                this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.update_areas_failed", StringUtils.translate("litematica.error.rvc_project.no_selection"));
                return;
            }

            GuiConfirmAction gui = new GuiConfirmAction(
                    420,
                    "litematica.gui.title.rvc_project.confirm_update_areas",
                    new UpdateAreasConfirmListener(this),
                    this,
                    "litematica.gui.message.rvc_project.confirm_update_areas",
                    regionCount
            );
            GuiBase.openGui(gui);
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.update_areas_failed", e.getMessage());
        }
    }

    private void updateAreasFromCurrentSelection()
    {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        Level world = minecraft.level;
        AreaSelection selection = DataManager.getSelectionManager().getCurrentSelection();

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

        if (selection == null)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.update_areas_failed", StringUtils.translate("litematica.error.rvc_project.no_selection"));
            return;
        }

        try
        {
            RvcPlayerIdentity identity = new RvcPlayerIdentity(player.getName().getString(), player.getUUID());
            RvcProjectService.UpdateAreasResult result = RvcProjectService.updateSemanticAreas(this.repositoryDirectory, identity, world, selection, "update areas");

            if (result.commit() == null)
            {
                this.initGui();
                this.addMessage(MessageType.INFO, "litematica.message.rvc_project.nothing_to_commit");
                return;
            }

            this.trackingStatus = StringUtils.translate("litematica.gui.label.rvc_project.semantic_tracking_unavailable");
            this.initGui();
            this.addMessage(MessageType.SUCCESS, "litematica.message.rvc_project.update_areas_updated", result.regionCount());
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.update_areas_failed", e.getMessage());
        }
    }

    private void scanChanges()
    {
        Minecraft minecraft = Minecraft.getInstance();
        Level world = minecraft.level;

        if (world == null)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.no_world");
            return;
        }

        if (!RvcProjectService.isSemanticProject(this.repositoryDirectory))
        {
            this.addMessage(MessageType.INFO, "litematica.message.rvc_project.scan_semantic_only");
            return;
        }

        try
        {
            RvcProjectService.SemanticScanResult result = RvcProjectService.scanSemanticChanges(this.repositoryDirectory, world);

            if (result.unknownChunks() > 0)
            {
                this.trackingStatus = StringUtils.translate(
                        "litematica.gui.label.rvc_project.semantic_scan_unknown",
                        result.changedChunks(),
                        result.addedChunks(),
                        result.removedChunks(),
                        result.unknownChunks()
                );
                this.addMessage(MessageType.INFO, "litematica.message.rvc_project.scan_unknown", result.unknownChunks(), result.dirtyChunks());
            }
            else if (result.clean())
            {
                this.trackingStatus = StringUtils.translate("litematica.gui.label.rvc_project.semantic_scan_clean", result.unchangedChunks());
                this.addMessage(MessageType.SUCCESS, "litematica.message.rvc_project.scan_clean", result.unchangedChunks());
            }
            else
            {
                this.trackingStatus = StringUtils.translate(
                        "litematica.gui.label.rvc_project.semantic_scan_dirty",
                        result.changedChunks(),
                        result.addedChunks(),
                        result.removedChunks()
                );
                this.addMessage(MessageType.INFO, "litematica.message.rvc_project.scan_dirty", result.changedChunks(), result.addedChunks(), result.removedChunks());
            }
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.scan_failed", e.getMessage());
        }
    }

    private String getRemoteDisplayText()
    {
        String value = this.remoteUrl != null && !this.remoteUrl.isBlank() ?
                this.remoteUrl :
                StringUtils.translate("litematica.gui.label.rvc_project.remote_not_set");
        String text = StringUtils.translate("litematica.gui.label.rvc_project.remote", value);
        int maxWidth = Math.max(40, this.getScreenWidth() - 32);

        return this.ellipsizeToWidth(text, maxWidth);
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

    private void loadTrackingOverlay()
    {
        try
        {
            this.removeTrackingOverlay();
            this.trackingOverlay = RvcProjectService.loadTrackingOverlay(this.repositoryDirectory, this.projectName, Minecraft.getInstance().level, this);

            if (this.trackingOverlay.verifierStarted())
            {
                this.trackingStatus = StringUtils.translate("litematica.gui.label.rvc_project.tracking_checking", this.trackingOverlay.placement().getName());
            }
            else
            {
                this.trackingStatus = StringUtils.translate("litematica.gui.label.rvc_project.tracking_loaded", this.trackingOverlay.placement().getName());
            }
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.tracking_failed", e.getMessage());
        }
    }

    private void removeTrackingOverlay()
    {
        if (this.trackingOverlay != null)
        {
            DataManager.getSchematicPlacementManager().removeSchematicPlacement(this.trackingOverlay.placement(), false);
            this.trackingOverlay = null;
        }
    }

    private void promptCheckoutSelectedCommit()
    {
        RvcProjectService.CommitInfo commit = this.requireSelectedCommit();

        if (commit != null)
        {
            this.promptCheckoutCommit(commit);
        }
    }

    @Nullable
    private RvcProjectService.CommitInfo requireSelectedCommit()
    {
        if (this.selectedCommit == null)
        {
            this.addMessage(MessageType.INFO, "litematica.message.rvc_project.no_commit_selected");
            return null;
        }

        return this.selectedCommit;
    }

    private void openBranchSelector()
    {
        if (this.detachedHead)
        {
            this.promptCheckoutBranch();
            return;
        }

        this.showNotImplemented("litematica.message.rvc_project.branch_selector_not_implemented");
    }

    private void showNotImplemented(String key)
    {
        this.addMessage(MessageType.INFO, key);
    }

    private void promptCheckoutCommit(RvcProjectService.CommitInfo commit)
    {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.no_world");
            return;
        }

        if (this.reportSemanticUnsupported("litematica.error.rvc_project.checkout_failed", SEMANTIC_CHECKOUT_UNSUPPORTED_KEY))
        {
            return;
        }

        Boolean hasUncommittedChanges = this.hasUncommittedChangesOrReport("litematica.error.rvc_project.checkout_failed");

        if (hasUncommittedChanges == null)
        {
            return;
        }

        if (hasUncommittedChanges)
        {
            GuiConfirmAction gui = new GuiConfirmAction(
                    420,
                    "litematica.gui.title.rvc_project.confirm_reset_checkout",
                    new ResetAndCheckoutCommitConfirmListener(this, commit),
                    this,
                    "litematica.gui.message.rvc_project.confirm_reset_checkout",
                    commit.shortId(),
                    commit.message()
            );
            GuiBase.openGui(gui);
            return;
        }

        GuiConfirmAction gui = new GuiConfirmAction(
                420,
                "litematica.gui.title.rvc_project.confirm_checkout",
                new CheckoutCommitConfirmListener(this, commit),
                this,
                "litematica.gui.message.rvc_project.confirm_checkout",
                commit.shortId(),
                commit.message()
        );
        GuiBase.openGui(gui);
    }

    private void checkoutCommit(RvcProjectService.CommitInfo commit)
    {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.no_world");
            return;
        }

        try
        {
            this.removeTrackingOverlay();
            RvcProjectService.SchematicWorldRestore restore = RvcProjectService.checkoutCommitToSchematicWorld(this.repositoryDirectory, this.projectName, commit.id(), minecraft.level, this);
            this.trackingOverlay = restore.overlay();
            this.updateTrackingStatusAfterRestore();
            this.initGui();
            this.addMessage(MessageType.SUCCESS, "litematica.message.rvc_project.checked_out", commit.shortId(), restore.boxCount());
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.checkout_failed", e.getMessage());
        }
    }

    private void resetWorkingTreeThenCheckoutCommit(RvcProjectService.CommitInfo commit)
    {
        try
        {
            RvcProjectService.resetWorkingTreeToHead(this.repositoryDirectory);
            this.checkoutCommit(commit);
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.checkout_failed", e.getMessage());
        }
    }

    private void resetWorkingTreeThenCheckoutMasterAndPromptCommitMessage()
    {
        try
        {
            RvcProjectService.resetWorkingTreeToHead(this.repositoryDirectory);
            this.checkoutMasterAndPromptCommitMessage();
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.checkout_failed", e.getMessage());
        }
    }

    private void resetWorkingTreeThenCheckoutBranch()
    {
        try
        {
            RvcProjectService.resetWorkingTreeToHead(this.repositoryDirectory);
            this.checkoutBranch();
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.checkout_failed", e.getMessage());
        }
    }

    private void updateTrackingStatusAfterRestore()
    {
        if (this.trackingOverlay == null)
        {
            this.trackingStatus = "";
            return;
        }

        if (this.trackingOverlay.verifierStarted())
        {
            this.trackingStatus = StringUtils.translate("litematica.gui.label.rvc_project.tracking_checking", this.trackingOverlay.placement().getName());
        }
        else
        {
            this.trackingStatus = StringUtils.translate("litematica.gui.label.rvc_project.tracking_loaded", this.trackingOverlay.placement().getName());
        }
    }

    @Override
    public void onTaskCompleted()
    {
        if (this.trackingOverlay == null)
        {
            return;
        }

        int errors = this.trackingOverlay.verifier().getTotalErrors();
        this.trackingStatus = errors == 0 ?
                StringUtils.translate("litematica.gui.label.rvc_project.tracking_clean") :
                StringUtils.translate("litematica.gui.label.rvc_project.tracking_dirty", errors);
    }

    @Override
    public void onTaskAborted()
    {
        this.trackingStatus = StringUtils.translate("litematica.gui.label.rvc_project.tracking_aborted");
    }

    private record CommitMetadataLine(String label, @Nullable String value, int indent, int valueOffset)
    {
    }

    private enum ButtonType
    {
        SAVE_VERSION("litematica.gui.button.rvc_project.save_version", null),
        DISCARD_CHANGES("litematica.gui.button.rvc_project.discard_changes", null),
        CLEAR_AREA("litematica.gui.button.rvc_project.clear_area", null),
        PUSH("litematica.gui.button.rvc_project.push", null),
        PULL("litematica.gui.button.rvc_project.pull", null),
        BRANCH_SELECTOR("litematica.gui.button.rvc_project.branch", ButtonIcons.SCHEMATIC_PROJECTS),
        CHECKOUT_VERSION("litematica.gui.button.rvc_project.checkout_version", null),
        VIEW_CHANGES("litematica.gui.button.rvc_project.view_changes", null),
        REVERT_CHANGES("litematica.gui.button.rvc_project.revert_changes", null),
        CREATE_BRANCH("litematica.gui.button.rvc_project.create_branch", null),
        PROJECT_EDITOR("litematica.gui.button.rvc_project.project_editor", ButtonIcons.AREA_EDITOR),
        PROJECT_SETTINGS("litematica.gui.button.rvc_project.project_settings", ButtonIcons.CONFIGURATION),
        CLOSE_PROJECT("litematica.gui.button.rvc_project.close_project", null),
        LITEMATICA_MENU("litematica.gui.button.rvc_project.litematica_menu", null);

        private final String translationKey;
        @Nullable private final IGuiIcon icon;

        ButtonType(String translationKey, @Nullable IGuiIcon icon)
        {
            this.translationKey = translationKey;
            this.icon = icon;
        }

        private String getLabel(GuiRvcProject gui)
        {
            if (this == BRANCH_SELECTOR)
            {
                return StringUtils.translate(this.translationKey, gui.checkoutBranchName);
            }

            return StringUtils.translate(this.translationKey);
        }
    }

    private record ButtonListener(ButtonType type, GuiRvcProject gui) implements IButtonActionListener
    {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton)
        {
            switch (this.type)
            {
                case SAVE_VERSION -> this.gui.promptCommitMessage();
                case DISCARD_CHANGES -> this.gui.showNotImplemented("litematica.message.rvc_project.discard_changes_not_implemented");
                case CLEAR_AREA -> this.gui.showNotImplemented("litematica.message.rvc_project.clear_area_not_implemented");
                case PUSH -> this.gui.push();
                case PULL -> this.gui.promptPull();
                case BRANCH_SELECTOR -> this.gui.openBranchSelector();
                case CHECKOUT_VERSION -> this.gui.promptCheckoutSelectedCommit();
                case VIEW_CHANGES -> this.gui.scanChanges();
                case REVERT_CHANGES -> this.gui.showNotImplemented("litematica.message.rvc_project.revert_changes_not_implemented");
                case CREATE_BRANCH -> this.gui.showNotImplemented("litematica.message.rvc_project.create_branch_not_implemented");
                case PROJECT_EDITOR -> this.gui.updateAreas();
                case PROJECT_SETTINGS -> this.gui.promptRemoteEdit(false);
                case CLOSE_PROJECT -> GuiBase.openGui(new GuiRvcProjectManager());
                case LITEMATICA_MENU -> GuiBase.openGui(new GuiMainMenu());
            }
        }
    }

    private record HistorySearchListener(GuiRvcProject gui) implements ITextFieldListener<GuiTextFieldGeneric>
    {
        @Override
        public boolean onTextChange(GuiTextFieldGeneric textField)
        {
            this.gui.historySearchQuery = textField.getValueWrapper();
            this.gui.retainSelectedVisibleCommit();
            return false;
        }
    }

    private record RemoteUrlSetter(GuiRvcProject gui, boolean pushAfterSave) implements IStringConsumerFeedback
    {
        @Override
        public boolean setString(String remoteUrl)
        {
            try
            {
                RvcProjectService.setRemote(this.gui.repositoryDirectory, remoteUrl);
                this.gui.refreshRepositoryState();

                if (this.pushAfterSave)
                {
                    RvcProjectService.push(this.gui.repositoryDirectory);
                    this.gui.addMessage(MessageType.SUCCESS, "litematica.message.rvc_project.pushed");
                }
                else
                {
                    this.gui.initGui();
                    this.gui.addMessage(MessageType.SUCCESS, "litematica.message.rvc_project.remote_updated", remoteUrl.trim());
                }

                return true;
            }
            catch (Exception e)
            {
                this.gui.addMessage(MessageType.ERROR, this.pushAfterSave ? "litematica.error.rvc_project.push_failed" : "litematica.error.rvc_project.remote_failed", RvcProjectService.describeRemoteFailure(e));
                return false;
            }
        }
    }

    private record CommitMessageSetter(GuiRvcProject gui) implements IStringConsumerFeedback
    {
        @Override
        public boolean setString(String message)
        {
            if (message == null || message.isBlank())
            {
                this.gui.addMessage(MessageType.ERROR, "litematica.error.rvc_project.commit_failed", "Commit message must not be blank");
                return false;
            }

            this.gui.commitStoredSelectionWithCurrentSelectionFallback(message);
            return true;
        }
    }

    private record CheckoutMasterBeforeCommitListener(GuiRvcProject gui) implements IConfirmationListener
    {
        @Override
        public boolean onActionConfirmed()
        {
            this.gui.checkoutMasterAndPromptCommitMessage();
            return true;
        }

        @Override
        public boolean onActionCancelled()
        {
            return true;
        }
    }

    private record ResetAndCheckoutMasterBeforeCommitListener(GuiRvcProject gui) implements IConfirmationListener
    {
        @Override
        public boolean onActionConfirmed()
        {
            this.gui.resetWorkingTreeThenCheckoutMasterAndPromptCommitMessage();
            return true;
        }

        @Override
        public boolean onActionCancelled()
        {
            return true;
        }
    }

    private record ResetAndCheckoutBranchConfirmListener(GuiRvcProject gui) implements IConfirmationListener
    {
        @Override
        public boolean onActionConfirmed()
        {
            this.gui.resetWorkingTreeThenCheckoutBranch();
            return true;
        }

        @Override
        public boolean onActionCancelled()
        {
            return true;
        }
    }

    private record ResetAndPullConfirmListener(GuiRvcProject gui) implements IConfirmationListener
    {
        @Override
        public boolean onActionConfirmed()
        {
            this.gui.resetWorkingTreeThenPull();
            return true;
        }

        @Override
        public boolean onActionCancelled()
        {
            return true;
        }
    }

    private record PullConfirmListener(GuiRvcProject gui) implements IConfirmationListener
    {
        @Override
        public boolean onActionConfirmed()
        {
            this.gui.pull();
            return true;
        }

        @Override
        public boolean onActionCancelled()
        {
            return true;
        }
    }

    private record UpdateAreasConfirmListener(GuiRvcProject gui) implements IConfirmationListener
    {
        @Override
        public boolean onActionConfirmed()
        {
            this.gui.updateAreasFromCurrentSelection();
            return true;
        }

        @Override
        public boolean onActionCancelled()
        {
            return true;
        }
    }

    private record ResetAndCheckoutCommitConfirmListener(GuiRvcProject gui, RvcProjectService.CommitInfo commit) implements IConfirmationListener
    {
        @Override
        public boolean onActionConfirmed()
        {
            this.gui.resetWorkingTreeThenCheckoutCommit(this.commit);
            return true;
        }

        @Override
        public boolean onActionCancelled()
        {
            return true;
        }
    }

    private record CheckoutCommitConfirmListener(GuiRvcProject gui, RvcProjectService.CommitInfo commit) implements IConfirmationListener
    {
        @Override
        public boolean onActionConfirmed()
        {
            this.gui.checkoutCommit(this.commit);
            return true;
        }

        @Override
        public boolean onActionCancelled()
        {
            return true;
        }
    }
}
