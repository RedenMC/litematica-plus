package me.zly2006.rvc;

import java.nio.file.Path;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextInput;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.interfaces.ICompletionListener;
import fi.dy.masa.malilib.interfaces.IStringConsumerFeedback;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.StringUtils;

public class GuiRvcProject extends GuiBase implements ICompletionListener
{
    private final Path repositoryDirectory;
    private final String projectName;
    private List<RvcProjectService.CommitInfo> history = List.of();
    @Nullable private RvcProjectService.TrackingOverlay trackingOverlay;
    private String trackingStatus = "";

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
        this.refreshHistory();

        int x = 12;
        int y = 28;
        x += this.createButton(x, y, ButtonType.UPDATE_AREAS);
        x += this.createButton(x, y, ButtonType.COMMIT);
        x += this.createButton(x, y, ButtonType.PUSH);
        x += this.createButton(x, y, ButtonType.PULL);

        String label = StringUtils.translate("litematica.gui.button.rvc_project.back_to_manager");
        int width = this.getStringWidth(label) + 20;
        this.addButton(new ButtonGeneric(this.getScreenWidth() - width - 12, y, width, 20, label), (button, mouseButton) -> GuiBase.openGui(new GuiRvcProjectManager()));

        this.createHistoryButtons();
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);

        int x = 16;
        int y = 78;

        if (this.trackingStatus.isBlank() == false)
        {
            ctx.drawString(ctx.fontRenderer(), this.trackingStatus, x, 54, 0xFF9FE870, false);
        }

        ctx.drawString(ctx.fontRenderer(), StringUtils.translate("litematica.gui.label.rvc_project.history"), x, y - 16, 0xFFFFFFFF, false);

        if (this.history.isEmpty())
        {
            ctx.drawString(ctx.fontRenderer(), StringUtils.translate("litematica.gui.label.rvc_project.history_empty"), x, y, 0xFFAAAAAA, false);
            return;
        }

        int maxY = this.getScreenHeight() - 18;

        for (RvcProjectService.CommitInfo commit : this.history)
        {
            if (y > maxY)
            {
                break;
            }

            ctx.drawString(ctx.fontRenderer(), commit.shortId(), x, y, 0xFFFFD36A, false);
            ctx.drawString(ctx.fontRenderer(), commit.message(), x + 76, y, 0xFFFFFFFF, false);
            ctx.drawString(ctx.fontRenderer(), commit.author() + "  " + commit.time(), x + 280, y, 0xFFAAAAAA, false);
            y += 18;
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
        int y = 74;
        int maxY = this.getScreenHeight() - 18;
        int checkoutWidth = this.getStringWidth(StringUtils.translate("litematica.gui.button.rvc_project.checkout")) + 16;
        int inspectWidth = this.getStringWidth(StringUtils.translate("litematica.gui.button.rvc_project.inspect")) + 16;
        int checkoutX = this.getScreenWidth() - checkoutWidth - 12;
        int inspectX = checkoutX - inspectWidth - 4;

        for (RvcProjectService.CommitInfo commit : this.history)
        {
            if (y > maxY)
            {
                break;
            }

            this.addButton(new ButtonGeneric(inspectX, y - 4, inspectWidth, 18, StringUtils.translate("litematica.gui.button.rvc_project.inspect")), new HistoryButtonListener(HistoryAction.INSPECT, commit, this));
            this.addButton(new ButtonGeneric(checkoutX, y - 4, checkoutWidth, 18, StringUtils.translate("litematica.gui.button.rvc_project.checkout")), new HistoryButtonListener(HistoryAction.CHECKOUT, commit, this));
            y += 18;
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
        GuiBase.openGui(new GuiTextInput(256, "litematica.gui.title.rvc_project.commit_message", "update schematic", this, new CommitMessageSetter(this)));
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
            RvcProjectService.commitStoredSelectionWithCurrentSelectionFallback(this.repositoryDirectory, this.projectName, identity, world, selectionFallback, false, message);
            this.loadTrackingOverlay();
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
            if (RvcProjectService.hasRemote(this.repositoryDirectory) == false)
            {
                GuiBase.openGui(new GuiTextInput(512, "litematica.gui.title.rvc_project.remote_url", RvcProjectService.DEFAULT_REMOTE_URL, this, new RemoteUrlSetter(this)));
                return;
            }

            RvcProjectService.push(this.repositoryDirectory);
            this.addMessage(MessageType.SUCCESS, "litematica.message.rvc_project.pushed");
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.push_failed", e.getMessage());
        }
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
            this.trackingOverlay = RvcProjectService.restoreWorkingTreeToGame(this.repositoryDirectory, this.projectName, minecraft.level, minecraft.level, this).overlay();
            this.updateTrackingStatusAfterRestore();
            this.initGui();
            this.addMessage(MessageType.SUCCESS, "litematica.message.rvc_project.pulled", result);
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

    private void loadTrackingOverlay()
    {
        try
        {
            if (this.trackingOverlay != null)
            {
                DataManager.getSchematicPlacementManager().removeSchematicPlacement(this.trackingOverlay.placement(), false);
            }

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

    private void inspectCommit(RvcProjectService.CommitInfo commit)
    {
        this.addMessage(MessageType.INFO, "litematica.message.rvc_project.inspect_commit", commit.shortId(), commit.message());
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
            RvcProjectService.GameRestore restore = RvcProjectService.checkoutCommitToGame(this.repositoryDirectory, this.projectName, commit.id(), minecraft.level, minecraft.level, this);
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
        PUSH("litematica.gui.button.rvc_project.push"),
        PULL("litematica.gui.button.rvc_project.pull");

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
                case PUSH -> this.gui.push();
                case PULL -> this.gui.pull();
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
                case CHECKOUT -> this.gui.checkoutCommit(this.commit);
            }
        }
    }

    private record RemoteUrlSetter(GuiRvcProject gui) implements IStringConsumerFeedback
    {
        @Override
        public boolean setString(String remoteUrl)
        {
            try
            {
                RvcProjectService.setRemote(this.gui.repositoryDirectory, remoteUrl);
                RvcProjectService.push(this.gui.repositoryDirectory);
                this.gui.addMessage(MessageType.SUCCESS, "litematica.message.rvc_project.pushed");
                return true;
            }
            catch (Exception e)
            {
                this.gui.addMessage(MessageType.ERROR, "litematica.error.rvc_project.push_failed", e.getMessage());
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
}
