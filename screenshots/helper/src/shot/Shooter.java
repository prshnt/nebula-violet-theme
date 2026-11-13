package shot;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.ui.TargetUIType;
import com.intellij.ide.ui.ThemeListProvider;
import com.intellij.ide.ui.laf.UiThemeProviderListManager;
import com.intellij.openapi.editor.colors.Groups;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationsManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JRootPane;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Sandbox-only helper: once the demo project is open and indexed, paints the IDE frame into a
 * PNG for each theme listed in -Dshot.themes (id=fileName,...), then exits the IDE.
 * Painting the Swing tree directly needs no macOS screen-recording permission.
 */
public final class Shooter implements AppLifecycleListener {

    private static final int WIDTH = 1440;
    private static final int HEIGHT = 900;
    private static final double SCALE = 2.0;

    @Override
    public void appStarted() {
        if (System.getProperty("shot.out") == null) {
            return;
        }
        Thread thread = new Thread(Shooter::run, "nebula-screenshot");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run() {
        Path out = Path.of(System.getProperty("shot.out"));
        try {
            Files.createDirectories(out);
            Project project = waitForProject();
            edt(() -> dumpThemeList(out.resolve("theme-list.txt")));
            DumbService.getInstance(project).waitForSmartMode();
            edt(() -> prepare(project));
            Thread.sleep(6000);

            for (String entry : System.getProperty("shot.themes").split(",")) {
                String[] parts = entry.split("=", 2);
                edt(() -> applyTheme(parts[0]));
                // Switching between Islands and classic themes rebuilds the frame.
                waitForFrame(project);
                edt(() -> prepare(project));
                edt(() -> dismissNotifications(project));
                Thread.sleep(4000);
                edt(() -> capture(project, out.resolve(parts[1] + ".png").toFile()));
            }
        } catch (Throwable t) {
            StringWriter trace = new StringWriter();
            t.printStackTrace(new PrintWriter(trace));
            try {
                Files.writeString(out.resolve("error.txt"), trace.toString());
            } catch (Exception ignored) {
                // nothing else to report to
            }
        } finally {
            ApplicationManager.getApplication().invokeLater(
                    () -> ApplicationManager.getApplication().exit(true, true, false));
        }
    }

    /** Records what Settings | Appearance | Theme offers, so a missing theme shows up in review. */
    private static void dumpThemeList(Path target) throws Exception {
        StringBuilder text = new StringBuilder("newUI=" + ExperimentalUI.Companion.isNewUI() + "\n");
        for (Groups.GroupInfo<UIThemeLookAndFeelInfo> group :
                ThemeListProvider.Companion.getInstance().getShownThemes().getInfos()) {
            text.append("group ").append(group.getTitle()).append('\n');
            for (UIThemeLookAndFeelInfo theme : group.getItems()) {
                text.append("  ").append(theme.getId()).append(" | ").append(theme.getName()).append('\n');
            }
        }
        UiThemeProviderListManager themes = UiThemeProviderListManager.Companion.getInstance();
        for (TargetUIType type : TargetUIType.values()) {
            text.append("targetUi ").append(type).append(':');
            themes.getThemeListForTargetUI(type).iterator()
                    .forEachRemaining(theme -> text.append(' ').append(theme.getId()));
            text.append('\n');
        }
        Files.writeString(target, text);
    }

    private static Project waitForProject() throws InterruptedException {
        while (true) {
            Project[] open = ProjectManager.getInstance().getOpenProjects();
            if (open.length > 0 && WindowManager.getInstance().getFrame(open[0]) != null) {
                return open[0];
            }
            Thread.sleep(500);
        }
    }

    private static void waitForFrame(Project project) throws InterruptedException {
        while (WindowManager.getInstance().getFrame(project) == null) {
            Thread.sleep(250);
        }
    }

    private static void prepare(Project project) {
        JFrame frame = WindowManager.getInstance().getFrame(project);
        frame.setExtendedState(JFrame.NORMAL);
        frame.setBounds(60, 60, WIDTH, HEIGHT);

        ToolWindow projectView = ToolWindowManager.getInstance(project).getToolWindow("Project");
        if (projectView != null) {
            projectView.show();
        }

        String filePath = System.getProperty("shot.file");
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath);
        if (file != null) {
            int line = Integer.getInteger("shot.line", 0);
            FileEditorManager.getInstance(project)
                    .openTextEditor(new OpenFileDescriptor(project, file, line, 8), true);
            ProjectView.getInstance(project).select(null, file, false);
        }
    }

    private static void applyTheme(String themeId) {
        LafManager lafManager = LafManager.getInstance();
        UIThemeLookAndFeelInfo laf = lafManager.findLaf(themeId);
        if (laf == null) {
            throw new IllegalStateException("Theme not found: " + themeId);
        }
        lafManager.setCurrentLookAndFeel(laf, false);
        lafManager.updateUI();

        // "Nebula Violet Colorblind Islands" uses the "Nebula Violet Colorblind" scheme. Match the
        // display name: the stored name of a bundled scheme can carry an internal "_@user_" prefix.
        String schemeName = laf.getName().replace(" Islands", "");
        EditorColorsManager colors = EditorColorsManager.getInstance();
        for (EditorColorsScheme scheme : colors.getAllSchemes()) {
            if (scheme.getDisplayName().equals(schemeName)) {
                colors.setGlobalScheme(scheme);
                return;
            }
        }
        throw new IllegalStateException("Editor scheme not found: " + schemeName);
    }

    private static void dismissNotifications(Project project) {
        for (Notification notification : NotificationsManager.getNotificationsManager()
                .getNotificationsOfType(Notification.class, project)) {
            notification.expire();
        }
    }

    private static void capture(Project project, File target) throws Exception {
        JRootPane root = root(project);
        BufferedImage image = new BufferedImage(
                (int) (root.getWidth() * SCALE), (int) (root.getHeight() * SCALE), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.scale(SCALE, SCALE);
        root.paint(g);
        g.dispose();
        ImageIO.write(image, "png", target);
    }

    private static JRootPane root(Project project) {
        return WindowManager.getInstance().getFrame(project).getRootPane();
    }

    private static void edt(ThrowingRunnable action) throws Exception {
        Exception[] failure = new Exception[1];
        ApplicationManager.getApplication().invokeAndWait(() -> {
            try {
                action.run();
            } catch (Exception e) {
                failure[0] = e;
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
