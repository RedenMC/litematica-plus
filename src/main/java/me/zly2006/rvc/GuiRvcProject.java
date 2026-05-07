package me.zly2006.rvc;

import java.nio.file.Path;
import java.util.List;
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
import fi.dy.masa.malilib.interfaces.IStringConsumerFeedback;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.StringUtils;

public class GuiRvcProject extends GuiBase
{
    private final Path repositoryDirectory;
    private final String projectName;
    private List<RvcProjectService.CommitInfo> history = List.of();

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
        x += this.createButton(x, y, ButtonType.COMMIT);
        x += this.createButton(x, y, ButtonType.PUSH);
        x += this.createButton(x, y, ButtonType.PULL);

        String label = StringUtils.translate("litematica.gui.button.rvc_project.back_to_manager");
        int width = this.getStringWidth(label) + 20;
        this.addButton(new ButtonGeneric(this.getScreenWidth() - width - 12, y, width, 20, label), (button, mouseButton) -> GuiBase.openGui(new GuiRvcProjectManager()));
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);

        int x = 16;
        int y = 64;
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

    private void commitCurrentSelection()
    {
        GuiBase.openGui(new GuiTextInput(256, "litematica.gui.title.rvc_project.commit_message", "update schematic", this, new CommitMessageSetter(this)));
    }

    private void commitCurrentSelection(String message)
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

        AreaSelection selection = DataManager.getSelectionManager().getCurrentSelection();

        if (selection == null || selection.getAllSubRegionBoxes().isEmpty())
        {
            this.addMessage(MessageType.ERROR, "litematica.message.error.schematic_save_no_area_selected");
            return;
        }

        try
        {
            RvcPlayerIdentity identity = new RvcPlayerIdentity(player.getName().getString(), player.getUUID());
            RvcProjectService.commitCurrentSelection(this.repositoryDirectory, this.projectName, identity, world, selection, false, message);
            this.refreshHistory();
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
        try
        {
            String result = RvcProjectService.pull(this.repositoryDirectory);
            this.refreshHistory();
            this.addMessage(MessageType.SUCCESS, "litematica.message.rvc_project.pulled", result);
        }
        catch (Exception e)
        {
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project.pull_failed", e.getMessage());
        }
    }

    private enum ButtonType
    {
        COMMIT("litematica.gui.button.rvc_project.commit"),
        PUSH("litematica.gui.button.rvc_project.push"),
        PULL("litematica.gui.button.rvc_project.pull");

        private final String translationKey;

        ButtonType(String translationKey)
        {
            this.translationKey = translationKey;
        }
    }

    private record ButtonListener(ButtonType type, GuiRvcProject gui) implements IButtonActionListener
    {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton)
        {
            switch (this.type)
            {
                case COMMIT -> this.gui.commitCurrentSelection();
                case PUSH -> this.gui.push();
                case PULL -> this.gui.pull();
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

            this.gui.commitCurrentSelection(message);
            return true;
        }
    }
}
