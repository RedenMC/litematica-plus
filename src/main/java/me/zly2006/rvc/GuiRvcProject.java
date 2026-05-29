package me.zly2006.rvc;

import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.eclipse.jgit.revwalk.RevCommit;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.gui.GuiTextInput;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.interfaces.IConfirmationListener;
import fi.dy.masa.malilib.interfaces.ICompletionListener;
import fi.dy.masa.malilib.interfaces.IStringConsumerFeedback;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.StringUtils;

public class GuiRvcProject extends GuiBase implements ICompletionListener
{
    private static final int HISTORY_ROW_HEIGHT = 30;
    private static final int HISTORY_METADATA_Y_OFFSET = 12;
    private static final String SEMANTIC_CHECKOUT_UNSUPPORTED_KEY = "litematica.error.rvc_project.semantic_checkout_restore_unimplemented";
    private static final String SEMANTIC_PULL_UNSUPPORTED_KEY = "litematica.error.rvc_project.semantic_pull_restore_unimplemented";

    private final Path repositoryDirectory;
    private final String projectName;
    private List<RvcProjectService.CommitInfo> history = List.of();
    @Nullable private RvcProjectService.TrackingOverlay trackingOverlay;
    @Nullable private String remoteUrl;
    private String trackingStatus = "";
    private boolean detachedHead;
    private String checkoutBranchName = RvcProjectService.DEFAULT_BRANCH;

    public GuiRvcProject(Path repositoryDirectory, String projectName)
    {
        this.repositoryDirectory = repositoryDirectory;
        this.projectName = projectName;
        this.title = StringUtils.translate("litematica.gui.title.rvc_project", projectName);
    }

    @Override
    public void initGui()
    {
        super.initGui();
        this.refreshRepositoryState();
        this.refreshHistory();

        int x = 12;
        int y = 28;
        x += this.createButton(x, y, ButtonType.UPDATE_AREAS);
        x += this.createButton(x, y, ButtonType.COMMIT);
        x += this.createButton(x, y, ButtonType.REMOTE);
        x += this.createButton(x, y, ButtonType.PUSH);
        x += this.createButton(x, y, ButtonType.PULL);

        String label = StringUtils.translate("litematica.gui.button.rvc_project.back_to_manager");
        int width = this.getStringWidth(label) + 20;
        this.addButton(new ButtonGeneric(this.getScreenWidth() - width - 12, y, width, 20, label), (button, mouseButton) -> GuiBase.openGui(new GuiRvcProjectManager()));

        this.createDetachedHeadButton();
        this.createHistoryButtons();
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);

        int x = 16;
        int y = this.getHistoryStartY();

        ctx.drawString(ctx.fontRenderer(), this.getRemoteDisplayText(), x, 54, 0xFFB7C5D8, false);

        if (!this.trackingStatus.isBlank())
        {
            ctx.drawString(ctx.fontRenderer(), this.trackingStatus, x, 68, 0xFF9FE870, false);
        }

        if (this.detachedHead)
        {
            ctx.drawString(ctx.fontRenderer(), StringUtils.translate("litematica.gui.label.rvc_project.detached_head"), x, 82, 0xFFFFB45C, false);
        }

        ctx.drawString(ctx.fontRenderer(), StringUtils.translate("litematica.gui.label.rvc_project.history"), x, y - 16, 0xFFFFFFFF, false);

        if (this.history.isEmpty())
        {
            ctx.drawString(ctx.fontRenderer(), StringUtils.translate("litematica.gui.label.rvc_project.history_empty"), x, y, 0xFFAAAAAA, false);
            return;
        }

        int maxY = this.getScreenHeight() - HISTORY_ROW_HEIGHT;
        int metadataX = x + 76;
        int metadataMaxWidth = Math.max(40, this.getHistoryActionsStartX() - metadataX - 8);

