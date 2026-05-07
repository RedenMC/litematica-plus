package me.zly2006.rvc;

import java.util.List;
import net.minecraft.client.Minecraft;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.StringUtils;

public class GuiRvcProjectManager extends GuiBase
{
    private List<RvcProjectService.Project> projects = List.of();

    public GuiRvcProjectManager()
    {
        this.title = StringUtils.translate("litematica.gui.title.rvc_project_manager");
    }

    @Override
    public void initGui()
    {
        super.initGui();
        this.refreshProjects();

        int x = 12;
        int y = 32;
        int openWidth = this.getStringWidth(StringUtils.translate("litematica.gui.button.rvc_project.open")) + 16;

        for (RvcProjectService.Project project : this.projects)
        {
            ButtonGeneric button = new ButtonGeneric(this.getScreenWidth() - openWidth - 12, y - 4, openWidth, 20, StringUtils.translate("litematica.gui.button.rvc_project.open"));
            this.addButton(button, new ButtonListenerOpen(project));
            y += 24;
        }

        String label = StringUtils.translate("litematica.gui.button.change_menu.to_main_menu");
        int width = this.getStringWidth(label) + 20;
        this.addButton(new ButtonGeneric(x, this.getScreenHeight() - 26, width, 20, label), (button, mouseButton) -> GuiBase.openGui(new fi.dy.masa.litematica.gui.GuiMainMenu()));
    }

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks)
    {
        super.drawContents(ctx, mouseX, mouseY, partialTicks);

        int x = 16;
        int y = 34;

        if (this.projects.isEmpty())
        {
            ctx.drawString(ctx.fontRenderer(), StringUtils.translate("litematica.gui.label.rvc_project_manager.empty"), x, y, 0xFFAAAAAA, false);
            return;
        }

        for (RvcProjectService.Project project : this.projects)
        {
            ctx.drawString(ctx.fontRenderer(), project.name(), x, y, 0xFFFFFFFF, false);
            ctx.drawString(ctx.fontRenderer(), project.directory().toString(), x + 140, y, 0xFFAAAAAA, false);
            y += 24;
        }
    }

    private void refreshProjects()
    {
        try
        {
            this.projects = RvcProjectService.listProjects(Minecraft.getInstance().gameDirectory.toPath());
        }
        catch (Exception e)
        {
            this.projects = List.of();
            this.addMessage(MessageType.ERROR, "litematica.error.rvc_project_manager.list_failed", e.getMessage());
        }
    }

    private record ButtonListenerOpen(RvcProjectService.Project project) implements IButtonActionListener
    {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton)
        {
            GuiBase.openGui(new GuiRvcProject(this.project.directory(), this.project.name()));
        }
    }
}