        for (RvcProjectService.CommitInfo commit : this.history)
        {
            if (y > maxY)
            {
                break;
            }

            ctx.drawString(ctx.fontRenderer(), commit.shortId(), x, y, 0xFFFFD36A, false);
            ctx.drawString(ctx.fontRenderer(), commit.message(), x + 76, y, 0xFFFFFFFF, false);
            ctx.drawString(ctx.fontRenderer(), this.ellipsizeToWidth(commit.author() + "  " + commit.time(), metadataMaxWidth), metadataX, y + HISTORY_METADATA_Y_OFFSET, 0xFFAAAAAA, false);
            y += HISTORY_ROW_HEIGHT;
        }
    }

    private int createButton(int x, int y, ButtonType type)
    {
        String label = StringUtils.translate(type.translationKey);
        int width = this.getStringWidth(label) + 20;
        this.addButton(new ButtonGeneric(x, y, width, 20, label), new ButtonListener(type, this));
        return width + 4;
    }

    private void createHistoryButtons()
    {
        int y = this.getHistoryStartY() - 4;
        int maxY = this.getScreenHeight() - HISTORY_ROW_HEIGHT;
        int checkoutWidth = this.getStringWidth(StringUtils.translate("litematica.gui.button.rvc_project.checkout")) + 16;
        int inspectWidth = this.getStringWidth(StringUtils.translate("litematica.gui.button.rvc_project.inspect")) + 16;
        int checkoutX = this.getScreenWidth() - checkoutWidth - 12;
        int inspectX = this.getHistoryActionsStartX();

        for (RvcProjectService.CommitInfo commit : this.history)
        {
            if (y > maxY)
            {
                break;
            }

            this.addButton(new ButtonGeneric(inspectX, y - 4, inspectWidth, 18, StringUtils.translate("litematica.gui.button.rvc_project.inspect")), new HistoryButtonListener(HistoryAction.INSPECT, commit, this));
            this.addButton(new ButtonGeneric(checkoutX, y - 4, checkoutWidth, 18, StringUtils.translate("litematica.gui.button.rvc_project.checkout")), new HistoryButtonListener(HistoryAction.CHECKOUT, commit, this));
            y += HISTORY_ROW_HEIGHT;
        }
    }

    private int getHistoryActionsStartX()
    {
        int checkoutWidth = this.getStringWidth(StringUtils.translate("litematica.gui.button.rvc_project.checkout")) + 16;
        int inspectWidth = this.getStringWidth(StringUtils.translate("litematica.gui.button.rvc_project.inspect")) + 16;
        int checkoutX = this.getScreenWidth() - checkoutWidth - 12;
        return checkoutX - inspectWidth - 4;
    }

    private void createDetachedHeadButton()
    {
        if (!this.detachedHead)
        {
            return;
        }

        String label = StringUtils.translate("litematica.gui.button.rvc_project.checkout_branch", this.checkoutBranchName);
        String message = StringUtils.translate("litematica.gui.label.rvc_project.detached_head");
        int width = this.getStringWidth(label) + 16;
        int x = Math.min(16 + this.getStringWidth(message) + 8, this.getScreenWidth() - width - 12);
        this.addButton(new ButtonGeneric(x, 76, width, 18, label), new ButtonListener(ButtonType.CHECKOUT_BRANCH, this));
    }

    private int getHistoryStartY()
    {
        return this.detachedHead ? 114 : 96;
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

            if (this.detachedHead)
            {
                this.checkoutBranchName = RvcProjectService.preferredCheckoutBranchName(this.repositoryDirectory);
            }
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
        }
        catch (Exception e)
        {
            this.history = List.of();
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.history_failed", e.getMessage());
        }
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
        this.addMessage(MessageType.INFO, "litematica.message.rvc_project.update_areas_todo");
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

    private void inspectCommit(RvcProjectService.CommitInfo commit)
    {
        this.addMessage(MessageType.INFO, "litematica.message.rvc_project.inspect_commit", commit.shortId(), commit.message());
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

    private enum ButtonType
    {
        UPDATE_AREAS("litematica.gui.button.rvc_project.update_areas"),
        COMMIT("litematica.gui.button.rvc_project.commit"),
        REMOTE("litematica.gui.button.rvc_project.remote"),
        PUSH("litematica.gui.button.rvc_project.push"),
        PULL("litematica.gui.button.rvc_project.pull"),
        CHECKOUT_BRANCH("litematica.gui.button.rvc_project.checkout_branch");

        private final String translationKey;

        ButtonType(String translationKey)
        {
            this.translationKey = translationKey;
        }
    }

    private enum HistoryAction
    {
        INSPECT,
        CHECKOUT
    }

    private record ButtonListener(ButtonType type, GuiRvcProject gui) implements IButtonActionListener
    {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton)
        {
            switch (this.type)
            {
                case UPDATE_AREAS -> this.gui.updateAreas();
                case COMMIT -> this.gui.promptCommitMessage();
                case REMOTE -> this.gui.promptRemoteEdit(false);
                case PUSH -> this.gui.push();
                case PULL -> this.gui.promptPull();
                case CHECKOUT_BRANCH -> this.gui.promptCheckoutBranch();
            }
        }
    }

    private record HistoryButtonListener(HistoryAction action, RvcProjectService.CommitInfo commit, GuiRvcProject gui) implements IButtonActionListener
    {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton)
        {
            switch (this.action)
            {
                case INSPECT -> this.gui.inspectCommit(this.commit);
                case CHECKOUT -> this.gui.promptCheckoutCommit(this.commit);
            }
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
